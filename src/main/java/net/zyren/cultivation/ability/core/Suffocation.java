package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.config.AirConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Qi suffocation (Air -> Sky): the target's air bar drains even on land, then they take drowning-style
 * damage. Water Breathing / Conduit Power don't help (it's Qi); Reinforce Body pauses it (user rec
 * 2026-10-29). The air bar is synced by vanilla, so the client sees the bubbles drain. When it ends,
 * vanilla refills the bar as normal.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class Suffocation {
	private static final class State {
		long until;
		int air = -1; // our own counter: vanilla adds air back every tick on land
	}

	private static final Map<UUID, State> ACTIVE = new HashMap<>();

	private Suffocation() {
	}

	/** Suffocate for {@code ticks} (extends a running one). */
	public static void apply(ServerPlayer target, int ticks) {
		if (ticks <= 0)
			return;
		State s = ACTIVE.computeIfAbsent(target.getUUID(), k -> new State());
		s.until = Math.max(s.until, target.serverLevel().getGameTime() + ticks);
	}

	public static boolean isSuffocating(ServerPlayer p) {
		State s = ACTIVE.get(p.getUUID());
		return s != null && p.serverLevel().getGameTime() < s.until;
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		State s = ACTIVE.get(p.getUUID());
		if (s == null)
			return;
		long now = p.serverLevel().getGameTime();
		if (now >= s.until || p.isCreative() || p.isSpectator()) {
			ACTIVE.remove(p.getUUID());
			return;
		}
		if (QiEffects.isReinforced(p))
			return; // paused while Reinforced
		if (s.air < 0)
			s.air = p.getAirSupply();
		s.air = Math.max(0, s.air - AirConfig.SUFFOCATE_AIR_PER_TICK.get());
		p.setAirSupply(s.air);
		if (s.air == 0 && now % 20 == 0) {
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().drown(), AirConfig.SUFFOCATE_DAMAGE.get().floatValue());
		}
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		ACTIVE.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		ACTIVE.remove(event.getEntity().getUUID());
	}

	public static void resetAll() {
		ACTIVE.clear();
	}
}
