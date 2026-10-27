package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.Thorns;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.Comparator;
import java.util.List;

/**
 * DARK (source-of-truth document + decisions 2026-10-06).
 *
 *   Passive        1 Qi/min. Night Vision. At night (13800-22200): 2x total damage output and
 *                  incoming damage / 1.5.
 *   Attack 1       10 Qi, next hit. Blade: 2x damage + Blindness. Non-blade: Knockback II +
 *                  Mining Fatigue + Weakness.
 *   Heavy          20 Qi, next hit. In darkness (light <= 5): 3x damage + Knockback V. Always:
 *                  clears the target's effects, then Nausea + Mining Fatigue + Blindness.
 *   Reinforce      30 Qi (15 if light < 5), 10 s, standard. Light < 5: Thorns II.
 *   Powerblast 1   Shadow Essence: 5 Qi, 180 s cd. Light <= 5 only: +100 max health, Strength II,
 *                  Thorns II for 3 min. Refused (no Qi spent) in brighter light.
 *   Powerblast 2   Yeet: 10 Qi, 30 s cd. Nearest other player thrown 40 blocks up + Blindness.
 *   Ability 1      Shadow-Walk: 10 Qi, no cd. Under 50 hearts: heal 30 hearts + teleport to the
 *                  darkest safe spot (light <= 5) within 50. Otherwise: teleport to the nearest
 *                  player within 40 who stands in light <= 5.
 *   Ultimate       60 Qi. Time -> midnight. Players within 50: Blindness + Unluck XIV; Light-aspect
 *                  players also Weakness III and -20% max health for 3 min.
 * Effects without a listed duration/level: 30 s, level I (decided). Qi effects on others are
 * nullified by their Reinforce Body.
 */
public final class DarkKit {
	static final AspectType D = AspectType.DARK;

	private DarkKit() {
	}

	public static AspectKit create() {
		return new AspectKit(D, new Passive(),
				new Attack1(), new Heavy(), new Reinforce(), new ShadowEssence(), new Yeet(), new ShadowWalk(), new Ultimate());
	}

	static int effectTicks() {
		return AbilityConfig.DARK_EFFECT_TICKS.get();
	}

	static boolean inDarkness(LivingEntity e) {
		return EffectHelper.lightAt(e) <= AbilityConfig.DARK_DARKNESS_LIGHT.get();
	}

	/** Night window, in dimensions with a day/night cycle. */
	static boolean isNight(Level level) {
		if (level.dimensionType().hasFixedTime())
			return false;
		long t = level.getDayTime() % 24000L;
		return t >= AbilityConfig.DARK_NIGHT_START.get() && t <= AbilityConfig.DARK_NIGHT_END.get();
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return perSeconds(AbilityConfig.DARK_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			// Night Vision flickers under 10 s, so keep it comfortably above that.
			EffectHelper.apply(player, MobEffects.NIGHT_VISION, 1, 260, false);
		}

		@Override
		public double outgoingMultiplier(ServerPlayer player, LivingEntity target, DamageSource source) {
			return isNight(player.level()) ? AbilityConfig.DARK_NIGHT_OUTPUT_MULTIPLIER.get() : 1.0;
		}

		@Override
		public double incomingReduction(ServerPlayer player, DamageSource source) {
			return isNight(player.level()) ? 1.0 - 1.0 / AbilityConfig.DARK_NIGHT_INCOMING_DIVISOR.get() : 0.0;
		}
	}

	// ------------------------------------------------------------------ abilities

	static final class Attack1 extends Ability {
		Attack1() {
			super(D, AbilitySlot.ATTACK_1, "Attack 1");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.DARK_ATTACK1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.DARK_ATTACK1_COOLDOWN.get();
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (attacker.getMainHandItem().is(ItemTags.SWORDS)) {
				QiEffects.applyEffect(target, MobEffects.BLINDNESS, 1, effectTicks(), false);
				return (float) (damage * AbilityConfig.DARK_ATTACK1_BLADE_MULTIPLIER.get());
			}
			QiEffects.knockback(target, attacker, AbilityConfig.DARK_ATTACK1_KNOCKBACK.get(), false);
			QiEffects.applyEffect(target, MobEffects.DIG_SLOWDOWN, 1, effectTicks(), false);
			QiEffects.applyEffect(target, MobEffects.WEAKNESS, 1, effectTicks(), false);
			return damage;
		}
	}

