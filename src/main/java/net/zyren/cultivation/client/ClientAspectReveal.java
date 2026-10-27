package net.zyren.cultivation.client;

import java.util.Map;

/**
 * Client copy of the Light Heavy reveal (entity id -> active aspect name). No client-only
 * imports, so packet code can reference it. Labels go stale after 1.5 s without an update.
 */
public final class ClientAspectReveal {
	private static final long STALE_NANOS = 1_500_000_000L;

	private static Map<Integer, String> labels = Map.of();
	private static long updatedNanos;

	private ClientAspectReveal() {
	}

	public static void update(Map<Integer, String> newLabels) {
		labels = Map.copyOf(newLabels);
		updatedNanos = System.nanoTime();
	}

	/** Label for this entity, or null. */
	public static String labelFor(int entityId) {
		if (labels.isEmpty() || System.nanoTime() - updatedNanos > STALE_NANOS)
			return null;
		return labels.get(entityId);
	}
}
