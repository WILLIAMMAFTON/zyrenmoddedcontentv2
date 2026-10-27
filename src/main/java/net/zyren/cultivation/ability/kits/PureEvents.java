package net.zyren.cultivation.ability.kits;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pure's hit hooks: BURNN's 60-damage follow-up and the level 5 passive (absorb Qi-enhanced
 * blows, release them on your next Qi-enhanced attack). Runs before the main damage pipeline,
 * so total-output multipliers still apply afterwards.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class PureEvents {
	private record BurnTarget(UUID target, long until) {
	}

	private static final Map<UUID, BurnTarget> BURN = new HashMap<>();
	/** Level 5: Qi absorbed from Qi-enhanced blows, waiting for the user's next Qi-enhanced attack. */
	private static final Map<UUID, Double> STORED = new HashMap<>();

	private PureEvents() {
	}

	static void markBurnTarget(ServerPlayer user, ServerPlayer target, int windowTicks) {
		BURN.put(user.getUUID(), new BurnTarget(target.getUUID(), user.serverLevel().getGameTime() + windowTicks));
	}

	private static boolean isMelee(DamageSource s, ServerPlayer attacker) {
		return s.is(DamageTypes.PLAYER_ATTACK) && s.getDirectEntity() == attacker && !QiEffects.isDealingQiDamage();
	}

	/** Qi ability damage, or a melee hit carrying an armed Attack 1 / Heavy style empowerment. */
	private static boolean isQiEnhanced(DamageSource s, ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target, long now) {
		if (s.is(DamageTypes.THORNS))
			return false; // thorns reflected during a Qi hit are not themselves Qi damage
		return QiEffects.isDealingQiDamage() || isMelee(s, attacker) && AbilityState.of(attacker).empoweredFor(target, now) != null;
	}

	private static boolean level5Active(ServerPlayer p) {
		return AspectData.of(p).getActive() == PureKit.P && AspectData.of(p).getPureLevel() >= 5 && AbilityState.of(p).passiveActive();
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onDamage(LivingDamageEvent.Pre event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity())
			return;
		long now = attacker.serverLevel().getGameTime();
		DamageSource source = event.getSource();
		boolean qiHit = isQiEnhanced(source, attacker, event.getEntity(), now);

		// Victim on Pure level 5 absorbs half the Qi behind a Qi-enhanced blow.
		if (qiHit && event.getEntity() instanceof ServerPlayer victim && level5Active(victim)) {
			double absorbed = AbilityState.of(attacker).lastPaidQi() * AbilityConfig.PURE_L5_ABSORB_FRACTION.get();
			AbilityState.of(attacker).clearLastPaid(); // one absorb per cast, not per hit it produces
			if (absorbed > 0) {
				STORED.merge(victim.getUUID(), absorbed, Double::sum);
				victim.displayClientMessage(Component.literal("Absorbed " + Math.round(absorbed) + " Qi into your next Qi attack."), true);
			}
		}

		// BURNN follow-up: your next melee hit on the burned-to player deals 60.
		BurnTarget b = BURN.get(attacker.getUUID());
		if (b != null) {
			if (now > b.until()) {
				BURN.remove(attacker.getUUID());
			} else if (isMelee(source, attacker) && event.getEntity().getUUID().equals(b.target())
					&& AspectData.of(attacker).getActive() == PureKit.P) {
				BURN.remove(attacker.getUUID());
				// If an empowered hit (e.g. EEEEEEEE = 50) landed on this same blow, keep the bigger one.
				event.setNewDamage(Math.max(event.getNewDamage(), AbilityConfig.PURE_HEAVY_DAMAGE.get().floatValue()));
			}
		}
	
		// Attacker on Pure level 5 releases what they stored.
		if (qiHit && level5Active(attacker)) {
			Double stored = STORED.remove(attacker.getUUID());
			if (stored != null && stored > 0)
				event.setNewDamage((float) (event.getNewDamage() + stored * AbilityConfig.PURE_L5_DAMAGE_PER_QI.get()));
		}
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer p) {
			BURN.remove(p.getUUID());
			STORED.remove(p.getUUID());
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		BURN.remove(event.getEntity().getUUID());
		STORED.remove(event.getEntity().getUUID());
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		BURN.clear();
		STORED.clear();
	}
}
