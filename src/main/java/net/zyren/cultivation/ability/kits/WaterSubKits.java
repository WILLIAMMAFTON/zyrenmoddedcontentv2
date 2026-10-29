package net.zyren.cultivation.ability.kits;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.SubAspectKit;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.config.WaterConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;

import static net.zyren.cultivation.ability.kits.WaterKit.isNight;
import static net.zyren.cultivation.ability.kits.WaterKit.seconds;

/**
 * Water's sub-aspects (ASPECT GUIDE + user decisions 2026-10-29: "run with your recommendations").
 * Slots a sub-aspect doesn't list fall back to the DEFAULT Water kit (Freezing: PB2; Body: PB1 + PB2;
 * every Reinforce). Every ability uses its parent WATER + the same slot, so cooldowns are shared with
 * the normal form. "Can break core": Attack 1/2 and Powerblasts no, Ability 1/2 yes (framework default,
 * matches the sheet). Event-side logic lives in WaterSubEvents.
 */
public final class WaterSubKits {
	static final AspectType W = AspectType.WATER;

	private WaterSubKits() {
	}

	/** Water / Fire users, or Space users in the Matter form (Freezing core's targets). */
	static boolean isWaterFireOrMatter(ServerPlayer p) {
		AspectType a = AspectData.of(p).getActive();
		return a == AspectType.WATER || a == AspectType.FIRE || KitResolver.formInUse(p) == SubAspectType.SPACE_MATTER;
	}

	static void snow(ServerPlayer at) {
		at.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, at.getX(), at.getY() + 1, at.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
	}

	// ================================================================== FREEZING

	static final SubAspectType FREEZING = SubAspectType.WATER_FREEZING;
	static final FreezingPassive FRZ_PASSIVE = new FreezingPassive();
	static final FreezingCore FRZ_CORE = new FreezingCore();
	static final FreezingBlast FRZ_BLAST = new FreezingBlast();
	static final WinteryDeath WINTERY = new WinteryDeath();
	static final FreezingApocalypse APOCALYPSE = new FreezingApocalypse();

	public static SubAspectKit freezing() {
		return new SubAspectKit(FREEZING, FRZ_PASSIVE, new FreezingAttack(), FRZ_CORE, FRZ_BLAST, WINTERY, APOCALYPSE)
				.onLeave(WaterSubEvents::leaveFreezing);
	}

