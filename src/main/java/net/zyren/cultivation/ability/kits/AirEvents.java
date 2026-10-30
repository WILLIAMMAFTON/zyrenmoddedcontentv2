package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.config.AirConfig;
import net.zyren.cultivation.system.AspectMastery;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Event side of the Air DEFAULT kit: passive fall reduction, pressuried hit charges. */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class AirEvents {
	private AirEvents() {
	}

	// ------------------------------------------------------------------ passive: fall damage

	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && AirKit.defaultPassiveOn(p))
			event.setDamageMultiplier(event.getDamageMultiplier() * (float) (1 - AirConfig.PASSIVE_FALL_REDUCTION.get()));
	}

	// ------------------------------------------------------------------ Attack 1: pressuried hit

	private static final Map<UUID, Integer> PRESSURE = new HashMap<>();

	static void startPressure(ServerPlayer p, int hits) {
		AbilityState.of(p).addBuff(new Buff(AirKit.PressuriedHit.BUFF, AirKit.A, 1.0, 0, false, Long.MAX_VALUE));
		PRESSURE.put(p.getUUID(), hits);
	}

	static int pressureCharges(ServerPlayer p) {
		Integer n = PRESSURE.get(p.getUUID());
		if (n == null)
			return 0;
		long now = p.serverLevel().getGameTime();
		AbilityState st = AbilityState.of(p);
		if (!st.hasBuff(AirKit.PressuriedHit.BUFF, now) || !KitResolver.isCurrent(p, AirKit.PRESSURE) || st.isNullified(AirKit.A, now)) {
			PRESSURE.remove(p.getUUID());
			return 0;
		}
		return n;
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (!WaterEvents.isMelee(event.getSource(), attacker) || !AirKit.isAirActive(attacker))
			return;
		int charges = pressureCharges(attacker);
		if (charges <= 0)
			return;
		if (QiEffects.canAffect(target, false)) {
			QiEffects.knockback(target, attacker, AirConfig.A1_KNOCKBACK.get(), false);
			QiEffects.applyEffect(target, MobEffects.BLINDNESS, AirConfig.A1_BLIND_LEVEL.get(), AirKit.seconds(AirConfig.A1_BLIND_SECONDS.get()), false);
		}
		AspectMastery.onAbilityHit(attacker, AirKit.A, target);
		if (--charges > 0) {
			PRESSURE.put(attacker.getUUID(), charges);
		} else {
			PRESSURE.remove(attacker.getUUID());
			AbilityState.of(attacker).removeBuff(AirKit.PressuriedHit.BUFF);
		}
	}

	// ------------------------------------------------------------------ cleanup

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		PRESSURE.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		PRESSURE.remove(event.getEntity().getUUID());
	}

	public static void resetAll() {
		PRESSURE.clear();
	}
}
