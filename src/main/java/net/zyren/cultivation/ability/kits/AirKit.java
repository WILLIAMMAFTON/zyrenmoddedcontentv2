package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.AirConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * AIR — DEFAULT kit (ASPECT GUIDE, user "use rec" 2026-10-29). Values: air-server.toml.
 * No Reinforce Body in the guide: Light's is used (framework fallback).
 *
 *   Passive      5 Qi/min: Speed II, -85% fall damage, step height 2, no knockback.
 *   Attack 1     pressuried hit: 15 Qi, 60 s. Next 4 hits: target Knockback V + Blindness II.
 *   Heavy        stabby wind: 30 Qi, 60 s. Next hit x2 damage + stun.
 *   Powerblast 1 piss off mate: 40 Qi, 110 s. Nearest unallied player thrown 15; hitting stone = 10 damage.
 *   Powerblast 2 sky high: 90 Qi, 120 s. Nearest unallied player within 15 thrown 35 up + 4 damage.
 *   Ability 1    my domain bro: 100 Qi, 280 s. Radius 30: elemental 4 + Slowness 120 s, others 8.
 *   Ability 2    violence was the option: 200 Qi, 380 s. Radius 50: 18 damage + Nausea 60 s.
 */
public final class AirKit {
	static final AspectType A = AspectType.AIR;
	static final String KB = "air_passive_kb", STEP = "air_passive_step";

	private AirKit() {
	}

	static final Passive PASSIVE = new Passive();
	static final PressuriedHit PRESSURE = new PressuriedHit();

	public static AspectKit create() {
		return new AspectKit(A, PASSIVE, PRESSURE, new StabbyWind(), new PissOffMate(), new SkyHigh(), new MyDomain(), new Violence());
	}

	// ------------------------------------------------------------------ helpers (shared with the sub-aspects)

	static int seconds(int s) {
		return s * 20;
	}

