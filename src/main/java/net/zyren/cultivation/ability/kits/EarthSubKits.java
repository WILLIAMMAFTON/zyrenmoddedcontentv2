package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.SubAspectKit;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.EarthConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.List;

import static net.zyren.cultivation.ability.kits.EarthDefaultKit.foesAround;
import static net.zyren.cultivation.ability.kits.EarthDefaultKit.noOne;
import static net.zyren.cultivation.ability.kits.EarthDefaultKit.seconds;
import static net.zyren.cultivation.ability.kits.EarthDefaultKit.unalliedWithin;

/**
 * Earth's sub-aspects (ASPECT GUIDE + user "rec all" 2026-11-05). Each lists all 7 slots except
 * Reinforce, which falls back to the Earth one. Every ability uses its parent EARTH + the same slot,
 * so cooldowns are shared. Event side: EarthKitEvents; terrain: EarthWorld.
 */
public final class EarthSubKits {
	static final AspectType E = AspectType.EARTH;

	private EarthSubKits() {
	}

	private static void effect(LivingEntity t, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e, int level, int ticks) {
		EffectHelper.apply(t, e, level, ticks, true);
	}

	// ================================================================== MAGMA

	static final SubAspectType MAGMA = SubAspectType.EARTH_MAGMA;
	static final MagmaPassive MG_PASSIVE = new MagmaPassive();
	static final FlamingFist FLAMING_FIST = new FlamingFist();

	public static SubAspectKit magma() {
		return new SubAspectKit(MAGMA, MG_PASSIVE, FLAMING_FIST, new VolcanicDespair(), new TectonicBlast(), new TremorOfHeat(), new VolcanicRaising(),
				new MagmaBowl()).onLeave(EarthKitEvents::leaveMagma);
	}

	static boolean inMagmaPlace(Player p) {
		if (p.level().dimension() == Level.NETHER || p.isInLava())
			return true;
		BlockState under = EarthWorld.below(p), at = p.level().getBlockState(p.blockPosition());
		return under.is(Blocks.MAGMA_BLOCK) || at.is(Blocks.MAGMA_BLOCK) || under.is(Blocks.LAVA) || at.is(Blocks.LAVA);
	}

