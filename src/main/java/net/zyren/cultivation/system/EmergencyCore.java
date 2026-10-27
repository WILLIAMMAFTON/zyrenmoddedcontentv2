package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.EmergencyCoreData;
import net.zyren.cultivation.data.QiData;

/**
 * Emergency Core Breakdown / Core Annihilation (source: Core Destruction Source of Truth + decisions
 * in claude/core-destruction.md).
 *
 * Toggle OFF (default): nothing here happens, and existing self-core-breaking abilities are blocked.
 * Toggle ON, Ability 1/2 used without enough Qi:
 *   - first time (or 30+ min after the last): core level -1, usable Qi = max Qi of the old level,
 *     cultivated Qi drops to the start of the new level (decided);
 *   - again within 30 min: Core Annihilation = the existing Core Break, plus usable Qi = sum of the
 *     configured level-up requirements of lines 1..(old level - 1) (decided). Aspects are kept and
 *     stay usable for 5 min x highest aspect level, max 20 min (decided).
 */
public final class EmergencyCore {
	private EmergencyCore() {
	}

	public static boolean allowed(Player player) {
		return EmergencyCoreData.of(player).isAllowed();
	}

	public static ActionResult toggle(ServerPlayer player) {
		EmergencyCoreData d = EmergencyCoreData.of(player);
		d.setAllowed(!d.isAllowed());
		return ActionResult.ok("Emergency Core Breakdown: " + (d.isAllowed() ? "ON" : "OFF"));
	}

	/**
	 * Called when Ability 1 or 2 can't be paid. @return true if Qi was restored (breakdown or
	 * annihilation happened); the caller re-checks the cost.
	 */
	public static boolean tryEmergency(ServerPlayer player, double cost) {
		if (!allowed(player))
			return false;
		QiData q = QiData.of(player);
		int level = q.getCoreLevel();
		if (level < CultivationConfig.EMERGENCY_MIN_CORE_LEVEL.get())
			return false; // "If the player cannot reduce their Core further, Emergency Core Breakdown cannot occur."
		long now = player.serverLevel().getGameTime();
		long last = EmergencyCoreData.of(player).getLastTime();
		boolean annihilation = last > 0 && now - last < CultivationConfig.EMERGENCY_WINDOW_TICKS.get();
		// "The ability can proceed using restored Qi": never sacrifice a level (or the core) for
		// Qi that still couldn't pay for the ability.
		if ((annihilation ? annihilationQi(level) : restoredQi(player, level)) < cost)
			return false;
		if (annihilation)
			annihilate(player, level, now);
		else
			breakdown(player, level, now);
		return true;
	}

	/** Qi a breakdown from {@code level} restores: that level's configured max Qi. */
	private static double restoredQi(ServerPlayer player, int level) {
		CoreProgression.LevelSpec old = CoreProgression.spec(level);
		return old != null && old.maxQi() > 0 ? old.maxQi() : QiData.of(player).getCoreMax();
	}

	private static void breakdown(ServerPlayer player, int level, long now) {
		QiData q = QiData.of(player);
		double restored = restoredQi(player, level);

		int newLevel = level - 1;
		AntiFarm.markLevelReached(player);
		q.setCoreLevel(newLevel);
		CoreProgression.LevelSpec spec = CoreProgression.spec(newLevel);
		if (spec != null && spec.maxQi() > 0)
			q.setCoreMax(spec.maxQi()); // not trimmed: the restored Qi may exceed the new max
		q.setCoreAmount(restored);

		// Cultivated Qi back to the start of the new level (decided), so the lost level has to be earned again.
		double start = CoreProgression.cultivatedAtStartOf(player, newLevel);
		if (q.getCultivated() > start)
			q.setCultivated(start);

		EmergencyCoreData.of(player).setLastTime(now == 0 ? 1 : now);
		player.displayClientMessage(Component.literal("EMERGENCY CORE BREAKDOWN! Core level " + level + " -> " + newLevel + ", Qi restored to "
				+ (long) restored + ". Another within " + CultivationConfig.EMERGENCY_WINDOW_TICKS.get() / 1200 + " min will annihilate your Core.")
				.withStyle(ChatFormatting.GOLD), false);
	}

	private static void annihilate(ServerPlayer player, int level, long now) {
		double qi = annihilationQi(level);
		CoreBreak.annihilate(player, now == 0 ? 1 : now);
		if (!player.isAlive())
			return; // a Corrupted core breaking kills the player (existing Core Break rule)
		QiData.of(player).setCoreAmount(qi);
		player.displayClientMessage(Component.literal("CORE ANNIHILATION! Your Core is gone, but its power floods you: " + (long) qi
				+ " Qi. Your aspects will hold for " + graceTicks(player) / 1200 + " min.").withStyle(ChatFormatting.DARK_RED), false);
	}

	/** Sum of the configured level-up requirements of lines 1..(level - 1) (decided), read live from the config. */
	public static double annihilationQi(int level) {
		double sum = 0;
		for (int l = 1; l < level; l++) {
			CoreProgression.LevelSpec s = CoreProgression.spec(l);
			if (s != null)
				sum += s.cultivatedRequired();
		}
		return sum;
	}

	/** Grace length: 5 min per level of the highest owned aspect (decided), capped at 20 min. */
	public static long graceTicks(Player player) {
		AspectData a = AspectData.of(player);
		int highest = 0;
		for (AspectType t : AspectType.values())
			highest = Math.max(highest, a.getLevel(t));
		return Math.min((long) highest * CultivationConfig.ANNIHILATION_GRACE_TICKS_PER_LEVEL.get(), CultivationConfig.ANNIHILATION_GRACE_MAX_TICKS.get());
	}

	/** Ticks of aspect use left after an Annihilation (0 = none). Works on both sides. */
	public static long graceTicksLeft(Player player) {
		if (QiData.of(player).getCoreLevel() >= 1)
			return 0;
		long last = EmergencyCoreData.of(player).getLastTime();
		if (last <= 0)
			return 0;
		return Math.max(0, last + graceTicks(player) - player.level().getGameTime());
	}

	public static boolean inGrace(Player player) {
		return graceTicksLeft(player) > 0;
	}

	/** Once a second: end the grace period and clear the repeat window when they run out. */
	static void tick(ServerPlayer player) {
		EmergencyCoreData d = EmergencyCoreData.of(player);
		long last = d.getLastTime();
		if (last <= 0)
			return;
		long now = player.serverLevel().getGameTime();
		boolean noCore = QiData.of(player).getCoreLevel() < 1;
		AspectData a = AspectData.of(player);
		if (noCore && a.getActive() != null && !inGrace(player)) {
			a.setActive(null);
			AspectSwitching.discard(player);
			player.displayClientMessage(Component.literal("Without a Core, your cultivated power fades.").withStyle(ChatFormatting.GRAY), false);
		}
		long until = Math.max(CultivationConfig.EMERGENCY_WINDOW_TICKS.get(), noCore ? graceTicks(player) : 0);
		if (now - last >= until)
			d.setLastTime(0);
	}
}
