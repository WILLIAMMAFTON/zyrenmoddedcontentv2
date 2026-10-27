package net.zyren.cultivation.system;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;

/**
 * A complete Core break (source of truth: "resets the Core/cultivation and forces the player
 * to restart cultivation"; "If Corrupted Core breaks: Player dies").
 *
 * Decided 2026-10-06: a break resets the CORE only (no_qi, core level, Qi, max, cultivated,
 * core type back to PURE). Aspects and their levels are kept. The active aspect is cleared,
 * since there's no core to channel it.
 */
public final class CoreBreak {
	private CoreBreak() {
	}

	/** @return true if the player had a core to break */
	public static boolean breakCore(ServerPlayer player, String cause) {
		return breakCore(player, cause, 0);
	}

	/**
	 * Core Annihilation (Emergency Core Breakdown twice within the window): the same reset, but the
	 * active aspect is kept (aspects stay usable for a while) and the time is recorded.
	 */
	static boolean annihilate(ServerPlayer player, long gameTime) {
		return breakCore(player, "Core Annihilation", gameTime);
	}

	private static boolean breakCore(ServerPlayer player, String cause, long annihilatedAt) {
		QiData q = QiData.of(player);
		// A core break needs an actual core (user decision 2026-10-07): pre-core no_qi progress is
		// never wiped and no "core broken" advancement is given.
		if (q.getCoreLevel() < 1)
			return false;
		AntiFarm.markLevelReached(player); // re-cultivating doesn't re-grant breakthrough choices
		boolean corrupted = q.getCoreType() == CoreType.CORRUPTED;
		int levelBefore = q.getCoreLevel();
		q.resetToInitialState();
		// Corruption belongs to the core: a re-formed core starts clean (it was carried over before).
		net.zyren.cultivation.data.CorruptionData.of(player).setCorruption(0);
		// A normal break ends any breakdown window; an annihilation starts the aspect grace period.
		net.zyren.cultivation.data.EmergencyCoreData.of(player).setLastTime(annihilatedAt);
		if (annihilatedAt <= 0) {
			AspectData.of(player).setActive(null);
			AspectSwitching.discard(player);
		}
		player.displayClientMessage(Component.literal("Your Qi Core shatters! (" + cause + ")"), false);
		CultivationAdvancements.onCoreBroken(player);
		if (corrupted) {
			CoreCrater.explode(player, levelBefore); // crater: radius = core level x 30 (decided 2026-10-07)
			player.kill(); // "If Corrupted Core breaks: Player dies."
		}
		return true;
	}
}