	/** On / in lava or magma, or in the Nether: Fire Resistance, Regeneration I, Resistance I. */
	static final class MagmaPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(EarthConfig.MG_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			if (!inMagmaPlace(p))
				return;
			EffectHelper.apply(p, MobEffects.FIRE_RESISTANCE, 1, 50, false);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 1, 50, false);
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, 1);
		}
	}

	/** Armed until the next bare-fist hit (EarthKitEvents). */
	static final class FlamingFist extends KitParts.Cfg {
		FlamingFist() {
			super(E, AbilitySlot.ATTACK_1, "flaming fist", MAGMA, EarthConfig.MG_A1_COST, EarthConfig.MG_A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return EarthKitEvents.FLAMING.left(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			EarthKitEvents.FLAMING.start(p, 1);
			return ActionResult.ok("Flaming fist ready (next bare-fist hit).");
		}
	}

	static final class VolcanicDespair extends KitParts.Cfg {
		VolcanicDespair() {
			super(E, AbilitySlot.HEAVY, "volcanic dispare", MAGMA, EarthConfig.MG_A2_COST, EarthConfig.MG_A2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return unalliedWithin(p, EarthConfig.MG_A2_RANGE.get()).isEmpty() ? noOne(EarthConfig.MG_A2_RANGE.get()) : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			List<ServerPlayer> near = unalliedWithin(p, EarthConfig.MG_A2_RANGE.get());
			if (near.isEmpty())
				return ActionResult.fail("Target lost.");
			ServerPlayer t = near.get(0);
			QiEffects.dealDamage(p, t, EarthConfig.MG_A2_DAMAGE.get().floatValue());
			if (QiEffects.canAffect(t, false)) {
				Motion.launchUp(t, EarthConfig.MG_A2_UP.get());
				FireKit.ignite(t);
			}
			t.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, t.getX(), t.getY(), t.getZ(), 20, 0.5, 0.2, 0.5, 0.1);
			return ActionResult.ok("Volcanic dispare: " + t.getGameProfile().getName() + ".");
		}
	}

	/** A line ahead (horizontal look): damage, Knockback V, burning; stone / deepslate under it -> magma (stays). */
	static final class TectonicBlast extends KitParts.Cfg {
		TectonicBlast() {
			super(E, AbilitySlot.POWERBLAST_1, "tectonic blast", MAGMA, EarthConfig.MG_PB1_COST, EarthConfig.MG_PB1_COOLDOWN, true);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerLevel level = p.serverLevel();
			Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
			if (dir.lengthSqr() < 1.0E-4)
				dir = Vec3.directionFromRotation(0, p.getYRot());
			dir = dir.normalize();
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			int len = EarthConfig.MG_PB1_LENGTH.get();
			double half = EarthConfig.MG_PB1_WIDTH.get() / 2.0;
			Vec3 o = p.position();
			int n = 0;
			for (LivingEntity e : foesAround(p, o, len + half + 1)) {
				Vec3 rel = e.position().subtract(o);
				double along = rel.dot(dir), across = Math.abs(rel.dot(side));
				if (along < 0 || along > len || across > half + e.getBbWidth() / 2 || Math.abs(rel.y) > 4)
					continue;
				QiEffects.dealDamage(p, e, EarthConfig.MG_PB1_DAMAGE.get().floatValue());
				if (QiEffects.canAffect(e, false)) {
					EffectHelper.knockback(e, p, 5);
					e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), seconds(EarthConfig.MG_PB1_BURN_SECONDS.get())));
				}
				n++;
			}
			// ground along the path: the first solid block at or below foot level (within 3)
			BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
			int w = (int) Math.floor(half);
			for (int i = 1; i <= len; i++)
				for (int s = -w; s <= w; s++) {
					Vec3 at = o.add(dir.scale(i)).add(side.scale(s));
					BlockPos.MutableBlockPos m = BlockPos.containing(at.x, o.y - 0.5, at.z).mutable();
					for (int k = 0; k < 4; k++, m.move(0, -1, 0)) {
						BlockState b = level.getBlockState(m);
						if (b.isAir() || b.canBeReplaced())
							continue;
						if (b.is(BlockTags.BASE_STONE_OVERWORLD) || b.is(Blocks.COBBLESTONE) || b.is(Blocks.COBBLED_DEEPSLATE))
							EarthWorld.replace(level, m, magma);
						break;
					}
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, at.x, o.y + 0.2, at.z, 2, 0.2, 0.1, 0.2, 0.02);
				}
			return ActionResult.ok("Tectonic blast: " + n + " hit.");
		}
	}

	/** Damage + Slowness III + launch (on the ground) in the radius; temporary magma patches. */
	static final class TremorOfHeat extends KitParts.Cfg {
		TremorOfHeat() {
			super(E, AbilitySlot.POWERBLAST_2, "tremor of heat", MAGMA, EarthConfig.MG_PB2_COST, EarthConfig.MG_PB2_COOLDOWN, true);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerLevel level = p.serverLevel();
			int r = EarthConfig.MG_PB2_RADIUS.get(), n = 0;
			for (LivingEntity e : foesAround(p, p.position(), r)) {
				boolean grounded = e.onGround();
				QiEffects.dealDamage(p, e, EarthConfig.MG_PB2_DAMAGE.get().floatValue());
				if (QiEffects.canAffect(e, false)) {
					effect(e, MobEffects.MOVEMENT_SLOWDOWN, 3, seconds(EarthConfig.MG_PB2_SLOW_SECONDS.get()));
					if (grounded)
						Motion.launchUp(e, EarthConfig.MG_PB2_UP.get());
				}
				n++;
			}
			var rnd = p.getRandom();
			int ticks = seconds(EarthConfig.MG_PB2_PATCH_SECONDS.get());
			for (int i = 0, placed = 0; i < EarthConfig.MG_PB2_PATCHES.get() * 4 && placed < EarthConfig.MG_PB2_PATCHES.get(); i++) {
				double ang = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
				int x = (int) Math.floor(p.getX() + Math.cos(ang) * d), z = (int) Math.floor(p.getZ() + Math.sin(ang) * d);
				BlockPos top = new BlockPos(x, EarthWorld.surface(level, x, z), z);
				if (Math.abs(top.getY() - p.getBlockY()) > 8 || !level.getFluidState(top).isEmpty() || level.getBlockState(top).isAir())
					continue;
				if (EarthWorld.temp(level, top, Blocks.MAGMA_BLOCK.defaultBlockState(), ticks, "tremor", false))
					placed++;
			}
			return ActionResult.ok("Tremor of heat: " + n + " hit.");
		}
	}

	/** Volcanic-rock platform rises under you (stays); enemies on it thrown up + damage; you: Resistance IV + Fire Resistance while it lasts. */
	static final class VolcanicRaising extends KitParts.Timed {
		VolcanicRaising() {
			super(E, AbilitySlot.ABILITY_1, "volcanic rasing", MAGMA, EarthConfig.MG_AB1_COST, EarthConfig.MG_AB1_COOLDOWN, EarthConfig.MG_AB1_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			ServerLevel level = p.serverLevel();
			int r = EarthConfig.MG_AB1_RADIUS.get(), h = EarthConfig.MG_AB1_HEIGHT.get();
			int baseY = p.getBlockY(), cx = p.getBlockX(), cz = p.getBlockZ();
			var rnd = p.getRandom();
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz > r * r)
						continue;
					for (int y = baseY; y < baseY + h; y++) {
						BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
						BlockState s = level.getBlockState(pos);
						if (s.isAir() || s.canBeReplaced()) // air, plants, water, lava
							level.setBlock(pos, (rnd.nextInt(10) < 7 ? Blocks.BASALT : Blocks.BLACKSTONE).defaultBlockState(), 3);
					}
				}
			// everything standing in the circle rides up onto the top
			Vec3 c = Vec3.atBottomCenterOf(p.blockPosition());
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r + 0.5, h + 1, r + 0.5),
					e -> e.isAlive() && !e.isSpectator())) {
				double dx = e.getX() - c.x, dz = e.getZ() - c.z;
				if (dx * dx + dz * dz > (r + 0.5) * (r + 0.5) || e.getY() < baseY - 2 || e.getY() >= baseY + h)
					continue;
				e.teleportTo(e.getX(), baseY + h, e.getZ());
				e.fallDistance = 0;
			}
			float dmg = EarthConfig.MG_AB1_DAMAGE.get().floatValue();
			for (LivingEntity e : foesAround(p, c.add(0, h, 0), r + 0.5)) {
				QiEffects.dealDamage(p, e, dmg);
				if (QiEffects.canAffect(e, false))
					Motion.launchUp(e, EarthConfig.MG_AB1_UP.get());
			}
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 4, 30, true);
			EffectHelper.apply(p, MobEffects.FIRE_RESISTANCE, 1, 30, true);
		}
	}

	/** Crater where you look (stays), magma floor, lava pools for a while; unallied inside pulled in + damage + Slowness IV. */
	static final class MagmaBowl extends KitParts.Cfg {
		MagmaBowl() {
			super(E, AbilitySlot.ABILITY_2, "cavous magma bowl", MAGMA, EarthConfig.MG_AB2_COST, EarthConfig.MG_AB2_COOLDOWN, true);
		}

		static BlockHitResult aim(ServerPlayer p) {
			Vec3 eye = p.getEyePosition();
			Vec3 far = eye.add(p.getLookAngle().scale(EarthConfig.MG_AB2_RANGE.get()));
			BlockHitResult hit = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
			return hit.getType() == HitResult.Type.MISS ? null : hit;
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return aim(p) == null ? ActionResult.fail("Look at the ground within " + EarthConfig.MG_AB2_RANGE.get() + " blocks.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			BlockHitResult hit = aim(p);
			if (hit == null)
				return ActionResult.fail("No target spot.");
			ServerLevel level = p.serverLevel();
			BlockPos c = hit.getBlockPos();
			int r = EarthConfig.MG_AB2_RADIUS.get(), depth = EarthConfig.MG_AB2_DEPTH.get();
			int topY = c.getY() + 1;
			EarthWorld.bowl(level, c.getX(), topY, c.getZ(), r, depth, 2, Blocks.MAGMA_BLOCK.defaultBlockState());
			// lava pools on the floor (removed after lava_seconds)
			var rnd = p.getRandom();
			int lavaTicks = seconds(EarthConfig.MG_AB2_LAVA_SECONDS.get());
			for (int i = 0; i < EarthConfig.MG_AB2_LAVA_POOLS.get(); i++) {
				double ang = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * r * 0.6;
				int dx = (int) Math.round(Math.cos(ang) * d), dz = (int) Math.round(Math.sin(ang) * d);
				double dd = Math.sqrt(dx * dx + dz * dz);
				int here = Math.max(1, (int) Math.ceil(depth * Math.sqrt(Math.max(0, 1 - (dd / r) * (dd / r)))));
				EarthWorld.temp(level, new BlockPos(c.getX() + dx, topY - here, c.getZ() + dz), Blocks.LAVA.defaultBlockState(), lavaTicks, "magma_bowl", true);
			}
			Vec3 center = Vec3.atBottomCenterOf(new BlockPos(c.getX(), topY - depth, c.getZ()));
			int n = 0;
			for (LivingEntity e : foesAround(p, Vec3.atCenterOf(c), r + 2)) {
				if (!(e instanceof Player))
					continue;
				QiEffects.dealDamage(p, e, EarthConfig.MG_AB2_DAMAGE.get().floatValue());
				if (QiEffects.canAffect(e, false)) {
					Motion.pullToward(e, center, r + 2, 0.8);
					effect(e, MobEffects.MOVEMENT_SLOWDOWN, 4, seconds(EarthConfig.MG_AB2_SLOW_SECONDS.get()));
				}
				n++;
			}
			int self = seconds(EarthConfig.MG_AB2_SELF_SECONDS.get());
			EffectHelper.apply(p, MobEffects.FIRE_RESISTANCE, 1, self, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 3, self, true);
			return ActionResult.ok("Cavous magma bowl: " + n + " caught.");
		}
	}

	// ================================================================== SAND

	static final SubAspectType SAND = SubAspectType.EARTH_SAND;
	static final SandPassive SD_PASSIVE = new SandPassive();
	static final SandBlock SAND_BLOCK = new SandBlock();

	public static SubAspectKit sand() {
		return new SubAspectKit(SAND, SD_PASSIVE, new PocketSand(), new EnhancedBody(), new Sandworm(), SAND_BLOCK, new SandyGrave(), new SunsHatred())
				.onLeave(EarthKitEvents::leaveSand);
	}

	static boolean onSand(Player p) {
		return EarthWorld.isSand(EarthWorld.below(p));
	}

	static boolean sandPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == SD_PASSIVE;
	}

	/** On sand / sandstone: Speed IV + taller step. Never suffocates in sand (EarthKitEvents). */
	static final class SandPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return !KitParts.passiveDisabled(p);
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(EarthConfig.SD_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			if (!onSand(p))
				return;
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, EarthConfig.SD_SPEED.get(), 50, false);
			double step = EarthConfig.SD_STEP.get();
			if (step > 0.6)
				TimedAttributes.apply(p, Attributes.STEP_HEIGHT, "earth_sand_step", step - 0.6, AttributeModifier.Operation.ADD_VALUE, 30);
		}
	}

	static final class PocketSand extends KitParts.Cfg {
		PocketSand() {
			super(E, AbilitySlot.ATTACK_1, "pocket sand", SAND, EarthConfig.SD_A1_COST, EarthConfig.SD_A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return unalliedWithin(p, EarthConfig.SD_A1_RANGE.get()).isEmpty() ? noOne(EarthConfig.SD_A1_RANGE.get()) : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			List<ServerPlayer> near = unalliedWithin(p, EarthConfig.SD_A1_RANGE.get());
			if (near.isEmpty())
				return ActionResult.fail("Target lost.");
			ServerPlayer t = near.get(0);
			int ticks = seconds(EarthConfig.SD_A1_SECONDS.get());
			QiEffects.applyEffect(t, MobEffects.BLINDNESS, 1, ticks, false);
			QiEffects.applyEffect(t, MobEffects.WEAKNESS, 1, ticks, false);
			t.serverLevel().sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
					t.getX(), t.getEyeY(), t.getZ(), 30, 0.3, 0.3, 0.3, 0.1);
			return ActionResult.ok("Pocket sand: " + t.getGameProfile().getName() + ".");
		}
	}

	static final class EnhancedBody extends KitParts.Timed {
		EnhancedBody() {
			super(E, AbilitySlot.HEAVY, "enhanced body", SAND, EarthConfig.SD_A2_COST, EarthConfig.SD_A2_COOLDOWN, EarthConfig.SD_A2_SECONDS, false);
		}

		@Override
		boolean disablesPassive() {
			return true;
		}

		@Override
		void second(ServerPlayer p) {
			if (!onSand(p))
				return;
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 3, 30, true);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, 3, 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 3, 30, true);
		}
	}

	/** Teleport onto the nearest sand block (room for you above, no unallied player near it). */
	static final class Sandworm extends KitParts.Cfg {
		Sandworm() {
			super(E, AbilitySlot.POWERBLAST_1, "sandworm", SAND, EarthConfig.SD_PB1_COST, EarthConfig.SD_PB1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			BlockPos to = EarthKitEvents.findSandExit(p);
			if (to == null)
				return ActionResult.fail("No safe sand within " + EarthConfig.SD_PB1_RADIUS.get() + " blocks.");
			EarthKitEvents.SAND_EXITS.put(p.getUUID(), to);
			return ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			BlockPos to = EarthKitEvents.SAND_EXITS.remove(p.getUUID());
			if (to == null)
				to = EarthKitEvents.findSandExit(p);
			if (to == null)
				return ActionResult.fail("No safe sand found.");
			ServerLevel level = p.serverLevel();
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
					p.getX(), p.getY() + 0.5, p.getZ(), 40, 0.4, 0.6, 0.4, 0.1);
			p.teleportTo(to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5);
			p.fallDistance = 0;
			return ActionResult.ok("Sandworm!");
		}
	}

	/** Melee / arrow hits on you are blocked by a sand wall (EarthKitEvents). */
	static final class SandBlock extends KitParts.Timed {
		SandBlock() {
			super(E, AbilitySlot.POWERBLAST_2, "sand block", SAND, EarthConfig.SD_PB2_COST, EarthConfig.SD_PB2_COOLDOWN, EarthConfig.SD_PB2_SECONDS, false);
		}
	}

	/** Square around you, surface down N blocks -> sand (spread over ticks); unallied players inside then pulled down. */
	static final class SandyGrave extends KitParts.Cfg {
		SandyGrave() {
			super(E, AbilitySlot.ABILITY_1, "a sandy grave", SAND, EarthConfig.SD_AB1_COST, EarthConfig.SD_AB1_COOLDOWN, true);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			EarthKitEvents.sandyGrave(p);
			return ActionResult.ok("A sandy grave...");
		}
	}

	/** Noon; surface -> sand; debuffs around where it was cast (EarthKitEvents). */
	static final class SunsHatred extends KitParts.Timed {
		SunsHatred() {
			super(E, AbilitySlot.ABILITY_2, "SUNS HATRED", SAND, EarthConfig.SD_AB2_COST, EarthConfig.SD_AB2_COOLDOWN, EarthConfig.SD_AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			EarthKitEvents.startSun(p);
		}

		@Override
		void second(ServerPlayer p) {
			EarthKitEvents.sunTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			EarthKitEvents.endSun(p);
		}
	}

	// ================================================================== NATURE

	static final SubAspectType NATURE = SubAspectType.EARTH_NATURE;
	static final NaturePassive NT_PASSIVE = new NaturePassive();
	static final ThornedFists THORNED = new ThornedFists();
	static final WorldTree WORLD_TREE = new WorldTree();

	public static SubAspectKit nature() {
		return new SubAspectKit(NATURE, NT_PASSIVE, THORNED, new VineLash(), new RootSnare(), new SporeCloud(), new Overgrowth(), WORLD_TREE)
				.onLeave(EarthKitEvents::leaveNature);
	}

	static boolean naturePassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == NT_PASSIVE;
	}

	/** Regeneration II; right-clicking plants bonemeals them for free (EarthKitEvents). */
	static final class NaturePassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(EarthConfig.NT_PASSIVE_QI_PER_5MIN.get(), 300);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, EarthConfig.NT_PASSIVE_REGEN.get());
		}
	}

	static final class ThornedFists extends KitParts.Cfg {
		ThornedFists() {
			super(E, AbilitySlot.ATTACK_1, "throned fists", NATURE, EarthConfig.NT_A1_COST, EarthConfig.NT_A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return EarthKitEvents.THORNS.left(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			EarthKitEvents.THORNS.start(p, EarthConfig.NT_A1_HITS.get());
			return ActionResult.ok("Thorned fists: next " + EarthConfig.NT_A1_HITS.get() + " bare-fist hits poison and root.");
		}
	}

	static final class VineLash extends KitParts.Cfg {
		VineLash() {
			super(E, AbilitySlot.HEAVY, "vine lash", NATURE, EarthConfig.NT_A2_COST, EarthConfig.NT_A2_COOLDOWN, false);
		}

		static ServerPlayer target(ServerPlayer p) {
			for (ServerPlayer o : unalliedWithin(p, EarthConfig.NT_A2_RANGE.get()))
				if (p.hasLineOfSight(o))
					return o;
			return null;
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return target(p) == null ? ActionResult.fail("No one in sight within " + EarthConfig.NT_A2_RANGE.get() + " blocks.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = target(p);
			if (t == null)
				return ActionResult.fail("Target lost.");
			if (QiEffects.canAffect(t, false)) {
				Motion.pullToward(t, p, EarthConfig.NT_A2_PULL.get(), 1.2);
				effect(t, MobEffects.MOVEMENT_SLOWDOWN, 1, seconds(EarthConfig.NT_A2_SLOW_SECONDS.get()));
			}
			Vec3 a = p.getEyePosition(), b = t.position().add(0, t.getBbHeight() * 0.5, 0), step = b.subtract(a);
			int n = (int) Math.max(1, step.length() * 2);
			for (int i = 0; i <= n; i++) {
				Vec3 at = a.add(step.scale(i / (double) n));
				p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.COMPOSTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
			return ActionResult.ok("Vine lash: " + t.getGameProfile().getName() + ".");
		}
	}

	/** Cobwebs at feet + head of unallied players standing on dirt / grass / moss (removed after the duration). */
	static final class RootSnare extends KitParts.Cfg {
		RootSnare() {
			super(E, AbilitySlot.POWERBLAST_1, "root snare", NATURE, EarthConfig.NT_PB1_COST, EarthConfig.NT_PB1_COOLDOWN, false);
		}

		static boolean rootable(ServerPlayer t) {
			BlockState b = EarthWorld.below(t);
			return t.onGround() && (b.is(BlockTags.DIRT) || b.is(Blocks.MOSS_BLOCK) || b.is(Blocks.MOSS_CARPET));
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			for (ServerPlayer t : unalliedWithin(p, EarthConfig.NT_PB1_RADIUS.get()))
				if (rootable(t))
					return ActionResult.ok("");
			return ActionResult.fail("No one on dirt / grass / moss within " + EarthConfig.NT_PB1_RADIUS.get() + " blocks.");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int ticks = seconds(EarthConfig.NT_PB1_SECONDS.get()), n = 0;
			BlockState web = Blocks.COBWEB.defaultBlockState();
			for (ServerPlayer t : unalliedWithin(p, EarthConfig.NT_PB1_RADIUS.get())) {
				if (!rootable(t) || !QiEffects.canAffect(t, false))
					continue;
				BlockPos feet = t.blockPosition();
				EarthWorld.temp(t.serverLevel(), feet, web, ticks, "root_snare", true);
				EarthWorld.temp(t.serverLevel(), feet.above(), web, ticks, "root_snare", true);
				n++;
			}
			return ActionResult.ok("Root snare: " + n + " held.");
		}
	}

	/** Cloud around you: unallied Poison + Nausea, allies Regeneration. */
	static final class SporeCloud extends KitParts.Timed {
		SporeCloud() {
			super(E, AbilitySlot.POWERBLAST_2, "spore cloud", NATURE, EarthConfig.NT_PB2_COST, EarthConfig.NT_PB2_COOLDOWN, EarthConfig.NT_PB2_SECONDS, false);
		}

		@Override
		void second(ServerPlayer p) {
			double r = EarthConfig.NT_PB2_RADIUS.get();
			for (LivingEntity e : foesAround(p, p.position(), r))
				if (QiEffects.canAffect(e, false)) {
					EffectHelper.apply(e, MobEffects.POISON, 1, 60, true);
					EffectHelper.apply(e, MobEffects.CONFUSION, 1, 120, true);
				}
			for (ServerPlayer o : p.serverLevel().players())
				if (o != p && !o.isSpectator() && net.zyren.cultivation.ability.core.Targeting.isAllied(p, o) && o.distanceTo(p) <= r)
					EffectHelper.applyPassive(o, MobEffects.REGENERATION, 1);
			p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SPORE_BLOSSOM_AIR, p.getX(), p.getY() + 1, p.getZ(), 60, r / 2, 1, r / 2, 0);
		}
	}

	/** Dirt -> grass + flowers around you (stays); on grass: Strength I + Resistance I; allies in the area: Regeneration I. */
	static final class Overgrowth extends KitParts.Timed {
		Overgrowth() {
			super(E, AbilitySlot.ABILITY_1, "overgrowth", NATURE, EarthConfig.NT_AB1_COST, EarthConfig.NT_AB1_COOLDOWN, EarthConfig.NT_AB1_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			EarthKitEvents.startOvergrowth(p);
		}

		@Override
		void second(ServerPlayer p) {
			EarthKitEvents.overgrowthTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			EarthKitEvents.endOvergrowth(p);
		}
	}

	/** A big temporary tree in front of you: Qi + Regeneration II near the trunk, roots hold unallied; Fire users can burn it down early. */
	static final class WorldTree extends KitParts.Timed {
		WorldTree() {
			super(E, AbilitySlot.ABILITY_2, "world tree", NATURE, EarthConfig.NT_AB2_COST, EarthConfig.NT_AB2_COOLDOWN, EarthConfig.NT_AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			EarthKitEvents.growTree(p);
		}

		@Override
		void second(ServerPlayer p) {
			EarthKitEvents.treeTick(p);
		}

		@Override
		void end(ServerPlayer p) {
			EarthKitEvents.fellTree(p);
		}
	}
}
