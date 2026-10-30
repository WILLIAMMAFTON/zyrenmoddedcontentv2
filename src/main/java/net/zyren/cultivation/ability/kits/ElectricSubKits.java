package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForgeMod;

import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.SubAspectKit;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.ElectricConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.List;

import static net.zyren.cultivation.ability.kits.ElectricKit.lightning;
import static net.zyren.cultivation.ability.kits.ElectricKit.seconds;
import static net.zyren.cultivation.ability.kits.ElectricKit.selfStrike;

/**
 * Electric's sub-aspects (ASPECT GUIDE + user "use rec" 2026-10-29). Slots a sub-aspect doesn't list
 * fall back to the DEFAULT Electric kit (Plasma / Chaos: the passive; Enhanced Receptors: PB1). Every
 * ability uses its parent ELECTRIC + the same slot, so cooldowns are shared. Event side: ElectricEvents.
 */
public final class ElectricSubKits {
	static final AspectType E = AspectType.ELECTRIC;

	private ElectricSubKits() {
	}

	private static void flight(ServerPlayer p, String id) {
		TimedAttributes.apply(p, NeoForgeMod.CREATIVE_FLIGHT, id, 1, AttributeModifier.Operation.ADD_VALUE, 40);
	}

	private static void step(ServerPlayer p, String id, double height) {
		if (height > 0.6)
			TimedAttributes.apply(p, Attributes.STEP_HEIGHT, id, height - 0.6, AttributeModifier.Operation.ADD_VALUE, 40);
	}

	// ================================================================== PLASMA

	static final SubAspectType PLASMA = SubAspectType.ELECTRIC_PLASMA;
	static final HeatedThunder HEATED = new HeatedThunder();
	static final BiggerBrother BIGGER_BROTHER = new BiggerBrother();
	static final DidISaySomethingWrong WRONG = new DidISaySomethingWrong();
	static final HeatedBreath BREATH = new HeatedBreath();

	public static SubAspectKit plasma() {
		return new SubAspectKit(PLASMA, null, HEATED, new BurningGaze(), new SongOfPlasma(), BIGGER_BROTHER, WRONG, BREATH)
				.onLeave(ElectricEvents::leavePlasma);
	}