	static List<ServerPlayer> unalliedWithin(ServerPlayer user, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer o : user.serverLevel().players())
			if (Targeting.isValid(user, o) && !Targeting.isAllied(user, o) && o.distanceTo(user) <= radius)
				out.add(o);
		return out;
	}

	static ServerPlayer nearestUnallied(ServerPlayer user, double range) {
		ServerPlayer best = null;
		for (ServerPlayer o : unalliedWithin(user, range))
			if (best == null || o.distanceToSqr(user) < best.distanceToSqr(user))
				best = o;
		return best;
	}

	/** Throw {@code target} away from the user, horizontally (lift 0) or with an upward kick. */
	static void throwAway(ServerPlayer user, LivingEntity target, double blocks, double lift, BiConsumer<LivingEntity, BlockState> onImpact) {
		Motion.push(target, user.position(), blocks, AirConfig.THROW_SPEED.get(), lift, onImpact);
	}

	static boolean isAirActive(ServerPlayer p) {
		return AspectData.of(p).getActive() == A;
	}

	/** The default Air passive is running (Sky and Storm use it too; Gale replaces it). */
	static boolean defaultPassiveOn(Player p) {
		return net.zyren.cultivation.ability.core.AbilityState.of(p).passiveActive()
				&& net.zyren.cultivation.ability.core.KitResolver.passiveFor(p) == PASSIVE;
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(AirConfig.PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, AirConfig.PASSIVE_SPEED.get(), 50, false);
			TimedAttributes.apply(p, Attributes.KNOCKBACK_RESISTANCE, KB, 1.0, AttributeModifier.Operation.ADD_VALUE, 40);
			double step = AirConfig.PASSIVE_STEP_HEIGHT.get() - 0.6;
			if (step > 0)
				TimedAttributes.apply(p, Attributes.STEP_HEIGHT, STEP, step, AttributeModifier.Operation.ADD_VALUE, 40);
		}
	}

	// ------------------------------------------------------------------ Attack 1

	/** Charges in AirEvents, tied to a buff (form change / nullify end them). */
	static final class PressuriedHit extends Ability {
		static final String BUFF = "air_pressure";

		PressuriedHit() {
			super(A, AbilitySlot.ATTACK_1, "pressuried hit");
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.A1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return AirEvents.pressureCharges(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int hits = AirConfig.A1_HITS.get();
			AirEvents.startPressure(p, hits);
			return ActionResult.ok("Pressuried hit: your next " + hits + " hits blow them away.");
		}
	}

	// ------------------------------------------------------------------ Heavy

	static final class StabbyWind extends Ability {
		StabbyWind() {
			super(A, AbilitySlot.HEAVY, "stabby wind");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.A2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Stabby wind ready.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (QiEffects.canAffect(target, false))
				Stun.apply(target, AirConfig.A2_STUN_TICKS.get());
			return (float) (damage * AirConfig.A2_MULTIPLIER.get());
		}
	}

	// ------------------------------------------------------------------ Powerblast 1

	static final class PissOffMate extends Ability {
		PissOffMate() {
			super(A, AbilitySlot.POWERBLAST_1, "piss off mate");
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.PB1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			ServerPlayer t = nearestUnallied(p, AirConfig.PB1_RANGE.get());
			if (t == null)
				return ActionResult.fail("No one within " + AirConfig.PB1_RANGE.get() + " blocks.");
			return QiEffects.canAffect(t, false) ? ActionResult.ok("") : ActionResult.fail(t.getGameProfile().getName() + " is Reinforced.");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = nearestUnallied(p, AirConfig.PB1_RANGE.get());
			if (t == null || !QiEffects.canAffect(t, false))
				return ActionResult.fail("Target lost.");
			throwAway(p, t, AirConfig.PB1_THROW.get(), 0, (hit, block) -> {
				if (FireWorld.isStoneType(block) && p.isAlive())
					QiEffects.dealDamage(p, hit, AirConfig.STONE_IMPACT_DAMAGE.get().floatValue());
			});
			return ActionResult.ok("Piss off, " + t.getGameProfile().getName() + ".");
		}
	}

	// ------------------------------------------------------------------ Powerblast 2

	static final class SkyHigh extends Ability {
		SkyHigh() {
			super(A, AbilitySlot.POWERBLAST_2, "sky high");
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.PB2_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			ServerPlayer t = nearestUnallied(p, AirConfig.PB2_RANGE.get());
			if (t == null)
				return ActionResult.fail("No one within " + AirConfig.PB2_RANGE.get() + " blocks.");
			return QiEffects.canAffect(t, false) ? ActionResult.ok("") : ActionResult.fail(t.getGameProfile().getName() + " is Reinforced.");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = nearestUnallied(p, AirConfig.PB2_RANGE.get());
			if (t == null || !QiEffects.canAffect(t, false))
				return ActionResult.fail("Target lost.");
			QiEffects.dealDamage(p, t, AirConfig.PB2_DAMAGE.get().floatValue());
			Motion.launchUp(t, AirConfig.PB2_HEIGHT.get());
			return ActionResult.ok("Sky high, " + t.getGameProfile().getName() + "!");
		}
	}

	// ------------------------------------------------------------------ Ability 1

	static final class MyDomain extends Ability {
		MyDomain() {
			super(A, AbilitySlot.ABILITY_1, "my domain bro");
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.AB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int elemental = 0, other = 0;
			for (ServerPlayer t : unalliedWithin(p, AirConfig.AB1_RADIUS.get())) {
				if (QiData.of(t).getCoreLevel() < 1)
					continue; // Qi users only
				AspectType a = AspectData.of(t).getActive();
				if (a != null && a.isElemental()) {
					QiEffects.dealDamage(p, t, AirConfig.AB1_ELEMENTAL_DAMAGE.get().floatValue());
					QiEffects.applyEffect(t, MobEffects.MOVEMENT_SLOWDOWN, AirConfig.AB1_SLOWNESS.get(), seconds(AirConfig.AB1_SECONDS.get()), false);
					elemental++;
				} else {
					QiEffects.dealDamage(p, t, AirConfig.AB1_OTHER_DAMAGE.get().floatValue());
					other++;
				}
			}
			return ActionResult.ok("My domain, bro. (" + elemental + " elemental, " + other + " other)");
		}
	}

	// ------------------------------------------------------------------ Ability 2

	static final class Violence extends Ability {
		Violence() {
			super(A, AbilitySlot.ABILITY_2, "violence was the option");
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.AB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int n = 0;
			for (ServerPlayer t : unalliedWithin(p, AirConfig.AB2_RADIUS.get())) {
				QiEffects.dealDamage(p, t, AirConfig.AB2_DAMAGE.get().floatValue());
				QiEffects.applyEffect(t, MobEffects.CONFUSION, 1, seconds(AirConfig.AB2_SECONDS.get()), false);
				n++;
			}
			p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.PLAYERS, 2f, 1f);
			return ActionResult.ok("Violence was the option" + (n > 0 ? ": " + n + " hit." : "."));
		}
	}
}
