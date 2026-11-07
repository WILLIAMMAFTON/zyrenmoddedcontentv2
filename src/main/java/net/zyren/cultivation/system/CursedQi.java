package net.zyren.cultivation.system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.CorruptionData;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;

/**
 * CURSED core type (claude/core-types.md, decided 2026-11-06).
 *
 *  - Immune to corruption: the corruption meter never fills. Every corruption point it would
 *    have gained (areas, Warden hits, chorus fruit, Corrupted-Qi food, meditation multiplier
 *    included) is stored as cursed_qi_per_corruption (default 1) Cursed Qi instead.
 *  - Core Resistance and the Purifying Crystal do nothing for Cursed players (no reduction, no wear).
 *  - The core can't turn Corrupted (meter, or Pure Qi clashing with a Poisoned core). It can still
 *    become Poisoned. Admin /qi corestate still works.
 *  - The container (MCreator variable cursed_qi) is capped at the core's max Qi, so it shrinks
 *    with the core and empties when the core breaks.
 *  - Usable ONLY by the Cursed aspect, which doesn't exist yet: for now it just fills.
 *
 * Collection follows the same rules as corruption: only a formed, PURE-state core collects.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class CursedQi {
	private CursedQi() {
	}

	/** CURSED core type with a formed core. Client-safe (synced vars). */
	public static boolean active(Player player) {
		QiData q = QiData.of(player);
		return q.getCoreType() == CoreType.CURSED && q.getCoreLevel() >= 1;
	}

	public static double cap(Player player) {
		return QiData.of(player).getCoreMax();
	}

	/** Corruption about to be gained. @return true if it was turned into Cursed Qi (caller stops). */
	static boolean absorb(ServerPlayer p, double corruption) {
		if (!active(p))
			return false;
		add(p, corruption * CultivationConfig.CURSED_QI_PER_CORRUPTION.get());
		return true;
	}

	/** Adds Cursed Qi, capped at max Qi. @return amount actually added. */
	public static double add(ServerPlayer p, double amount) {
		QiData q = QiData.of(p);
		double before = q.getCursedQi();
		double after = Math.min(cap(p), before + Math.max(0, amount));
		if (after > before)
			q.setCursedQi(after);
		return Math.max(0, after - before);
	}

	/** For the future Cursed aspect: spends only if the full amount is there. */
	public static boolean trySpend(ServerPlayer p, double amount) {
		QiData q = QiData.of(p);
		if (amount < 0 || q.getCursedQi() < amount)
			return false;
		q.setCursedQi(q.getCursedQi() - amount);
		return true;
	}

	public static void set(ServerPlayer p, double amount) {
		QiData.of(p).setCursedQi(Math.min(cap(p), Math.max(0, amount)));
	}

	/** Corruption already on the meter (old saves / just became Cursed) turns into Cursed Qi. */
	static void convertMeter(ServerPlayer p) {
		if (!active(p))
			return;
		CorruptionData c = CorruptionData.of(p);
		double existing = c.getCorruption();
		if (existing <= 0)
			return;
		c.setCorruption(0);
		add(p, existing * CultivationConfig.CURSED_QI_PER_CORRUPTION.get());
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer p)
			convertMeter(p);
	}

	/** Once a second: keep the container within the cap (core max can drop: level loss, break). */
	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 20 != 0)
			return;
		QiData q = QiData.of(p);
		if (q.getCursedQi() > cap(p))
			q.setCursedQi(cap(p));
	}
}
