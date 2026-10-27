package net.zyren.cultivation.data;

import net.minecraft.world.entity.player.Player;

import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables;
import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables.PlayerVariables;

/**
 * The ONLY class that reads or writes the Emergency Core Breakdown player variables (MCreator, player_persistent):
 *   allow_emergency_core_breakdown      Logic   the player's toggle (default false = OFF)
 *   last_emergency_core_breakdown_time  Number  game time of the last Emergency Breakdown or Annihilation (0 = none)
 */
public final class EmergencyCoreData {
	private final PlayerVariables vars;

	private EmergencyCoreData(Player player) {
		this.vars = player.getData(ZyrenmoddedcontentV2ModVariables.PLAYER_VARIABLES);
	}

	public static EmergencyCoreData of(Player player) {
		return new EmergencyCoreData(player);
	}

	public boolean isAllowed() {
		return vars.allow_emergency_core_breakdown;
	}

	public void setAllowed(boolean value) {
		vars.allow_emergency_core_breakdown = value;
		vars.markSyncDirty();
	}

	public long getLastTime() {
		return (long) vars.last_emergency_core_breakdown_time;
	}

	public void setLastTime(long gameTime) {
		double v = Math.max(0, gameTime);
		if (vars.last_emergency_core_breakdown_time != v) {
			vars.last_emergency_core_breakdown_time = v;
			vars.markSyncDirty();
		}
	}
}
