package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
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
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LIGHT (source-of-truth document + decisions 2026-10-06).
 *
 *   Passive        All of daytime (22200-13800, decided): Resistance I + Speed II, 15 Qi / 30 min.
 *   Attack 1       10 Qi: Strength V (30 s placeholder).
 *   Heavy          20 Qi: for 30 s, players in line of sight show the aspect they have active
 *                  right now above their heads.
 *   Reinforce      15 Qi: standard + Regeneration 255 30 s; flown to the sky limit; creative
 *                  flight + Feather Falling for 2 min.
 *   Powerblast 1   Sorry But Please Go Away: 25 Qi. Unallied players within 50 pushed ~100 blocks away.
 *   Powerblast 2   May the Light Bless Your Soul: 35 Qi. Non-Reinforced Dark-active players nullified.
 *   Ability 1      I Am Good: 40 Qi. Teleport to the nearest spot with light >= 10, 30-50 blocks away.
 *   Ultimate       Wakey Wakey: 80 Qi. Daytime; Light users (enemies too) Resistance V, Haste V,
 *                  Speed V, Glowing; Dark users Weakness II.
 */
public final class LightKit {
	static final AspectType L = AspectType.LIGHT;

	private LightKit() {
	}

	public static AspectKit create() {
		return new AspectKit(L, new Passive(),
				new Attack1(), new Heavy(), new Reinforce(), new GoAway(), new Bless(), new IAmGood(), new WakeyWakey());
	}

	/** Daytime window (wraps past midnight), in dimensions with a day/night cycle. */
	static boolean isDaytime(Level level) {
		if (level.dimensionType().hasFixedTime())
			return false;
		long t = level.getDayTime() % 24000L;
		int start = AbilityConfig.LIGHT_DAY_START.get(), end = AbilityConfig.LIGHT_DAY_END.get();
		return start <= end ? t >= start && t < end : t >= start || t < end;
	}

