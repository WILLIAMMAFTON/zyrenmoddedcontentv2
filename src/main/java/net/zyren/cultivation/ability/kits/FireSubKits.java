package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.SubAspectKit;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.FireConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;

import static net.zyren.cultivation.ability.kits.FireKit.fireCost;
import static net.zyren.cultivation.ability.kits.FireKit.seconds;

/**
 * Fire's sub-aspects (ASPECT GUIDE + user decisions 2026-10-27). Slots a sub-aspect doesn't list
 * (PB1 / PB2 for all three; the passive for Volcanic) fall back to the DEFAULT Fire kit.
 * Every ability uses its parent FIRE + the same slot, so cooldowns are shared with the normal form.
 * "Can break core" follows the sheet: Attack 1/2 no, Ability 1/2 yes (the framework default).
 * Event-side logic lives in FireSubEvents.
 */
public final class FireSubKits {
	static final AspectType F = AspectType.FIRE;

	private FireSubKits() {
	}

	// ================================================================== BODY

	static final SubAspectType BODY = SubAspectType.FIRE_BODY;
	static final BodyPassive BODY_PASSIVE = new BodyPassive();
	static final BodyFission FISSION = new BodyFission();
	static final BodyHeatingTouch HEATING_TOUCH = new BodyHeatingTouch();

	public static SubAspectKit body() {
		return new SubAspectKit(BODY, BODY_PASSIVE, new BodyAttack1(), FISSION, new BodyLine(), HEATING_TOUCH)
				.onLeave(FireSubEvents::leaveBody);
	}

