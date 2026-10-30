package net.zyren.cultivation.ability.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Ability-driven movement of entities: pushes ("thrown N blocks"), pulls, launches and slams.
 * Velocity-based and applied at the end of the server tick (so vanilla hit knockback in the same tick
 * can't cancel it), which means solid blocks stop them naturally. A push that is stopped by a block
 * reports the block it hit (e.g. Air: "if the target hits stone, deal 10 damage").
 * One motion per entity: a new one replaces the old.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class Motion {
	private static final class Job {
		LivingEntity entity;
		Vec3 dir; // horizontal push direction (null for pull / vertical-only)
		Vec3 point; // pull toward a fixed point, or ...
		Entity anchor; // ... toward this entity
		boolean pull;
		double distance, travelled, speed, lift;
		int slamTicks; // > 0: forced downward motion until on the ground
		Vec3 last;
		int ticks, maxTicks, stuck;
		BiConsumer<LivingEntity, BlockState> onImpact;
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	private static final java.util.List<Runnable> IMPACTS = new java.util.ArrayList<>();

	private Motion() {
	}

	/**
	 * Throw {@code target} horizontally away from {@code from} for about {@code blocks}, starting with
	 * upward speed {@code lift} (0 = a small hop). {@code onImpact} (may be null) runs once if a block
	 * stops the throw early, with that block.
	 */
	public static void push(LivingEntity target, Vec3 from, double blocks, double speed, double lift, BiConsumer<LivingEntity, BlockState> onImpact) {
		if (blocks <= 0)
			return;
		Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0E-4)
			dir = new Vec3(1, 0, 0);
		Job j = new Job();
		j.entity = target;
		j.dir = dir.normalize();
		j.distance = blocks;
		j.speed = speed;
		j.lift = lift;
		j.onImpact = onImpact;
		start(j);
	}

	/** Straight up, about {@code blocks} high. */
	public static void launchUp(LivingEntity target, double blocks) {
		Job j = new Job();
		j.entity = target;
		j.lift = EffectHelper.launchSpeedForHeight(blocks);
		j.distance = 0;
		j.speed = 1;
		start(j);
	}

	/** Drag toward a moving entity (stops next to it or at a solid block). */
	public static void pullToward(LivingEntity target, Entity anchor, double blocks, double speed) {
		pull(target, anchor, null, blocks, speed);
	}

	/** Drag toward a fixed point. */
	public static void pullToward(LivingEntity target, Vec3 point, double blocks, double speed) {
		pull(target, null, point, blocks, speed);
	}

	/** Forced down at {@code speed} blocks/tick until it lands (or {@code maxTicks}). Fall damage applies normally. */
	public static void slam(LivingEntity target, double speed, int maxTicks) {
		Job j = new Job();
		j.entity = target;
		j.speed = speed;
		j.slamTicks = Math.max(1, maxTicks);
		start(j);
		if (target instanceof Player p && p.isFallFlying())
			p.stopFallFlying();
	}

	private static void pull(LivingEntity target, Entity anchor, Vec3 point, double blocks, double speed) {
		if (blocks <= 0)
			return;
		Job j = new Job();
		j.entity = target;
		j.pull = true;
		j.anchor = anchor;
		j.point = point;
		j.distance = blocks;
		j.speed = speed;
		start(j);
	}

	private static void start(Job j) {
		j.last = j.entity.position();
		j.maxTicks = j.slamTicks > 0 ? j.slamTicks : (int) Math.ceil(j.distance / Math.max(0.05, j.speed)) + 10;
		JOBS.put(j.entity.getUUID(), j);
	}

	public static boolean isMoving(LivingEntity e) {
		return JOBS.containsKey(e.getUUID());
	}

	/** @return true when finished */
	private static boolean tick(Job j) {
		LivingEntity e = j.entity;
		if (!e.isAlive() || e.isRemoved() || j.ticks++ > j.maxTicks)
			return true;

		if (j.slamTicks > 0) {
			if (j.ticks > 1 && e.onGround())
				return true;
			if (e instanceof Player p && p.isFallFlying())
				p.stopFallFlying();
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x * 0.5, -j.speed, v.z * 0.5);
			e.hurtMarked = true;
			return false;
		}
		if (j.dir == null && !j.pull) { // launch straight up: one kick, gravity does the rest
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x, j.lift, v.z);
			e.hurtMarked = true;
			return true;
		}

		Vec3 pos = e.position();
		double moved = pos.subtract(j.last).multiply(1, 0, 1).length();
		j.last = pos;
		if (j.ticks > 2) {
			j.travelled += moved;
			j.stuck = moved < j.speed * 0.25 ? j.stuck + 1 : 0;
		}
		Vec3 dir = j.dir;
		if (j.pull) {
			Vec3 to = j.anchor != null ? j.anchor.position() : j.point;
			if (to == null || j.anchor != null && (!j.anchor.isAlive() || j.anchor.level() != e.level()))
				return stop(e);
			Vec3 d = to.subtract(pos).multiply(1, 0, 1);
			if (d.length() <= 1.3)
				return stop(e);
			dir = d.normalize();
		}
		if (j.travelled >= j.distance)
			return stop(e);
		if (j.stuck >= 2) { // a solid block is in the way
			if (j.onImpact != null) {
				BlockPos front = BlockPos.containing(pos.add(dir.scale(0.8)).add(0, 0.5, 0));
				BlockState hit = e.level().getBlockState(front);
				IMPACTS.add(() -> j.onImpact.accept(e, hit)); // run after the loop: a callback may start a new motion
			}
			return stop(e);
		}
		double y;
		if (j.pull)
			y = Math.min(e.getDeltaMovement().y, 0.1);
		else if (j.ticks == 1)
			y = j.lift > 0 ? j.lift : 0.4;
		else
			y = e.getDeltaMovement().y; // gravity takes over after the first kick
		e.setDeltaMovement(dir.x * j.speed, y, dir.z * j.speed);
		e.hurtMarked = true;
		if (j.lift <= 0)
			e.fallDistance = 0;
		return false;
	}

	private static boolean stop(LivingEntity e) {
		Vec3 v = e.getDeltaMovement();
		e.setDeltaMovement(0, Math.min(v.y, 0), 0);
		e.hurtMarked = true;
		return true;
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (JOBS.isEmpty())
			return;
		JOBS.values().removeIf(Motion::tick);
		if (!IMPACTS.isEmpty()) {
			var run = new java.util.ArrayList<>(IMPACTS);
			IMPACTS.clear();
			run.forEach(Runnable::run);
		}
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		JOBS.remove(event.getEntity().getUUID());
	}

	public static void resetAll() {
		JOBS.clear();
		IMPACTS.clear();
	}
}
