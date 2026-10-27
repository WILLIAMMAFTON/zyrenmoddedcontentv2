package net.zyren.cultivation.system;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.QiData;

/**
 * Core breakthroughs. Stage 1 implements only the first breakthrough (spec section 4):
 *
 *     if no_qi >= 100:
 *         no_qi = 0
 *         qi_core_level = 1
 *
 * Later core-level progression is not finalized and is deliberately not implemented.
 */
public final class QiBreakthrough {
	private QiBreakthrough() {
	}

	/** no_qi needed to form the core (level 0 -> 1). Only this step uses no_qi. */
	public static double noQiRequired() {
		return CultivationConfig.FIRST_BREAKTHROUGH_NO_QI_REQUIRED.get();
	}

	/** Max Qi when the core forms: the "1:..." line's max Qi wins over first_breakthrough.initial_core_max. */
	public static double initialCoreMax() {
		CoreProgression.LevelSpec one = CoreProgression.spec(1);
		return one != null ? one.maxQi() : CultivationConfig.FIRST_BREAKTHROUGH_CORE_MAX.get();
	}

	public static boolean canFirstBreakthrough(Player player) {
		QiData data = QiData.of(player);
		return data.getCoreLevel() == 0 && data.getNoQi() >= noQiRequired();
	}

	/** @return true if the breakthrough happened */
	public static boolean tryFirstBreakthrough(Player player) {
		if (player.level().isClientSide() || !canFirstBreakthrough(player))
			return false;

		QiData data = QiData.of(player);
		data.setNoQi(0);
		data.setCoreLevel(1);

		// Placeholder values from config (both default to 0 until designed).
		double max = initialCoreMax();
		// Trims leftover Qi (e.g. after a Core Annihilation) to the new core's pool (user decision 2026-10-07).
		QiCoreStorage.setCoreMax(player, max);
		QiCoreStorage.addQi(player, CultivationConfig.FIRST_BREAKTHROUGH_QI_REWARD.get());

		// Pure is always available to cultivators; it gets its starting level when the core forms.
		// Forming the core does NOT grant an aspect choice (choices start at core level 2).
		AspectData aspects = AspectData.of(player);
		aspects.setPureLevel(Math.max(aspects.getPureLevel(), CultivationConfig.PURE_LEVEL_ON_CORE_FORMATION.get()));

		player.displayClientMessage(Component.literal("Your Qi Core has formed."), false);
		return true;
	}
}
