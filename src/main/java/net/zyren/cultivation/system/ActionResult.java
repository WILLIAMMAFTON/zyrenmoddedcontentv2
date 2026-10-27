package net.zyren.cultivation.system;

/** Outcome of a player action (choice, switch request...), with a message to show the player. */
public record ActionResult(boolean success, String message) {
	public static ActionResult ok(String message) {
		return new ActionResult(true, message);
	}

	public static ActionResult fail(String message) {
		return new ActionResult(false, message);
	}
}
