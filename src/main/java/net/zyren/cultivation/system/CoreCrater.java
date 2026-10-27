package net.zyren.cultivation.system;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.config.CultivationConfig;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A Corrupted core exploding (decided 2026-10-07): a full sphere crater, radius = core level x 30
 * blocks (config, optional cap), and explosion damage to living things inside it that falls off
 * toward the edge. Blocks are cleared over several ticks so big craters don't freeze the server;
 * unloaded chunks are skipped (never force-loaded); unbreakable blocks (bedrock etc.) stay.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class CoreCrater {
	private static final class Job {
		final ServerLevel level;
		final int cx, cy, cz, r;
		final long r2;
		int dx, dy, dz;

		Job(ServerLevel level, BlockPos c, int r) {
			this.level = level;
			this.cx = c.getX();
			this.cy = c.getY();
			this.cz = c.getZ();
			this.r = r;
			this.r2 = (long) r * r;
			this.dx = -r;
			this.dy = -r;
			this.dz = -r;
		}

		/** @return true when finished */
		boolean step(int budget) {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int minY = level.getMinBuildHeight(), maxY = level.getMaxBuildHeight();
			while (budget-- > 0) {
				if (dx > r)
					return true;
				int x = cx + dx, y = cy + dy, z = cz + dz;
				if ((long) dx * dx + (long) dy * dy + (long) dz * dz <= r2 && y >= minY && y < maxY
						&& level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0) {
						// "No drops": empty containers first, or chests/furnaces spill on removal.
						net.minecraft.world.Clearable.tryClear(level.getBlockEntity(pos));
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				// advance z -> y -> x
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

	private static final List<Job> JOBS = new ArrayList<>();

	private CoreCrater() {
	}

	public static int radiusFor(int coreLevel) {
		int r = coreLevel * CultivationConfig.CRATER_RADIUS_PER_LEVEL.get();
		int cap = CultivationConfig.CRATER_MAX_RADIUS.get();
		return cap > 0 ? Math.min(r, cap) : r;
	}

	/** Called when a Corrupted core breaks, before the player dies. */
	public static void explode(ServerPlayer player, int coreLevel) {
		if (!CultivationConfig.CRATER_ENABLED.get() || coreLevel < 1)
			return;
		int r = radiusFor(coreLevel);
		if (r < 1)
			return;
		ServerLevel level = player.serverLevel();
		Vec3 c = player.position();

		// Sound + flash only (blocks are handled by the crater job, damage below). Not level.explode():
		// that also hurts and knocks back entities, which would hit everything near the centre twice.
		level.playSound(null, c.x, c.y, c.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.BLOCKS, 8.0F, 0.6F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 6, 2, 2, 2, 0);

		// Explosion damage, strongest at the centre, 0 at the edge
		double maxDamage = CultivationConfig.CRATER_CENTER_DAMAGE.get();
		if (maxDamage > 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r))) {
				if (e == player || !e.isAlive())
					continue;
				double d = e.position().distanceTo(c);
				if (d > r)
					continue;
				float dmg = (float) (maxDamage * (1 - d / r));
				if (dmg > 0)
					// No attacker: the exploding player's core is already reset, so crediting kills to
					// them would pay kill rewards (re-forming a core) right before they die.
					e.hurt(level.damageSources().explosion(null, null), dmg);
			}
		}

		JOBS.add(new Job(level, player.blockPosition(), r));
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (JOBS.isEmpty())
			return;
		int budget = CultivationConfig.CRATER_BLOCKS_PER_TICK.get();
		for (Iterator<Job> it = JOBS.iterator(); it.hasNext();) {
			if (it.next().step(budget))
				it.remove();
			break; // one crater at a time keeps the per-tick cost fixed
		}
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		JOBS.clear();
	}
}
