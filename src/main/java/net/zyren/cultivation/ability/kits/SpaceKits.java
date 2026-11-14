package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.CastRules;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.SubAspectKit;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.SpaceConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SPACE — DEFAULT kit + Dimensional / Abyssal / Energy sub-aspects (ASPECT GUIDE, user "rec"
 * 2026-11-14). Energy replaced Matter. Space has no Reinforce Body of its own (Light's is used).
 * Values: space-server.toml. Event side: SpaceKitEvents; teleport / safe spots: SpaceWorld.
 */
public final class SpaceKits {
	static final AspectType S = AspectType.SPACE;

	private SpaceKits() {
	}

	// ================================================================== helpers

	static int seconds(int s) {
		return s * 20;
	}

	/** Effect without particles or HUD icon ("without potion effects"). */
	static void hidden(LivingEntity e, Holder<MobEffect> effect, int level, int ticks) {
		if (level <= 0 || ticks <= 0)
			return;
		e.addEffect(new MobEffectInstance(effect, ticks, Math.min(level, 255) - 1, false, false, false));
	}

	static List<ServerPlayer> unalliedWithin(ServerPlayer user, Vec3 center, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer o : user.serverLevel().players())
			if (Targeting.isValid(user, o) && !Targeting.isAllied(user, o) && o.position().distanceTo(center) <= radius)
				out.add(o);
		out.sort(Comparator.comparingDouble(o -> o.position().distanceToSqr(center)));
		return out;
	}

	/** Block looked at within range, else null. */
	static Vec3 aimPoint(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? null : Vec3.atBottomCenterOf(hit.getBlockPos().above());
	}

	static boolean on(Player p, AspectPassive passive) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == passive;
	}

	// ================================================================== DEFAULT

	static final Passive PASSIVE = new Passive();
	static final BladeComposition BLADE = new BladeComposition();
	static final MindScanning MIND = new MindScanning();
	static final SpatialAnchor ANCHOR = new SpatialAnchor();
	static final Relocation RELOCATION = new Relocation();
	static final DimensionalShift SHIFT = new DimensionalShift();
	static final HiThere HI_THERE = new HiThere();

	public static AspectKit create() {
		CastRules.register(SpaceKitEvents.RULES);
		return new AspectKit(S, PASSIVE, BLADE, MIND, ANCHOR, RELOCATION, SHIFT, HI_THERE);
	}

	/** Chorus fruit kept topped up (SpaceKitEvents) + Resistance. */
	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(SpaceConfig.PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, SpaceConfig.PASSIVE_RESISTANCE.get(), 50, false);
		}
	}

	static final class BladeComposition extends KitParts.Cfg {
		BladeComposition() {
			super(S, AbilitySlot.ATTACK_1, "enhanced compisiotion of the blade", null, SpaceConfig.A1_COST, SpaceConfig.A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return SpaceKitEvents.BLADE_ARM.left(p) > 0 ? ActionResult.fail(name() + " is already ready.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			SpaceKitEvents.BLADE_ARM.start(p, 1, 0);
			return ActionResult.ok("Next hit: +" + Math.round(SpaceConfig.A1_FRACTION.get() * 100) + "% of the target's health (max " + SpaceConfig.A1_CAP.get() + ").");
		}
	}

	static final class MindScanning extends KitParts.Timed {
		MindScanning() {
			super(S, AbilitySlot.HEAVY, "mind scanning", null, SpaceConfig.A2_COST, SpaceConfig.A2_COOLDOWN, SpaceConfig.A2_SECONDS, false);
		}
	}

	static final class SpatialAnchor extends KitParts.Cfg {
		SpatialAnchor() {
			super(S, AbilitySlot.POWERBLAST_1, "spatial anchor", null, SpaceConfig.PB1_COST, SpaceConfig.PB1_COOLDOWN, false);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			SpaceKitEvents.setAnchor(p);
			return ActionResult.ok("Spatial anchor set. Press 'Space Anchor Recall' (B) to return.");
		}
	}

	static final class Relocation extends KitParts.Cfg {
		Relocation() {
			super(S, AbilitySlot.POWERBLAST_2, "relocation", null, SpaceConfig.PB2_COST, SpaceConfig.PB2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return SpaceKitEvents.planRelocation(p) ? ActionResult.ok("") : ActionResult.fail("No safe spot within " + SpaceConfig.PB2_RANGE.get() + " blocks and no bed.");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return SpaceKitEvents.relocate(p) ? ActionResult.ok("Relocated.") : ActionResult.fail("No safe spot found.");
		}
	}

	static final class DimensionalShift extends KitParts.Timed {
		DimensionalShift() {
			super(S, AbilitySlot.ABILITY_1, "dimentional shift", null, SpaceConfig.AB1_COST, SpaceConfig.AB1_COOLDOWN, SpaceConfig.AB1_SECONDS, false);
		}

		@Override
		void second(ServerPlayer p) {
			hidden(p, MobEffects.INVISIBILITY, 1, 40);
			hidden(p, MobEffects.MOVEMENT_SPEED, SpaceConfig.AB1_SPEED.get(), 40);
			hidden(p, MobEffects.WEAKNESS, SpaceConfig.AB1_WEAKNESS.get(), 40);
			hidden(p, MobEffects.DIG_SLOWDOWN, SpaceConfig.AB1_FATIGUE.get(), 40);
			CastRules.hideFromTargeting(p, 40);
			for (net.minecraft.world.entity.Mob m : p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(32),
					m -> m.getTarget() == p))
				m.setTarget(null);
		}

		@Override
		void end(ServerPlayer p) {
			p.removeEffect(MobEffects.INVISIBILITY);
			p.removeEffect(MobEffects.WEAKNESS);
			p.removeEffect(MobEffects.DIG_SLOWDOWN);
			CastRules.clear(p.getUUID());
		}
	}

	static final class HiThere extends KitParts.Timed {
		HiThere() {
			super(S, AbilitySlot.ABILITY_2, "Hi there", null, SpaceConfig.AB2_COST, SpaceConfig.AB2_COOLDOWN, SpaceConfig.AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			SpaceKitEvents.HI_TICKS.put(p.getUUID(), 0);
		}

		@Override
		void second(ServerPlayer p) {
			SpaceKitEvents.hiThereTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			SpaceKitEvents.HI_TICKS.remove(p.getUUID());
		}
	}

	// ================================================================== DIMENSIONAL

	static final SubAspectType DIMENSIONAL = SubAspectType.SPACE_DIMENSIONAL;
	static final DimensionalPassive DM_PASSIVE = new DimensionalPassive();
	static final AltReality ALT = new AltReality();
	static final Nexus NEXUS = new Nexus();
	static final Rift RIFT = new Rift();

	public static SubAspectKit dimensional() {
		return new SubAspectKit(DIMENSIONAL, DM_PASSIVE, new BladesOfTheWalker(), ALT, new HarmonicShift(), new Blink(), NEXUS, RIFT)
				.onLeave(SpaceKitEvents::leaveDimensional);
	}

	/** Sneak + right-click air: travel (SpaceKitEvents.dimensionalJump). Cost per use. */
	static final class DimensionalPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return 0;
		}
	}

	static final class BladesOfTheWalker extends KitParts.Timed {
		BladesOfTheWalker() {
			super(S, AbilitySlot.ATTACK_1, "blades of the walker", DIMENSIONAL, SpaceConfig.DM_A1_COST, SpaceConfig.DM_A1_COOLDOWN, SpaceConfig.DM_A1_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			AbilityState.of(p).addBuff(new Buff("space_blades", S, SpaceConfig.DM_A1_OUTPUT.get(), 0, false, p.serverLevel().getGameTime() + sustainTicks()));
		}

		@Override
		void end(ServerPlayer p) {
			AbilityState.of(p).removeBuff("space_blades");
		}
	}

	static final class AltReality extends KitParts.Cfg {
		AltReality() {
			super(S, AbilitySlot.HEAVY, "pushed into a alt reality", DIMENSIONAL, SpaceConfig.DM_A2_COST, SpaceConfig.DM_A2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return SpaceKitEvents.ALT_ARM.left(p) > 0 ? ActionResult.fail(name() + " is already ready.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			SpaceKitEvents.ALT_ARM.start(p, 1, 0);
			return ActionResult.ok("Your next hit pushes the target into an alternate reality.");
		}
	}

	static final class HarmonicShift extends KitParts.Cfg {
		HarmonicShift() {
			super(S, AbilitySlot.POWERBLAST_1, "harmonic shift", DIMENSIONAL, SpaceConfig.DM_PB1_COST, SpaceConfig.DM_PB1_COOLDOWN, false);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int n = 0;
			double lift = EffectHelper.launchSpeedForHeight(SpaceConfig.DM_PB1_UP.get());
			for (ServerPlayer t : unalliedWithin(p, p.position(), SpaceConfig.DM_PB1_RADIUS.get())) {
				if (!QiEffects.canAffect(t, false))
					continue;
				Motion.push(t, p.position(), SpaceConfig.DM_PB1_BACK.get(), 1.6, lift, null);
				n++;
			}
			p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 80, 3, 1, 3, 0.2);
			return ActionResult.ok("Harmonic shift: " + n + " thrown.");
		}
	}

	static final class Blink extends KitParts.Cfg {
		Blink() {
			super(S, AbilitySlot.POWERBLAST_2, "blink", DIMENSIONAL, SpaceConfig.DM_PB2_COST, SpaceConfig.DM_PB2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			BlockPos to = SpaceWorld.randomSafe(p, SpaceConfig.DM_PB2_MIN.get(), SpaceConfig.DM_PB2_MAX.get());
			if (to == null)
				return ActionResult.fail("No safe spot found nearby.");
			SpaceKitEvents.PLANNED.put(p.getUUID(), to);
			return ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			BlockPos to = SpaceKitEvents.PLANNED.remove(p.getUUID());
			if (to == null)
				return ActionResult.fail("No safe spot found.");
			SpaceKitEvents.poof(p);
			SpaceWorld.teleport(p, p.serverLevel(), to);
			return ActionResult.ok("Blink!");
		}
	}

	static final class Nexus extends KitParts.Timed {
		Nexus() {
			super(S, AbilitySlot.ABILITY_1, "nexus point", DIMENSIONAL, SpaceConfig.DM_AB1_COST, SpaceConfig.DM_AB1_COOLDOWN, SpaceConfig.DM_AB1_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			SpaceKitEvents.placeNexus(p);
		}

		@Override
		void second(ServerPlayer p) {
			SpaceKitEvents.nexusTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			SpaceKitEvents.NEXUS.remove(p.getUUID());
		}
	}

	static final class Rift extends KitParts.Timed {
		Rift() {
			super(S, AbilitySlot.ABILITY_2, "a abyssal rift in reality", DIMENSIONAL, SpaceConfig.DM_AB2_COST, SpaceConfig.DM_AB2_COOLDOWN, SpaceConfig.DM_AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			Vec3 at = aimPoint(p, SpaceConfig.DM_AB2_RANGE.get());
			SpaceKitEvents.RIFTS.put(p.getUUID(), new SpaceKitEvents.Point(p.serverLevel(), at != null ? at : p.position()));
		}

		@Override
		void second(ServerPlayer p) {
			SpaceKitEvents.riftTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			SpaceKitEvents.RIFTS.remove(p.getUUID());
			net.zyren.cultivation.network.SensedPlayersMessage.send(p, List.of());
		}
	}

	// ================================================================== ABYSSAL

	static final SubAspectType ABYSSAL = SubAspectType.SPACE_ABYSSAL;
	static final NoPassive AB_PASSIVE = new NoPassive();
	static final SlowDeath SLOW_DEATH = new SlowDeath();
	static final QiSuction SUCTION = new QiSuction();
	static final VoidAttack VOID = new VoidAttack();

	public static SubAspectKit abyssal() {
		return new SubAspectKit(ABYSSAL, AB_PASSIVE, VOID, SLOW_DEATH, SUCTION, new Err()).onLeave(SpaceKitEvents::leaveAbyssal);
	}

	/** Abyssal: "PASSIVE(NONE)" — no passive, and the Space passive does not apply either. */
	static final class NoPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return false;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return 0;
		}
	}

	static final class VoidAttack extends KitParts.Cfg {
		VoidAttack() {
			super(S, AbilitySlot.ATTACK_1, "void attack", ABYSSAL, SpaceConfig.AB_A1_COST, SpaceConfig.AB_A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return SpaceKitEvents.VOID_ARM.left(p) > 0 ? ActionResult.fail(name() + " is already ready.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			SpaceKitEvents.VOID_ARM.start(p, 1, 0);
			return ActionResult.ok("Void attack ready (next hit).");
		}
	}

	static final class SlowDeath extends KitParts.Timed {
		SlowDeath() {
			super(S, AbilitySlot.HEAVY, "a slow death", ABYSSAL, SpaceConfig.AB_A2_COST, SpaceConfig.AB_A2_COOLDOWN, SpaceConfig.AB_A2_MAX_SECONDS, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			if (net.zyren.cultivation.system.CoreLife.health(p) >= net.zyren.cultivation.system.CoreLife.maxHealth(p))
				return ActionResult.fail("You are already at full health.");
			return unalliedWithin(p, p.position(), SpaceConfig.AB_A2_RANGE.get()).isEmpty()
					? ActionResult.fail("No one within " + SpaceConfig.AB_A2_RANGE.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		void start(ServerPlayer p) {
			List<ServerPlayer> near = unalliedWithin(p, p.position(), SpaceConfig.AB_A2_RANGE.get());
			if (!near.isEmpty())
				SpaceKitEvents.DRAINING.put(p.getUUID(), near.get(0).getUUID());
		}

		@Override
		void second(ServerPlayer p) {
			SpaceKitEvents.slowDeathTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			SpaceKitEvents.DRAINING.remove(p.getUUID());
		}
	}

	static final class QiSuction extends KitParts.Timed {
		QiSuction() {
			super(S, AbilitySlot.ABILITY_1, "qi suction", ABYSSAL, SpaceConfig.AB_AB1_COST, SpaceConfig.AB_AB1_COOLDOWN, SpaceConfig.AB_AB1_SECONDS, true);
		}

		@Override
		void second(ServerPlayer p) {
			SpaceKitEvents.suctionTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			net.zyren.cultivation.network.SensedPlayersMessage.send(p, List.of());
		}
	}

	static final class Err extends KitParts.Cfg {
		Err() {
			super(S, AbilitySlot.ABILITY_2, "ERR", ABYSSAL, SpaceConfig.AB_AB2_COST, SpaceConfig.AB_AB2_COOLDOWN, true);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return SpaceKitEvents.err(p);
		}
	}

	// ================================================================== ENERGY

	static final SubAspectType ENERGY = SubAspectType.SPACE_ENERGY;
	static final EnergyPassive EN_PASSIVE = new EnergyPassive();
	static final RiftStep RIFT_STEP = new RiftStep();
	static final AnchorWell WELL = new AnchorWell();
	static final EventHorizon HORIZON = new EventHorizon();
	static final FoldPunch FOLD = new FoldPunch();
	static final GravitySpike SPIKE = new GravitySpike();

	public static SubAspectKit energy() {
		return new SubAspectKit(ENERGY, EN_PASSIVE, FOLD, SPIKE, RIFT_STEP, WELL, new DimensionalShear(), HORIZON)
				.onLeave(SpaceKitEvents::leaveEnergy);
	}

	/** Sensing / phase / no fall damage: SpaceKitEvents. */
	static final class EnergyPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(SpaceConfig.EN_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			if (!p.onGround() || p.level().dimension() == net.minecraft.world.level.Level.END)
				EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, 1, 30, false);
		}
	}

	static final class FoldPunch extends KitParts.Cfg {
		FoldPunch() {
			super(S, AbilitySlot.ATTACK_1, "Fold Punch", ENERGY, SpaceConfig.EN_A1_COST, SpaceConfig.EN_A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return SpaceKitEvents.FOLD_ARM.left(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			SpaceKitEvents.FOLD_ARM.start(p, SpaceConfig.EN_A1_HITS.get(), seconds(SpaceConfig.EN_A1_SECONDS.get()));
			return ActionResult.ok("Fold punch: next " + SpaceConfig.EN_A1_HITS.get() + " hits.");
		}
	}

	static final class GravitySpike extends KitParts.Cfg {
		GravitySpike() {
			super(S, AbilitySlot.HEAVY, "Gravity Spike", ENERGY, SpaceConfig.EN_A2_COST, SpaceConfig.EN_A2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return SpaceKitEvents.SPIKE_ARM.left(p) > 0 ? ActionResult.fail(name() + " is already ready.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			SpaceKitEvents.SPIKE_ARM.start(p, 1, seconds(SpaceConfig.EN_A2_SECONDS.get()));
			return ActionResult.ok("Gravity spike ready (next hit).");
		}
	}

	static final class RiftStep extends KitParts.Cfg {
		RiftStep() {
			super(S, AbilitySlot.POWERBLAST_1, "Rift Step", ENERGY, SpaceConfig.EN_PB1_COST, SpaceConfig.EN_PB1_COOLDOWN, false);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return SpaceKitEvents.riftStep(p);
		}
	}

	static final class AnchorWell extends KitParts.Timed {
		AnchorWell() {
			super(S, AbilitySlot.POWERBLAST_2, "Anchor Well", ENERGY, SpaceConfig.EN_PB2_COST, SpaceConfig.EN_PB2_COOLDOWN, SpaceConfig.EN_PB2_SECONDS, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return aimPoint(p, SpaceConfig.EN_PB2_RANGE.get()) == null ? ActionResult.fail("Look at a spot within " + SpaceConfig.EN_PB2_RANGE.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		void start(ServerPlayer p) {
			Vec3 at = aimPoint(p, SpaceConfig.EN_PB2_RANGE.get());
			SpaceKitEvents.WELLS.put(p.getUUID(), new SpaceKitEvents.Point(p.serverLevel(), at != null ? at : p.position()));
		}

		@Override
		void half(ServerPlayer p) {
			SpaceKitEvents.wellTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			SpaceKitEvents.WELLS.remove(p.getUUID());
		}
	}

	static final class DimensionalShear extends KitParts.Cfg {
		DimensionalShear() {
			super(S, AbilitySlot.ABILITY_1, "Dimensional Shear", ENERGY, SpaceConfig.EN_AB1_COST, SpaceConfig.EN_AB1_COOLDOWN, true);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return SpaceKitEvents.shear(p);
		}
	}

	static final class EventHorizon extends KitParts.Timed {
		EventHorizon() {
			super(S, AbilitySlot.ABILITY_2, "Event Horizon", ENERGY, SpaceConfig.EN_AB2_COST, SpaceConfig.EN_AB2_COOLDOWN, SpaceConfig.EN_AB2_SECONDS, true);
		}

		@Override
		void second(ServerPlayer p) {
			SpaceKitEvents.horizonTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			SpaceKitEvents.horizonEnd(p);
		}
	}

	static ServerLevel level(ServerPlayer p) {
		return p.serverLevel();
	}
}
