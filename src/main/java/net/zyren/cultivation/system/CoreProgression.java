package net.zyren.cultivation.system;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.QiData;

/**
 * Core breakthroughs from level 1 on, configured per level by the server (core_progression.levels).
 * Each line "level:cultivated_qi_to_level_up:max_qi" describes being AT that level: its Qi pool,
 * and the qi_cultivated needed to go up to the next level. No line for your level = no further
 * automatic level-ups. On reaching a level, its line's max Qi is applied (if it has a line).
 * qi_cultivated is checked against the requirement but not consumed (unconfirmed).
 *
 * The first breakthrough (level 0 -> 1) stays in {@link QiBreakthrough}.
 */
public final class CoreProgression {
	/** One configured core level. */
	public record LevelSpec(int level, double cultivatedRequired, double maxQi) {
	}

	private CoreProgression() {
	}

	/** The config line for {@code level}, or null if that level isn't configured. */
	public static LevelSpec spec(int level) {
		for (String line : CultivationConfig.CORE_LEVELS.get()) {
			String[] p = line.trim().split(":");
			try {
				if (p.length == 3 && Integer.parseInt(p[0].trim()) == level)
					return new LevelSpec(level, Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
			} catch (NumberFormatException ignored) {
				// invalid lines are rejected by the config validator; skip defensively
			}
		}
		return null;
	}

	/**
	 * qi_cultivated needed to go from the player's level to the next one (from the CURRENT level's
	 * line), including the extra-aspect multiplier; 0 if the current level has no line.
	 */
	public static double requirementFor(Player player) {
		LevelSpec current = spec(QiData.of(player).getCoreLevel());
		if (current == null)
			return 0;
		return current.cultivatedRequired()
				* Math.pow(CultivationConfig.EXTRA_ASPECT_MULTIPLIER.get(), AspectData.of(player).ownedSlotCount());
	}

	/** Advances as many configured levels as qi_cultivated allows. @return true if at least one level was gained */
	public static boolean tryNextBreakthrough(Player player) {
		if (player.level().isClientSide() || !CultivationConfig.CORE_PROGRESSION_ENABLED.get())
			return false;
		QiData data = QiData.of(player);
		boolean any = false;
		for (int guard = 0; guard < 1000; guard++) {
			if (data.getCoreLevel() < 1 || spec(data.getCoreLevel()) == null)
				break;
			if (data.getCultivated() < requirementFor(player))
				break;
			advanceCoreLevel(player);
			any = true;
		}
		return any;
	}

	/**
	 * Raises the core by one level (level must already be >= 1), applies that level's max Qi if
	 * it's configured, and grants an aspect choice from aspects.choice_min_core_level on. Used by
	 * real breakthroughs and by /qi breakthrough force.
	 */
	public static void advanceCoreLevel(Player player) {
		if (player.level().isClientSide())
			return;
		QiData data = QiData.of(player);
		if (data.getCoreLevel() < 1)
			return;
		int newLevel = data.getCoreLevel() + 1;
		data.setCoreLevel(newLevel);
		LevelSpec spec = spec(newLevel);
		String extra = "";
		if (spec != null && spec.maxQi() > 0) {
			QiCoreStorage.setCoreMax(player, spec.maxQi());
			extra = " Max Qi: " + (long) spec.maxQi() + ".";
		}
		player.displayClientMessage(Component.literal("Your Qi Core reaches level " + newLevel + "." + extra), false);
		// One choice per breakthrough level, ever: a level regained after an Emergency Breakdown,
		// the Poisoned potion or a core break grants nothing new (user decision 2026-10-07).
		boolean firstTime = AntiFarm.firstTimeReaching(player, newLevel);
		if (newLevel >= CultivationConfig.ASPECT_CHOICE_MIN_CORE_LEVEL.get()) {
			if (firstTime)
				AspectChoices.grantChoices(player, 1);
			else
				player.displayClientMessage(Component.literal("(Level regained: its breakthrough choice was already granted.)"), true);
		}
	}

	/** qi_cultivated at the START of {@code level}: what it took to reach it (0 for level 1). */
	public static double cultivatedAtStartOf(Player player, int level) {
		LevelSpec below = spec(level - 1);
		if (level <= 1 || below == null)
			return 0;
		return below.cultivatedRequired()
				* Math.pow(CultivationConfig.EXTRA_ASPECT_MULTIPLIER.get(), AspectData.of(player).ownedSlotCount());
	}

	/**
	 * Drops the core to {@code newLevel} consistently: that level's max Qi (current Qi trimmed to
	 * it) and qi_cultivated back to the start of the level. Without the cultivated reset, the next
	 * cultivated gain would instantly re-run the lost breakthroughs (and grant their aspect
	 * choices again). Used by the Poisoned Qi potion.
	 */
	public static void lowerCoreLevel(Player player, int newLevel) {
		if (player.level().isClientSide())
			return;
		QiData data = QiData.of(player);
		AntiFarm.markLevelReached(player);
		newLevel = Math.max(1, newLevel);
		data.setCoreLevel(newLevel);
		LevelSpec spec = spec(newLevel);
		if (spec != null && spec.maxQi() > 0)
			QiCoreStorage.setCoreMax(player, spec.maxQi());
		double start = cultivatedAtStartOf(player, newLevel);
		if (data.getCultivated() > start)
			data.setCultivated(start);
	}
}
