package net.zyren.cultivation.ability.kits;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.CastRules;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.SubAspectKit;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.PhysicalConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.List;
import java.util.function.Supplier;

import static net.zyren.cultivation.ability.kits.PhysicalDefaultKit.around;
import static net.zyren.cultivation.ability.kits.PhysicalDefaultKit.horizontal;
import static net.zyren.cultivation.ability.kits.PhysicalDefaultKit.lookedOrNearestVisible;
import static net.zyren.cultivation.ability.kits.PhysicalDefaultKit.moveDirection;
import static net.zyren.cultivation.ability.kits.PhysicalDefaultKit.noOne;
import static net.zyren.cultivation.ability.kits.PhysicalDefaultKit.seconds;
import static net.zyren.cultivation.ability.kits.PhysicalDefaultKit.slide;
import static net.zyren.cultivation.ability.kits.PhysicalDefaultKit.unalliedWithin;

/**
 * Physical's sub-aspects (ASPECT GUIDE + user "rec" 2026-11-06): Warrior, Body, Instinct (Instinct
 * replaced Mind). Each lists all 7 slots except Reinforce (falls back to Physical's). Every ability
 * uses its parent PHYSICAL + the same slot, so cooldowns are shared. Event side: PhysicalKitEvents.
 */
public final class PhysicalSubKits {
	static final AspectType P = AspectType.PHYSICAL;

	private PhysicalSubKits() {
	}

	/** Timed ability whose cooldown starts when it ends ("cooldown starts after duration is up"). */
	abstract static class TimedAfter extends KitParts.Timed {
		private final Supplier<? extends Number> after, secs;

		TimedAfter(AbilitySlot slot, String name, SubAspectType sub, Supplier<? extends Number> cost, Supplier<? extends Number> cdAfter,
				Supplier<? extends Number> seconds, boolean breaksCore) {
			super(P, slot, name, sub, cost, cdAfter, seconds, breaksCore);
			this.after = cdAfter;
			this.secs = seconds;
		}

		@Override
		public int cooldownTicks() {
			return (after.get().intValue() + secs.get().intValue()) * 20;
		}
	}

	static boolean isSword(ItemStack s) {
		return s.is(ItemTags.SWORDS);
	}

	// ================================================================== WARRIOR

	static final SubAspectType WARRIOR = SubAspectType.PHYSICAL_WARRIOR;
	static final WarriorPassive WR_PASSIVE = new WarriorPassive();
	static final MeasuredStrike MEASURED = new MeasuredStrike();
	static final Parry PARRY = new Parry();
	static final Sidestep SIDESTEP = new Sidestep();
	static final WarBanner BANNER = new WarBanner();
	static final FlowingStance FLOWING = new FlowingStance();

	public static SubAspectKit warrior() {
		return new SubAspectKit(WARRIOR, WR_PASSIVE, MEASURED, PARRY, SIDESTEP, BANNER, FLOWING, new ThousandCuts()).onLeave(PhysicalKitEvents::leaveWarrior);
	}

