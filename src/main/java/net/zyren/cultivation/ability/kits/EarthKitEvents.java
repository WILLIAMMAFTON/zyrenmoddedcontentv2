package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiCostDebuffs;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.EarthConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.AspectMastery;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Event side of the Earth kits (EarthDefaultKit + EarthSubKits). Anything that hurts or moves an
 * entity from inside a damage event is deferred to the end of the server tick.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class EarthKitEvents {
	private static final AspectType E = AspectType.EARTH;

	private EarthKitEvents() {
	}

	private static boolean running(ServerPlayer p, Ability a) {
		return KitParts.running(p, a);
	}

	private static int seconds(int s) {
		return s * 20;
	}

	// ================================================================== hit charges

	/** "Next N hits": counted here, tied to a buff (form change / nullify / switch end them). */
	static final class Charges {
		private final String buff;
		private final Supplier<Ability> owner;
		private final Map<UUID, Integer> left = new HashMap<>();

		Charges(String buff, Supplier<Ability> owner) {
			this.buff = buff;
			this.owner = owner;
		}

		void start(ServerPlayer p, int hits) {
			AbilityState.of(p).addBuff(new Buff(buff, E, 1.0, 0, false, Long.MAX_VALUE));
			left.put(p.getUUID(), hits);
		}

		int left(ServerPlayer p) {
			Integer n = left.get(p.getUUID());
			if (n == null)
				return 0;
			long now = p.serverLevel().getGameTime();
			AbilityState st = AbilityState.of(p);
			if (!st.hasBuff(buff, now) || !KitResolver.isCurrent(p, owner.get()) || st.isNullified(E, now)) {
				left.remove(p.getUUID());
				return 0;
			}
			return n;
		}

		void use(ServerPlayer p) {
			int n = left(p) - 1;
			if (n > 0) {
				left.put(p.getUUID(), n);
			} else {
				left.remove(p.getUUID());
				AbilityState.of(p).removeBuff(buff);
			}
		}

		void clear(ServerPlayer p) {
			left.remove(p.getUUID());
			AbilityState.of(p).removeBuff(buff);
		}

		void clear(UUID id) {
			left.remove(id);
		}

		void clearAll() {
			left.clear();
		}
	}

	static final Charges BLADE = new Charges("earth_blade", () -> EarthDefaultKit.BLADE);
	static final Charges FLAMING = new Charges("earth_magma_flaming_fist", () -> EarthSubKits.FLAMING_FIST);
	static final Charges THORNS = new Charges("earth_nature_thorns", () -> EarthSubKits.THORNED);

	// ================================================================== deferred actions

	private static final List<Runnable> LATER = new ArrayList<>();

	private static void later(Runnable r) {
		LATER.add(r);
	}

	private static void queuePush(ServerPlayer from, LivingEntity target, double blocks) {
		Vec3 origin = from.position();
		later(() -> {
			if (target.isAlive() && QiEffects.canAffect(target, false))
				Motion.push(target, origin, blocks, 0.8, 0.3, null);
		});
	}

	// ================================================================== Sand: sandworm exit

	/** checkUse -> perform hand-off, so the search runs once per cast. */
	static final Map<UUID, BlockPos> SAND_EXITS = new HashMap<>();

	private static boolean standable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return (s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty() && s.getCollisionShape(level, pos).isEmpty();
	}

	/** Nearest sand block within the radius (and 8 up / down) with 2 free blocks above and no unallied player near it. */
	static BlockPos findSandExit(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		int R = EarthConfig.SD_PB1_RADIUS.get();
		double clear = EarthConfig.SD_PB1_CLEAR.get();
		List<ServerPlayer> enemies = EarthDefaultKit.unalliedWithin(p, R + clear + 2);
		int px = p.getBlockX(), py = p.getBlockY(), pz = p.getBlockZ();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int r = 2; r <= R && r <= bestD; r++)
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
						continue;
					int x = px + dx, z = pz + dz;
					if (!level.getChunkSource().hasChunk(x >> 4, z >> 4))
						continue;
					for (int dy = 8; dy >= -8; dy--) {
						m.set(x, py + dy, z);
						if (!level.isInWorldBounds(m) || !EarthWorld.isSand(level.getBlockState(m)))
							continue;
						double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
						if (d > R || d >= bestD || !standable(level, m.above()) || !standable(level, m.above(2)))
							continue;
						Vec3 c = Vec3.atBottomCenterOf(m.above());
						boolean safe = true;
						for (ServerPlayer e : enemies)
							if (e.position().distanceTo(c) <= clear) {
								safe = false;
								break;
							}
						if (safe) {
							best = m.immutable();
							bestD = d;
						}
					}
				}
		return best;
	}

	// ================================================================== Sand: sand block wall

	private static void sandWall(ServerPlayer victim, Vec3 from) {
		ServerLevel level = victim.serverLevel();
		Vec3 dir = from.subtract(victim.position()).multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0E-4)
			dir = victim.getLookAngle().multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0E-4)
			return;
		dir = dir.normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 center = victim.position().add(dir.scale(1.5));
		int ticks = seconds(EarthConfig.SD_PB2_WALL_SECONDS.get());
		for (int s = -1; s <= 1; s++) {
			Vec3 at = center.add(side.scale(s));
			BlockPos base = BlockPos.containing(at.x, victim.getY() + 0.1, at.z);
			for (int h = 0; h < 2; h++) {
				BlockPos pos = base.above(h);
				if (!EarthWorld.hasLivingInside(level, pos))
					EarthWorld.temp(level, pos, Blocks.SAND.defaultBlockState(), ticks, "sand_wall", true);
			}
		}
	}

	// ================================================================== Sand: a sandy grave

	/** Every position of the square, column by column, from the surface down {@code depth}. */
	private static Iterator<BlockPos> graveCells(ServerLevel level, int x0, int z0, int size, int depth) {
		return new Iterator<>() {
			int col = 0, dy = 0, top;

			@Override
			public boolean hasNext() {
				return col < size * size;
			}

			@Override
			public BlockPos next() {
				if (!hasNext())
					throw new NoSuchElementException();
				int x = x0 + col % size, z = z0 + col / size;
				if (dy == 0)
					top = level.getChunkSource().hasChunk(x >> 4, z >> 4) ? EarthWorld.surface(level, x, z) : Integer.MIN_VALUE;
				BlockPos pos = new BlockPos(x, top == Integer.MIN_VALUE ? level.getMinBuildHeight() - 1 : top - dy, z);
				if (++dy >= depth) {
					dy = 0;
					col++;
				}
				return pos;
			}
		};
	}

	static void sandyGrave(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		int size = EarthConfig.SD_AB1_SIZE.get(), depth = EarthConfig.SD_AB1_DEPTH.get();
		int x0 = p.getBlockX() - size / 2, z0 = p.getBlockZ() - size / 2;
		BlockState sand = Blocks.SAND.defaultBlockState();
		int minY = p.getBlockY() - depth - 20, maxY = p.getBlockY() + 20;
		EarthWorld.spread(graveCells(level, x0, z0, size, depth), pos -> EarthWorld.replace(level, pos, sand), () -> {
			if (!p.isAlive() || p.level() != level)
				return;
			int pull = EarthConfig.SD_AB1_PULL.get();
			for (ServerPlayer t : level.players()) {
				if (!Targeting.isValid(p, t) || Targeting.isAllied(p, t) || !QiEffects.canAffect(t, false))
					continue;
				if (t.getX() < x0 || t.getX() >= x0 + size || t.getZ() < z0 || t.getZ() >= z0 + size || t.getY() < minY || t.getY() > maxY)
					continue;
				t.teleportTo(t.getX(), t.getY() - pull, t.getZ());
				t.fallDistance = 0;
			}
		});
	}

	// ================================================================== Sand: SUNS HATRED

	private static final class Field {
		ServerLevel level;
		Vec3 center;
		BlockPos base;
		String group;
		int burns;
		boolean burning;
	}

	private static final Map<UUID, Field> SUNS = new HashMap<>();
	/** Unallied players whose natural (food) healing is off until this game tick. */
	private static final Map<UUID, Long> NO_REGEN = new HashMap<>();

	private static void noon(ServerLevel any) {
		ServerLevel world = any.getServer().overworld();
		long t = world.getDayTime(), inDay = t % 24000;
		world.setDayTime(t - inDay + (inDay <= 6000 ? 6000 : 24000 + 6000));
	}

	static void startSun(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Field f = new Field();
		f.level = level;
		f.center = p.position();
		SUNS.put(p.getUUID(), f);
		noon(level);
		if (!EarthConfig.SD_AB2_SAND.get())
			return;
		int r = EarthConfig.SD_AB2_RADIUS.get(), cx = p.getBlockX(), cz = p.getBlockZ();
		BlockState sand = Blocks.SAND.defaultBlockState();
		Iterator<BlockPos> cols = BlockPos.betweenClosed(cx - r, 0, cz - r, cx + r, 0, cz + r).iterator();
		EarthWorld.spread(cols, col -> {
			int x = col.getX(), z = col.getZ(), dx = x - cx, dz = z - cz;
			if (dx * dx + dz * dz > r * r || !level.getChunkSource().hasChunk(x >> 4, z >> 4))
				return;
			BlockPos top = new BlockPos(x, EarthWorld.surface(level, x, z), z);
			BlockState s = level.getBlockState(top);
			if (!s.isAir() && s.getFluidState().isEmpty() && !EarthWorld.isSand(s))
				EarthWorld.replace(level, top, sand);
		}, null);
	}

	static void sunTick(ServerPlayer p) {
		Field f = SUNS.get(p.getUUID());
		if (f == null)
			return;
		long now = f.level.getGameTime();
		int r = EarthConfig.SD_AB2_RADIUS.get();
		for (ServerPlayer o : f.level.players()) {
			if (o == p || o.isSpectator() || o.position().distanceTo(f.center) > r)
				continue;
			AspectType a = AspectData.of(o).getActive();
			if (a == AspectType.WATER || a == AspectType.DARK)
				QiCostDebuffs.apply(o, EarthConfig.SD_AB2_COST_MULTIPLIER.get(), 40, now);
			if (a == AspectType.FIRE) {
				QiCostDebuffs.discount(o, EarthConfig.SD_AB2_FIRE_DISCOUNT.get(), 40, now);
				continue;
			}
			if (Targeting.isAllied(p, o) || !QiEffects.canAffect(o, false))
				continue;
			NO_REGEN.put(o.getUUID(), now + 40);
			EffectHelper.apply(o, MobEffects.WEAKNESS, 1, 40, true);
			EffectHelper.apply(o, MobEffects.GLOWING, 1, 40, false);
		}
	}

	static void endSun(ServerPlayer p) {
		SUNS.remove(p.getUUID());
	}

	/**
	 * SUNS HATRED: natural regeneration off. Food healing is a heal of at most 1 while food is 18+;
	 * heals from Regeneration (and anything bigger, e.g. Instant Health) still go through.
	 */
	@SubscribeEvent
	public static void onHeal(LivingHealEvent event) {
		if (NO_REGEN.isEmpty() || !(event.getEntity() instanceof ServerPlayer p))
			return;
		Long until = NO_REGEN.get(p.getUUID());
		if (until == null)
			return;
		if (p.serverLevel().getGameTime() >= until) {
			NO_REGEN.remove(p.getUUID());
			return;
		}
		if (event.getAmount() <= 1.0f && p.getFoodData().getFoodLevel() >= 18 && !p.hasEffect(MobEffects.REGENERATION))
			event.setCanceled(true);
	}

	// ================================================================== Nature: overgrowth

	private static final Map<UUID, Field> OVERGROWTH = new HashMap<>();

	private static final Block[] FLOWERS = { Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM,
			Blocks.SHORT_GRASS, Blocks.SHORT_GRASS };

	static void startOvergrowth(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Field f = new Field();
		f.level = level;
		f.center = p.position();
		OVERGROWTH.put(p.getUUID(), f);
		int r = EarthConfig.NT_AB1_RADIUS.get(), cx = p.getBlockX(), cz = p.getBlockZ();
		double chance = EarthConfig.NT_AB1_FLOWER_CHANCE.get();
		var rnd = p.getRandom();
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r || !level.getChunkSource().hasChunk((cx + dx) >> 4, (cz + dz) >> 4))
					continue;
				BlockPos top = new BlockPos(cx + dx, EarthWorld.surface(level, cx + dx, cz + dz), cz + dz);
				if (Math.abs(top.getY() - p.getBlockY()) > 8)
					continue;
				BlockState s = level.getBlockState(top);
				if (s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT))
					EarthWorld.replace(level, top, Blocks.GRASS_BLOCK.defaultBlockState());
				if (level.getBlockState(top).is(Blocks.GRASS_BLOCK) && level.getBlockState(top.above()).isAir() && rnd.nextDouble() < chance)
					EarthWorld.fill(level, top.above(), FLOWERS[rnd.nextInt(FLOWERS.length)].defaultBlockState());
			}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 0.5, p.getZ(), 80, r / 2.0, 0.5, r / 2.0, 0);
	}

	static void overgrowthTick(ServerPlayer p) {
		Field f = OVERGROWTH.get(p.getUUID());
		if (f == null)
			return;
		if (EarthWorld.below(p).is(Blocks.GRASS_BLOCK)) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 1, 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 1, 30, true);
		}
		int r = EarthConfig.NT_AB1_RADIUS.get();
		for (ServerPlayer o : f.level.players())
			if (o != p && !o.isSpectator() && Targeting.isAllied(p, o) && o.position().distanceTo(f.center) <= r)
				EffectHelper.applyPassive(o, MobEffects.REGENERATION, 1);
	}

	static void endOvergrowth(ServerPlayer p) {
		OVERGROWTH.remove(p.getUUID());
	}

	// ================================================================== Nature: world tree

	private static final Map<UUID, Field> TREES = new HashMap<>();

	static void growTree(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		fellTree(p);
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : dir.normalize();
		BlockPos base = BlockPos.containing(p.position().add(dir.scale(3)).subtract(0.5, 0, 0.5)); // 2x2 trunk: base .. base+1
		int h = EarthConfig.NT_AB2_HEIGHT.get();
		int ticks = EarthConfig.NT_AB2_SECONDS.get() * 20 + 40; // also reverts by time if the ability never ends normally
		Field f = new Field();
		f.level = level;
		f.base = base;
		f.center = new Vec3(base.getX() + 1, base.getY(), base.getZ() + 1);
		f.group = "world_tree_" + p.getUUID();
		TREES.put(p.getUUID(), f);
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		for (int y = 0; y < h; y++)
			for (int dx = 0; dx < 2; dx++)
				for (int dz = 0; dz < 2; dz++) {
					BlockPos pos = base.offset(dx, y, dz);
					if (!EarthWorld.hasLivingInside(level, pos))
						EarthWorld.temp(level, pos, log, ticks, f.group, true);
				}
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		Vec3 crown = new Vec3(base.getX() + 1, base.getY() + h - 1, base.getZ() + 1);
		int cr = 5;
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(crown).offset(-cr, -cr + 1, -cr), BlockPos.containing(crown).offset(cr, cr, cr))) {
			if (Vec3.atCenterOf(pos).distanceTo(crown) > cr + 0.3)
				continue;
			EarthWorld.temp(level, pos.immutable(), leaves, ticks, f.group, true);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, f.center.x, f.center.y + 1, f.center.z, 60, 1.5, 2, 1.5, 0);
	}

	/** Horizontal distance to the trunk, if within the tree's height band. */
	private static double fromTrunk(Field f, Entity e) {
		if (e.level() != f.level || e.getY() < f.base.getY() - 3 || e.getY() > f.base.getY() + EarthConfig.NT_AB2_HEIGHT.get() + 3)
			return Double.MAX_VALUE;
		double dx = e.getX() - f.center.x, dz = e.getZ() - f.center.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	static void treeTick(ServerPlayer p) {
		Field f = TREES.get(p.getUUID());
		if (f == null)
			return;
		if (fromTrunk(f, p) <= EarthConfig.NT_AB2_NEAR.get()) {
			QiCoreStorage.addQi(p, EarthConfig.NT_AB2_QI_PER_SECOND.get());
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, 2);
		}
		int r = EarthConfig.NT_AB2_ROOT_RADIUS.get();
		for (ServerPlayer o : f.level.players())
			if (Targeting.isValid(p, o) && !Targeting.isAllied(p, o) && fromTrunk(f, o) <= r && QiEffects.canAffect(o, false))
				Stun.apply(o, 25);
	}

	static void fellTree(ServerPlayer p) {
		Field f = TREES.remove(p.getUUID());
		if (f != null)
			EarthWorld.revertGroup(f.group);
	}

	/** Fire users landing hits / Qi damage near a world tree burn it. */
	private static void burnTrees(ServerPlayer fire) {
		if (TREES.isEmpty())
			return;
		int r = EarthConfig.NT_AB2_ROOT_RADIUS.get(), need = EarthConfig.NT_AB2_BURNS.get();
		for (Map.Entry<UUID, Field> en : TREES.entrySet()) {
			Field f = en.getValue();
			if (f.burning || fire.getUUID().equals(en.getKey()) || fromTrunk(f, fire) > r)
				continue;
			f.burns++;
			f.level.sendParticles(ParticleTypes.FLAME, f.center.x, f.center.y + 3, f.center.z, 15, 0.8, 2, 0.8, 0.02);
			if (f.burns < need)
				continue;
			f.burning = true;
			ServerPlayer owner = f.level.getServer().getPlayerList().getPlayer(en.getKey());
			if (owner != null) {
				AbilityState.of(owner).endSustained(EarthSubKits.WORLD_TREE, owner.serverLevel().getGameTime());
				owner.displayClientMessage(Component.literal("Your world tree burned down!"), true);
			}
			f.level.sendParticles(ParticleTypes.LARGE_SMOKE, f.center.x, f.center.y + 5, f.center.z, 80, 2, 4, 2, 0.02);
		}
	}

	// ================================================================== events

	/** Outgoing melee hit effects. HIGH: after empowered hits (HIGHEST), before output multipliers (LOW). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (AspectData.of(attacker).getActive() != E || AbilityState.of(attacker).isNullified(E, attacker.serverLevel().getGameTime()))
			return;
		if (!WaterEvents.isMelee(event.getSource(), attacker))
			return;
		float dmg = event.getNewDamage();
		boolean unarmed = EarthDefaultKit.unarmed(attacker);
		boolean sword = attacker.getMainHandItem().is(ItemTags.SWORDS);

		if (unarmed && running(attacker, EarthDefaultKit.FIST)) {
			dmg += EarthConfig.A1_EXTRA.get().floatValue();
			queuePush(attacker, target, EarthConfig.A1_PUSH.get());
		}
		if (sword && BLADE.left(attacker) > 0) {
			dmg *= EarthConfig.A2_MULTIPLIER.get().floatValue();
			BLADE.use(attacker);
			AspectMastery.onAbilityHit(attacker, E, target);
		}
		if (unarmed && FLAMING.left(attacker) > 0) {
			dmg += EarthConfig.MG_A1_EXTRA.get().floatValue();
			if (QiEffects.canAffect(target, false))
				FireKit.ignite(target);
			queuePush(attacker, target, EarthConfig.MG_A1_PUSH.get());
			FLAMING.use(attacker);
			AspectMastery.onAbilityHit(attacker, E, target);
		}
		if (unarmed && THORNS.left(attacker) > 0) {
			int poison = seconds(EarthConfig.NT_A1_POISON_SECONDS.get()), root = EarthConfig.NT_A1_ROOT_TICKS.get();
			later(() -> {
				if (target.isAlive() && QiEffects.canAffect(target, false)) {
					EffectHelper.apply(target, MobEffects.POISON, 1, poison, true);
					Stun.apply(target, root);
				}
			});
			THORNS.use(attacker);
			AspectMastery.onAbilityHit(attacker, E, target);
		}
		event.setNewDamage(dmg);
	}

	/** Incoming: Sand never suffocates in sand; Sand block walls off melee / arrow hits. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer victim))
			return;
		DamageSource src = event.getSource();
		if (src.is(DamageTypes.IN_WALL)) {
			if (EarthSubKits.sandPassiveOn(victim) && EarthWorld.isSand(victim.level().getBlockState(BlockPos.containing(victim.getEyePosition()))))
				event.setCanceled(true);
			return;
		}
		if (QiEffects.isDealingQiDamage() || !running(victim, EarthSubKits.SAND_BLOCK))
			return;
		Entity attacker = src.getEntity(), direct = src.getDirectEntity();
		boolean melee = attacker instanceof LivingEntity && attacker != victim && direct == attacker;
		boolean arrow = direct instanceof AbstractArrow;
		if (!melee && !arrow)
			return;
		event.setCanceled(true);
		Vec3 from = (melee ? attacker : direct).position();
		later(() -> {
			if (!victim.isAlive())
				return;
			sandWall(victim, from);
			if (melee && attacker instanceof LivingEntity le && le.isAlive() && le.getBoundingBox().inflate(1).intersects(victim.getBoundingBox()))
				Motion.push(le, victim.position(), 1, 0.5, 0.1, null);
		});
	}

	/** After damage landed: Fire users burning world trees. */
	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		if (TREES.isEmpty() || event.getNewDamage() <= 0 || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity())
			return;
		if (AspectData.of(attacker).getActive() == AspectType.FIRE)
			burnTrees(attacker);
	}

	private static final Map<UUID, Long> BONEMEAL_USED = new HashMap<>();

	/** Nature passive: right-clicking a plant bonemeals it for free. */
	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer p))
			return;
		if (!EarthSubKits.naturePassiveOn(p))
			return;
		ServerLevel level = p.serverLevel();
		BlockPos pos = event.getPos();
		BlockState s = level.getBlockState(pos);
		if (!(s.getBlock() instanceof BonemealableBlock b) || s.is(BlockTags.DIRT) || s.is(BlockTags.NYLIUM))
			return; // plants only (not grass / moss / nylium ground)
		long now = level.getGameTime();
		Long last = BONEMEAL_USED.get(p.getUUID());
		if (last != null && now - last < EarthConfig.NT_BONEMEAL_COOLDOWN_TICKS.get())
			return;
		if (!b.isValidBonemealTarget(level, pos, s))
			return;
		BONEMEAL_USED.put(p.getUUID(), now);
		if (b.isBonemealSuccess(level, level.random, pos, s))
			b.performBonemeal(level, level.random, pos, s);
		level.levelEvent(1505, pos, 15); // bone meal particles + sound
		p.swing(InteractionHand.MAIN_HAND, true);
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (LATER.isEmpty())
			return;
		List<Runnable> run = new ArrayList<>(LATER);
		LATER.clear();
		run.forEach(Runnable::run);
	}

	// ================================================================== form leave / cleanup

	static void leaveMagma(ServerPlayer p) {
		FLAMING.clear(p);
	}

	static void leaveSand(ServerPlayer p) {
		SAND_EXITS.remove(p.getUUID());
		SUNS.remove(p.getUUID());
	}

	static void leaveNature(ServerPlayer p) {
		THORNS.clear(p);
		OVERGROWTH.remove(p.getUUID());
		fellTree(p);
	}

	private static void clear(UUID id) {
		BLADE.clear(id);
		FLAMING.clear(id);
		THORNS.clear(id);
		SAND_EXITS.remove(id);
		SUNS.remove(id);
		NO_REGEN.remove(id);
		OVERGROWTH.remove(id);
		BONEMEAL_USED.remove(id);
		Field tree = TREES.remove(id);
		if (tree != null)
			EarthWorld.revertGroup(tree.group);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer p)
			clear(p.getUUID());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity().getUUID());
	}

	public static void resetAll() {
		BLADE.clearAll();
		FLAMING.clearAll();
		THORNS.clearAll();
		LATER.clear();
		SAND_EXITS.clear();
		SUNS.clear();
		NO_REGEN.clear();
		OVERGROWTH.clear();
		TREES.clear();
		BONEMEAL_USED.clear();
	}

}
