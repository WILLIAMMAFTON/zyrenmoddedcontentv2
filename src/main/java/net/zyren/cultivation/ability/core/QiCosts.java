package net.zyren.cultivation.ability.core;

import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;

/**
 * Every Qi cost goes through here, so the Corrupted Core rule ("Corrupted Qi costs 3x Qi")
 * applies to all of them: ability costs and passive upkeep alike.
 */
public final class QiCosts {
	private QiCosts() {
	}

	public static double effective(Player player, double baseCost) {
		if (baseCost <= 0)
			return Math.max(0, baseCost);
		double cost = QiData.of(player).getCoreType() == CoreType.CORRUPTED
				? baseCost * CultivationConfig.CORRUPTED_QI_COST_MULTIPLIER.get()
				: baseCost;
		if (net.zyren.cultivation.system.Corruption.hasResistance(player))
			cost *= net.zyren.cultivation.config.CorruptionConfig.RESISTANCE_COST_MULTIPLIER.get(); // Core Resistance: ability costs x2
		if (!player.level().isClientSide())
			cost *= QiCostDebuffs.multiplier(player); // e.g. Fire Blast of Flames on Water/Air users
		return cost;
	}
}