	static final class HeatedThunder extends KitParts.Cfg {
		HeatedThunder() {
			super(E, AbilitySlot.ATTACK_1, "heated thunder", PLASMA, ElectricConfig.PL_A1_COST, ElectricConfig.PL_A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return ElectricEvents.HEATED.left(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ElectricEvents.HEATED.start(p, ElectricConfig.PL_A1_HITS.get());
			return ActionResult.ok("Heated thunder: next " + ElectricConfig.PL_A1_HITS.get() + " hits burn.");
		}
	}

	static final class BurningGaze extends KitParts.Cfg {
		BurningGaze() {
			super(E, AbilitySlot.HEAVY, "burning gaze", PLASMA, ElectricConfig.PL_A2_COST, ElectricConfig.PL_A2_COOLDOWN, false);
		}

		static ServerPlayer target(ServerPlayer p) {
			for (ServerPlayer o : ElectricKit.unalliedWithin(p, ElectricConfig.PL_A2_RANGE.get()))
				if (p.hasLineOfSight(o))
					return o;
			return null;
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return target(p) == null ? ActionResult.fail("No one in sight within " + ElectricConfig.PL_A2_RANGE.get() + " blocks.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = target(p);
			if (t == null)
				return ActionResult.fail("Target lost.");
			LightningEvents.strike(p, t, lightning());
			Vec3 back = p.position().subtract(t.position()).multiply(1, 0, 1);
			Vec3 at = back.lengthSqr() < 1.0E-4 ? t.position() : t.position().add(back.normalize().scale(1.2));
			p.teleportTo(at.x, t.getY(), at.z);
			p.fallDistance = 0;
			return ActionResult.ok("Burning gaze.");
		}
	}

	static final class SongOfPlasma extends KitParts.Cfg {
		SongOfPlasma() {
			super(E, AbilitySlot.POWERBLAST_1, "the song of plasma", PLASMA, ElectricConfig.PL_PB1_COST, ElectricConfig.PL_PB1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return ElectricKit.unalliedWithin(p, ElectricConfig.PL_PB1_RANGE.get()).isEmpty()
					? ActionResult.fail("No one within " + ElectricConfig.PL_PB1_RANGE.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			List<ServerPlayer> near = ElectricKit.unalliedWithin(p, ElectricConfig.PL_PB1_RANGE.get());
			if (near.isEmpty())
				return ActionResult.fail("Target lost.");
			ServerPlayer t = near.get(0);
			selfStrike(p);
			LightningEvents.strike(p, t, lightning());
			if (QiEffects.canAffect(t, false)) {
				Motion.launchUp(t, ElectricConfig.PL_PB1_UP.get());
				EffectHelper.apply(t, MobEffects.GLOWING, 1, seconds(ElectricConfig.PL_PB1_GLOW_SECONDS.get()), true);
			}
			return ActionResult.ok("The song of plasma.");
		}
	}

	/** Every melee / arrow hit: fire + lightning (ElectricEvents). */
	static final class BiggerBrother extends KitParts.Timed {
		BiggerBrother() {
			super(E, AbilitySlot.POWERBLAST_2, "fires bigger brother", PLASMA, ElectricConfig.PL_PB2_COST, ElectricConfig.PL_PB2_COOLDOWN, ElectricConfig.PL_PB2_SECONDS, false);
		}
	}

	/** Waits for the first unallied player to hurt you (ElectricEvents). */
	static final class DidISaySomethingWrong extends KitParts.Timed {
		DidISaySomethingWrong() {
			super(E, AbilitySlot.ABILITY_1, "did i say something wrong?", PLASMA, ElectricConfig.PL_AB1_COST, ElectricConfig.PL_AB1_COOLDOWN, ElectricConfig.PL_AB1_SECONDS, true);
		}
	}

	/** Beam every 0.5 s (ElectricEvents); crater at its end point when it finishes. */
	static final class HeatedBreath extends KitParts.Timed {
		HeatedBreath() {
			super(E, AbilitySlot.ABILITY_2, "nukes heated breath", PLASMA, ElectricConfig.PL_AB2_COST, ElectricConfig.PL_AB2_COOLDOWN, ElectricConfig.PL_AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			ElectricEvents.startBreath(p, sustainTicks());
		}

		@Override
		void half(ServerPlayer p) {
			ElectricEvents.breathTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			ElectricEvents.endBreath(p);
		}
	}

	// ================================================================== ENHANCED RECEPTORS

	static final SubAspectType RECEPTORS = SubAspectType.ELECTRIC_ENHANCED_RECEPTORS;
	static final ReceptorPassive ER_PASSIVE = new ReceptorPassive();
	static final LocalizedEnhancements LOCALIZED = new LocalizedEnhancements();
	static final IAmHim HIM = new IAmHim();
	static final TheReason REASON = new TheReason();

	public static SubAspectKit receptors() {
		return new SubAspectKit(RECEPTORS, ER_PASSIVE, new FistsOfBs(), new RidingTheLightning(), LOCALIZED, HIM, REASON) // PB1 -> default
				.onLeave(ElectricEvents::leaveReceptors);
	}

	static boolean receptorPassiveOn(net.minecraft.world.entity.player.Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == ER_PASSIVE;
	}

	/** Mode 1: storm buffs (10 Qi/min). Mode 2: automatic defense slots (10 Qi/s). Switch: key V. */
	static final class ReceptorPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			if (KitParts.passiveDisabled(p))
				return false;
			return ElectricEvents.mode(p) == 2 || ElectricKit.inStorm(p);
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return ElectricEvents.mode(p) == 2 ? perSeconds(ElectricConfig.ER_MODE2_QI_PER_SECOND.get(), 1)
					: perSeconds(ElectricConfig.ER_MODE1_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			if (ElectricEvents.mode(p) == 2)
				return; // defense slots: ElectricEvents
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, ElectricConfig.ER_M1_SPEED.get(), 50, false);
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, ElectricConfig.ER_M1_STRENGTH.get(), 50, false);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, ElectricConfig.ER_M1_RESISTANCE.get(), 50, false);
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, ElectricConfig.ER_M1_REGEN.get());
		}
	}

	/** Stays armed until an unarmed hit, which deals at least 12. */
	static final class FistsOfBs extends KitParts.Empower {
		FistsOfBs() {
			super(E, AbilitySlot.ATTACK_1, "enhanced fists of bs", RECEPTORS, ElectricConfig.ER_A1_COST, ElectricConfig.ER_A1_COOLDOWN, false);
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (!attacker.getMainHandItem().isEmpty()) {
				AirSubKits.rearm(attacker, this);
				return damage;
			}
			return (float) Math.max(damage, ElectricConfig.ER_A1_DAMAGE.get());
		}
	}

	static final class RidingTheLightning extends KitParts.Cfg {
		RidingTheLightning() {
			super(E, AbilitySlot.HEAVY, "riding the lightning", RECEPTORS, ElectricConfig.ER_A2_COST, ElectricConfig.ER_A2_COOLDOWN, false);
		}

