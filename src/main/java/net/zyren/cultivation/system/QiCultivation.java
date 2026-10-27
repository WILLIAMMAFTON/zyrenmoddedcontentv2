package net.zyren.cultivation.system;

import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.data.QiData;

/**
 * Cultivation gain. Sources of gain (fishing etc.) are not designed yet; whatever they end up
 * being should call into these methods rather than touching the variables directly.
 *
 * From an MCreator procedure (custom code block):
 *     net.zyren.cultivation.system.QiCultivation.addNoQi((net.minecraft.world.entity.player.Player) entity, 5);
 */
public final class QiCultivation {
	private QiCultivation() {
	}

	/**
	 * Adds pre-cultivation progress (no_qi). Only applies before the core has formed
	 * (qi_core_level == 0). Triggers the first breakthrough when the requirement is reached.
	 *
	 * @return true if the gain was applied
	 */
	public static boolean addNoQi(Player player, double amount) {
		if (player.level().isClientSide() || amount <= 0)
			return false;
		QiData data = QiData.of(player);
		if (data.getCoreLevel() > 0)
			return false; // core already formed; no_qi is no longer used
		data.setNoQi(data.getNoQi() + amount);
		QiBreakthrough.tryFirstBreakthrough(player);
		return true;
	}

	/**
	 * Adds to qi_cultivated (cultivation progress). Kept separate from usable Qi
	 * (qi_core_amount) per the spec. Checked against the core level 2+ requirement by
	 * {@link CoreProgression}, which is disabled until core progression is finalized.
	 *
	 * @return true if the gain was applied
	 */
	public static boolean addQiCultivated(Player player, double amount) {
		if (player.level().isClientSide() || amount <= 0)
			return false;
		QiData data = QiData.of(player);
		data.setCultivated(data.getCultivated() + amount);
		CoreProgression.tryNextBreakthrough(player);
		return true;
	}
}