	static boolean freezingPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == FRZ_PASSIVE;
	}

	/** In the cold biomes (config list): Absorption IV, Regeneration II, Speed III, no suffocation / freeze damage. The Nether debuff is in WaterSubEvents. */
	static final class FreezingPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return WaterSubEvents.inColdBiome(p);
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(WaterConfig.FRZ_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			EffectHelper.applyPassive(p, MobEffects.ABSORPTION, WaterConfig.FRZ_ABSORPTION.get());
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, WaterConfig.FRZ_REGEN.get());
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, WaterConfig.FRZ_SPEED.get(), 50, false);
		}
	}

	static final class FreezingAttack extends Ability {
		FreezingAttack() {
			super(W, AbilitySlot.ATTACK_1, "freezing attack", FREEZING);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.FRZ_A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.FRZ_A1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Freezing attack ready.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (QiEffects.canAffect(target, false))
				Stun.apply(target, WaterConfig.FRZ_A1_FREEZE_TICKS.get());
			attacker.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
			return (float) (damage + WaterConfig.FRZ_A1_DAMAGE.get());
		}
	}

	/** Stays armed until it hits a Water / Fire / Space->Matter player (user 2026-10-29). */
	static final class FreezingCore extends Ability {
		FreezingCore() {
			super(W, AbilitySlot.HEAVY, "freezing core", FREEZING);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.FRZ_A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.FRZ_A2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Freezing core ready: hit a Water, Fire or Space→Matter user.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (!(target instanceof ServerPlayer t) || !isWaterFireOrMatter(t)) {
				// not a valid target: stay armed for the next hit
				AbilityState.of(attacker).arm(this, attacker.serverLevel().getGameTime(), AbilityConfig.EMPOWER_WINDOW_TICKS.get());
				return damage;
			}
			if (QiEffects.canAffect(t, false)) {
				QiEffects.nullifyAll(t, seconds(WaterConfig.FRZ_A2_NULLIFY_SECONDS.get()));
				EffectHelper.apply(t, MobEffects.BLINDNESS, 1, seconds(WaterConfig.FRZ_A2_BLIND_SECONDS.get()), true);
				snow(t);
			}
			return damage;
		}
	}

	static final class FreezingBlast extends Ability {
		FreezingBlast() {
			super(W, AbilitySlot.POWERBLAST_1, "FREEZING BLAST", FREEZING);
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.FRZ_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.FRZ_PB1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return WaterSubEvents.blastTarget(p) == null
					? ActionResult.fail("No player in sight within " + WaterConfig.FRZ_PB1_RANGE.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = WaterSubEvents.blastTarget(p);
			if (t == null)
				return ActionResult.fail("Target lost.");
			QiEffects.dealDamage(p, t, WaterConfig.FRZ_PB1_DAMAGE.get().floatValue());
			if (QiEffects.canAffect(t, false)) {
				int freeze = seconds(WaterConfig.FRZ_PB1_FREEZE_SECONDS.get());
				Stun.apply(t, freeze);
				EffectHelper.apply(t, MobEffects.BLINDNESS, 1, freeze, true);
				if (AspectData.of(t).getActive() == AspectType.FIRE)
					QiEffects.nullify(t, AspectType.FIRE, seconds(WaterConfig.FRZ_PB1_FIRE_NULLIFY_SECONDS.get()));
			}
			snow(t);
			p.serverLevel().playSound(null, t.blockPosition(), net.minecraft.sounds.SoundEvents.GLASS_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.6f);
			return ActionResult.ok("FREEZING BLAST hits " + t.getGameProfile().getName() + ".");
		}
	}

	/** Aura (WaterSubEvents). */
	static final class WinteryDeath extends Ability {
		WinteryDeath() {
			super(W, AbilitySlot.ABILITY_1, "WINTERY DEATH", FREEZING);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(WaterConfig.FRZ_AB1_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.FRZ_AB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.FRZ_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("WINTERY DEATH.");
		}

		@Override
		public void onStop(ServerPlayer p) {
			WaterSubEvents.stopWintery(p);
		}
	}

	static final class FreezingApocalypse extends Ability {
		FreezingApocalypse() {
			super(W, AbilitySlot.ABILITY_2, "FREEZING APOCALYPSE", FREEZING);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(WaterConfig.FRZ_AB2_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.FRZ_AB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.FRZ_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			WaterKit.rain(p.serverLevel(), sustainTicks());
			if (WaterConfig.FRZ_AB2_SNOW.get() && !p.serverLevel().dimensionType().ultraWarm())
				WaterSubEvents.startSnow(p.serverLevel(), p.blockPosition(), WaterConfig.FRZ_AB2_RADIUS.get());
			p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.POWDER_SNOW_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 3f, 0.5f);
			return ActionResult.ok("FREEZING APOCALYPSE.");
		}

		@Override
		public void onStop(ServerPlayer p) {
			WaterSubEvents.stopApocalypse(p);
		}
	}

	// ================================================================== BODY

	static final SubAspectType BODY = SubAspectType.WATER_BODY;
	static final BodyPassive BODY_PASSIVE = new BodyPassive();
	static final InfusedBlood INFUSED_BLOOD = new InfusedBlood();
	static final EnhancedPower POWER = new EnhancedPower();
	static final InfusedWithWater INFUSED = new InfusedWithWater();
	static final IAmHim HIM = new IAmHim();

	public static SubAspectKit body() {
		return new SubAspectKit(BODY, BODY_PASSIVE, INFUSED_BLOOD, POWER, INFUSED, HIM) // PB1 / PB2 -> default Water
				.onLeave(WaterSubEvents::leaveBody);
	}

	static boolean bodyPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == BODY_PASSIVE;
	}

	/** In water: Strength V, Regeneration II, Absorption IV. Double cost out of a source block. Off while INFUSED WITH THE WATER runs. */
	static final class BodyPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return p.isInWater() && !(AbilityState.of(p).isSustained(INFUSED) && KitResolver.isCurrent(p, INFUSED));
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			double perSecond = WaterConfig.BODY_PASSIVE_QI_PER_SECOND.get();
			if (!WaterKit.inWaterSource(p))
				perSecond *= WaterConfig.BODY_FLOWING_MULTIPLIER.get();
			return perSeconds(perSecond, 1);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, WaterConfig.BODY_STRENGTH.get(), 50, false);
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, WaterConfig.BODY_REGEN.get());
			EffectHelper.applyPassive(p, MobEffects.ABSORPTION, WaterConfig.BODY_ABSORPTION.get());
		}
	}

	/** Charges in WaterSubEvents, tied to the buff (form change / nullify end them). */
	static final class InfusedBlood extends Ability {
		static final String BUFF = "water_body_blood";

		InfusedBlood() {
			super(W, AbilitySlot.ATTACK_1, "infused blood", BODY);
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.BODY_A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.BODY_A1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return WaterSubEvents.bloodCharges(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int hits = WaterConfig.BODY_A1_HITS.get();
			WaterSubEvents.startBlood(p, hits);
			return ActionResult.ok("Infused blood: your next " + hits + " unarmed hits heal you.");
		}
	}

	static final class EnhancedPower extends Ability {
		static final String BUFF = "water_body_power";

		EnhancedPower() {
			super(W, AbilitySlot.HEAVY, "enhanced power", BODY);
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.BODY_A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.BODY_A2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int ticks = seconds(WaterConfig.BODY_A2_SECONDS.get());
			// "Strength II, stacks with the passive": vanilla effects don't stack, so it's added as attack damage
			TimedAttributes.apply(p, Attributes.ATTACK_DAMAGE, BUFF, WaterConfig.BODY_A2_DAMAGE_BONUS.get(), AttributeModifier.Operation.ADD_VALUE, ticks);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, WaterConfig.BODY_A2_SPEED.get(), ticks, true);
			AbilityState.of(p).addBuff(new Buff(BUFF, W, 1.0, 0, false, p.serverLevel().getGameTime() + ticks));
			WaterSubEvents.trackPower(p);
			return ActionResult.ok("Enhanced power.");
		}
	}

	static final class InfusedWithWater extends Ability {
		InfusedWithWater() {
			super(W, AbilitySlot.ABILITY_1, "INFUSED WITH THE WATER", BODY);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(WaterConfig.BODY_AB1_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.BODY_AB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.BODY_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("INFUSED WITH THE WATER: your passive yields to the tide.");
		}
	}

	static final class IAmHim extends Ability {
		IAmHim() {
			super(W, AbilitySlot.ABILITY_2, "I AM HIM", BODY);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(WaterConfig.BODY_AB2_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.BODY_AB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.BODY_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("I AM HIM.");
		}
	}

	// ================================================================== MOON

	static final SubAspectType MOON = SubAspectType.WATER_MOON;
	static final MoonPassive MOON_PASSIVE = new MoonPassive();
	static final HighTide HIGH_TIDE = new HighTide();

	public static SubAspectKit moon() {
		return new SubAspectKit(MOON, MOON_PASSIVE, new TidalGrasp(), new Undertow(), new Repulsed(), new Riptide(), HIGH_TIDE, new Tsunami())
				.onLeave(WaterSubEvents::leaveMoon);
	}

	static boolean moonPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == MOON_PASSIVE;
	}

	/** At night: Resistance II, no knockback, aspect sight (WaterSubEvents); in water also Regeneration II + Strength II. */
	static final class MoonPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return isNight(p.level());
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(WaterConfig.MOON_PASSIVE_QI_PER_SECOND.get(), 1);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, WaterConfig.MOON_RESISTANCE.get(), 50, false);
			TimedAttributes.apply(p, Attributes.KNOCKBACK_RESISTANCE, WaterSubEvents.MOON_KB, 1.0, AttributeModifier.Operation.ADD_VALUE, 40);
			if (p.isInWater()) {
				EffectHelper.applyPassive(p, MobEffects.REGENERATION, WaterConfig.MOON_WATER_REGEN.get());
				EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, WaterConfig.MOON_WATER_STRENGTH.get(), 50, false);
			}
		}
	}

	static final class TidalGrasp extends Ability {
		TidalGrasp() {
			super(W, AbilitySlot.ATTACK_1, "tidal grasp", MOON);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.MOON_A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.MOON_A1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Tidal grasp ready.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (QiEffects.canAffect(target, false)) {
				boolean night = isNight(attacker.level());
				WaterEvents.pullToward(target, attacker, (night ? WaterConfig.MOON_A1_PULL_NIGHT : WaterConfig.MOON_A1_PULL).get());
				EffectHelper.apply(target, MobEffects.MOVEMENT_SLOWDOWN, WaterConfig.MOON_SLOWNESS.get(), seconds(WaterConfig.MOON_A1_SLOW_SECONDS.get()), true);
			}
			return damage;
		}
	}

	static final class Undertow extends Ability {
		Undertow() {
			super(W, AbilitySlot.HEAVY, "undertow", MOON);
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.MOON_A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.MOON_A2_COOLDOWN.get());
		}

		static int range(Player p) {
			return (isNight(p.level()) ? WaterConfig.MOON_A2_RANGE_NIGHT : WaterConfig.MOON_A2_RANGE).get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return WaterKit.nearestVisibleUnallied(p, range(p)) == null ? ActionResult.fail("No one in sight within " + range(p) + " blocks.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = WaterKit.nearestVisibleUnallied(p, range(p));
			if (t == null)
				return ActionResult.fail("Target lost.");
			QiEffects.dealDamage(p, t, WaterConfig.MOON_A2_DAMAGE.get().floatValue());
			if (QiEffects.canAffect(t, false))
				WaterEvents.pullToward(t, p, (isNight(p.level()) ? WaterConfig.MOON_A2_PULL_NIGHT : WaterConfig.MOON_A2_PULL).get());
			return ActionResult.ok("Undertow drags " + t.getGameProfile().getName() + ".");
		}
	}

	static final class Repulsed extends Ability {
		Repulsed() {
			super(W, AbilitySlot.POWERBLAST_1, "repulsed", MOON);
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.MOON_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.MOON_PB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			boolean night = isNight(p.level());
			int r = (night ? WaterConfig.MOON_PB1_RADIUS_NIGHT : WaterConfig.MOON_PB1_RADIUS).get();
			double push = (night ? WaterConfig.MOON_PB1_PUSH_NIGHT : WaterConfig.MOON_PB1_PUSH).get();
			int n = 0;
			for (ServerPlayer t : WaterKit.unalliedWithin(p, r)) {
				QiEffects.dealDamage(p, t, WaterConfig.MOON_PB1_DAMAGE.get().floatValue());
				if (QiEffects.canAffect(t, false))
					WaterEvents.push(t, p.position(), push);
				n++;
			}
			p.serverLevel().sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.5, p.getZ(), 80, r / 3.0, 0.3, r / 3.0, 0.1);
			return ActionResult.ok("Repulsed" + (n > 0 ? ": " + n + " pushed away." : "."));
		}
	}

	static final class Riptide extends Ability {
		Riptide() {
			super(W, AbilitySlot.POWERBLAST_2, "riptide", MOON);
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.MOON_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.MOON_PB2_COOLDOWN.get());
		}

		static int range(Player p) {
			return (isNight(p.level()) ? WaterConfig.MOON_PB2_RANGE_NIGHT : WaterConfig.MOON_PB2_RANGE).get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			ServerPlayer t = WaterKit.nearestVisibleUnallied(p, range(p));
			if (t == null)
				return ActionResult.fail("No one in sight within " + range(p) + " blocks.");
			return QiEffects.canAffect(t, false) ? ActionResult.ok("") : ActionResult.fail(t.getGameProfile().getName() + " is Reinforced.");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = WaterKit.nearestVisibleUnallied(p, range(p));
			if (t == null || !QiEffects.canAffect(t, false))
				return ActionResult.fail("Target lost.");
			int ticks = seconds((isNight(p.level()) ? WaterConfig.MOON_PB2_SECONDS_NIGHT : WaterConfig.MOON_PB2_SECONDS).get());
			EffectHelper.apply(t, MobEffects.MOVEMENT_SLOWDOWN, WaterConfig.MOON_SLOWNESS.get(), ticks, true);
			WaterSubEvents.noSprint(t, ticks);
			return ActionResult.ok("Riptide holds " + t.getGameProfile().getName() + ".");
		}
	}

	/** Field fixed where cast (WaterSubEvents). Press again: everyone inside is pushed out and it ends. */
	static final class HighTide extends Ability {
		HighTide() {
			super(W, AbilitySlot.ABILITY_1, "HIGH TIDE", MOON);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(WaterConfig.MOON_AB1_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.MOON_AB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.MOON_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult onRecast(ServerPlayer p) {
			if (!AbilityState.of(p).isSustained(this) || !KitResolver.isCurrent(p, this))
				return null;
			int n = WaterSubEvents.highTidePush(p);
			AbilityState.of(p).endSustained(this, p.serverLevel().getGameTime());
			return ActionResult.ok("The tide recedes: " + n + " pushed out.");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			WaterSubEvents.startHighTide(p);
			return ActionResult.ok("HIGH TIDE. Press again to push them all out.");
		}

		@Override
		public void onStop(ServerPlayer p) {
			WaterSubEvents.stopHighTide(p);
		}
	}

	static final class Tsunami extends Ability {
		Tsunami() {
			super(W, AbilitySlot.ABILITY_2, "TSUNAMI", MOON);
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.MOON_AB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.MOON_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			boolean night = isNight(p.level());
			int r = (night ? WaterConfig.MOON_AB2_RADIUS_NIGHT : WaterConfig.MOON_AB2_RADIUS).get();
			float dmg = (night ? WaterConfig.MOON_AB2_DAMAGE_NIGHT : WaterConfig.MOON_AB2_DAMAGE).get().floatValue();
			int n = 0;
			for (ServerPlayer t : WaterKit.unalliedWithin(p, r)) {
				if (!QiEffects.canAffect(t, false) || !p.hasLineOfSight(t)) // Reinforced players and players behind terrain are spared
					continue;
				QiEffects.dealDamage(p, t, dmg);
				WaterEvents.push(t, p.position(), WaterConfig.MOON_AB2_PUSH.get());
				EffectHelper.apply(t, MobEffects.MOVEMENT_SLOWDOWN, WaterConfig.MOON_SLOWNESS.get(), seconds(WaterConfig.MOON_AB2_SLOW_SECONDS.get()), true);
				n++;
			}
			Vec3 c = p.position();
			p.serverLevel().sendParticles(ParticleTypes.SPLASH, c.x, c.y + 1, c.z, 300, r / 4.0, 1.0, r / 4.0, 0.3);
			p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.PLAYERS, 4f, 0.5f);
			return ActionResult.ok("TSUNAMI" + (n > 0 ? ": " + n + " swept away." : "."));
		}
	}
}
