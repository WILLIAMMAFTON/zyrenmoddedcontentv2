package net.zyren.cultivation.ability.core;

import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.data.AspectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Short-lived ability state per player, kept in server memory (not saved to disk):
 * cooldowns, the armed empowered hit, active buffs, nullifications, last damaged player.
 *
 * Keyed by UUID, so it survives respawns and relogs while the server runs (cooldowns can't be
 * reset by relogging). Buffs and the empowered hit are cleared on death and logout.
 */
public final class AbilityState {
	private static final Map<UUID, AbilityState> STATES = new HashMap<>();

	public static AbilityState of(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new AbilityState());
	}

	// key = aspect id + ":" + slot
	private final Map<String, Long> cooldownEnd = new HashMap<>();
	private Ability empowered;
	private long empoweredUntil; // Long.MAX_VALUE = until next hit
	private final List<Buff> buffs = new ArrayList<>();
	private final Map<AspectType, Long> nullifiedUntil = new EnumMap<>(AspectType.class);
	private UUID lastDamagedPlayer;
	boolean passiveActive;
	/** The passive that was in use last second (detects aspect switches / form toggles). */
	AspectPassive lastPassive;
	/** The sub-aspect form in use last tick (detects leaving a form by any route). */
	net.zyren.cultivation.data.SubAspectType lastForm;
	AspectType lastSyncedActive;
	boolean dirty = true;

	private AbilityState() {
	}

	// ---- Cooldowns ----

	static String key(Ability a) {
		return a.aspect().id() + ":" + a.slot().name();
	}

	public long cooldownRemaining(Ability a, long now) {
		Long end = cooldownEnd.get(key(a));
		return end == null ? 0 : Math.max(0, end - now);
	}

	/** Dev/testing: every ability cooldown ends now (/qi cooldowns clear). */
	public void clearCooldowns() {
		cooldownEnd.clear();
		dirty = true;
	}

	public void startCooldown(Ability a, long now, int ticks) {
		if (ticks > 0) {
			cooldownEnd.put(key(a), now + ticks);
			dirty = true;
		}
	}

	// ---- Empowered hit ----

	public void arm(Ability a, long now, int windowTicks) {
		empowered = a;
		empoweredUntil = windowTicks <= 0 ? Long.MAX_VALUE : now + windowTicks;
		dirty = true;
	}

	public Ability empowered(long now) {
		if (empowered != null && now > empoweredUntil) {
			empowered = null;
			dirty = true;
		}
		return empowered;
	}

	// The empowered hit that just landed (consumed at the start of the damage chain), so later
	// handlers in the same hit can still tell it was a Qi-empowered blow.
	private Ability landedEmpowered;
	private int landedTarget;
	private long landedTick = -1;

	public void recordEmpoweredHit(Ability a, net.minecraft.world.entity.Entity target, long now) {
		landedEmpowered = a;
		landedTarget = target.getId();
		landedTick = now;
	}

	/** The empowered ability that landed on {@code target} this tick, else the one still armed. */
	public Ability empoweredFor(net.minecraft.world.entity.Entity target, long now) {
		if (landedTick == now && landedTarget == target.getId() && landedEmpowered != null)
			return landedEmpowered;
		return empowered(now);
	}

	public Ability consumeEmpowered(long now) {
		Ability a = empowered(now);
		if (a != null) {
			empowered = null;
			dirty = true;
		}
		return a;
	}

	// ---- Sustained abilities / lethal guards ----

	private final Map<Ability, Long> sustained = new java.util.LinkedHashMap<>();
	private Ability lethalGuard;

	public void startSustained(Ability a, long endTick) {
		sustained.put(a, endTick);
		dirty = true;
	}

	/** Ends a running sustained ability on the next tick (onStop runs as usual). */
	public void endSustained(Ability a, long now) {
		sustained.computeIfPresent(a, (k, v) -> Math.min(v, now));
	}

	public boolean isSustained(Ability a) {
		return sustained.containsKey(a);
	}

	/** Bitmask (by slot ordinal) of running sustained abilities and the armed lethal guard. */
	public int activeSlotsMask() {
		int mask = 0;
		for (Ability a : sustained.keySet())
			mask |= 1 << a.slot().ordinal();
		if (lethalGuard != null)
			mask |= 1 << lethalGuard.slot().ordinal();
		return mask;
	}

	Map<Ability, Long> sustainedMap() {
		return sustained;
	}

	public void setLethalGuard(Ability a) {
		lethalGuard = a;
		dirty = true;
	}

	public Ability lethalGuard() {
		return lethalGuard;
	}

	// ---- Buffs ----

	public void removeBuff(String id) {
		if (buffs.removeIf(b -> b.id().equals(id)))
			dirty = true;
	}

	// ---- Passive cost cut (Protective HOOO RAAA) ----

	private double passiveCostCut;
	private long passiveCostCutUntil;

	public void setPassiveCostCut(double fraction, long until) {
		passiveCostCut = fraction;
		passiveCostCutUntil = until;
	}

	/** Fraction (0-1) taken off passive upkeep right now. */
	public double passiveCostCut(long now) {
		return now < passiveCostCutUntil ? passiveCostCut : 0;
	}

	public void addBuff(Buff buff) {
		buffs.removeIf(b -> b.id().equals(buff.id()));
		buffs.add(buff);
		// Remember which ability created it (if any), so a form change can end exactly that ability's buffs.
		Ability origin = AbilityService.performing();
		if (origin != null)
			buffOrigin.put(buff.id(), origin);
		else
			buffOrigin.remove(buff.id());
		dirty = true;
	}

	/** Buff id -> the ability whose cast created it. Entries for removed buffs are harmless leftovers. */
	private final Map<String, Ability> buffOrigin = new HashMap<>();

	/**
	 * Form change: everything started by these abilities ends now. Their buffs, an armed hit,
	 * an armed lethal guard, and running sustained abilities (onStop runs on the next tick).
	 */
	public void endFrom(java.util.Set<Ability> abilities, long now) {
		if (abilities.isEmpty())
			return;
		if (buffs.removeIf(b -> abilities.contains(buffOrigin.get(b.id()))))
			dirty = true;
		if (empowered != null && abilities.contains(empowered)) {
			empowered = null;
			dirty = true;
		}
		if (lethalGuard != null && abilities.contains(lethalGuard)) {
			lethalGuard = null;
			dirty = true;
		}
		for (Ability a : abilities)
			endSustained(a, now);
	}

	/** Switching aspects: buffs from every other aspect end. */
	public void removeBuffsExcept(AspectType keep) {
		if (buffs.removeIf(b -> b.source() != keep))
			dirty = true;
	}

	public boolean hasBuff(String id, long now) {
		return buffs.stream().anyMatch(b -> b.id().equals(id) && b.endTick() > now);
	}

	public List<Buff> buffs() {
		return buffs;
	}

	void expireBuffs(long now) {
		if (buffs.removeIf(b -> b.endTick() <= now))
			dirty = true;
	}

	// ---- Nullify ----

	public void nullify(AspectType aspect, long until) {
		nullifiedUntil.merge(aspect, until, Math::max);
		if (lethalGuard != null && lethalGuard.aspect() == aspect)
			lethalGuard = null;
		// sustained abilities of this aspect are stopped by the ticker on its next pass
		if (buffs.removeIf(b -> b.source() == aspect))
			dirty = true;
		if (empowered != null && empowered.aspect() == aspect)
			empowered = null;
		dirty = true;
	}

	/** Removes ended nullifications. @return the aspects whose nullification just ended */
	public java.util.List<AspectType> expireNullify(long now) {
		java.util.List<AspectType> ended = new java.util.ArrayList<>();
		var it = nullifiedUntil.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			if (e.getValue() <= now) {
				ended.add(e.getKey());
				it.remove();
			}
		}
		if (!ended.isEmpty())
			dirty = true;
		return ended;
	}

	public long nullifyRemaining(AspectType aspect, long now) {
		Long until = nullifiedUntil.get(aspect);
		return until == null ? 0 : Math.max(0, until - now);
	}

	/** Lifts every nullification (Pure "Protect Ye"). @return true if anything was nullified */
	public boolean clearNullify(long now) {
		boolean any = nullifiedUntil.values().stream().anyMatch(u -> u > now);
		nullifiedUntil.clear();
		dirty = true;
		return any;
	}

	public boolean isNullifiedAny(long now) {
		return nullifiedUntil.values().stream().anyMatch(u -> u > now);
	}

	// ---- Qi spent on the last ability (Pure level 5 absorb) ----

	private double lastPaidQi;

	public void recordPaid(double qi) {
		lastPaidQi = qi;
	}

	public double lastPaidQi() {
		return lastPaidQi;
	}

	/** Pure L5 absorbs a cast's Qi once, not on every hit that cast produces. */
	public void clearLastPaid() {
		lastPaidQi = 0;
	}

	public boolean isNullified(AspectType aspect, long now) {
		Long until = nullifiedUntil.get(aspect);
		return until != null && until > now;
	}

	// ---- Targeting memory ----

	public UUID lastDamagedPlayer() {
		return lastDamagedPlayer;
	}

	public void setLastDamagedPlayer(UUID id) {
		lastDamagedPlayer = id;
	}

	// ---- Read-only views for client sync ----

	public long cooldownRemaining(AspectType aspect, AbilitySlot slot, long now) {
		Long end = cooldownEnd.get(aspect.id() + ":" + slot.name());
		return end == null ? 0 : Math.max(0, end - now);
	}

	/** Armed ability without expiring/consuming it, or null. */
	public Ability armedAbility() {
		return empowered;
	}

	/** Ticks left on the longest active Reinforce-type buff (0 if none). */
	public long reinforceRemaining(long now) {
		long best = 0;
		for (Buff b : buffs)
			if (b.nullifiesIncomingQiEffects())
				best = Math.max(best, b.endTick() - now);
		return Math.max(0, best);
	}

	// ---- Lifecycle ----

	/** Death / logout: drop buffs and the armed hit; keep cooldowns and nullifications. */
	void clearTransient(net.minecraft.server.level.ServerPlayer player) {
		for (Ability a : new ArrayList<>(sustained.keySet()))
			a.onStop(player);
		sustained.clear();
		lethalGuard = null;
		buffs.clear();
		empowered = null;
		passiveActive = false;
		dirty = true;
	}

	public boolean passiveActive() {
		return passiveActive;
	}

	public void markDirty() {
		dirty = true;
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		STATES.clear();
	}
}
