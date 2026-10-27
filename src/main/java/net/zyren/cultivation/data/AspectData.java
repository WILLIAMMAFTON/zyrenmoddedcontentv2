package net.zyren.cultivation.data;

import net.minecraft.world.entity.player.Player;

import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables;
import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables.PlayerVariables;

/**
 * The ONLY class that reads or writes the aspect player variables (sibling of {@link QiData}).
 *
 * Variables (MCreator, player_persistent):
 *   aspect_1_type .. aspect_4_type   String  ("" = empty slot)
 *   aspect_1_level .. aspect_4_level Number
 *   pure_level                       Number  (Pure is leveled but never uses a slot)
 *   aspect_1_xp .. aspect_4_xp       Number  (mastery XP toward the slot's next level; Pure has none)
 *   active_aspect                    String  ("" = none, "PURE", or an owned slot's aspect id)
 *   pending_breakthrough_choices     Number
 *
 * Sub-aspect variables are owned by {@link SubAspectData}; this class only calls its hooks when
 * a slot's aspect changes, the active aspect changes, or everything is reset.
 *
 * aspect_N_cultivated from the spec is intentionally not implemented yet: aspect levels only
 * rise through breakthrough choices, so nothing would write it.
 *
 * Slots are numbered 1..4 everywhere in the public API.
 */
public final class AspectData {
	public static final int SLOT_COUNT = 4;

	private final PlayerVariables vars;

	private AspectData(Player player) {
		this.vars = player.getData(ZyrenmoddedcontentV2ModVariables.PLAYER_VARIABLES);
	}

	public static AspectData of(Player player) {
		return new AspectData(player);
	}

	// ---- Slots ----

	/** @return the aspect in a slot (1..4), or null if empty */
	public AspectType getSlotType(int slot) {
		return AspectType.fromId(rawType(slot));
	}

	public int getSlotLevel(int slot) {
		return switch (checkSlot(slot)) {
			case 1 -> (int) vars.aspect_1_level;
			case 2 -> (int) vars.aspect_2_level;
			case 3 -> (int) vars.aspect_3_level;
			default -> (int) vars.aspect_4_level;
		};
	}

	/** Sets a slot. Pass null to empty it (also resets its level to 0). */
	public void setSlot(int slot, AspectType type, int level) {
		if (type != null && type.isSpecial())
			throw new IllegalArgumentException(type + " cannot occupy a slot");
		if (type != getSlotType(slot)) {
			setSlotXp(slot, 0); // a different (or no) aspect starts with no mastery XP
			SubAspectData.clearSlot(vars, slot); // ...and its sub-aspects leave with the old aspect
			if (type != null)
				SubAspectData.applyPending(vars, slot, type); // sub-aspects learned before owning it arrive now
		}
		String id = type == null ? "" : type.id();
		double lvl = type == null ? 0 : Math.max(0, level);
		switch (checkSlot(slot)) {
			case 1 -> { vars.aspect_1_type = id; vars.aspect_1_level = lvl; }
			case 2 -> { vars.aspect_2_type = id; vars.aspect_2_level = lvl; }
			case 3 -> { vars.aspect_3_type = id; vars.aspect_3_level = lvl; }
			default -> { vars.aspect_4_type = id; vars.aspect_4_level = lvl; }
		}
		dirty();
	}

	/** Mastery XP toward this slot's next level. */
	public double getSlotXp(int slot) {
		return switch (checkSlot(slot)) {
			case 1 -> vars.aspect_1_xp;
			case 2 -> vars.aspect_2_xp;
			case 3 -> vars.aspect_3_xp;
			default -> vars.aspect_4_xp;
		};
	}

	public void setSlotXp(int slot, double xp) {
		double v = Math.max(0, xp);
		switch (checkSlot(slot)) {
			case 1 -> vars.aspect_1_xp = v;
			case 2 -> vars.aspect_2_xp = v;
			case 3 -> vars.aspect_3_xp = v;
			default -> vars.aspect_4_xp = v;
		}
		dirty();
	}

	public void setSlotLevel(int slot, int level) {
		setSlot(slot, getSlotType(slot), level);
	}