	static boolean isActive(ServerPlayer p, AspectType aspect) {
		return AspectData.of(p).getActive() == aspect;
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return isDaytime(player.level());
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return perSeconds(AbilityConfig.LIGHT_PASSIVE_QI_PER_30MIN.get(), 1800);
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.LIGHT_PASSIVE_RESISTANCE.get(), 50, false);
			EffectHelper.apply(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.LIGHT_PASSIVE_SPEED.get(), 50, false);
		}
	}

	// ------------------------------------------------------------------ abilities

	static final class Attack1 extends Ability {
		Attack1() {
			super(L, AbilitySlot.ATTACK_1, "Attack 1");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.LIGHT_ATTACK1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.LIGHT_ATTACK1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.DAMAGE_BOOST, AbilityConfig.LIGHT_ATTACK1_STRENGTH.get(), AbilityConfig.LIGHT_ATTACK1_TICKS.get(), true);
			return ActionResult.ok("Radiant strength.");
		}
	}

	static final class Heavy extends Ability {
		Heavy() {
			super(L, AbilitySlot.HEAVY, "Heavy");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.LIGHT_HEAVY_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.LIGHT_HEAVY_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			LightEvents.startReveal(player, AbilityConfig.LIGHT_HEAVY_TICKS.get());
			return ActionResult.ok("Your eyes see through Qi: aspects revealed for 30 s.");
		}
	}

	static final class Reinforce extends Ability {
		Reinforce() {
			super(L, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.LIGHT_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.LIGHT_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.LIGHT_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			QiEffects.applyReinforce(player, L, AbilityConfig.LIGHT_REINFORCE_DURATION.get());
			EffectHelper.apply(player, MobEffects.REGENERATION, 255, AbilityConfig.LIGHT_REINFORCE_REGEN_TICKS.get(), true);
			LightEvents.ascendAndFly(player);
			return ActionResult.ok("Body reinforced. Rising to the sky; flight for 2 minutes.");
		}
	}

	static final class GoAway extends Ability {
		GoAway() {
			super(L, AbilitySlot.POWERBLAST_1, "Sorry But Please Go Away");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.LIGHT_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.LIGHT_PB1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			double r = AbilityConfig.LIGHT_PB1_RADIUS.get();
			player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 150, 2, 1, 2, 0.4);
			int n = 0;
			for (ServerPlayer t : player.serverLevel().players()) {
				if (!Targeting.isValid(player, t) || Targeting.isAllied(player, t) || t.distanceTo(player) > r || !QiEffects.canAffect(t, false))
					continue;
				LightEvents.pushAway(player, t);
				n++;
			}
			return ActionResult.ok(n == 0 ? "Nobody to send away." : "Sorry, but please go away. (" + n + ")");
		}
	}

	static final class Bless extends Ability {
		Bless() {
			super(L, AbilitySlot.POWERBLAST_2, "May the Light Bless Your Soul");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.LIGHT_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.LIGHT_PB2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			double r = AbilityConfig.LIGHT_PB2_RADIUS.get();
			int n = 0;
			for (ServerPlayer t : player.serverLevel().players()) {
				if (!Targeting.isValid(player, t) || Targeting.isAllied(player, t) || t.distanceTo(player) > r
						|| !isActive(t, AspectType.DARK) || QiEffects.isReinforced(t))
					continue;
				QiEffects.nullify(t, AspectType.DARK, AbilityConfig.LIGHT_PB2_NULLIFY_TICKS.get());
				t.serverLevel().sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY() + 1, t.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
				n++;
			}
			return ActionResult.ok(n == 0 ? "No darkness here to cleanse." : "Blessed " + n + " Dark cultivator" + (n == 1 ? "." : "s."));
		}
	}

	static final class IAmGood extends Ability {
		private final Map<UUID, Vec3> found = new HashMap<>();

		IAmGood() {
			super(L, AbilitySlot.ABILITY_1, "I Am Good");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.LIGHT_ABILITY1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.LIGHT_ABILITY1_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			Vec3 spot = findBrightSpot(player);
			if (spot == null) {
				found.remove(player.getUUID());
				return ActionResult.fail("No bright spot (light " + AbilityConfig.LIGHT_ABILITY1_MIN_LIGHT.get() + "+) 30-50 blocks away.");
			}
			found.put(player.getUUID(), spot);
			return ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			Vec3 to = found.remove(player.getUUID());
			if (to == null)
				return ActionResult.fail("Light lost.");
			player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 30, 0.3, 0.6, 0.3, 0.05);
			player.teleportTo(to.x, to.y, to.z);
			player.fallDistance = 0;
			return ActionResult.ok("I am good.");
		}

		/** Nearest standable spot with enough light, between min and max distance (sampled). */
		private static Vec3 findBrightSpot(ServerPlayer player) {
			ServerLevel level = player.serverLevel();
			RandomSource random = player.getRandom();
			BlockPos origin = player.blockPosition();
			double min = AbilityConfig.LIGHT_ABILITY1_MIN_DISTANCE.get(), max = AbilityConfig.LIGHT_ABILITY1_MAX_DISTANCE.get();
			int minLight = AbilityConfig.LIGHT_ABILITY1_MIN_LIGHT.get();
			BlockPos best = null;
			double bestDist = Double.MAX_VALUE;
			for (int i = 0; i < 500; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double dist = min + random.nextDouble() * (max - min);
				BlockPos column = origin.offset((int) Math.round(Math.cos(angle) * dist), 0, (int) Math.round(Math.sin(angle) * dist));
				BlockPos stand = findStandable(level, column, 16);
				if (stand == null)
					continue;
				double d = Math.sqrt(stand.distSqr(origin));
				if (d < min || d > max || level.getMaxLocalRawBrightness(stand) < minLight)
					continue;
				if (d < bestDist) {
					best = stand;
					bestDist = d;
				}
			}
			return best == null ? null : Vec3.atBottomCenterOf(best);
		}

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

	static final class WakeyWakey extends Ability {
		WakeyWakey() {
			super(L, AbilitySlot.ULTIMATE, "Wakey Wakey");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.LIGHT_ULT_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.LIGHT_ULT_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			// Force daytime (next morning, 1000) in every dimension with a day cycle.
			for (ServerLevel level : player.server.getAllLevels()) {
				if (level.dimensionType().hasFixedTime())
					continue;
				long t = level.getDayTime();
				long morning = t - t % 24000L + 1000L;
				if (morning < t)
					morning += 24000L;
				level.setDayTime(morning);
			}

			double r = AbilityConfig.LIGHT_ULT_RADIUS.get();
			int ticks = AbilityConfig.LIGHT_ULT_EFFECT_TICKS.get();
			for (ServerPlayer t : player.serverLevel().players()) {
				if (t.isSpectator() || !t.isAlive() || t.distanceTo(player) > r)
					continue;
				if (isActive(t, L)) { // all Light users, the user and enemies included
					EffectHelper.apply(t, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.LIGHT_ULT_RESISTANCE.get(), ticks, true);
					EffectHelper.apply(t, MobEffects.DIG_SPEED, AbilityConfig.LIGHT_ULT_HASTE.get(), ticks, true);
					EffectHelper.apply(t, MobEffects.MOVEMENT_SPEED, AbilityConfig.LIGHT_ULT_SPEED.get(), ticks, true);
					EffectHelper.apply(t, MobEffects.GLOWING, 1, ticks, false);
				} else if (isActive(t, AspectType.DARK)) {
					QiEffects.applyEffect(t, MobEffects.WEAKNESS, AbilityConfig.LIGHT_ULT_WEAKNESS.get(), ticks, false);
				}
			}
			return ActionResult.ok("Wakey wakey!");
		}
	}
}
