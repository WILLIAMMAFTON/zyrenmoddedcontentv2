package net.zyren.cultivation.data;

import net.minecraft.world.entity.player.Player;

import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables;
import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables.PlayerVariables;

/**
 * The ONLY class that reads or writes the corruption player variables (MCreator, player_persistent):
 *   core_corruption       Number  corruption points (0..max, default max 1000)
 *   end_exposure_ticks    Number  continuous time spent in the End (for the 3-day delay)
 *   sculk_exposure_ticks  Number  continuous time spent near sculk (for the 100,000-tick delay)
 */
public final class CorruptionData {
	private final PlayerVariables vars;

	private CorruptionData(Player player) {
		this.vars = player.getData(ZyrenmoddedcontentV2ModVariables.PLAYER_VARIABLES);
	}

	public static CorruptionData of(Player player) {
		return new CorruptionData(player);
	}

	public double getCorruption() {
		return vars.core_corruption;
	}

	public void setCorruption(double value) {
		vars.core_corruption = Math.max(0, value);
		vars.markSyncDirty();
	}

	public double getEndTicks() {
		return vars.end_exposure_ticks;
	}

	public void setEndTicks(double value) {
		if (vars.end_exposure_ticks != value) {
			vars.end_exposure_ticks = Math.max(0, value);
			vars.markSyncDirty();
		}
	}

	public double getSculkTicks() {
		return vars.sculk_exposure_ticks;
	}

	public void setSculkTicks(double value) {
		if (vars.sculk_exposure_ticks != value) {
			vars.sculk_exposure_ticks = Math.max(0, value);
			vars.markSyncDirty();
		}
	}
}