		static boolean low(ServerPlayer p) {
			return p.getHealth() < ElectricConfig.ER_A2_LOW_HEALTH.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			if (low(p))
				return ElectricEvents.escapeSpot(p) == null ? ActionResult.fail("Nowhere safe to escape to.") : ActionResult.ok("");
			return ElectricKit.unalliedWithin(p, ElectricConfig.ER_A2_RANGE.get()).isEmpty()
					? ActionResult.fail("No one within " + ElectricConfig.ER_A2_RANGE.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			selfStrike(p);
			if (low(p)) {
				BlockPos spot = ElectricEvents.escapeSpot(p);
				if (spot == null)
					return ActionResult.fail("Nowhere safe to escape to.");
				p.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
				p.fallDistance = 0;
				selfStrike(p);
				return ActionResult.ok("Riding the lightning to safety.");
			}
			List<ServerPlayer> near = ElectricKit.unalliedWithin(p, ElectricConfig.ER_A2_RANGE.get());
			if (near.isEmpty())
				return ActionResult.fail("Target lost.");
			ServerPlayer t = near.get(0);
			Vec3 back = p.position().subtract(t.position()).multiply(1, 0, 1);
			Vec3 at = back.lengthSqr() < 1.0E-4 ? t.position() : t.position().add(back.normalize().scale(2.0));
			p.teleportTo(at.x, t.getY(), at.z);
			p.fallDistance = 0;
			selfStrike(p);
			return ActionResult.ok("Riding the lightning to " + t.getGameProfile().getName() + ".");
		}
	}

	static final class LocalizedEnhancements extends KitParts.Timed {
		static final String FLIGHT = "electric_er_flight";

		LocalizedEnhancements() {
			super(E, AbilitySlot.POWERBLAST_2, "localized ehancements", RECEPTORS, ElectricConfig.ER_PB2_COST, ElectricConfig.ER_PB2_COOLDOWN, ElectricConfig.ER_PB2_SECONDS, true);
		}

		@Override
		boolean disablesPassive() {
			return true;
		}

		@Override
		void start(ServerPlayer p) {
			selfStrike(p);
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, 5, 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 3, 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 4, 30, true);
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, 2);
			flight(p, FLIGHT);
		}

		@Override
		void end(ServerPlayer p) {
			TimedAttributes.remove(p, FLIGHT);
			ElectricEvents.landSafely(p);
		}
	}

	/** Blocks by chance / retaliates (ElectricEvents); weapon hits x2. */
	static final class IAmHim extends KitParts.Timed {
		IAmHim() {
			super(E, AbilitySlot.ABILITY_1, "I AM HIM", RECEPTORS, ElectricConfig.ER_AB1_COST, ElectricConfig.ER_AB1_COOLDOWN, ElectricConfig.ER_AB1_SECONDS, true);
		}
	}

	/** Storm fixed where cast (ElectricEvents). */
	static final class TheReason extends KitParts.Timed {
		static final String FLIGHT = "electric_er_reason_flight";

		TheReason() {
			super(E, AbilitySlot.ABILITY_2, "the reason we dont mess with me", RECEPTORS, ElectricConfig.ER_AB2_COST, ElectricConfig.ER_AB2_COOLDOWN, ElectricConfig.ER_AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			selfStrike(p);
			ElectricEvents.startReason(p);
			ElectricKit.thunder(p.serverLevel(), sustainTicks());
		}

		@Override
		void second(ServerPlayer p) {
			if (!ElectricEvents.insideReason(p)) {
				TimedAttributes.remove(p, FLIGHT);
				return;
			}
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, 5, 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 3, 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 3, 30, true);
			flight(p, FLIGHT);
		}

		@Override
		void half(ServerPlayer p) {
			ElectricEvents.reasonTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			ElectricEvents.endReason(p);
			TimedAttributes.remove(p, FLIGHT);
			ElectricEvents.landSafely(p);
		}
	}

	// ================================================================== CHAOS

	static final SubAspectType CHAOS = SubAspectType.ELECTRIC_CHAOS;
	static final SmokingCrater SMOKING = new SmokingCrater();
	static final MovementOfTheStorm MOVEMENT = new MovementOfTheStorm();
	static final IAmChaos I_AM_CHAOS = new IAmChaos();
	static final EyeOfTheStorm EYE = new EyeOfTheStorm();

	public static SubAspectKit chaos() {
		return new SubAspectKit(CHAOS, null, new HorizonYeeting(), SMOKING, MOVEMENT, new HiICanFly(), I_AM_CHAOS, EYE)
				.onLeave(ElectricEvents::leaveChaos);
	}

	static final class HorizonYeeting extends KitParts.Empower {
		HorizonYeeting() {
			super(E, AbilitySlot.ATTACK_1, "horizon yeeting", CHAOS, ElectricConfig.CH_A1_COST, ElectricConfig.CH_A1_COOLDOWN, false);
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (QiEffects.canAffect(target, false))
				ElectricEvents.queueYeet(attacker, target);
			return damage;
		}
	}

