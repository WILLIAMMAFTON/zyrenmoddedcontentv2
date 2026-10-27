package net.zyren.cultivation.ability.kits;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Protective's hooks: damage -> Qi, the damage log for Heavy, U SAID WHAT flight, HOOO RAAA aura.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class ProtectiveEvents {
	private record Hit(long tick, float amount) {
	}

	private static final class Flight {
		UUID target;
		int ticksLeft;
	}

	/** Incoming attack damage (before reductions) per player, for Heavy's heal. */
	private static final Map<UUID, Deque<Hit>> DAMAGE_LOG = new HashMap<>();
	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	/** Longest window anyone asks for; older log entries are dropped. */
	private static final int LOG_KEEP_TICKS = 1200;

	private ProtectiveEvents() {
	}

	// ------------------------------------------------------------------ damage taken

	@SubscribeEvent
	public static void onDamage(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		float incoming = event.getOriginalDamage();
		if (incoming <= 0)
			return;
		long now = player.serverLevel().getGameTime();
		if (event.getSource().getEntity() != null) {
			Deque<Hit> log = DAMAGE_LOG.computeIfAbsent(player.getUUID(), u -> new ArrayDeque<>());
			log.addLast(new Hit(now, incoming));
			while (!log.isEmpty() && log.peekFirst().tick() < now - LOG_KEEP_TICKS)
				log.removeFirst();
		}
	}

	/**
	 * Passive: "Incoming damage points add to Qi pool" — only damage actually TAKEN, after every
	 * reduction (user decision 2026-10-08). Counting blocked damage let players stand in lava for
	 * free Qi forever.
	 */
	@SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
	public static void onDamageTaken(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0)
			return;
		if (AspectData.of(player).getActive() == ProtectiveKit.PR && AbilityState.of(player).passiveActive())
			QiCoreStorage.addQi(player, event.getNewDamage() * AbilityConfig.PROT_PASSIVE_QI_PER_DAMAGE.get());
	}

	/** Attack damage (before reductions) taken within the last {@code window} ticks. */
	static double recentDamage(ServerPlayer player, int window) {
		Deque<Hit> log = DAMAGE_LOG.get(player.getUUID());
		if (log == null)
			return 0;
		long from = player.serverLevel().getGameTime() - window;
		double sum = 0;
		for (Hit h : log)
			if (h.tick() >= from)
				sum += h.amount();
		return sum;
	}

	// ------------------------------------------------------------------ U SAID WHAT flight

	static boolean isFlying(ServerPlayer player) {
		return FLIGHTS.containsKey(player.getUUID());
	}

	static void startFlight(ServerPlayer player, ServerPlayer target) {
		Flight f = new Flight();
		f.target = target.getUUID();
		f.ticksLeft = AbilityConfig.PROT_PB1_MAX_FLIGHT_TICKS.get();
		FLIGHTS.put(player.getUUID(), f);
	}

	private static void tickFlight(ServerPlayer player, Flight f) {
		ServerPlayer target = player.server.getPlayerList().getPlayer(f.target);
		if (target == null || !Targeting.isValid(player, target) || f.ticksLeft-- <= 0) {
			FLIGHTS.remove(player.getUUID());
			player.displayClientMessage(Component.literal("Missed."), true); // no contact effects
			return;
		}
		if (player.distanceTo(target) <= AbilityConfig.PROT_PB1_CONTACT_RANGE.get()) {
			FLIGHTS.remove(player.getUUID());
			QiEffects.dealDamage(player, target, AbilityConfig.PROT_PB1_DAMAGE.get().floatValue());
			if (target.isAlive())
				QiEffects.nullify(target, AspectType.PHYSICAL, AbilityConfig.PROT_PB1_NULLIFY_TICKS.get());
			player.setDeltaMovement(Vec3.ZERO);
			player.hurtMarked = true;
			return;
		}
		Vec3 aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(player.position().add(0, player.getBbHeight() / 2, 0));
		player.setDeltaMovement(aim.normalize().scale(AbilityConfig.PROT_PB1_SPEED.get()));
		player.hurtMarked = true;
		player.fallDistance = 0;
	}

	// ------------------------------------------------------------------ ticks

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		Flight f = FLIGHTS.get(player.getUUID());
		if (f != null)
			tickFlight(player, f);

		// HOOO RAAA: allies in range get the passive cost cut, refreshed every second.
		long now = player.serverLevel().getGameTime();
		if (now % 20 == 0 && AbilityState.of(player).isSustained(ProtectiveKit.HOOO_RAAA)) {
			double cut = AbilityConfig.PROT_A1_CUT.get();
			for (Player ally : ProtectiveKit.alliesNear(player, AbilityConfig.PROT_A1_RADIUS.get()))
				AbilityState.of(ally).setPassiveCostCut(cut, now + 40);
		}
	}

	private static void clear(UUID id) {
		DAMAGE_LOG.remove(id);
		FLIGHTS.remove(id);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer p)
			clear(p.getUUID());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity().getUUID());
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		DAMAGE_LOG.clear();
		FLIGHTS.clear();
	}
}
