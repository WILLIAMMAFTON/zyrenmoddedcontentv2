package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.config.SpaceConfig;

/**
 * Safe-spot search and teleports for the Space kits. A safe spot: solid ground (not magma / fire /
 * campfire / cactus / berry bush / powder snow), two free non-liquid blocks above it, and no hostile
 * mob or other player within the clear radius (config). Searches stay in loaded chunks, except
 * when arriving in another dimension.
 */
final class SpaceWorld {
	private SpaceWorld() {
	}

	private static boolean hazard(BlockState s) {
		return s.is(Blocks.MAGMA_BLOCK) || s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.CACTUS) || s.is(Blocks.SWEET_BERRY_BUSH)
				|| s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE) || !s.getFluidState().isEmpty();
	}

	private static boolean free(ServerLevel l, BlockPos p) {
		BlockState s = l.getBlockState(p);
		return s.getCollisionShape(l, p).isEmpty() && !hazard(s);
	}

	/** Ground + 2 free blocks (feet = the first free block). */
	static boolean standable(ServerLevel l, BlockPos feet) {
		if (!l.isInWorldBounds(feet) || !l.isInWorldBounds(feet.above()) || !l.isInWorldBounds(feet.below()))
			return false;
		BlockPos below = feet.below();
		BlockState g = l.getBlockState(below);
		return g.isFaceSturdy(l, below, Direction.UP) && !hazard(g) && free(l, feet) && free(l, feet.above());
	}

	/** No hostile mob and no other (non-spectator) player near the spot. */
	static boolean clear(ServerLevel l, BlockPos feet, ServerPlayer user) {
		double r = SpaceConfig.SAFE_CLEAR_RADIUS.get();
		if (r <= 0)
			return true;
		Vec3 c = Vec3.atBottomCenterOf(feet);
		return l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
				e -> e != user && e.isAlive() && !e.isSpectator() && e.position().distanceTo(c) <= r && (e instanceof Enemy || e instanceof ServerPlayer)).isEmpty();
	}

	static boolean safe(ServerLevel l, BlockPos feet, ServerPlayer user) {
		return standable(l, feet) && clear(l, feet, user);
	}

	/** A safe spot in this column near {@code aroundY} (the surface first), or null. Loaded chunks only unless {@code load}. */
	static BlockPos safeInColumn(ServerLevel l, int x, int z, int aroundY, int span, ServerPlayer user, boolean load) {
		if (!load && !l.getChunkSource().hasChunk(x >> 4, z >> 4))
			return null;
		int top = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos s = new BlockPos(x, top, z);
		if (l.dimensionType().hasCeiling() ? false : safe(l, s, user))
			return s;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dy = 0; dy <= span; dy++)
			for (int sign : new int[] { 1, -1 }) {
				if (dy == 0 && sign < 0)
					continue;
				m.set(x, aroundY + dy * sign, z);
				if (safe(l, m, user))
					return m.immutable();
			}
		return null;
	}

	/** Nearest safe spot within maxR (rings, denser close by), or null. */
	static BlockPos nearestSafe(ServerPlayer p, int maxR) {
		ServerLevel l = p.serverLevel();
		int px = p.getBlockX(), py = p.getBlockY(), pz = p.getBlockZ();
		BlockPos here = safeInColumn(l, px, pz, py, 6, p, false);
		if (here != null && !here.equals(p.blockPosition()))
			return here;
		for (int r = 1; r <= maxR; r += r < 16 ? 1 : 4) {
			int samples = Math.min(64, Math.max(8, (int) (r * 2 * Math.PI / 2)));
			double off = p.getRandom().nextDouble() * Math.PI * 2;
			for (int i = 0; i < samples; i++) {
				double a = off + i * Math.PI * 2 / samples;
				BlockPos s = safeInColumn(l, px + (int) Math.round(Math.cos(a) * r), pz + (int) Math.round(Math.sin(a) * r), py, 8, p, false);
				if (s != null)
					return s;
			}
		}
		return null;
	}

	/** A random safe spot between minR and maxR away, or null. */
	static BlockPos randomSafe(ServerPlayer p, int minR, int maxR) {
		ServerLevel l = p.serverLevel();
		maxR = Math.max(maxR, minR + 1);
		for (int i = 0; i < 80; i++) {
			double a = p.getRandom().nextDouble() * Math.PI * 2, d = minR + p.getRandom().nextDouble() * (maxR - minR);
			BlockPos s = safeInColumn(l, p.getBlockX() + (int) Math.round(Math.cos(a) * d), p.getBlockZ() + (int) Math.round(Math.sin(a) * d), p.getBlockY(), 8, p,
					false);
			if (s != null)
				return s;
		}
		return null;
	}

	static void teleport(ServerPlayer p, ServerLevel level, BlockPos feet) {
		double x = feet.getX() + 0.5, y = feet.getY(), z = feet.getZ() + 0.5;
		if (level == p.serverLevel())
			p.teleportTo(x, y, z);
		else
			p.teleportTo(level, x, y, z, p.getYRot(), p.getXRot());
		p.fallDistance = 0;
	}

	/**
	 * Where the player arrives in {@code dest}: the End's spawn platform; the Nether / Overworld at
	 * the scaled position (x8 / /8), nearest safe spot around it (chunks load); a small obsidian
	 * platform as the last resort.
	 */
	static BlockPos arrival(ServerPlayer p, ServerLevel dest) {
		if (dest.dimension() == Level.END) {
			net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(dest, ServerLevel.END_SPAWN_POINT.below(), true);
			return ServerLevel.END_SPAWN_POINT;
		}
		double scale = p.serverLevel().dimensionType().coordinateScale() / dest.dimensionType().coordinateScale();
		int x = (int) Math.floor(p.getX() * scale), z = (int) Math.floor(p.getZ() * scale);
		x = Math.max(-29_999_000, Math.min(29_999_000, x));
		z = Math.max(-29_999_000, Math.min(29_999_000, z));
		boolean nether = dest.dimensionType().hasCeiling();
		int midY = nether ? 64 : dest.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int span = nether ? 56 : 24;
		for (int r = 0; r <= 24; r += 2) {
			int samples = r == 0 ? 1 : 16;
			for (int i = 0; i < samples; i++) {
				double a = i * Math.PI * 2 / samples;
				BlockPos s = safeInColumn(dest, x + (int) Math.round(Math.cos(a) * r), z + (int) Math.round(Math.sin(a) * r), midY, span, p, true);
				if (s != null)
					return s;
			}
		}
		// last resort: 3x3 obsidian floor with air above
		BlockPos base = new BlockPos(x, nether ? 64 : Math.max(dest.getMinBuildHeight() + 2, midY), z);
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++) {
				dest.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.OBSIDIAN.defaultBlockState());
				for (int dy = 0; dy < 3; dy++)
					dest.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
			}
		return base;
	}

	/** Spot about {@code back} blocks behind the target (opposite its look), or null if blocked. */
	static Vec3 behind(ServerPlayer user, LivingEntity t, double back) {
		Vec3 look = Vec3.directionFromRotation(0, t.getYRot());
		for (double d = back; d >= 1; d -= 0.5) {
			Vec3 at = t.position().subtract(look.scale(d));
			if (user.serverLevel().noCollision(user, user.getBoundingBox().move(at.subtract(user.position()))))
				return at;
		}
		return null;
	}
}
