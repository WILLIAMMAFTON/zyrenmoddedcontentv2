package net.zyren.cultivation.data;

import net.minecraft.world.entity.player.Player;

import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables;
import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables.PlayerVariables;

import java.util.ArrayList;
import java.util.List;

/**
 * The ONLY class that reads or writes the sub-aspect player variables (sibling of {@link AspectData}).
 *
 * Variables (MCreator, player_persistent; copied on respawn and synced to the client by MCreator):
 *   aspect_1_sub_learned .. aspect_4_sub_learned   String  comma-separated sub-aspect ids ("" = none)
 *   aspect_1_sub_equipped .. aspect_4_sub_equipped String  one sub-aspect id ("" = none)
 *   pure_sub_learned, pure_sub_equipped            String  same, for Pure (Pure has no slot)
 *   sub_form_active                                String  "1" = the ACTIVE aspect is in its sub-aspect form ("" = normal)
 *   pending_sub_learned                            String  comma-separated sub-aspects learned for aspects the
 *                                                          player doesn't own yet (moved into the slot when gained)
 *
 * Rules kept here so no caller can break them:
 *   - a learned sub-aspect always belongs to the aspect in that slot (cleared when the slot's
 *     aspect changes: {@link AspectData#setSlot} calls {@link #clearSlot});
 *   - the equipped sub-aspect is always one of the learned ones (or none);
 *   - the form flag is only on while the active aspect has an equipped sub-aspect;
 *   - pending sub-aspects never belong to an owned aspect (they move into its slot when gained).
 * Level/tier requirements are NOT checked here (that's acquisition / use logic); data stays
 * valid whatever the config says, so changing the config never deletes anything.
 *
 * Internally "slot 0" = Pure, slots 1..4 = the aspect slots.
 */
public final class SubAspectData {
	private final Player player;
	private final PlayerVariables vars;

	private SubAspectData(Player player) {
		this.player = player;
		this.vars = player.getData(ZyrenmoddedcontentV2ModVariables.PLAYER_VARIABLES);
	}

	public static SubAspectData of(Player player) {
		return new SubAspectData(player);
	}

	// ---------------------------------------------------------------- queries

	/** Learned sub-aspects of an owned aspect (declaration order). Empty if the aspect isn't owned. */
	public List<SubAspectType> learned(AspectType aspect) {
		int slot = slotOf(aspect);
		return slot < 0 ? new ArrayList<>() : parse(rawLearned(slot), aspect);
	}

	/** Sub-aspects learned (from a book) for aspects the player doesn't own yet. */
	public List<SubAspectType> pending() {
		return parseAny(clean(vars.pending_sub_learned));
	}

	/** Learned OR waiting for its aspect. Books refuse anything the player already knows. */
	public boolean knows(SubAspectType sub) {
		return hasLearned(sub) || pending().contains(sub);
	}

	/**
	 * Remembers a sub-aspect of an aspect the player doesn't own; it is learned automatically when
	 * they gain that aspect. @return false if the aspect IS owned (use learn) or it's already pending.
	 */
	public boolean addPending(SubAspectType sub) {
		if (sub == null || slotOf(sub.parent()) >= 0)
			return false;
		List<SubAspectType> list = pending();
		if (list.contains(sub))
			return false;
		list.add(sub);
		vars.pending_sub_learned = join(list);
		dirty();
		return true;
	}

	/** Drops pending sub-aspects of one aspect (null = all). Admin correction. @return how many were removed */
	public int clearPending(AspectType aspect) {
		List<SubAspectType> list = pending();
		int before = list.size();
		list.removeIf(s -> aspect == null || s.parent() == aspect);
		if (list.size() != before) {
			vars.pending_sub_learned = join(list);
			dirty();
		}
		return before - list.size();
	}

	/** Removes one pending sub-aspect. @return false if it wasn't pending */
	public boolean unlearnPending(SubAspectType sub) {
		List<SubAspectType> list = pending();
		if (!list.remove(sub))
			return false;
		vars.pending_sub_learned = join(list);
		dirty();
		return true;
	}

	public boolean hasLearned(SubAspectType sub) {
		return sub != null && learned(sub.parent()).contains(sub);
	}

	/** The equipped sub-aspect of an owned aspect, or null. */
	public SubAspectType equipped(AspectType aspect) {
		int slot = slotOf(aspect);
		if (slot < 0)
			return null;
		SubAspectType sub = SubAspectType.fromId(rawEquipped(slot));
		return sub != null && sub.parent() == aspect ? sub : null;
	}

	/** True if the active aspect is currently in its sub-aspect form. */
	public boolean isFormActive() {
		return formOn(vars);
	}

	/** The sub-aspect whose form is in use right now (active aspect, form on, equipped), or null. */
	public SubAspectType activeForm() {
		if (!isFormActive())
			return null;
		AspectType active = AspectData.of(player).getActive();
		return active == null ? null : equipped(active);
	}

	// ---------------------------------------------------------------- changes