	static final class Heavy extends Ability {
		Heavy() {
			super(D, AbilitySlot.HEAVY, "Heavy");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.DARK_HEAVY_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.DARK_HEAVY_COOLDOWN.get();
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			float result = damage;
			if (inDarkness(attacker)) {
				result = (float) (damage * AbilityConfig.DARK_HEAVY_DARK_MULTIPLIER.get());
				QiEffects.knockback(target, attacker, AbilityConfig.DARK_HEAVY_KNOCKBACK.get(), false);
			}
			if (QiEffects.canAffect(target, false)) {
				target.removeAllEffects(); // "Clears target potion effects"
				QiEffects.applyEffect(target, MobEffects.CONFUSION, 1, effectTicks(), false);
				QiEffects.applyEffect(target, MobEffects.DIG_SLOWDOWN, 1, effectTicks(), false);
				QiEffects.applyEffect(target, MobEffects.BLINDNESS, 1, effectTicks(), false);
			}
			return result;
		}
	}

	static final class Reinforce extends Ability {
		Reinforce() {
			super(D, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		/** "Base 30 Qi; if light level < 5, cost reduced 50%." */
		@Override
		public double baseCost(Player p) {
			double base = AbilityConfig.DARK_REINFORCE_COST.get();
			return base > 0 && EffectHelper.lightAt(p) < 5 ? base * 0.5 : base;
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.DARK_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.DARK_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = AbilityConfig.DARK_REINFORCE_DURATION.get();
			QiEffects.applyReinforce(player, D, ticks);
			if (EffectHelper.lightAt(player) < 5) {
				Thorns.grant(player, "dark_reinforce", AbilityConfig.DARK_REINFORCE_THORNS.get(), ticks);
				return ActionResult.ok("Body reinforced in shadow (Thorns II).");
			}
			return ActionResult.ok("Body reinforced.");
		}
	}

	static final class ShadowEssence extends Ability {
		ShadowEssence() {
			super(D, AbilitySlot.POWERBLAST_1, "Shadow Essence");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.DARK_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.DARK_PB1_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return EffectHelper.lightAt(player) <= AbilityConfig.DARK_DARKNESS_LIGHT.get() ? ActionResult.ok("")
					: ActionResult.fail("Too bright: Shadow Essence needs light level " + AbilityConfig.DARK_DARKNESS_LIGHT.get() + " or lower.");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = AbilityConfig.DARK_PB1_DURATION.get();
			TimedAttributes.apply(player, Attributes.MAX_HEALTH, "dark_shadow_essence", AbilityConfig.DARK_PB1_MAX_HEALTH.get(),
					AttributeModifier.Operation.ADD_VALUE, ticks);
			EffectHelper.apply(player, MobEffects.DAMAGE_BOOST, AbilityConfig.DARK_PB1_STRENGTH.get(), ticks, true);
			Thorns.grant(player, "dark_shadow_essence", AbilityConfig.DARK_PB1_THORNS.get(), ticks);
			player.serverLevel().sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
			return ActionResult.ok("Shadow Essence: +max health, Strength II, Thorns II.");
		}
	}

	static final class Yeet extends Ability {
		Yeet() {
			super(D, AbilitySlot.POWERBLAST_2, "Yeet");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.DARK_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.DARK_PB2_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return Targeting.nearestPlayer(player) == null ? ActionResult.fail("No other player to throw.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer target = Targeting.nearestPlayer(player);
			if (target == null)
				return ActionResult.fail("Target lost.");
			if (!QiEffects.canAffect(target, false))
				return ActionResult.ok(target.getName().getString() + "'s Reinforced Body nullified it.");
			Vec3 v = target.getDeltaMovement();
			target.setDeltaMovement(v.x, EffectHelper.launchSpeedForHeight(AbilityConfig.DARK_PB2_HEIGHT.get()), v.z);
			target.hurtMarked = true;
			QiEffects.applyEffect(target, MobEffects.BLINDNESS, 1, effectTicks(), false);
			return ActionResult.ok("Yeet!");
		}
	}

	static final class ShadowWalk extends Ability {
		ShadowWalk() {
			super(D, AbilitySlot.ABILITY_1, "Shadow-Walk");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.DARK_ABILITY1_COST.get();
		}

		/** Spot found by checkUse, reused by perform in the same cast (sampling is random). */
		private static final java.util.Map<java.util.UUID, Vec3> FOUND = new java.util.HashMap<>();

		private static boolean lowHealth(ServerPlayer p) {
			return p.getHealth() < AbilityConfig.DARK_ABILITY1_HEALTH_THRESHOLD.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			if (lowHealth(player)) {
				Vec3 spot = findDarkestSpot(player, AbilityConfig.DARK_ABILITY1_ESCAPE_RANGE.get());
				FOUND.put(player.getUUID(), spot);
				return spot == null ? ActionResult.fail("No dark spot (light " + AbilityConfig.DARK_DARKNESS_LIGHT.get() + " or lower) within range.") : ActionResult.ok("");
			}
			return playerInShadow(player) == null ? ActionResult.fail("No player standing in darkness within range.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			if (lowHealth(player)) {
				Vec3 spot = FOUND.remove(player.getUUID()); // found by checkUse this same call
				if (spot == null)
					spot = findDarkestSpot(player, AbilityConfig.DARK_ABILITY1_ESCAPE_RANGE.get());
				if (spot == null)
					return ActionResult.fail("No dark spot found.");
				player.heal(AbilityConfig.DARK_ABILITY1_HEAL.get().floatValue());
				teleport(player, spot);
				return ActionResult.ok("You slip into the shadows.");
			}
			ServerPlayer target = playerInShadow(player);
			if (target == null)
				return ActionResult.fail("Target lost.");
			teleport(player, target.position());
			return ActionResult.ok("You step out of " + target.getName().getString() + "'s shadow.");
		}

		private static ServerPlayer playerInShadow(ServerPlayer player) {
			double r = AbilityConfig.DARK_ABILITY1_PLAYER_RANGE.get();
			return player.serverLevel().players().stream()
					.filter(p -> Targeting.isValid(player, p) && p.distanceTo(player) <= r && EffectHelper.lightAt(p) <= AbilityConfig.DARK_DARKNESS_LIGHT.get())
					.min(Comparator.comparingDouble(p -> p.distanceToSqr(player)))
					.orElse(null);
		}

		private static void teleport(ServerPlayer player, Vec3 to) {
			player.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.01);
			player.teleportTo(to.x, to.y, to.z);
			player.fallDistance = 0;
		}

		/**
		 * Darkest safe standing spot (light <= 5) within range: samples candidate columns around the
		 * player, finds standable ground near the player's height, and keeps the darkest (nearest on ties).
		 */
		static Vec3 findDarkestSpot(ServerPlayer player, int range) {
			ServerLevel level = player.serverLevel();
			RandomSource random = player.getRandom();
			BlockPos origin = player.blockPosition();
			BlockPos best = null;
			int bestLight = Integer.MAX_VALUE;
			double bestDist = Double.MAX_VALUE;
			for (int i = 0; i < 400; i++) {
				int dx = random.nextInt(range * 2 + 1) - range;
				int dz = random.nextInt(range * 2 + 1) - range;
				if (dx * dx + dz * dz > range * range)
					continue;
				BlockPos stand = findStandable(level, origin.offset(dx, 0, dz), 16);
				if (stand == null)
					continue;
				int light = level.getMaxLocalRawBrightness(stand);
				if (light > AbilityConfig.DARK_DARKNESS_LIGHT.get())
					continue;
				double dist = stand.distSqr(origin);
				if (light < bestLight || light == bestLight && dist < bestDist) {
					best = stand;
					bestLight = light;
					bestDist = dist;
				}
			}
			return best == null ? null : Vec3.atBottomCenterOf(best);
		}

		/** A position with solid ground below and two free blocks, searched downward from +span to -span. */
		private static BlockPos findStandable(ServerLevel level, BlockPos column, int span) {
			for (int dy = span; dy >= -span; dy--) {
				BlockPos p = column.above(dy);
				if (!level.isInWorldBounds(p) || !level.isLoaded(p))
					continue;
				BlockPos below = p.below();
				if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
						&& level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty()
						&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty() && level.getFluidState(p.above()).isEmpty())
					return p;
			}
			return null;
		}
	}

