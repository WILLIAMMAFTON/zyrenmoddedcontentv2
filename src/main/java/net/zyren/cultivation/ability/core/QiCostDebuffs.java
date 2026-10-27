package net.zyren.cultivation.ability.core;

import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Timed "your Qi costs are multiplied" debuffs (e.g. Fire Blast of Flames: x2 for 15 s on
 * Water/Air users). Applied to EVERY Qi cost through {@link QiCosts}. Server memory only; the
 * strongest active multiplier wins (they don't stack).
 */
public final class QiCostDebuffs {
	private record Debuff(double multiplier, long until) {
	}

	private static final Map<UUID, Debuff> ACTIVE = new HashMap<>();

	private QiCostDebuffs() {
	}

	public static void apply(Player target, double multiplier, int ticks, long now) {
		if (multiplier <= 1 || ticks <= 0)
			return;
		Debuff old = ACTIVE.get(target.getUUID());
		if (old == null || old.until() <= now || old.multiplier() <= multiplier)
			ACTIVE.put(target.getUUID(), new Debuff(multiplier, now + ticks));
	}

	/** Current multiplier (1 = none). */
	public static double multiplier(Player player) {
		Debuff d = ACTIVE.get(player.getUUID());
		if (d == null)
			return 1;
		if (player.level().getGameTime() >= d.until()) {
			ACTIVE.remove(player.getUUID());
			return 1;
		}
		return d.multiplier();
	}

	public static void resetAll() {
		ACTIVE.clear();
	}
}