	/** Adds a learned sub-aspect. @return false if its aspect isn't owned or it was already learned. */
	public boolean learn(SubAspectType sub) {
		if (sub == null)
			return false;
		int slot = slotOf(sub.parent());
		if (slot < 0)
			return false;
		List<SubAspectType> list = parse(rawLearned(slot), sub.parent());
		if (list.contains(sub))
			return false;
		list.add(sub);
		setRawLearned(slot, join(list));
		return true;
	}

	/** Removes a learned sub-aspect (and unequips it). @return false if it wasn't learned. */
	public boolean unlearn(SubAspectType sub) {
		if (sub == null)
			return false;
		int slot = slotOf(sub.parent());
		if (slot < 0)
			return false;
		List<SubAspectType> list = parse(rawLearned(slot), sub.parent());
		if (!list.remove(sub))
			return false;
		setRawLearned(slot, join(list));
		if (equipped(sub.parent()) == sub)
			equip(sub.parent(), null);
		return true;
	}

	/**
	 * Equips a learned sub-aspect, or unequips with null. Unequipping the active aspect's
	 * sub-aspect also turns the form off.
	 *
	 * @return false if the aspect isn't owned or the sub-aspect isn't learned / doesn't belong to it
	 */
	public boolean equip(AspectType aspect, SubAspectType sub) {
		int slot = slotOf(aspect);
		if (slot < 0)
			return false;
		if (sub != null && (sub.parent() != aspect || !hasLearned(sub)))
			return false;
		setRawEquipped(slot, sub == null ? "" : sub.id());
		if (aspect == AspectData.of(player).getActive() && (sub == null || isFormActive()))
			setFormActive(false); // changing or removing the equipped sub always drops back to normal form
		return true;
	}

	/** Turns the form flag on/off. Turning it on is refused (false) without an equipped sub-aspect. */
	public boolean setFormActive(boolean on) {
		if (on) {
			AspectType active = AspectData.of(player).getActive();
			if (active == null || equipped(active) == null)
				return false;
		}
		if (formOn(vars) != on) {
			vars.sub_form_active = on ? FORM_ON : "";
			dirty();
		}
		return true;
	}

	/** Forgets every sub-aspect of one owned aspect (admin correction). */
	public void clearAspect(AspectType aspect) {
		int slot = slotOf(aspect);
		if (slot < 0)
			return;
		clearSlot(vars, slot);
		if (aspect == AspectData.of(player).getActive())
			setFormActive(false);
	}

	/**
	 * Login clean-up: strips quote marks / unknown ids / sub-aspects that don't belong to the
	 * slot's aspect, unequips anything not learned, and turns the form off if it can't be on.
	 */
	public void normalize() {
		AspectData a = AspectData.of(player);
		for (int slot = 0; slot <= AspectData.SLOT_COUNT; slot++) {
			AspectType owner = slot == 0 ? AspectType.PURE : a.getSlotType(slot);
			if (owner == null) {
				if (!rawLearned(slot).isEmpty() || !rawEquipped(slot).isEmpty())
					clearSlot(vars, slot);
				continue;
			}
			List<SubAspectType> list = parse(rawLearned(slot), owner);
			String cleanLearned = join(list);
			if (!cleanLearned.equals(rawLearned(slot)))
				setRawLearned(slot, cleanLearned);
			SubAspectType eq = SubAspectType.fromId(rawEquipped(slot));
			String cleanEquipped = eq != null && list.contains(eq) ? eq.id() : "";
			if (!cleanEquipped.equals(rawEquipped(slot)))
				setRawEquipped(slot, cleanEquipped);
		}
		if (isFormActive() && activeForm() == null)
			setFormActive(false);
		// Pending list: drop unknown ids; anything whose aspect is owned now moves into its slot.
		List<SubAspectType> pend = pending();
		List<SubAspectType> keep = new ArrayList<>();
		for (SubAspectType s : pend) {
			if (slotOf(s.parent()) >= 0)
				learn(s);
			else
				keep.add(s);
		}
		String cleanPending = join(keep);
		if (!cleanPending.equals(clean(vars.pending_sub_learned))) {
			vars.pending_sub_learned = cleanPending;
			dirty();
		}
	}

	// ---------------------------------------------------------------- package hooks (AspectData)

	/** A slot (1..4) just received {@code type}: pending sub-aspects of that aspect become learned there. */
	static void applyPending(PlayerVariables vars, int slot, AspectType type) {
		List<SubAspectType> pend = parseAny(clean(vars.pending_sub_learned));
		List<SubAspectType> moving = new ArrayList<>();
		for (SubAspectType s : pend)
			if (s.parent() == type)
				moving.add(s);
		if (moving.isEmpty())
			return;
		pend.removeAll(moving);
		List<SubAspectType> learned = parse(clean(raw(vars, slot, true)), type);
		for (SubAspectType s : moving)
			if (!learned.contains(s))
				learned.add(s);
		set(vars, slot, true, join(learned));
		vars.pending_sub_learned = join(pend);
		vars.markSyncDirty();
	}

