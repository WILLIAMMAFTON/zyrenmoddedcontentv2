package net.zyren.cultivation.client;

import java.util.List;

/** Client copy of meditation timers and (core level 20+) nearby Poisoned cores. No client-only imports. */
public final class ClientCorruptionState {
	private static final long NANOS_PER_TICK = 50_000_000L;

	private static long meditationEnd;
	private static long afterEnd;
	private static List<String> poisoned = List.of();

	private ClientCorruptionState() {
	}

	public static void update(int meditationTicks, int afterTicks, List<String> poisonedNearby) {
		long now = System.nanoTime();
		meditationEnd = meditationTicks > 0 ? now + meditationTicks * NANOS_PER_TICK : 0;
		afterEnd = afterTicks > 0 ? now + afterTicks * NANOS_PER_TICK : 0;
		poisoned = List.copyOf(poisonedNearby);
	}

	public static int meditationSeconds() {
		long left = meditationEnd - System.nanoTime();
		return left <= 0 ? 0 : (int) ((left + 999_999_999L) / 1_000_000_000L);
	}

	public static int afterSeconds() {
		long left = afterEnd - System.nanoTime();
		return left <= 0 ? 0 : (int) ((left + 999_999_999L) / 1_000_000_000L);
	}

	public static List<String> poisonedNearby() {
		return poisoned;
	}

	public static void reset() {
		meditationEnd = 0;
		afterEnd = 0;
		poisoned = List.of();
	}
}
