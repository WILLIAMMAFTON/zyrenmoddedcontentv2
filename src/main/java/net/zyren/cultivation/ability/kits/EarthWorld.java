package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.config.EarthConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Terrain helpers for the Earth kits: shells / pillars / craters, temporary blocks that revert
 * (and never drop items when mined), and big edits spread over several ticks (blocks_per_tick).
 * Never touches unbreakable blocks or blocks with a block entity (chests, furnaces...).
 * Temporary blocks still standing when the server stops are reverted first.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class EarthWorld {
	private EarthWorld() {
	}

	// ================================================================== block tests

	/** Stone- or dirt-tagged (DEFAULT passive ground). */
	static boolean isGround(BlockState s) {
		return EarthKit.isStone(s) || s.is(BlockTags.DIRT);
	}

	static boolean isSand(BlockState s) {
		return s.is(BlockTags.SAND) || s.is(Tags.Blocks.SANDS) || s.is(Tags.Blocks.SANDSTONE_BLOCKS);
	}

	static BlockState below(Entity e) {
		return e.level().getBlockState(BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ()));
	}

	/** Free to fill: air, or a replaceable non-liquid block (short grass, flowers, snow layer). */
	static boolean open(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty();
	}

	/** May be replaced: in the world, breakable, no block entity. */
	static boolean reshapable(ServerLevel level, BlockPos pos) {
		return level.isInWorldBounds(pos) && EarthKit.canReshape(level, pos);
	}

	static boolean hasLivingInside(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty();
	}

	/** Top solid-ish block of the column (leaves ignored). */
	static int surface(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	// ================================================================== permanent edits

	/** Places {@code state} only where the block is open. */
	static boolean fill(ServerLevel level, BlockPos pos, BlockState state) {
		if (!level.isInWorldBounds(pos) || !open(level, pos))
			return false;
		level.setBlock(pos, state, Block.UPDATE_ALL);
		return true;
	}

	/** Replaces any reshapable block (air too) with {@code state}. */
	static boolean replace(ServerLevel level, BlockPos pos, BlockState state) {
		if (!reshapable(level, pos) || level.getBlockState(pos) == state)
			return false;
		forget(level, pos);
		level.setBlock(pos, state, Block.UPDATE_ALL);
		return true;
	}

	/** Shell {@code thick} blocks thick around the inner box (inclusive corners); fills open blocks only. */
	static void shell(ServerLevel level, BlockPos innerMin, BlockPos innerMax, int thick, BlockState state) {
		for (BlockPos p : BlockPos.betweenClosed(innerMin.offset(-thick, -thick, -thick), innerMax.offset(thick, thick, thick))) {
			boolean inside = p.getX() >= innerMin.getX() && p.getX() <= innerMax.getX() && p.getY() >= innerMin.getY() && p.getY() <= innerMax.getY()
					&& p.getZ() >= innerMin.getZ() && p.getZ() <= innerMax.getZ();
			if (!inside)
				fill(level, p, state);
		}
	}

	/** Bowl-shaped hole below {@code topY}: {@code depth} deep in the middle, radius {@code r}; also clears {@code clearAbove} blocks from topY up; optional floor block. */
	static void bowl(ServerLevel level, int cx, int topY, int cz, int r, int depth, int clearAbove, BlockState floor) {
		if (r <= 0 || depth <= 0)
			return;
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r)
					continue;
				int here = Math.max(1, (int) Math.ceil(depth * Math.sqrt(1 - (d / r) * (d / r))));
				BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
				for (int y = topY - here; y < topY + clearAbove; y++)
					replace(level, m.set(cx + dx, y, cz + dz), Blocks.AIR.defaultBlockState());
				if (floor != null)
					replace(level, m.set(cx + dx, topY - here - 1, cz + dz), floor);
			}
	}

	// ================================================================== temporary blocks

	private record Key(ResourceKey<Level> dim, BlockPos pos) {
	}

	private record Temp(ServerLevel level, BlockState original, BlockState placed, long until, String group) {
	}

	private static final Map<Key, Temp> TEMPS = new LinkedHashMap<>();

	/**
	 * Places a block that reverts after {@code ticks} (or when {@link #revertGroup} runs). It only
	 * reverts if the block is still the one placed (same block type). Mining it drops nothing.
	 * @param onlyOpen true: only where the block is open; false: any reshapable block
	 */
	static boolean temp(ServerLevel level, BlockPos pos, BlockState state, int ticks, String group, boolean onlyOpen) {
		if (!level.isInWorldBounds(pos) || (onlyOpen ? !open(level, pos) : !EarthKit.canReshape(level, pos)))
			return false;
		Key k = new Key(level.dimension(), pos.immutable());
		Temp old = TEMPS.get(k);
		BlockState original = old != null && level.getBlockState(pos).is(old.placed().getBlock()) ? old.original() : level.getBlockState(pos);
		level.setBlock(pos, state, Block.UPDATE_ALL);
		TEMPS.put(k, new Temp(level, original, state, level.getGameTime() + Math.max(1, ticks), group));
		return true;
	}

	static void revertGroup(String group) {
		TEMPS.entrySet().removeIf(e -> {
			if (!e.getValue().group().equals(group))
				return false;
			revert(e.getKey().pos(), e.getValue());
			return true;
		});
	}

	private static void revert(BlockPos pos, Temp t) {
		if (t.level().getBlockState(pos).is(t.placed().getBlock())) // same block (leaves change their distance state)
			t.level().setBlock(pos, t.original(), Block.UPDATE_ALL);
	}

	/** A block we replace for good is no longer temporary. */
	private static void forget(ServerLevel level, BlockPos pos) {
		if (!TEMPS.isEmpty())
			TEMPS.remove(new Key(level.dimension(), pos));
	}

	@SubscribeEvent
	public static void onDrops(BlockDropsEvent event) {
		if (TEMPS.isEmpty())
			return;
		Key k = new Key(event.getLevel().dimension(), event.getPos());
		Temp t = TEMPS.get(k);
		if (t != null && event.getState().is(t.placed().getBlock())) {
			event.getDrops().clear();
			event.setDroppedExperience(0);
			TEMPS.remove(k);
		}
	}

	// ================================================================== budgeted jobs

	private static final class Job {
		Iterator<BlockPos> it;
		Consumer<BlockPos> action;
		Runnable then;
	}

	private static final List<Job> JOBS = new ArrayList<>();

	/** Runs {@code action} for every position, blocks_per_tick per server tick; then {@code then} (may be null). */
	static void spread(Iterator<BlockPos> positions, Consumer<BlockPos> action, Runnable then) {
		Job j = new Job();
		j.it = positions;
		j.action = action;
		j.then = then;
		JOBS.add(j);
	}

	// ================================================================== pillars (Heaven's staircase)

	private static final class Pillar {
		ServerLevel level;
		LivingEntity target;
		int nextY, ticks;
		double topY, lastY;
	}

	private static final List<Pillar> PILLARS = new ArrayList<>();

	/** A stone column grows under {@code target} while it rises; it lands on top. */
	static void pillarUnder(LivingEntity target) {
		if (!(target.level() instanceof ServerLevel level))
			return;
		Pillar p = new Pillar();
		p.level = level;
		p.target = target;
		p.nextY = target.getBlockY();
		p.topY = target.getY();
		p.lastY = target.getY();
		PILLARS.add(p);
	}

	private static boolean tickPillar(Pillar p) {
		LivingEntity t = p.target;
		if (!t.isAlive() || t.level() != p.level || ++p.ticks > 300)
			return true;
		p.topY = Math.max(p.topY, t.getY());
		// players' motion isn't known server-side: use the position change
		boolean falling = p.ticks > 3 && t.getY() < p.lastY - 0.01;
		p.lastY = t.getY();
		int top = (int) Math.floor(p.topY) - 1;
		BlockState stone = Blocks.STONE.defaultBlockState();
		for (; p.nextY <= top; p.nextY++)
			fill(p.level, new BlockPos(t.getBlockX(), p.nextY, t.getBlockZ()), stone);
		return falling || t.onGround() && p.ticks > 3;
	}

	// ================================================================== "after it lands"

	private static final class Landing {
		LivingEntity target;
		Consumer<LivingEntity> then;
		int ticks, max;
	}

	private static final List<Landing> LANDINGS = new ArrayList<>();

	/** Runs {@code then} once the target stopped being thrown and touches ground / water (or after {@code maxTicks}). */
	static void whenLanded(LivingEntity target, int maxTicks, Consumer<LivingEntity> then) {
		Landing l = new Landing();
		l.target = target;
		l.then = then;
		l.max = maxTicks;
		LANDINGS.add(l);
	}

	// ================================================================== ticking

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (!JOBS.isEmpty()) {
			int budget = EarthConfig.BLOCKS_PER_TICK.get();
			while (budget > 0 && !JOBS.isEmpty()) {
				Job j = JOBS.get(0);
				while (budget > 0 && j.it.hasNext()) {
					j.action.accept(j.it.next());
					budget--;
				}
				if (!j.it.hasNext()) {
					JOBS.remove(0);
					if (j.then != null)
						j.then.run();
				}
			}
		}
		if (!PILLARS.isEmpty())
			PILLARS.removeIf(EarthWorld::tickPillar);
		if (!LANDINGS.isEmpty()) {
			List<Landing> done = new ArrayList<>();
			LANDINGS.removeIf(l -> {
				LivingEntity e = l.target;
				if (!e.isAlive())
					return true;
				l.ticks++;
				boolean landed = l.ticks > 2 && !Motion.isMoving(e) && (e.onGround() || e.isInWater());
				if (landed || l.ticks >= l.max) {
					done.add(l);
					return true;
				}
				return false;
			});
			done.forEach(l -> l.then.accept(l.target));
		}
		if (!TEMPS.isEmpty() && event.getServer().getTickCount() % 5 == 0) {
			long now = event.getServer().overworld().getGameTime();
			TEMPS.entrySet().removeIf(e -> {
				if (e.getValue().until() > now)
					return false;
				revert(e.getKey().pos(), e.getValue());
				return true;
			});
		}
	}

	/** Revert every temporary block while the worlds still exist. */
	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		TEMPS.forEach((k, t) -> revert(k.pos(), t));
		resetAll();
	}

	public static void resetAll() {
		TEMPS.clear();
		JOBS.clear();
		PILLARS.clear();
		LANDINGS.clear();
	}
}
