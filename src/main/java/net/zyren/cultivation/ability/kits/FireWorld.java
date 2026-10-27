package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.config.FireConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fire's big permanent world edits, spread over many ticks (FireConfig.blocks_per_tick) so they don't
 * freeze the server. Unloaded chunks are skipped, never force-loaded. Unbreakable blocks stay.
 * Removed containers are emptied first (no item spill). One job runs at a time.
 *
 * HELL ON EARTH (radius 100, ground band only: from depth_below_surface under the ground up to
 * height_above_surface above it, per column):
 *   water touching air -> gone; other water sources -> lava source (placed after all water is gone)
 *   dirt / grass (dirt tag) -> magma;  wooden blocks -> 50% set aflame, else deleted
 * THE WORLD WILL BURN (Volcanic, radius 50 sphere): stone-type -> magma, sand -> soul sand, water
 *   removed then lava where the sources were, fire on every air block resting on solid ground.
 * METEOR (radius 25 sphere around the caster):
 *   sphere removed; if buried, a shaft is opened up to the surface; then every crater surface
 *   block becomes lava (50%) or gets fire on it; if the crater reached bedrock, lava sources are
 *   placed 15 blocks above the bedrock across the crater.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class FireWorld {
	private interface Job {
		/** @return true when finished */
		boolean step(int budget);

		ServerLevel level();
	}

	private static final List<Job> JOBS = new ArrayList<>();

	private FireWorld() {
	}

	static void hellOnEarth(ServerLevel level, BlockPos center, int radius) {
		JOBS.add(new HellJob(level, center, radius));
	}

	static void meteor(ServerLevel level, BlockPos center, int radius) {
		JOBS.add(new MeteorJob(level, center, radius));
	}

	/** Volcanic 'the world will burn'. */
	static void worldBurn(ServerLevel level, BlockPos center, int radius) {
		JOBS.add(new WorldBurnJob(level, center, radius));
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (JOBS.isEmpty())
			return;
		Job job = JOBS.get(0);
		if (job.step(FireConfig.BLOCKS_PER_TICK.get()))
			JOBS.remove(0);
	}

	static void resetAll() {
		JOBS.clear();
	}

	// ------------------------------------------------------------------ shared helpers

	private static boolean loaded(ServerLevel level, int x, int z) {
		return level.getChunkSource().hasChunk(x >> 4, z >> 4);
	}

	private static boolean breakable(ServerLevel level, BlockPos p, BlockState s) {
		return s.getDestroySpeed(level, p) >= 0;
	}

	/** Remove without drops / container spill and without neighbour updates (water doesn't rush in mid-job). */
	private static void remove(ServerLevel level, BlockPos p) {
		Clearable.tryClear(level.getBlockEntity(p));
		level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	private static boolean placeFireOn(ServerLevel level, BlockPos solid) {
		BlockPos up = solid.above();
		if (level.getBlockState(up).isAir() && level.getBlockState(solid).isFaceSturdy(level, solid, Direction.UP)) {
			level.setBlock(up, BaseFireBlock.getState(level, up), Block.UPDATE_ALL);
			return true;
		}
		return false;
	}

	private static final Set<Block> EXTRA_WOOD = Set.of(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.CRAFTING_TABLE,
			Blocks.BOOKSHELF, Blocks.CHISELED_BOOKSHELF, Blocks.LECTERN, Blocks.LADDER, Blocks.COMPOSTER, Blocks.LOOM,
			Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.SMITHING_TABLE, Blocks.BEEHIVE, Blocks.NOTE_BLOCK, Blocks.JUKEBOX);

	/** "Anything crafted mostly from wood". */
	static boolean isWooden(BlockState s) {
		return s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS)
				|| s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.WOODEN_DOORS)
				|| s.is(BlockTags.WOODEN_TRAPDOORS) || s.is(BlockTags.WOODEN_BUTTONS) || s.is(BlockTags.WOODEN_PRESSURE_PLATES)
				|| s.is(BlockTags.ALL_SIGNS) || EXTRA_WOOD.contains(s.getBlock());
	}

	// ------------------------------------------------------------------ HELL ON EARTH

	private static final class HellJob implements Job {
		final ServerLevel level;
		final int cx, cz, r;
		int dx, dz;
		/** 0 = scan columns, 1 = place lava at remembered sources */
		int phase;
		final List<BlockPos> lavaAt = new ArrayList<>();
		int lavaIndex;
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		HellJob(ServerLevel level, BlockPos c, int r) {
			this.level = level;
			this.cx = c.getX();
			this.cz = c.getZ();
			this.r = r;
			this.dx = -r;
			this.dz = -r;
		}

		@Override
		public ServerLevel level() {
			return level;
		}

		@Override
		public boolean step(int budget) {
			if (phase == 1) {
				while (lavaIndex < lavaAt.size() && budget-- > 0) {
					BlockPos p = lavaAt.get(lavaIndex++);
					if (loaded(level, p.getX(), p.getZ()) && level.getBlockState(p).isAir())
						level.setBlock(p, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
				}
				return lavaIndex >= lavaAt.size();
			}
			int depth = FireConfig.AB1_DEPTH.get(), height = FireConfig.AB1_HEIGHT.get();
			double burn = FireConfig.AB1_WOOD_BURN_CHANCE.get();
			while (budget > 0) {
				if (dx > r) {
					phase = 1;
					return lavaAt.isEmpty();
				}
				int x = cx + dx, z = cz + dz;
				if ((long) dx * dx + (long) dz * dz <= (long) r * r && loaded(level, x, z)) {
					int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
					int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), ground + height);
					int bottom = Math.max(level.getMinBuildHeight(), ground - depth);
					// bottom-up, so deeper water still sees the water above it (and becomes lava)
					for (int y = bottom; y <= top; y++) {
						budget--;
						pos.set(x, y, z);
						BlockState s = level.getBlockState(pos);
						if (s.isAir())
							continue;
						if (s.getFluidState().is(FluidTags.WATER)) {
							boolean touchesAir = false;
							for (Direction d : Direction.values())
								if (level.getBlockState(pos.relative(d)).isAir()) {
									touchesAir = true;
									break;
								}
							BlockPos p = pos.immutable();
							if (!touchesAir && s.getFluidState().isSource())
								lavaAt.add(p);
							BlockState dry = s.hasProperty(BlockStateProperties.WATERLOGGED) ? s.setValue(BlockStateProperties.WATERLOGGED, false) : Blocks.AIR.defaultBlockState();
							level.setBlock(p, dry, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						} else if (s.is(BlockTags.DIRT) && breakable(level, pos, s)) {
							level.setBlock(pos.immutable(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (isWooden(s) && breakable(level, pos, s)) {
							BlockPos p = pos.immutable();
							if (level.random.nextDouble() < burn) {
								// set aflame: fire in a free neighbouring spot; the fire does the rest
								for (Direction d : Direction.values()) {
									BlockPos n = p.relative(d);
									if (level.getBlockState(n).isAir()) {
										level.setBlock(n, BaseFireBlock.getState(level, n), Block.UPDATE_ALL);
										break;
									}
								}
							} else {
								remove(level, p);
							}
						}
					}
				}
				if (++dz > r) {
					dz = -r;
					dx++;
				}
			}
			return false;
		}
	}

	// ------------------------------------------------------------------ THE WORLD WILL BURN

	static boolean isStoneType(BlockState s) {
		return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(net.neoforged.neoforge.common.Tags.Blocks.COBBLESTONES);
	}

	private static final class WorldBurnJob implements Job {
		final ServerLevel level;
		final int cx, cy, cz, r;
		/** 0 = convert blocks + remove water, 1 = lava at the old water sources, 2 = fire on the ground */
		int phase;
		int dx, dy, dz;
		final List<BlockPos> lavaAt = new ArrayList<>();
		int lavaIndex;
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		WorldBurnJob(ServerLevel level, BlockPos c, int r) {
			this.level = level;
			this.cx = c.getX();
			this.cy = c.getY();
			this.cz = c.getZ();
			this.r = r;
			dx = dy = dz = -r;
		}

		@Override
		public ServerLevel level() {
			return level;
		}

		@Override
		public boolean step(int budget) {
			if (phase == 1) {
				while (lavaIndex < lavaAt.size() && budget-- > 0) {
					BlockPos p = lavaAt.get(lavaIndex++);
					if (loaded(level, p.getX(), p.getZ()) && level.getBlockState(p).isAir())
						level.setBlock(p, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
				}
				if (lavaIndex >= lavaAt.size()) {
					phase = 2;
					dx = dy = dz = -r;
				}
				return false;
			}
			int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight();
			long r2 = (long) r * r;
			while (budget-- > 0) {
				if (dx > r) {
					if (phase == 0) {
						phase = 1;
						return false;
					}
					return true;
				}
				int x = cx + dx, y = cy + dy, z = cz + dz;
				if ((long) dx * dx + (long) dy * dy + (long) dz * dz <= r2 && y >= minY && y < maxY && loaded(level, x, z)) {
					pos.set(x, y, z);
					BlockState s = level.getBlockState(pos);
					if (phase == 0) {
						if (s.getFluidState().is(FluidTags.WATER)) {
							BlockPos p = pos.immutable();
							if (s.getFluidState().isSource())
								lavaAt.add(p);
							BlockState dry = s.hasProperty(BlockStateProperties.WATERLOGGED) ? s.setValue(BlockStateProperties.WATERLOGGED, false) : Blocks.AIR.defaultBlockState();
							level.setBlock(p, dry, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						} else if (isStoneType(s) && breakable(level, pos, s)) {
							level.setBlock(pos.immutable(), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
						} else if (s.is(BlockTags.SAND) && breakable(level, pos, s)) {
							level.setBlock(pos.immutable(), Blocks.SOUL_SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					} else if (s.isAir()) {
						placeFireOn(level, pos.below().immutable());
					}
				}
				if (++dz > r) {
					dz = -r;
					if (++dy > r) {
						dy = -r;
						dx++;
					}
				}
			}
			return false;
		}
	}

	// ------------------------------------------------------------------ METEOR

	private static final class MeteorJob implements Job {
		final ServerLevel level;
		final int cx, cy, cz, r;
		/** 0 = carve sphere, 1 = shaft to surface, 2 = surface fire/lava, 3 = bedrock lava */
		int phase;
		int dx, dy, dz;
		int bedrockY = Integer.MIN_VALUE;
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		MeteorJob(ServerLevel level, BlockPos c, int r) {
			this.level = level;
			this.cx = c.getX();
			this.cy = c.getY();
			this.cz = c.getZ();
			this.r = r;
			resetCursor(r);
		}

		private void resetCursor(int rr) {
			dx = -rr;
			dy = -rr;
			dz = -rr;
		}

		@Override
		public ServerLevel level() {
			return level;
		}

		@Override
		public boolean step(int budget) {
			int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight();
			long r2 = (long) r * r;
			while (budget-- > 0) {
				switch (phase) {
					case 0 -> { // carve the sphere
						if (dx > r) {
							phase = 1;
							dx = -r;
							dz = -r;
							continue;
						}
						int x = cx + dx, y = cy + dy, z = cz + dz;
						if ((long) dx * dx + (long) dy * dy + (long) dz * dz <= r2 && y >= minY && y < maxY && loaded(level, x, z)) {
							pos.set(x, y, z);
							BlockState s = level.getBlockState(pos);
							if (s.is(Blocks.BEDROCK))
								bedrockY = Math.max(bedrockY, y);
							else if (!s.isAir() && breakable(level, pos, s))
								remove(level, pos.immutable());
						}
						advanceCube(r);
					}
					case 1 -> { // shaft up to the surface if the crater is buried
						if (dx > r) {
							phase = 2;
							resetCursor(r + 1);
							continue;
						}
						int x = cx + dx, z = cz + dz;
						if ((long) dx * dx + (long) dz * dz <= r2 && loaded(level, x, z)) {
							int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
							for (int y = cy + r + 1; y < surface && y < maxY; y++) {
								budget--;
								pos.set(x, y, z);
								BlockState s = level.getBlockState(pos);
								if (!s.isAir() && breakable(level, pos, s))
									remove(level, pos.immutable());
							}
						}
						if (++dz > r) {
							dz = -r;
							dx++;
						}
					}
					case 2 -> { // crater surface: lava or fire on top
						int rr = r + 1;
						if (dx > rr) {
							phase = 3;
							dx = -r;
							dz = -r;
							continue;
						}
						long d2 = (long) dx * dx + (long) dy * dy + (long) dz * dz;
						int x = cx + dx, y = cy + dy, z = cz + dz;
						if (d2 > r2 && d2 <= (long) rr * rr && y >= minY && y < maxY && loaded(level, x, z)) {
							pos.set(x, y, z);
							BlockState s = level.getBlockState(pos);
							if (!s.isAir() && !s.liquid() && breakable(level, pos, s)) {
								BlockPos p = pos.immutable();
								if (level.random.nextDouble() < FireConfig.AB2_LAVA_CHANCE.get())
									level.setBlock(p, Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
								else
									placeFireOn(level, p);
							}
						}
						advanceCube(rr);
					}
					default -> { // lava 15 above the bedrock, across the crater
						if (bedrockY == Integer.MIN_VALUE || dx > r)
							return true;
						int x = cx + dx, z = cz + dz, y = bedrockY + FireConfig.AB2_BEDROCK_LAVA_HEIGHT.get();
						if ((long) dx * dx + (long) dz * dz <= r2 && y < maxY && loaded(level, x, z)) {
							pos.set(x, y, z);
							if (level.getBlockState(pos).isAir())
								level.setBlock(pos.immutable(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
						}
						if (++dz > r) {
							dz = -r;
							dx++;
						}
					}
				}
			}
			return false;
		}

		/** z -> y -> x over a cube of half-size rr. */
		private void advanceCube(int rr) {
			if (++dz > rr) {
				dz = -rr;
				if (++dy > rr) {
					dy = -rr;
					dx++;
				}
			}
		}
	}
}