	/** The aspect in a slot (1..4) changed or was removed: its sub-aspects go with it. */
	static void clearSlot(PlayerVariables vars, int slot) {
		boolean changed = !clean(raw(vars, slot, true)).isEmpty() || !clean(raw(vars, slot, false)).isEmpty();
		set(vars, slot, true, "");
		set(vars, slot, false, "");
		if (changed)
			vars.markSyncDirty();
	}

	/** Full reset: every slot, Pure, and the form flag. */
	static void clearAll(PlayerVariables vars) {
		for (int slot = 0; slot <= AspectData.SLOT_COUNT; slot++) {
			set(vars, slot, true, "");
			set(vars, slot, false, "");
		}
		vars.sub_form_active = "";
		vars.pending_sub_learned = "";
		vars.markSyncDirty();
	}

	/** The active aspect changed: always start in normal form. */
	static void formOff(PlayerVariables vars) {
		if (formOn(vars)) {
			vars.sub_form_active = "";
			vars.markSyncDirty();
		}
	}

	// ---------------------------------------------------------------- internals

	private static final String FORM_ON = "1";

	/** sub_form_active is a String variable: "1" = on; "", "\"\"" (MCreator default) or anything else = off. */
	private static boolean formOn(PlayerVariables v) {
		return FORM_ON.equals(clean(v.sub_form_active));
	}

	/** 0 for Pure, 1..4 for an owned slot aspect, -1 if not owned. */
	private int slotOf(AspectType aspect) {
		if (aspect == null)
			return -1;
		if (aspect == AspectType.PURE)
			return 0;
		int s = AspectData.of(player).findSlot(aspect);
		return s == 0 ? -1 : s;
	}

	private static List<SubAspectType> parse(String raw, AspectType owner) {
		List<SubAspectType> list = new ArrayList<>();
		if (raw.isEmpty())
			return list;
		for (String part : raw.split(",")) {
			SubAspectType s = SubAspectType.fromId(part);
			if (s != null && s.parent() == owner && !list.contains(s))
				list.add(s);
		}
		list.sort(null);
		return list;
	}

	/** Any valid sub-aspect ids (no owner filter), de-duplicated, declaration order. */
	private static List<SubAspectType> parseAny(String raw) {
		List<SubAspectType> list = new ArrayList<>();
		if (raw.isEmpty())
			return list;
		for (String part : raw.split(",")) {
			SubAspectType s = SubAspectType.fromId(part);
			if (s != null && !list.contains(s))
				list.add(s);
		}
		list.sort(null);
		return list;
	}

	private static String join(List<SubAspectType> list) {
		List<SubAspectType> sorted = new ArrayList<>(list);
		sorted.sort(null); // declaration order, so the stored string is stable
		StringBuilder sb = new StringBuilder();
		for (SubAspectType s : sorted)
			sb.append(sb.length() == 0 ? "" : ",").append(s.id());
		return sb.toString();
	}

	private String rawLearned(int slot) {
		return clean(raw(vars, slot, true));
	}

	private String rawEquipped(int slot) {
		return clean(raw(vars, slot, false));
	}

	private void setRawLearned(int slot, String value) {
		set(vars, slot, true, value);
		dirty();
	}

	private void setRawEquipped(int slot, String value) {
		set(vars, slot, false, value);
		dirty();
	}

	/** MCreator stores an empty String default as two quote marks. */
	private static String clean(String s) {
		return s == null ? "" : s.replace("\"", "").trim();
	}

	private static String raw(PlayerVariables v, int slot, boolean learned) {
		return switch (slot) {
			case 0 -> learned ? v.pure_sub_learned : v.pure_sub_equipped;
			case 1 -> learned ? v.aspect_1_sub_learned : v.aspect_1_sub_equipped;
			case 2 -> learned ? v.aspect_2_sub_learned : v.aspect_2_sub_equipped;
			case 3 -> learned ? v.aspect_3_sub_learned : v.aspect_3_sub_equipped;
			case 4 -> learned ? v.aspect_4_sub_learned : v.aspect_4_sub_equipped;
			default -> throw new IllegalArgumentException("Sub-aspect slot must be 0..4, got " + slot);
		};
	}

	private static void set(PlayerVariables v, int slot, boolean learned, String value) {
		switch (slot) {
			case 0 -> { if (learned) v.pure_sub_learned = value; else v.pure_sub_equipped = value; }
			case 1 -> { if (learned) v.aspect_1_sub_learned = value; else v.aspect_1_sub_equipped = value; }
			case 2 -> { if (learned) v.aspect_2_sub_learned = value; else v.aspect_2_sub_equipped = value; }
			case 3 -> { if (learned) v.aspect_3_sub_learned = value; else v.aspect_3_sub_equipped = value; }
			case 4 -> { if (learned) v.aspect_4_sub_learned = value; else v.aspect_4_sub_equipped = value; }
			default -> throw new IllegalArgumentException("Sub-aspect slot must be 0..4, got " + slot);
		}
	}

	private void dirty() {
		vars.markSyncDirty();
	}
}
