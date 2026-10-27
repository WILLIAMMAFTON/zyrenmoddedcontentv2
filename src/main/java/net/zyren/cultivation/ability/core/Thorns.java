package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thorns granted by abilities (Dark, Physical, Lightning, Protective...). Thorns is an armor
 * enchantment in vanilla, so it's emulated the same way (decided 2026-10-06): each time the
 * player is hurt by an attacker, there's a 15% chance per level to deal 1-4 thorns damage back.
 * The highest active level applies; it stacks with real Thorns armor as two separate rolls.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class Thorns {
	private record Grant(int level, long endTick) {
	}

	private static final Map<UUID, Map<String, Grant>> GRANTS = new HashMap<>();

	private Thorns() {
	}

	/** Grants Thorns {@code level} for {@code ticks}. {@code id} lets a source refresh its own grant. */
	public static void grant(ServerPlayer player, String id, int level, int ticks) {
		if (level <= 0 || ticks <= 0)
			return;
		GRANTS.computeIfAbsent(player.getUUID(), u -> new HashMap<>())
				.put(id, new Grant(Math.min(level, EffectHelper.MAX_LEVEL), player.serverLevel().getGameTime() + ticks));
	}

	public static void revoke(ServerPlayer player, String id) {
		Map<String, Grant> m = GRANTS.get(player.getUUID());
		if (m != null)
			m.remove(id);
	}

	public static int level(ServerPlayer player) {
		Map<String, Grant> m = GRANTS.get(player.getUUID());
		if (m == null)
			return 0;
		long now = player.serverLevel().getGameTime();
		m.values().removeIf(g -> g.endTick() <= now);
		return m.values().stream().mapToInt(Grant::level).max().orElse(0);
	}

	@SubscribeEvent
	public static void onHurt(LivingDamageEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer victim) || event.getNewDamage() <= 0)
			return;
		if (event.getSource().is(DamageTypes.THORNS) || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim)
			return;
		int level = level(victim);
		if (level <= 0)
			return;
		var random = victim.getRandom();
		if (random.nextFloat() < 0.15f * level)
			attacker.hurt(victim.damageSources().thorns(victim), 1 + random.nextInt(4));
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			GRANTS.remove(player.getUUID());
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		GRANTS.clear();
	}
}