	static boolean warriorPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == WR_PASSIVE;
	}

	static final String WR_KB = "physical_warrior_kb", WR_SPD = "physical_warrior_speed", WR_DMG = "physical_warrior_damage";

	static void removeWarriorAttributes(ServerPlayer p) {
		TimedAttributes.remove(p, WR_KB);
		TimedAttributes.remove(p, WR_SPD);
		TimedAttributes.remove(p, WR_DMG);
	}

	/** Holding a sword: + attack knockback / attack speed / attack damage (exact attribute values). */
	static final class WarriorPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(PhysicalConfig.WR_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			if (!isSword(p.getMainHandItem())) {
				removeWarriorAttributes(p);
				return;
			}
			var add = AttributeModifier.Operation.ADD_VALUE;
			TimedAttributes.apply(p, Attributes.ATTACK_KNOCKBACK, WR_KB, PhysicalConfig.WR_KNOCKBACK.get(), add, 30);
			TimedAttributes.apply(p, Attributes.ATTACK_SPEED, WR_SPD, PhysicalConfig.WR_ATTACK_SPEED.get(), add, 30);
			TimedAttributes.apply(p, Attributes.ATTACK_DAMAGE, WR_DMG, PhysicalConfig.WR_DAMAGE.get(), add, 30);
		}
	}

	/** Fully charged sword hits deal bonus (PhysicalKitEvents). */
	static final class MeasuredStrike extends TimedAfter {
		MeasuredStrike() {
			super(AbilitySlot.ATTACK_1, "Measured strike", WARRIOR, PhysicalConfig.WR_A1_COST, PhysicalConfig.WR_A1_COOLDOWN, PhysicalConfig.WR_A1_SECONDS, false);
		}
	}

	/** Parry window (PhysicalKitEvents decides what is parried). Whiff = longer cooldown. */
	static final class Parry extends KitParts.Timed {
		Parry() {
			super(P, AbilitySlot.HEAVY, "PARRY COUNTERPLAY", WARRIOR, PhysicalConfig.WR_A2_COST, PhysicalConfig.WR_A2_COOLDOWN, PhysicalConfig.WR_A2_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			PhysicalKitEvents.startParry(p);
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.endParry(p);
		}
	}

	static final class Sidestep extends KitParts.Cfg {
		Sidestep() {
			super(P, AbilitySlot.POWERBLAST_1, "SIDESTEP", WARRIOR, PhysicalConfig.WR_PB1_COST, PhysicalConfig.WR_PB1_COOLDOWN, false);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			Vec3 look = horizontal(p.getLookAngle());
			if (look == Vec3.ZERO)
				look = horizontal(Vec3.directionFromRotation(0, p.getYRot()));
			Vec3 right = new Vec3(-look.z, 0, look.x);
			int side = PhysicalKitEvents.recentStrafe(p); // +1 right, -1 left, 0 none
			Vec3 dir = side < 0 ? right.scale(-1) : right;
			slide(p, dir, PhysicalConfig.WR_PB1_HOP.get(), false);
			int dodge = PhysicalConfig.WR_PB1_DODGE_TICKS.get();
			CastRules.hideFromTargeting(p, dodge);
			for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p))
				m.setTarget(null);
			PhysicalKitEvents.onSidestep(p);
			return ActionResult.ok("Sidestep!");
		}
	}

	/** Banner at your feet; buffs Physical allies near it; one unallied hit breaks it (PhysicalKitEvents). */
	static final class WarBanner extends TimedAfter {
		WarBanner() {
			super(AbilitySlot.POWERBLAST_2, "WAR BANNER", WARRIOR, PhysicalConfig.WR_PB2_COST, PhysicalConfig.WR_PB2_COOLDOWN, PhysicalConfig.WR_PB2_SECONDS, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return EarthWorld.open(p.serverLevel(), p.blockPosition()) ? ActionResult.ok("") : ActionResult.fail("No room for a banner here.");
		}

		@Override
		void start(ServerPlayer p) {
			PhysicalKitEvents.plantBanner(p, sustainTicks());
		}

		@Override
		void second(ServerPlayer p) {
			PhysicalKitEvents.bannerTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.removeBanner(p);
		}
	}

	/** Parry / sidestep refunds + cooldown resets, parry damage stacks (PhysicalKitEvents). */
	static final class FlowingStance extends KitParts.Timed {
		FlowingStance() {
			super(P, AbilitySlot.ABILITY_1, "FLOWING STANCE", WARRIOR, PhysicalConfig.WR_AB1_COST, PhysicalConfig.WR_AB1_COOLDOWN, PhysicalConfig.WR_AB1_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			PhysicalKitEvents.STACKS.remove(p.getUUID());
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.STACKS.remove(p.getUUID());
		}
	}

	/** Sword wave in an arc ahead: everything (allies too) damaged, bleeding, Reinforce broken + locked. */
	static final class ThousandCuts extends KitParts.Cfg {
		ThousandCuts() {
			super(P, AbilitySlot.ABILITY_2, "THOUSAND CUTS", WARRIOR, PhysicalConfig.WR_AB2_COST, PhysicalConfig.WR_AB2_COOLDOWN, true);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerLevel level = p.serverLevel();
			double range = PhysicalConfig.WR_AB2_RANGE.get(), half = Math.toRadians(PhysicalConfig.WR_AB2_ARC.get() / 2.0);
			Vec3 look = horizontal(p.getLookAngle());
			if (look == Vec3.ZERO)
				look = horizontal(Vec3.directionFromRotation(0, p.getYRot()));
			int n = 0;
			for (LivingEntity e : around(p, p.position(), range + 1, true)) {
				Vec3 to = horizontal(e.position().subtract(p.position()));
				if (e.distanceTo(p) > range || (to != Vec3.ZERO && Math.acos(Math.max(-1, Math.min(1, to.dot(look)))) > half))
					continue;
				QiEffects.dealDamage(p, e, PhysicalConfig.WR_AB2_DAMAGE.get().floatValue());
				PhysicalKitEvents.bleed(p, e, PhysicalConfig.WR_AB2_BLEED_SECONDS.get());
				if (e instanceof ServerPlayer sp)
					PhysicalKitEvents.breakReinforce(sp, PhysicalConfig.WR_AB2_REINFORCE_BREAK_SECONDS.get());
				n++;
			}
			double yaw = Math.atan2(look.z, look.x);
			for (int i = -6; i <= 6; i++) {
				double a = yaw + half * i / 6.0;
				for (double r = 1.5; r <= range; r += 1.5)
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(a) * r, p.getY() + 1, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
			return ActionResult.ok("THOUSAND CUTS: " + n + " hit.");
		}
	}

	// ================================================================== BODY

	static final SubAspectType BODY = SubAspectType.PHYSICAL_BODY;
	static final BodyPassive BD_PASSIVE = new BodyPassive();
	static final BodyBuilder BODY_BUILDER = new BodyBuilder();
	static final Kratos KRATOS = new Kratos();
	static final HardenedFist HARDENED = new HardenedFist();

	public static SubAspectKit body() {
		return new SubAspectKit(BODY, BD_PASSIVE, HARDENED, new Horizons(), new Boulder(), new DashingFists(), BODY_BUILDER, KRATOS)
				.onLeave(PhysicalKitEvents::leaveBody);
	}

	static final TagKey<Item> QI_POWERED = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "qipowered"));
	static final TagKey<Item> CULTIVATED_MADE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "cultivatedmade"));

	/** Nothing, leather, chainmail, or tagged qipowered / cultivatedmade in every armor slot. */
	static boolean bodyArmorOk(Player p) {
		for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack st = p.getItemBySlot(s);
			if (st.isEmpty() || st.is(QI_POWERED) || st.is(CULTIVATED_MADE))
				continue;
			if (st.getItem() instanceof ArmorItem a && (a.getMaterial().is(ArmorMaterials.LEATHER) || a.getMaterial().is(ArmorMaterials.CHAIN)))
				continue;
			return false;
		}
		return true;
	}

	static boolean bodyPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == BD_PASSIVE;
	}

	static final String BD_STEP = "physical_body_step";

	static final class BodyPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(PhysicalConfig.BD_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			if (!bodyArmorOk(p))
				return;
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, PhysicalConfig.BD_STRENGTH.get(), 50, false);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, PhysicalConfig.BD_SPEED.get(), 50, false);
			double step = PhysicalConfig.BD_STEP.get();
			if (step > 0.6)
				TimedAttributes.apply(p, Attributes.STEP_HEIGHT, BD_STEP, step - 0.6, AttributeModifier.Operation.ADD_VALUE, 30);
		}
	}

	static final class HardenedFist extends KitParts.Cfg {
		HardenedFist() {
			super(P, AbilitySlot.ATTACK_1, "hardend fist", BODY, PhysicalConfig.BD_A1_COST, PhysicalConfig.BD_A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return PhysicalKitEvents.HARDENED.left(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			PhysicalKitEvents.HARDENED.start(p, PhysicalConfig.BD_A1_HITS.get());
			return ActionResult.ok("Hardened fist: next " + PhysicalConfig.BD_A1_HITS.get() + " bare-fist hits +" + PhysicalConfig.BD_A1_EXTRA.get() + ".");
		}
	}

	/** Ballistic leap: peaks at vertical_blocks, lands about horizontal_blocks away (player air drag included). */
	static final class Horizons extends KitParts.Cfg {
		Horizons() {
			super(P, AbilitySlot.HEAVY, "to the horizons", BODY, PhysicalConfig.BD_A2_COST, PhysicalConfig.BD_A2_COOLDOWN, false);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			double vy = EffectHelper.launchSpeedForHeight(PhysicalConfig.BD_A2_VERTICAL.get());
			// simulate the vertical flight back to launch height: player gravity 0.08, drag 0.98
			int ticks = 0;
			double y = 0, v = vy;
			do {
				y += v;
				v = (v - 0.08) * 0.98;
				ticks++;
			} while (y > 0 && ticks < 400);
			// horizontal: v0 * (1 - 0.91^T) / (1 - 0.91) = distance (air drag 0.91 per tick)
			double h = PhysicalConfig.BD_A2_HORIZONTAL.get() * (1 - 0.91) / (1 - Math.pow(0.91, ticks));
			Vec3 dir = moveDirection(p);
			p.setDeltaMovement(dir.x * h, vy, dir.z * h);
			p.hurtMarked = true;
			PhysicalKitEvents.noFallUntilLanded(p);
			return ActionResult.ok("To the horizons!");
		}
	}

	/** Fly at a player, breaking through up to N blocks; damage on contact. */
	static final class Boulder extends KitParts.Cfg {
		Boulder() {
			super(P, AbilitySlot.POWERBLAST_1, "i am the boulder", BODY, PhysicalConfig.BD_PB1_COST, PhysicalConfig.BD_PB1_COOLDOWN, false);
		}

		static ServerPlayer target(ServerPlayer p) {
			int r = PhysicalConfig.BD_PB1_RANGE.get();
			ServerPlayer looked = Targeting.lookedAtPlayer(p);
			if (looked != null && !Targeting.isAllied(p, looked) && looked.distanceTo(p) <= r)
				return looked;
			List<ServerPlayer> near = unalliedWithin(p, r);
			return near.isEmpty() ? null : near.get(0);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return target(p) == null ? ActionResult.fail("No one within " + PhysicalConfig.BD_PB1_RANGE.get() + " blocks.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = target(p);
			if (t == null)
				return ActionResult.fail("Target lost.");
			PhysicalKitEvents.fly(p, t, 0, 1.5, PhysicalConfig.BD_PB1_THROUGH.get(), PhysicalConfig.BD_PB1_DAMAGE.get().floatValue(), null);
			return ActionResult.ok("I am the boulder → " + t.getGameProfile().getName() + ".");
		}
	}

	/** Dash to the nearest unallied player, then a punch combo (PhysicalKitEvents). */
	static final class DashingFists extends KitParts.Cfg {
		DashingFists() {
			super(P, AbilitySlot.POWERBLAST_2, "dashing fists of fury", BODY, PhysicalConfig.BD_PB2_COST, PhysicalConfig.BD_PB2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return unalliedWithin(p, PhysicalConfig.BD_PB2_RANGE.get()).isEmpty() ? ActionResult.fail("No one within " + PhysicalConfig.BD_PB2_RANGE.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			List<ServerPlayer> near = unalliedWithin(p, PhysicalConfig.BD_PB2_RANGE.get());
			if (near.isEmpty())
				return ActionResult.fail("Target lost.");
			ServerPlayer t = near.get(0);
			PhysicalKitEvents.fly(p, t, 0, 1.5, 0, 0, () -> PhysicalKitEvents.startPunches(p, t));
			return ActionResult.ok("Dashing fists of fury → " + t.getGameProfile().getName() + ".");
		}
	}

	static final class BodyBuilder extends KitParts.Timed {
		BodyBuilder() {
			super(P, AbilitySlot.ABILITY_1, "expert body builder", BODY, PhysicalConfig.BD_AB1_COST, PhysicalConfig.BD_AB1_COOLDOWN, PhysicalConfig.BD_AB1_SECONDS, true);
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, PhysicalConfig.BD_AB1_STRENGTH.get(), 30, true);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, PhysicalConfig.BD_AB1_SPEED.get(), 30, true);
			EffectHelper.apply(p, MobEffects.DIG_SPEED, PhysicalConfig.BD_AB1_HASTE.get(), 30, true);
			// same id as the passive step: the passive's (higher) value wins while it applies
			if (!(bodyPassiveOn(p) && bodyArmorOk(p)) && PhysicalConfig.BD_AB1_STEP.get() > 0.6)
				TimedAttributes.apply(p, Attributes.STEP_HEIGHT, BD_STEP, PhysicalConfig.BD_AB1_STEP.get() - 0.6, AttributeModifier.Operation.ADD_VALUE, 30);
			for (ServerPlayer o : p.serverLevel().players())
				if (Targeting.isValid(p, o) && o.distanceTo(p) <= PhysicalConfig.BD_AB1_GLOW_RADIUS.get())
					QiEffects.applyEffect(o, MobEffects.GLOWING, 1, 40, false);
		}
	}

	/** Launched up; landing makes the crater + flings (PhysicalKitEvents); no fall damage while it runs. */
	static final class Kratos extends KitParts.Timed {
		Kratos() {
			super(P, AbilitySlot.ABILITY_2, "kratos crator", BODY, PhysicalConfig.BD_AB2_COST, PhysicalConfig.BD_AB2_COOLDOWN, PhysicalConfig.BD_AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			Motion.launchUp(p, PhysicalConfig.BD_AB2_UP.get());
			PhysicalKitEvents.startKratos(p);
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.KRATOS_JOBS.remove(p.getUUID());
		}
	}

	// ================================================================== INSTINCT

	static final SubAspectType INSTINCT = SubAspectType.PHYSICAL_INSTINCT;
	static final InstinctPassive IN_PASSIVE = new InstinctPassive();
	static final EnhancedCombo COMBO = new EnhancedCombo();
	static final SurvivalInstinct SURVIVAL = new SurvivalInstinct();
	static final QiGuided GUIDED = new QiGuided();
	static final Awareness AWARENESS = new Awareness();
	static final Unchained UNCHAINED = new Unchained();
	static final QiInfusedBody INFUSED = new QiInfusedBody();

	public static SubAspectKit instinct() {
		return new SubAspectKit(INSTINCT, IN_PASSIVE, COMBO, SURVIVAL, GUIDED, AWARENESS, UNCHAINED, INFUSED).onLeave(PhysicalKitEvents::leaveInstinct);
	}

	static boolean instinctPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == IN_PASSIVE;
	}

	/** Dodge dashes: PhysicalKitEvents. */
	static final class InstinctPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return !KitParts.passiveDisabled(p);
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(PhysicalConfig.IN_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			PhysicalKitEvents.regenDashes(p);
		}
	}

	static final class EnhancedCombo extends KitParts.Timed {
		EnhancedCombo() {
			super(P, AbilitySlot.ATTACK_1, "enhanced combo", INSTINCT, PhysicalConfig.IN_A1_COST, PhysicalConfig.IN_A1_COOLDOWN, PhysicalConfig.IN_A1_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			PhysicalKitEvents.STREAKS.remove(p.getUUID());
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DIG_SPEED, PhysicalConfig.IN_A1_HASTE.get(), 30, true);
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.STREAKS.remove(p.getUUID());
		}
	}

	static final class SurvivalInstinct extends KitParts.Timed {
		SurvivalInstinct() {
			super(P, AbilitySlot.HEAVY, "survival instinct", INSTINCT, PhysicalConfig.IN_A2_COST, PhysicalConfig.IN_A2_COOLDOWN, PhysicalConfig.IN_A2_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			PhysicalKitEvents.SHIELD_BLOCKS.put(p.getUUID(), PhysicalConfig.IN_A2_BLOCKS.get());
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.SHIELD_BLOCKS.remove(p.getUUID());
		}
	}

	static final class QiGuided extends KitParts.Timed {
		QiGuided() {
			super(P, AbilitySlot.POWERBLAST_1, "qi guided attacks", INSTINCT, PhysicalConfig.IN_PB1_COST, PhysicalConfig.IN_PB1_COOLDOWN, PhysicalConfig.IN_PB1_SECONDS, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return unalliedWithin(p, PhysicalConfig.IN_PB1_RADIUS.get()).isEmpty() ? ActionResult.fail("No one within " + PhysicalConfig.IN_PB1_RADIUS.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		void start(ServerPlayer p) {
			List<ServerPlayer> near = unalliedWithin(p, PhysicalConfig.IN_PB1_RADIUS.get());
			if (!near.isEmpty())
				PhysicalKitEvents.GUIDED_TARGET.put(p.getUUID(), near.get(0).getUUID());
		}

		@Override
		void second(ServerPlayer p) {
			ServerPlayer t = PhysicalKitEvents.guidedTarget(p);
			if (t != null && t.distanceTo(p) <= PhysicalConfig.IN_PB1_RADIUS.get())
				QiEffects.applyEffect(t, MobEffects.GLOWING, 1, 40, false);
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.GUIDED_TARGET.remove(p.getUUID());
		}
	}

	static final class Awareness extends KitParts.Timed {
		Awareness() {
			super(P, AbilitySlot.POWERBLAST_2, "awareness of the hunter", INSTINCT, PhysicalConfig.IN_PB2_COST, PhysicalConfig.IN_PB2_COOLDOWN, PhysicalConfig.IN_PB2_SECONDS, false);
		}

		@Override
		void second(ServerPlayer p) {
			PhysicalKitEvents.senseHunters(p);
		}

		@Override
		void end(ServerPlayer p) {
			net.zyren.cultivation.network.SensedPlayersMessage.send(p, List.of());
		}
	}

	static final class Unchained extends KitParts.Timed {
		Unchained() {
			super(P, AbilitySlot.ABILITY_1, "unchained awareness of the parrying blade", INSTINCT, PhysicalConfig.IN_AB1_COST, PhysicalConfig.IN_AB1_COOLDOWN,
					PhysicalConfig.IN_AB1_SECONDS, true);
		}

		@Override
		boolean disablesPassive() {
			return true;
		}

		@Override
		void start(ServerPlayer p) {
			PhysicalKitEvents.UNCHAINED_HITS.put(p.getUUID(), PhysicalConfig.IN_AB1_HITS.get());
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.UNCHAINED_HITS.remove(p.getUUID());
		}
	}

	static final class QiInfusedBody extends KitParts.Timed {
		QiInfusedBody() {
			super(P, AbilitySlot.ABILITY_2, "qi infused body", INSTINCT, PhysicalConfig.IN_AB2_COST, PhysicalConfig.IN_AB2_COOLDOWN, PhysicalConfig.IN_AB2_SECONDS, true);
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, PhysicalConfig.IN_AB2_STRENGTH.get(), 30, true);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, PhysicalConfig.IN_AB2_SPEED.get(), 30, true);
			EffectHelper.apply(p, MobEffects.DIG_SPEED, PhysicalConfig.IN_AB2_HASTE.get(), 30, true);
		}
	}
}
