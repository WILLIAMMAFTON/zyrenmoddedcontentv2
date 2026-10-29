package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.network.RevealAspectsMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Light's timed effects: Heavy aspect reveal, Reinforce sky climb + Feather Falling, Powerblast 1 pushes.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class LightEvents {
	private record Push(Vec3 dir, double speed, int ticksLeft, boolean first) {
	}

	private record Feather(int level, long endTick) {
	}

	private static final class Climb {
		double lastY;
		int stuckTicks;
		int ticks;
	}

	private static final Map<UUID, Long> REVEAL_UNTIL = new HashMap<>();
	private static final Map<UUID, Climb> CLIMBS = new HashMap<>();
	private static final Map<UUID, Feather> FEATHER = new HashMap<>();
	private static final Map<UUID, Push> PUSHES = new HashMap<>();

	private LightEvents() {
	}

	// ------------------------------------------------------------------ Heavy: reveal

	/** True while this player's Light Heavy reveal runs (Water Moon's aspect sight then stays quiet). */
	public static boolean isRevealing(ServerPlayer player) {
		Long until = REVEAL_UNTIL.get(player.getUUID());
		return until != null && player.serverLevel().getGameTime() < until;
	}

	static void startReveal(ServerPlayer player, int ticks) {
		REVEAL_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + ticks);
		sendReveal(player);
	}

	/** Every player in line of sight, labelled with the aspect they have active right now. */
	private static void sendReveal(ServerPlayer player) {
		Map<Integer, String> labels = new HashMap<>();
		for (ServerPlayer t : player.serverLevel().players()) {
			if (!Targeting.isValid(player, t) || !player.hasLineOfSight(t))
				continue;
			AspectType a = AspectData.of(t).getActive();
			labels.put(t.getId(), a == null ? "No aspect" : a.displayName());
		}
		RevealAspectsMessage.send(player, labels);
	}

	// ------------------------------------------------------------------ Reinforce: climb, flight, Feather Falling

	static void ascendAndFly(ServerPlayer player) {
		int flight = AbilityConfig.LIGHT_REINFORCE_FLIGHT_TICKS.get();
		TimedAttributes.apply(player, NeoForgeMod.CREATIVE_FLIGHT, "light_reinforce_flight", 1, AttributeModifier.Operation.ADD_VALUE, flight);
		int ff = AbilityConfig.LIGHT_REINFORCE_FEATHER_FALLING.get();
		if (ff > 0)
			FEATHER.put(player.getUUID(), new Feather(ff, player.serverLevel().getGameTime() + flight));
		Climb c = new Climb();
		c.lastY = player.getY();
		CLIMBS.put(player.getUUID(), c);
	}

	private static void tickClimb(ServerPlayer player, Climb c) {
		c.ticks++;
		c.stuckTicks = player.getY() - c.lastY < 0.1 ? c.stuckTicks + 1 : 0;
		c.lastY = player.getY();
		if (c.ticks > 1200 || c.stuckTicks > 10 || player.getY() >= player.serverLevel().getMaxBuildHeight() - 2) {
			CLIMBS.remove(player.getUUID()); // reached the sky limit (or a ceiling)
			player.setDeltaMovement(Vec3.ZERO);
			player.hurtMarked = true;
			player.fallDistance = 0;
			// Start them flying (they have creative flight for 2 min): otherwise they just drop
			// ~250 blocks from the sky limit unless they double-tap jump in time.
			if (player.mayFly() && !player.getAbilities().flying) {
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
			}
			return;
		}
		player.setDeltaMovement(0, AbilityConfig.LIGHT_REINFORCE_ASCEND_SPEED.get(), 0);
		player.hurtMarked = true;
		player.fallDistance = 0;
	}

	/** Feather Falling, enchantment-style: 12% less fall damage per level. */
	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		Feather f = FEATHER.get(player.getUUID());
		if (f == null)
			return;
		if (player.serverLevel().getGameTime() >= f.endTick()) {
			FEATHER.remove(player.getUUID());
			return;
		}
		event.setDamageMultiplier(event.getDamageMultiplier() * (float) Math.max(0, 1 - 0.12 * f.level()));
	}

	// ------------------------------------------------------------------ Powerblast 1: push away

	static void pushAway(ServerPlayer user, ServerPlayer target) {
		pushAway(user, target, AbilityConfig.LIGHT_PB1_DISTANCE.get(), AbilityConfig.LIGHT_PB1_SPEED.get());
	}

	/** Shove {@code target} horizontally away from {@code user} at {@code speed} until it has covered about {@code distance} blocks. */
	static void pushAway(ServerPlayer user, ServerPlayer target, double distance, double speed) {
		Vec3 dir = target.position().subtract(user.position()).multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0E-4)
			dir = user.getLookAngle().multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0E-4)
			dir = new Vec3(1, 0, 0);
		int ticks = (int) Math.ceil(distance / speed);
		PUSHES.put(target.getUUID(), new Push(dir.normalize(), speed, ticks, true));
	}

	private static void tickPush(ServerPlayer player, Push p) {
		if (p.ticksLeft() <= 0) {
			PUSHES.remove(player.getUUID());
			return;
		}
		Vec3 v = p.dir().scale(p.speed());
		player.setDeltaMovement(v.x, p.first() ? 0.6 : 0.1, v.z); // lift off, then near-level flight
		player.hurtMarked = true;
		PUSHES.put(player.getUUID(), new Push(p.dir(), p.speed(), p.ticksLeft() - 1, false));
	}

	// ------------------------------------------------------------------ ticks + cleanup

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		UUID id = player.getUUID();
		long now = player.serverLevel().getGameTime();

		Long reveal = REVEAL_UNTIL.get(id);
		if (reveal != null) {
			if (now >= reveal) {
				REVEAL_UNTIL.remove(id);
				RevealAspectsMessage.send(player, Map.of());
			} else if (now % 10 == 0) {
				sendReveal(player);
			}
		}
		Climb c = CLIMBS.get(id);
		if (c != null)
			tickClimb(player, c);
		Push p = PUSHES.get(id);
		if (p != null)
			tickPush(player, p);
	}

	private static void clear(UUID id) {
		REVEAL_UNTIL.remove(id);
		CLIMBS.remove(id);
		FEATHER.remove(id);
		PUSHES.remove(id);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			if (REVEAL_UNTIL.containsKey(player.getUUID()))
				RevealAspectsMessage.send(player, Map.of());
			clear(player.getUUID());
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity().getUUID());
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		REVEAL_UNTIL.clear();
		CLIMBS.clear();
		FEATHER.clear();
		PUSHES.clear();
	}
}
