package net.zyren.cultivation.system;

import net.minecraft.world.entity.player.Player;

/**
 * "Is the player's current aspect in use?" Switching is refused while it is.
 *
 * Hook only: there is no Qi technique/combat system yet, so this always returns false.
 * The combat stage should make this return true while a Qi attack or technique is active.
 */
public final class AspectUsage {
	private AspectUsage() {
	}

	public static boolean isInUse(Player player) {
		return false;
	}
}
