package net.zyren.cultivation.client;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.network.SwitchStatusMessage;

/**
 * Client-side copy of short-lived UI state the server tells us about: the aspect switch in
 * progress (for the loading bar) and the last action feedback message.
 *
 * Deliberately has NO client-only imports, so packet handlers in common code can reference it
 * without breaking dedicated servers.
 */
public final class ClientCultivationState {
	private static AspectType switchTarget;
	private static long switchStartNanos;
	private static int switchDurationTicks;

	private static String message = "";
	private static boolean messageSuccess;
	private static long messageNanos;

	private ClientCultivationState() {
	}

	public static void onSwitchStatus(int state, AspectType target, int durationTicks, String msg) {
		if (state == SwitchStatusMessage.STARTED) {
			switchTarget = target;
			switchStartNanos = System.nanoTime();
			switchDurationTicks = Math.max(1, durationTicks);
		} else {
			switchTarget = null;
		}
		setMessage(state == SwitchStatusMessage.STARTED || state == SwitchStatusMessage.COMPLETED, msg);
	}

	public static void onFeedback(boolean success, String msg) {
		setMessage(success, msg);
	}

	private static void setMessage(boolean success, String msg) {
		message = msg == null ? "" : msg;
		messageSuccess = success;
		messageNanos = System.nanoTime();
	}

	public static boolean isSwitching() {
		return switchTarget != null;
	}

	public static AspectType switchTarget() {
		return switchTarget;
	}

	/** 0..1 progress of the current switch, estimated from wall-clock time (20 ticks/second). */
	public static double switchProgress() {
		if (switchTarget == null)
			return 0;
		double elapsedTicks = (System.nanoTime() - switchStartNanos) / 50_000_000.0;
		return Math.min(1.0, elapsedTicks / switchDurationTicks);
	}

	/** Last message, or "" once it is older than 6 seconds. */
	public static String message() {
		return System.nanoTime() - messageNanos > 6_000_000_000L ? "" : message;
	}

	public static boolean messageSuccess() {
		return messageSuccess;
	}

	/** Clears everything (e.g. on disconnect). */
	public static void reset() {
		switchTarget = null;
		message = "";
	}
}
