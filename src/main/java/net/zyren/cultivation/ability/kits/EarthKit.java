package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.Tags;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.List;

/**
 * EARTH (source-of-truth document + decisions 2026-10-06). Terrain changes are permanent (decided).
 *
 *   Passive        2 Qi / 15 s. Haste V. Constant Fortune III on mined blocks.
 *   Attack 1       5 Qi, next hit: 2x damage, Knockback V, Slowness II 1 min. If the target is
 *                  slammed into a qualifying solid block: the hit's damage again + 2 s stun.
 *   Heavy          20 Qi. Non-allied players within 50: launched 20 up + 40 damage.
 *   Reinforce      10 Qi, 10 s: standard + 50% knockback resistance, input locked 3 s,
 *                  Resistance XV while standing on stone.
 *   Powerblast 1   15 Qi, 15 s cd. Target: 30 dmg + Knockback IV; stunned 2 s if it touches
 *                  stone; stone line laid from you to the target.
 *   Powerblast 2   40 Qi, 60 s cd. Non-allied within 20: Slowness III + shaken for 1-2 s. Grass
 *                  within 20 turns to stone; then everything on stone (not you) is launched 50 up.
 *                  You get Regeneration 255 for 1 s.
 *   Ability 1      25 Qi, 2 min cd. Ground around you (radius 5) becomes stone and rises 5
 *                  blocks. You: Resistance XV; allies within 30: Regeneration III (30 s).
 *   Ultimate       60 Qi. Non-allied within 50: sent to the sky limit, Mining Fatigue + Blindness
 *                  + Nausea; the ground under each rises 15-40 blocks. You: Regeneration 255 3 s,
 *                  Resistance XXV 10 s.
 */
public final class EarthKit {
	static final AspectType E = AspectType.EARTH;

	private EarthKit() {
	}

	public static AspectKit create() {
		return new AspectKit(E, new Passive(),
				new Attack1(), new Heavy(), new Reinforce(), new Powerblast1(), new Powerblast2(), new Ability1(), new Ultimate());
	}

	static int effectTicks() {
		return AbilityConfig.EARTH_EFFECT_TICKS.get();
	}

	/** "Stone-type" block: vanilla/NeoForge stone and cobblestone families, stone bricks. */
	static boolean isStone(BlockState s) {
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(Tags.Blocks.STONES)
				|| s.is(Tags.Blocks.COBBLESTONES) || s.is(BlockTags.STONE_BRICKS);
	}

