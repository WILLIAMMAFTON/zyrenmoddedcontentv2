package net.zyren.cultivation.ability.core;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Timed "your Qi costs are multiplied" debuffs (e.g. Fire Blast of Flames: x2 for 15 s on
 * Water/Air users) and timed discounts (Water My Area Now). Applied to EVERY Qi cost through {@link QiCosts}. Server memory only; the
 * strongest active multiplier wins (they don't stack).
 */
public final class QiCostDebuffs {
	private record Debuff(double multiplier, long until) {
	}

	private static final Map<UUID, Debuff> ACTIVE = new HashMap<>();
	/** Timed cost cuts (e.g. Water My Area Now: -50% for Water allies). multiplier = 1 - fraction. */
	private static final Map<UUID, Debuff> DISCOUNTS = new HashMap<>();

	private QiCostDebuffs() {
	}

	public static void apply(Player target, double multiplier, int ticks, long now) {
		if (multiplier <= 1 || ticks <= 0)
			return;
		Debuff old = ACTIVE.get(target.getUUID());
		if (old == null || old.until() <= now || old.multiplier() <= multiplier)
			ACTIVE.put(target.getUUID(), new Debuff(multiplier, now + ticks));
	}

	/** Timed discount: every Qi cost x (1 - fraction). The largest active discount wins (they don't stack). */
	public static void discount(Player target, double fraction, int ticks, long now) {
		if (fraction <= 0 || ticks <= 0)
			return;
		double mult = Math.max(0, 1 - Math.min(1, fraction));
		Debuff old = DISCOUNTS.get(target.getUUID());
		if (old == null || old.until() <= now || old.multiplier() >= mult)
			DISCOUNTS.put(target.getUUID(), new Debuff(mult, now + ticks));
	}

	/** Current multiplier (1 = none): strongest debuff x strongest discount. */
	public static double multiplier(Player player) {
		return current(ACTIVE, player) * current(DISCOUNTS, player);
	}

	private static double current(Map<UUID, Debuff> map, Player player) {
		Debuff d = map.get(player.getUUID());
		if (d == null)
			return 1;
		if (player.level().getGameTime() >= d.until()) {
			map.remove(player.getUUID());
			return 1;
		}
		return d.multiplier();
	}

	public static void resetAll() {
		ACTIVE.clear();
		DISCOUNTS.clear();
	}
}