	/** Every hit explodes (ElectricEvents). */
	static final class SmokingCrater extends KitParts.Timed {
		SmokingCrater() {
			super(E, AbilitySlot.HEAVY, "smoking crater", CHAOS, ElectricConfig.CH_A2_COST, ElectricConfig.CH_A2_COOLDOWN, ElectricConfig.CH_A2_SECONDS, false);
		}
	}

	static final class MovementOfTheStorm extends KitParts.Timed {
		static final String STEP = "electric_chaos_storm_step";

		MovementOfTheStorm() {
			super(E, AbilitySlot.POWERBLAST_1, "movement of the storm", CHAOS, ElectricConfig.CH_PB1_COST, ElectricConfig.CH_PB1_COOLDOWN, ElectricConfig.CH_PB1_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			selfStrike(p);
			ElectricKit.thunder(p.serverLevel(), sustainTicks());
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, ElectricConfig.CH_PB1_SPEED.get(), 30, true);
			step(p, STEP, ElectricConfig.CH_PB1_STEP.get());
		}

		@Override
		void end(ServerPlayer p) {
			TimedAttributes.remove(p, STEP);
		}
	}

	static final class HiICanFly extends KitParts.Timed {
		static final String FLIGHT = "electric_chaos_fly";

		HiICanFly() {
			super(E, AbilitySlot.POWERBLAST_2, "hi i can fly now", CHAOS, ElectricConfig.CH_PB2_COST, ElectricConfig.CH_PB2_COOLDOWN, ElectricConfig.CH_PB2_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			selfStrike(p);
		}

		@Override
		void second(ServerPlayer p) {
			flight(p, FLIGHT);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, ElectricConfig.CH_PB2_SPEED.get(), 30, true);
		}

		@Override
		void end(ServerPlayer p) {
			TimedAttributes.remove(p, FLIGHT);
			ElectricEvents.landSafely(p);
		}
	}

	static final class IAmChaos extends KitParts.Timed {
		static final String STEP = "electric_chaos_step";

		IAmChaos() {
			super(E, AbilitySlot.ABILITY_1, "i am chaos", CHAOS, ElectricConfig.CH_AB1_COST, ElectricConfig.CH_AB1_COOLDOWN, ElectricConfig.CH_AB1_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			ElectricKit.thunder(p.serverLevel(), sustainTicks());
			ElectricEvents.makeNight(p.serverLevel());
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, ElectricConfig.CH_AB1_SPEED.get(), 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, ElectricConfig.CH_AB1_RESISTANCE.get(), 30, true);
			EffectHelper.apply(p, MobEffects.JUMP, ElectricConfig.CH_AB1_JUMP.get(), 30, true);
			EffectHelper.apply(p, MobEffects.NIGHT_VISION, 1, 260, false);
			step(p, STEP, ElectricConfig.CH_AB1_STEP.get());
		}

		@Override
		void end(ServerPlayer p) {
			TimedAttributes.remove(p, STEP);
		}
	}

	static final class EyeOfTheStorm extends KitParts.Timed {
		static final String FLIGHT = "electric_chaos_eye_flight";

		EyeOfTheStorm() {
			super(E, AbilitySlot.ABILITY_2, "welcome to the eye of the storm, i still exist thou", CHAOS, ElectricConfig.CH_AB2_COST, ElectricConfig.CH_AB2_COOLDOWN,
					ElectricConfig.CH_AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			selfStrike(p);
			ElectricEvents.startEye(p);
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, ElectricConfig.CH_AB2_SPEED.get(), 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, ElectricConfig.CH_AB2_RESISTANCE.get(), 30, true);
			flight(p, FLIGHT);
		}

		@Override
		void half(ServerPlayer p) {
			ElectricEvents.eyeTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			ElectricEvents.endEye(p);
			TimedAttributes.remove(p, FLIGHT);
			ElectricEvents.landSafely(p);
		}
	}

	/** Build height spot this far the way the attacker faces (used by horizon yeeting). */
	static Vec3 yeetSpot(ServerPlayer attacker, LivingEntity target) {
		Vec3 look = attacker.getLookAngle().multiply(1, 0, 1);
		if (look.lengthSqr() < 1.0E-4)
			look = new Vec3(0, 0, 1);
		Vec3 to = target.position().add(look.normalize().scale(ElectricConfig.CH_A1_DISTANCE.get()));
		int top = target.level().getMaxBuildHeight() - 2;
		int ground = target.level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(to.x), (int) Math.floor(to.z));
		return new Vec3(to.x, Math.max(top, ground + 1), to.z);
	}
}