	static boolean onStone(Entity e) {
		BlockPos below = BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ());
		return isStone(e.level().getBlockState(below));
	}

	/** Blocks the user may reshape: not air-only checks, never unbreakable or block-entity blocks. */
	static boolean canReshape(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.getDestroySpeed(level, pos) >= 0 && !s.hasBlockEntity();
	}

	static void launch(LivingEntity target, double height) {
		Vec3 v = target.getDeltaMovement();
		target.setDeltaMovement(v.x, EffectHelper.launchSpeedForHeight(height), v.z);
		target.hurtMarked = true;
	}

	static List<ServerPlayer> nonAlliedWithin(ServerPlayer user, double radius) {
		return user.serverLevel().players().stream()
				.filter(p -> Targeting.isValid(user, p) && !Targeting.isAllied(user, p) && p.distanceTo(user) <= radius)
				.toList();
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return perSeconds(AbilityConfig.EARTH_PASSIVE_QI_PER_15S.get(), 15);
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.DIG_SPEED, AbilityConfig.EARTH_PASSIVE_HASTE.get(), 50, false);
			// Fortune III is applied to block drops in EarthEvents.
		}
	}

	// ------------------------------------------------------------------ abilities

	static final class Attack1 extends Ability {
		Attack1() {
			super(E, AbilitySlot.ATTACK_1, "Attack 1");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.EARTH_ATTACK1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.EARTH_ATTACK1_COOLDOWN.get();
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			float result = (float) (damage * AbilityConfig.EARTH_ATTACK1_MULTIPLIER.get());
			if (QiEffects.knockback(target, attacker, AbilityConfig.EARTH_ATTACK1_KNOCKBACK.get(), false)) {
				EffectHelper.apply(target, MobEffects.MOVEMENT_SLOWDOWN, AbilityConfig.EARTH_ATTACK1_SLOWNESS.get(), AbilityConfig.EARTH_ATTACK1_SLOWNESS_TICKS.get(), true);
				EarthEvents.watchWallImpact(attacker, target, result);
			}
			return result;
		}
	}

	static final class Heavy extends Ability {
		Heavy() {
			super(E, AbilitySlot.HEAVY, "Heavy");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.EARTH_HEAVY_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.EARTH_HEAVY_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			List<ServerPlayer> targets = nonAlliedWithin(player, AbilityConfig.EARTH_HEAVY_RADIUS.get());
			for (ServerPlayer t : targets) {
				QiEffects.dealDamage(player, t, AbilityConfig.EARTH_HEAVY_DAMAGE.get().floatValue());
				if (QiEffects.canAffect(t, false))
					launch(t, AbilityConfig.EARTH_HEAVY_HEIGHT.get());
			}
			return ActionResult.ok("The earth erupts under " + targets.size() + " player(s).");
		}
	}

	static final class Reinforce extends Ability {
		Reinforce() {
			super(E, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.EARTH_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.EARTH_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.EARTH_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = AbilityConfig.EARTH_REINFORCE_DURATION.get();
			QiEffects.applyReinforce(player, E, ticks);
			TimedAttributes.apply(player, Attributes.KNOCKBACK_RESISTANCE, "earth_reinforce_kb", AbilityConfig.EARTH_REINFORCE_KB_RESIST.get(),
					AttributeModifier.Operation.ADD_VALUE, ticks);
			Stun.apply(player, AbilityConfig.EARTH_REINFORCE_LOCK_TICKS.get()); // "Lock user input 3s"
			return ActionResult.ok("Body reinforced. Rooted in place.");
		}
	}

	static final class Powerblast1 extends Ability {
		Powerblast1() {
			super(E, AbilitySlot.POWERBLAST_1, "Powerblast 1");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.EARTH_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.EARTH_PB1_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return Targeting.defaultTarget(player) == null ? ActionResult.fail("No target.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer target = Targeting.defaultTarget(player);
			if (target == null)
				return ActionResult.fail("Target lost.");
			layStoneLine(player.serverLevel(), player.blockPosition().below(), target.blockPosition().below());
			QiEffects.dealDamage(player, target, AbilityConfig.EARTH_PB1_DAMAGE.get().floatValue());
			if (QiEffects.knockback(target, player, AbilityConfig.EARTH_PB1_KNOCKBACK.get(), false))
				EarthEvents.watchStoneContact(target, AbilityConfig.EARTH_PB1_STUN_TICKS.get());
			return ActionResult.ok("Stone surges toward " + target.getName().getString() + ".");
		}

		/** Longest stone line laid (the target can be any distance away; a 10,000-block line stalled the server). */
		private static final int MAX_LINE = 64;

		/** Ground-level line of stone from the user toward the target (replaces reshapeable blocks; loaded chunks only). */
		private static void layStoneLine(ServerLevel level, BlockPos from, BlockPos to) {
			double dist = Math.sqrt(from.distSqr(to));
			int steps = (int) Math.ceil(dist);
			int laid = Math.min(steps, MAX_LINE);
			for (int i = 0; i <= laid; i++) {
				double t = steps == 0 ? 0 : i / (double) steps;
				BlockPos p = BlockPos.containing(from.getX() + 0.5 + (to.getX() - from.getX()) * t,
						from.getY() + 0.5 + (to.getY() - from.getY()) * t, from.getZ() + 0.5 + (to.getZ() - from.getZ()) * t);
				if (level.getChunkSource().hasChunk(p.getX() >> 4, p.getZ() >> 4) && canReshape(level, p))
					level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
			}
		}
	}

	static final class Powerblast2 extends Ability {
		Powerblast2() {
			super(E, AbilitySlot.POWERBLAST_2, "Powerblast 2");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.EARTH_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.EARTH_PB2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			double r = AbilityConfig.EARTH_PB2_RADIUS.get();
			ServerLevel level = player.serverLevel();

			// Grass -> stone in the area (permanent)
			BlockPos c = player.blockPosition();
			int ri = (int) Math.ceil(r);
			int changed = 0;
			for (BlockPos p : BlockPos.betweenClosed(c.offset(-ri, -8, -ri), c.offset(ri, 8, ri))) {
				if (p.distSqr(c) <= r * r && level.getBlockState(p).is(Blocks.GRASS_BLOCK)) {
					level.setBlockAndUpdate(p.immutable(), Blocks.STONE.defaultBlockState());
					changed++;
				}
			}

			// Slowness III + shake on non-allied players
			List<ServerPlayer> targets = nonAlliedWithin(player, r);
			for (ServerPlayer t : targets) {
				if (!QiEffects.canAffect(t, false))
					continue;
				EffectHelper.apply(t, MobEffects.MOVEMENT_SLOWDOWN, AbilityConfig.EARTH_PB2_SLOWNESS.get(), effectTicks(), true);
				EarthEvents.shake(t, 20 + player.getRandom().nextInt(21)); // 1-2 s
			}

			// After the shaking (2 s), everything standing on stone is thrown 50 up; the user is unaffected.
			EarthEvents.scheduleStoneLaunch(player, r, 40);
			EffectHelper.apply(player, MobEffects.REGENERATION, 255, 20, true);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), player.getX(), player.getY(), player.getZ(), 120, r / 3, 0.3, r / 3, 0.1);
			return ActionResult.ok("The ground quakes (" + changed + " grass blocks turned to stone).");
		}
	}

	static final class Ability1 extends Ability {
		Ability1() {
			super(E, AbilitySlot.ABILITY_1, "Ability 1");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.EARTH_ABILITY1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.EARTH_ABILITY1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerLevel level = player.serverLevel();
			int r = AbilityConfig.EARTH_ABILITY1_RADIUS.get();
			int rise = AbilityConfig.EARTH_ABILITY1_RISE.get();
			BlockPos ground = player.blockPosition().below();

			// Lift everyone standing in the area first, so nobody ends up inside the new stone.
			AABB area = new AABB(ground).inflate(r, 3, r);
			for (Entity e : level.getEntities(player, area.expandTowards(0, 1, 0), en -> en instanceof LivingEntity))
				e.teleportTo(e.getX(), e.getY() + rise, e.getZ());
			player.teleportTo(player.getX(), player.getY() + rise, player.getZ());

			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > r * r)
						continue;
					for (int dy = 0; dy <= rise; dy++) {
						BlockPos p = ground.offset(dx, dy, dz);
						if (canReshape(level, p))
							level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
					}
				}

			int ticks = effectTicks();
			EffectHelper.apply(player, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.EARTH_ABILITY1_RESISTANCE.get(), ticks, true);
			double allyRange = AbilityConfig.EARTH_ABILITY1_ALLY_RANGE.get();
			for (ServerPlayer p : level.players())
				if (p != player && Targeting.isValid(player, p) && Targeting.isAllied(player, p) && p.distanceTo(player) <= allyRange)
					EffectHelper.apply(p, MobEffects.REGENERATION, AbilityConfig.EARTH_ABILITY1_ALLY_REGEN.get(), ticks, true);
			return ActionResult.ok("The ground rises beneath you.");
		}
	}

	static final class Ultimate extends Ability {
		Ultimate() {
			super(E, AbilitySlot.ULTIMATE, "Ultimate");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.EARTH_ULT_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.EARTH_ULT_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerLevel level = player.serverLevel();
			int ticks = effectTicks();
			int min = AbilityConfig.EARTH_ULT_RAISE_MIN.get();
			int max = Math.max(min, AbilityConfig.EARTH_ULT_RAISE_MAX.get());
			int hit = 0;
			for (ServerPlayer t : nonAlliedWithin(player, AbilityConfig.EARTH_ULT_RADIUS.get())) {
				if (!QiEffects.canAffect(t, false))
					continue;
				hit++;
				// Raise the ground under the target (3x3 column), then send them to the sky limit.
				BlockPos base = t.blockPosition().below();
				int height = min + player.getRandom().nextInt(max - min + 1);
				BlockState baseState = level.getBlockState(base);
				// Raise natural ground only: copying whatever they stand on duplicated diamond/gold/debris blocks.
				boolean natural = baseState.is(net.minecraft.tags.BlockTags.DIRT) || baseState.is(net.minecraft.tags.BlockTags.SAND)
						|| baseState.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || baseState.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER)
						|| baseState.is(Blocks.GRAVEL) || baseState.is(Blocks.COBBLESTONE) || baseState.is(Blocks.SANDSTONE);
				BlockState fill = natural && !baseState.hasBlockEntity() && baseState.isCollisionShapeFullBlock(level, base) ? baseState : Blocks.STONE.defaultBlockState();
				for (int dx = -1; dx <= 1; dx++)
					for (int dz = -1; dz <= 1; dz++)
						for (int dy = 1; dy <= height; dy++) {
							BlockPos p = base.offset(dx, dy, dz);
							if (p.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(p).canBeReplaced() && canReshape(level, p))
								level.setBlockAndUpdate(p, fill);
						}
				t.teleportTo(t.getX(), skyLimitY(level, t.blockPosition()), t.getZ());
				EffectHelper.apply(t, MobEffects.DIG_SLOWDOWN, 1, ticks, true);
				EffectHelper.apply(t, MobEffects.BLINDNESS, 1, ticks, true);
				EffectHelper.apply(t, MobEffects.CONFUSION, 1, ticks, true);
			}
			EffectHelper.apply(player, MobEffects.REGENERATION, 255, AbilityConfig.EARTH_ULT_REGEN_TICKS.get(), true);
			EffectHelper.apply(player, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.EARTH_ULT_RESISTANCE.get(), AbilityConfig.EARTH_ULT_RESISTANCE_TICKS.get(), true);
			return ActionResult.ok("The world heaves. " + hit + " player(s) sent skyward.");
		}
	}

	/**
	 * "Sky limit" for the Ultimate. In dimensions with a ceiling (the Nether) the build limit is
	 * above the bedrock roof, which stranded targets on top of it: there it's the highest open
	 * 2-block space BELOW the roof instead (user decision 2026-10-08).
	 */
	static double skyLimitY(ServerLevel level, BlockPos at) {
		if (!level.dimensionType().hasCeiling())
			return level.getMaxBuildHeight() - 1;
		int top = Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.getLogicalHeight()) - 2;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = top; y > at.getY(); y--) {
			m.set(at.getX(), y, at.getZ());
			if (level.getBlockState(m).getCollisionShape(level, m).isEmpty()
					&& level.getBlockState(m.above()).getCollisionShape(level, m.above()).isEmpty()
					&& !level.getBlockState(m.above(2)).isAir()) // directly under the roof / a ceiling
				return y;
		}
		return at.getY(); // no open space above them: leave the height alone
	}
}
