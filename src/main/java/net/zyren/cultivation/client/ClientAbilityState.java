package net.zyren.cultivation.client;

import net.zyren.cultivation.ability.core.AbilitySlot;

/**
 * Client copy of ability timers from {@link net.zyren.cultivation.network.AbilityStateMessage},
 * counted down locally with wall-clock time (20 ticks/second).
 * No client-only imports, so common packet code can reference it safely.
 */
public final class ClientAbilityState {
	private static final long NANOS_PER_TICK = 50_000_000L;

	private static long[] cooldownEndNanos = new long[AbilitySlot.values().length];
	private static int armedSlot = -1;
	private static long reinforceEndNanos;
	private static boolean passiveActive;
	private static int activeMask;
	private static float xpNeeded = -1;
	private static long[] nullifyEndNanos = new long[net.zyren.cultivation.data.AspectType.values().length];

	private ClientAbilityState() {
	}

	public static void update(int[] cooldownTicks, int armed, int reinforceTicks, boolean passive, int active, int[] nullifyTicks, float xpForNextLevel) {
		xpNeeded = xpForNextLevel;
		long now = System.nanoTime();
		long[] nul = new long[net.zyren.cultivation.data.AspectType.values().length];
		for (int i = 0; i < Math.min(nul.length, nullifyTicks.length); i++)
			nul[i] = nullifyTicks[i] > 0 ? now + nullifyTicks[i] * NANOS_PER_TICK : 0;
		nullifyEndNanos = nul;
		long[] ends = new long[AbilitySlot.values().length];
		for (int i = 0; i < Math.min(ends.length, cooldownTicks.length); i++)
			ends[i] = cooldownTicks[i] > 0 ? now + cooldownTicks[i] * NANOS_PER_TICK : 0;
		cooldownEndNanos = ends;
		armedSlot = armed;
		reinforceEndNanos = reinforceTicks > 0 ? now + reinforceTicks * NANOS_PER_TICK : 0;
		passiveActive = passive;
		activeMask = active;
	}

	/** Sustained ability running, or lethal guard armed, in this slot. */
	public static boolean isActive(AbilitySlot slot) {
		return (activeMask & (1 << slot.ordinal())) != 0;
	}

	/** Seconds of cooldown left (rounded up), 0 if ready. */
	public static int cooldownSeconds(AbilitySlot slot) {
		long left = cooldownEndNanos[slot.ordinal()] - System.nanoTime();
		return left <= 0 ? 0 : (int) ((left + 999_999_999L) / 1_000_000_000L);
	}

	public static AbilitySlot armedSlot() {
		return AbilitySlot.byOrdinal(armedSlot);
	}

	public static int reinforceSeconds() {
		long left = reinforceEndNanos - System.nanoTime();
		return left <= 0 ? 0 : (int) ((left + 999_999_999L) / 1_000_000_000L);
	}

	/** Seconds this aspect stays nullified (rounded up), 0 if not. */
	public static int nullifiedSeconds(net.zyren.cultivation.data.AspectType aspect) {
		long left = nullifyEndNanos[aspect.ordinal()] - System.nanoTime();
		return left <= 0 ? 0 : (int) ((left + 999_999_999L) / 1_000_000_000L);
	}

	/** If every aspect is nullified (Qi sealed): seconds until the first one returns; else 0. */
	public static int sealedSeconds() {
		int min = Integer.MAX_VALUE;
		for (var a : net.zyren.cultivation.data.AspectType.values()) {
			int s = nullifiedSeconds(a);
			if (s <= 0)
				return 0;
			min = Math.min(min, s);
		}
		return min;
	}

	/** Mastery XP the active aspect needs for its next level; negative = none (Pure, capped, disabled). */
	public static float xpNeeded() {
		return xpNeeded;
	}

	public static boolean passiveActive() {
		return passiveActive;
	}

	public static void reset() {
		cooldownEndNanos = new long[AbilitySlot.values().length];
		armedSlot = -1;
		reinforceEndNanos = 0;
		passiveActive = false;
		activeMask = 0;
		xpNeeded = -1;
		nullifyEndNanos = new long[net.zyren.cultivation.data.AspectType.values().length];
	}
}