	/** @return slot number 1..4 holding this aspect, or 0 */
	public int findSlot(AspectType type) {
		for (int s = 1; s <= SLOT_COUNT; s++)
			if (getSlotType(s) == type)
				return s;
		return 0;
	}

	/** @return first empty slot 1..4, or 0 if all are full */
	public int firstEmptySlot() {
		for (int s = 1; s <= SLOT_COUNT; s++)
			if (getSlotType(s) == null)
				return s;
		return 0;
	}

	/** Number of slot aspects owned (Pure is not counted). */
	public int ownedSlotCount() {
		int n = 0;
		for (int s = 1; s <= SLOT_COUNT; s++)
			if (getSlotType(s) != null)
				n++;
		return n;
	}

	/** True for PURE (always owned) or any aspect currently in a slot. */
	public boolean owns(AspectType type) {
		return type != null && (type.isSpecial() || findSlot(type) != 0);
	}

	/** Level of any owned aspect, including Pure. 0 if not owned. */
	public int getLevel(AspectType type) {
		if (type == null)
			return 0;
		if (type == AspectType.PURE)
			return getPureLevel();
		int slot = findSlot(type);
		return slot == 0 ? 0 : getSlotLevel(slot);
	}

	// ---- Pure ----

	public int getPureLevel() {
		return (int) vars.pure_level;
	}

	public void setPureLevel(int level) {
		vars.pure_level = Math.max(0, level);
		dirty();
	}

	// ---- Active aspect ----

	/** @return the active aspect, or null if none */
	public AspectType getActive() {
		return AspectType.fromId(vars.active_aspect);
	}

	public void setActive(AspectType type) {
		if (type != getActive())
			SubAspectData.formOff(vars); // a newly active aspect always starts in its normal form
		vars.active_aspect = type == null ? "" : type.id();
		dirty();
	}

	// ---- Pending breakthrough choices ----

	public int getPendingChoices() {
		return (int) vars.pending_breakthrough_choices;
	}

	public void setPendingChoices(int value) {
		vars.pending_breakthrough_choices = Math.max(0, value);
		dirty();
	}

	// ---- Maintenance ----

	/**
	 * Rewrites stored strings into their clean form (e.g. MCreator's quoted defaults, LIGHTNING
	 * -> ELECTRIC) and clears an active aspect the player no longer owns.
	 */
	public void normalize() {
		for (int s = 1; s <= SLOT_COUNT; s++) {
			AspectType t = getSlotType(s);
			if (t != null && t.isSpecial())
				t = null; // Pure in a slot is invalid
			String clean = t == null ? "" : t.id();
			if (!clean.equals(rawType(s)))
				setSlot(s, t, getSlotLevel(s));
		}
		AspectType active = getActive();
		String cleanActive = active != null && owns(active) ? active.id() : "";
		if (!cleanActive.equals(vars.active_aspect)) {
			vars.active_aspect = cleanActive;
			dirty();
		}
	}

	/** Removes all slot aspects, resets Pure level, active aspect and pending choices. */
	public void resetAll() {
		for (int s = 1; s <= SLOT_COUNT; s++) {
			setSlot(s, null, 0);
			setSlotXp(s, 0);
		}
		vars.pure_level = 0;
		vars.active_aspect = "";
		vars.pending_breakthrough_choices = 0;
		SubAspectData.clearAll(vars); // learned / equipped sub-aspects (incl. Pure's) and the form flag
		dirty();
	}

	private String rawType(int slot) {
		return switch (checkSlot(slot)) {
			case 1 -> vars.aspect_1_type;
			case 2 -> vars.aspect_2_type;
			case 3 -> vars.aspect_3_type;
			default -> vars.aspect_4_type;
		};
	}

	private static int checkSlot(int slot) {
		if (slot < 1 || slot > SLOT_COUNT)
			throw new IllegalArgumentException("Aspect slot must be 1.." + SLOT_COUNT + ", got " + slot);
		return slot;
	}

	private void dirty() {
		vars.markSyncDirty();
	}
}