	static final class Ultimate extends Ability {
		Ultimate() {
			super(D, AbilitySlot.ULTIMATE, "Ultimate");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.DARK_ULT_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.DARK_ULT_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			// Force world/server time to midnight (18000), moving forward to the next midnight.
			for (ServerLevel level : player.server.getAllLevels()) {
				long t = level.getDayTime();
				long midnight = t - t % 24000L + 18000L;
				if (midnight < t)
					midnight += 24000L;
				level.setDayTime(midnight);
			}

			double r = AbilityConfig.DARK_ULT_RADIUS.get();
			int ticks = effectTicks();
			int hit = 0;
			for (ServerPlayer p : player.serverLevel().players()) {
				if (!Targeting.isValid(player, p) || p.distanceTo(player) > r || !QiEffects.canAffect(p, false))
					continue;
				hit++;
				EffectHelper.apply(p, MobEffects.BLINDNESS, 1, ticks, true);
				EffectHelper.apply(p, MobEffects.UNLUCK, AbilityConfig.DARK_ULT_UNLUCK.get(), ticks, true);
				if (AspectData.of(p).getActive() == AspectType.LIGHT) {
					int lt = AbilityConfig.DARK_ULT_LIGHT_DURATION.get();
					EffectHelper.apply(p, MobEffects.WEAKNESS, AbilityConfig.DARK_ULT_LIGHT_WEAKNESS.get(), lt, true);
					TimedAttributes.apply(p, Attributes.MAX_HEALTH, "dark_ultimate_light_penalty", -AbilityConfig.DARK_ULT_LIGHT_HEALTH_REDUCTION.get(),
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, lt);
				}
			}
			return ActionResult.ok("Darkness falls. " + hit + " player(s) engulfed.");
		}
	}
}