	static boolean bodyPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == BODY_PASSIVE;
	}

	/** In lava / fire / the Nether: Regeneration I, Strength II, Speed I. Qi only while a condition holds. */
	static final class BodyPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return FireKit.inFireOrLava(p) || p.level().dimension() == Level.NETHER;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(FireConfig.BODY_PASSIVE_QI_PER_SECOND.get(), 1);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, 1);
			EffectHelper.applyPassive(p, MobEffects.DAMAGE_BOOST, 2);
			EffectHelper.applyPassive(p, MobEffects.MOVEMENT_SPEED, 1);
		}
	}

	static final class BodyAttack1 extends Ability {
		BodyAttack1() {
			super(F, AbilitySlot.ATTACK_1, "did i say u could be here?", BODY);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.BODY_A1_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.BODY_A1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return bodyPassiveOn(p) ? ActionResult.ok("") : ActionResult.fail("Needs your Body passive (stand in lava, fire, or be in the Nether).");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (QiEffects.canAffect(target, false)) {
				FireKit.ignite(target);
				if (target instanceof ServerPlayer t && AspectData.of(t).getActive() == F && attacker.level().dimension() == Level.OVERWORLD)
					FireSubEvents.sendToNether(t); // after this hit resolves
			}
			return (float) (damage + FireConfig.BODY_A1_EXTRA.get());
		}
	}

	/** Fission blade: Strength II + Haste III; sword/axe hits get Sharpness II + Fire Aspect (simulated in FireSubEvents). */
	static final class BodyFission extends Ability {
		static final String BUFF = "fire_body_fission";

		BodyFission() {
			super(F, AbilitySlot.HEAVY, "Fission blade", BODY);
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.BODY_A2_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.BODY_A2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int ticks = seconds(FireConfig.BODY_A2_SECONDS.get());
			AbilityState.of(p).addBuff(new Buff(BUFF, F, 1.0, 0, false, p.serverLevel().getGameTime() + ticks));
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 2, ticks, true);
			EffectHelper.apply(p, MobEffects.DIG_SPEED, 3, ticks, true); // "attack speed 3" = Haste III (project convention)
			return ActionResult.ok("Fission blade: your sword or axe burns.");
		}
	}

	/** A line straight from hell: forward charge, scaled by Fire-active players within 50. */
	static final class BodyLine extends Ability {
		BodyLine() {
			super(F, AbilitySlot.ABILITY_1, "a line straight from hell", BODY);
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.BODY_AB1_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.BODY_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return FireSubEvents.isCharging(p) ? ActionResult.fail("Already charging.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int r = FireConfig.BODY_AB1_COUNT_RADIUS.get();
			int n = 0;
			for (ServerPlayer o : p.serverLevel().players()) // allies and the user count too
				if (o.isAlive() && !o.isSpectator() && o.distanceTo(p) <= r && AspectData.of(o).getActive() == F)
					n++;
			int range = Math.min(FireConfig.BODY_AB1_MAX_RANGE.get(), FireConfig.BODY_AB1_BASE_RANGE.get() + n * FireConfig.BODY_AB1_RANGE_PER_PLAYER.get());
			double dmg = FireConfig.BODY_AB1_BASE_DAMAGE.get() + n * FireConfig.BODY_AB1_DAMAGE_PER_PLAYER.get();
			FireSubEvents.startCharge(p, range, dmg);
			return ActionResult.ok("Hellfire charge! (" + n + " Fire user" + (n == 1 ? "" : "s") + ": " + range + " blocks, " + (int) dmg + " damage)");
		}
	}

	/** Heating Touch: aura; Water/Air players inside are nullified while inside (bypasses Reinforce). */
	static final class BodyHeatingTouch extends Ability {
		BodyHeatingTouch() {
			super(F, AbilitySlot.ABILITY_2, "Heating Touch", BODY);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(FireConfig.BODY_AB2_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.BODY_AB2_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.BODY_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Heating Touch: Water and Air Qi can't survive near you.");
		}

		@Override
		public void onStop(ServerPlayer p) {
			FireSubEvents.stopHeatingTouch(p);
		}
	}

	// ================================================================== VOLCANIC

	static final SubAspectType VOLCANIC = SubAspectType.FIRE_VOLCANIC;
	static final VolcExplosive EXPLOSIVE = new VolcExplosive();
	static final VolcDoomFists DOOM_FISTS = new VolcDoomFists();
	static final VolcDoom DOOM = new VolcDoom();
	static final VolcWorldBurn WORLD_BURN = new VolcWorldBurn();

	public static SubAspectKit volcanic() {
		return new SubAspectKit(VOLCANIC, null, EXPLOSIVE, DOOM_FISTS, DOOM, WORLD_BURN); // passive NONE -> default Fire passive
	}

	static final class VolcExplosive extends Ability {
		static final String BUFF = "fire_volc_explosive";

		VolcExplosive() {
			super(F, AbilitySlot.ATTACK_1, "explosive magma", VOLCANIC);
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.VOLC_A1_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.VOLC_A1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			AbilityState.of(p).addBuff(new Buff(BUFF, F, 1.0, 0, false, p.serverLevel().getGameTime() + seconds(FireConfig.VOLC_A1_SECONDS.get())));
			return ActionResult.ok("Explosive magma: everything you hit erupts.");
		}
	}

	static final class VolcDoomFists extends Ability {
		static final String BUFF = "fire_volc_doom_fists";

		VolcDoomFists() {
			super(F, AbilitySlot.HEAVY, "explosive flaming fists for doom", VOLCANIC);
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.VOLC_A2_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.VOLC_A2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			AbilityState.of(p).addBuff(new Buff(BUFF, F, 1.0, 0, false, p.serverLevel().getGameTime() + seconds(FireConfig.VOLC_A2_SECONDS.get())));
			return ActionResult.ok("Fists of doom: every hit with an item in hand explodes.");
		}
	}

	/** Volcanic doom: sustained; effects only while standing in lava (FireSubEvents). */
	static final class VolcDoom extends Ability {
		VolcDoom() {
			super(F, AbilitySlot.ABILITY_1, "volcanic doom", VOLCANIC);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(FireConfig.VOLC_AB1_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.VOLC_AB1_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.VOLC_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Volcanic doom: the lava answers you.");
		}
	}

	static final class VolcWorldBurn extends Ability {
		static final String BUFF = "fire_volc_world_burn";

		VolcWorldBurn() {
			super(F, AbilitySlot.ABILITY_2, "the world will burn", VOLCANIC);
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.VOLC_AB2_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.VOLC_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int ticks = seconds(FireConfig.VOLC_AB2_SECONDS.get());
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 3, ticks, true);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, 2, ticks, true);
			AbilityState.of(p).addBuff(new Buff(BUFF, F, 1.0, 0, false, p.serverLevel().getGameTime() + ticks));
			FireWorld.worldBurn(p.serverLevel(), p.blockPosition(), FireConfig.VOLC_AB2_RADIUS.get());
			p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.BLAZE_AMBIENT, net.minecraft.sounds.SoundSource.PLAYERS, 3f, 0.4f);
			return ActionResult.ok("The world will burn.");
		}
	}

	// ================================================================== INFERNO

	static final SubAspectType INFERNO = SubAspectType.FIRE_INFERNO;
	static final InfernoPassive INFERNO_PASSIVE = new InfernoPassive();
	static final InfernoHeat HEAT = new InfernoHeat();
	static final InfernoHeart HEART = new InfernoHeart();
	static final InfernoUnbound UNBOUND = new InfernoUnbound();

	public static SubAspectKit inferno() {
		return new SubAspectKit(INFERNO, INFERNO_PASSIVE, HEAT, new InfernoRaise(), HEART, UNBOUND)
				.onLeave(FireSubEvents::leaveInferno);
	}

	public static boolean isInfernoPassive(AspectPassive p) {
		return p == INFERNO_PASSIVE;
	}

	/** 15 Qi / 5 min. Covered by fire: Regen II + 10 hearts (armour: Regen I + 5), Water/Air Qi attacks -50%. Standing in fire: -10% costs (FireKit.fireCost). */
	static final class InfernoPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(FireConfig.INF_PASSIVE_QI_PER_5MIN.get(), 300);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			FireSubEvents.refreshInfernoPassive(p);
		}
	}

	/** The inferno's heat: next 3 unarmed hits; a weapon in hand ends it with a refund. */
	static final class InfernoHeat extends Ability {
		InfernoHeat() {
			super(F, AbilitySlot.ATTACK_1, "the infernos heat", INFERNO);
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.INF_A1_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.INF_A1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return FireKit.isWeapon(p.getMainHandItem()) ? ActionResult.fail("Only with bare fists.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			FireSubEvents.startHeat(p, FireConfig.INF_A1_HITS.get());
			return ActionResult.ok("The inferno's heat fills your fists.");
		}
	}

	/** Raising the heat: damage Water/Air/Space->Matter players around; Strength III to you and Fire-active allies. */
	static final class InfernoRaise extends Ability {
		InfernoRaise() {
			super(F, AbilitySlot.HEAVY, "raising the heat", INFERNO);
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.INF_A2_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.INF_A2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int r = FireConfig.INF_A2_RADIUS.get();
			int ticks = seconds(FireConfig.INF_A2_SECONDS.get());
			int hit = 0;
			for (ServerPlayer o : p.serverLevel().players()) {
				if (o == p || !o.isAlive() || o.isSpectator() || o.distanceTo(p) > r)
					continue;
				if (FireSubEvents.isWaterAirOrMatter(o)) {
					QiEffects.dealDamage(p, o, FireConfig.INF_A2_DAMAGE.get().floatValue());
					hit++;
				}
				if (Targeting.isAllied(p, o) && AspectData.of(o).getActive() == F)
					EffectHelper.apply(o, MobEffects.DAMAGE_BOOST, 3, ticks, true);
			}
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 3, ticks, true);
			return ActionResult.ok("Raising the heat" + (hit > 0 ? ": " + hit + " scorched." : "."));
		}
	}

	static final class InfernoHeart extends Ability {
		InfernoHeart() {
			super(F, AbilitySlot.ABILITY_1, "heart", INFERNO);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(FireConfig.INF_AB1_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.INF_AB1_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.INF_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Heart of the inferno.");
		}
	}

	static final class InfernoUnbound extends Ability {
		InfernoUnbound() {
			super(F, AbilitySlot.ABILITY_2, "unbound", INFERNO);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(FireConfig.INF_AB2_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.INF_AB2_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.INF_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			FireSubEvents.surroundWithFire(p);
			return ActionResult.ok("Unbound.");
		}
	}
}
