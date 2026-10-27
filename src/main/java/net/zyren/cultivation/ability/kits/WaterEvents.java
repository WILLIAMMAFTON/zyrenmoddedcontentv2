package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Water's world interactions: Feather Falling + Reinforce fall immunity, Water Surge's Fire-aspect
 * resistance, the Domain Expansion flood (drains later), Did I Say's launch and fire extinguishing.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class WaterEvents {
	/** Burning within this long after a Fire-aspect hit counts as a Fire-aspect effect. */
	private static final int FIRE_HIT_MEMORY_TICKS = 200;
	private static final int MAX_FIRES_PER_TICK = 4096;

	private record Flood(ServerLevel level, List<BlockPos> placed, long drainTick, BlockPos center, int reach, java.util.Set<Long> waterBefore) {
	}

	private static final class Flight {
		UUID target;
		int ticksLeft;
	}

	private static final class Extinguish {
		final ServerLevel level;
		final BlockPos center;
		final int radius;
		final long total;
		long cursor;

		Extinguish(ServerLevel level, BlockPos center, int radius) {
			this.level = level;
			this.center = center;
			this.radius = radius;
			long side = 2L * radius + 1;
			this.total = side * side * side;
		}
	}

	private static final Map<UUID, Long> SURGE_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> LAST_FIRE_HIT = new HashMap<>();
	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	private static final List<Flood> FLOODS = new ArrayList<>();
	private static final List<Extinguish> EXTINGUISH = new ArrayList<>();

	private WaterEvents() {
	}

	// ------------------------------------------------------------------ falls

	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || AspectData.of(player).getActive() != WaterKit.W)
			return;
		long now = player.serverLevel().getGameTime();
		AbilityState st = AbilityState.of(player);
		if (st.hasBuff(QiEffects.reinforceId(WaterKit.W), now)) {
			event.setCanceled(true); // Reinforce: immune to fall damage
			return;
		}
		if (st.passiveActive()) {
			int ff = AbilityConfig.WATER_PASSIVE_FEATHER_FALLING.get();
			event.setDamageMultiplier(event.getDamageMultiplier() * (float) Math.max(0, 1 - 0.12 * ff));
		}
	}

	// ------------------------------------------------------------------ Water Surge: Fire-aspect resistance

	static void startSurge(ServerPlayer player, int ticks) {
		SURGE_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + ticks);
	}

	private static double fireResistance(ServerPlayer p) {
		Long until = SURGE_UNTIL.get(p.getUUID());
		long now = p.serverLevel().getGameTime();
		if (until == null || now >= until)
			return 0;
		// Part of Water Surge: gone when Water is switched off or nullified (like its other effects).
		if (AspectData.of(p).getActive() != WaterKit.W || AbilityState.of(p).isNullified(WaterKit.W, now))
			return 0;
		return Math.min(1.0, AbilityConfig.WATER_PB1_FIRE_RESIST_LEVEL.get() * AbilityConfig.WATER_PB1_FIRE_RESIST_PER_LEVEL.get());
	}

	/**
	 * Fire-aspect powers = Qi damage or empowered hits from a Fire-active player, plus burning shortly
	 * after such a hit (Fire abilities set targets aflame).
	 */
	@SubscribeEvent
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer victim))
			return;
		long now = victim.serverLevel().getGameTime();
		boolean fromFire = false;
		if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != victim
				&& AspectData.of(attacker).getActive() == AspectType.FIRE) {
			var armed = AbilityState.of(attacker).empowered(now);
			fromFire = QiEffects.isDealingQiDamage() || armed != null && armed.aspect() == AspectType.FIRE;
			if (fromFire)
				LAST_FIRE_HIT.put(victim.getUUID(), now);
		} else if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
			Long last = LAST_FIRE_HIT.get(victim.getUUID());
			fromFire = last != null && now - last <= FIRE_HIT_MEMORY_TICKS;
		}
		if (!fromFire)
			return;
		double res = fireResistance(victim);
		if (res >= 1.0) {
			event.setCanceled(true);
			victim.clearFire();
		} else if (res > 0) {
			event.setAmount((float) (event.getAmount() * (1 - res)));
		}
	}

	// ------------------------------------------------------------------ Domain Expansion flood

	/** One-block-high water on the ground around the player; drained later. @return blocks placed */
	static int flood(ServerPlayer player, double radius) {
		ServerLevel level = player.serverLevel();
		BlockPos c = player.blockPosition();
		int r = (int) Math.ceil(radius);
		List<BlockPos> placed = new ArrayList<>();
		// Remember the water that was already here: placed sources can spawn new ones (vanilla's
		// infinite-water rule) and flow up to 8 blocks out; the drain removes everything else.
		int reach = r + FLOW_MARGIN;
		java.util.Set<Long> before = new java.util.HashSet<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -reach; dx <= reach; dx++)
			for (int dz = -reach; dz <= reach; dz++)
				for (int dy = DY_MIN; dy <= DY_MAX; dy++) {
					m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (level.getChunkSource().hasChunk(m.getX() >> 4, m.getZ() >> 4) && level.getFluidState(m).is(net.minecraft.tags.FluidTags.WATER))
						before.add(m.asLong());
				}
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > radius * radius)
					continue;
				for (int dy = 4; dy >= -6; dy--) { // first air block resting on solid ground near your height
					BlockPos p = c.offset(dx, dy, dz);
					BlockPos below = p.below();
					if (level.getBlockState(p).isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
						level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
						placed.add(p);
						break;
					}
				}
			}
		FLOODS.add(new Flood(level, placed, level.getGameTime() + AbilityConfig.WATER_PB2_FLOOD_TICKS.get(), c, reach, before));
		return placed.size();
	}

	private static final int FLOW_MARGIN = 8, DY_MIN = -16, DY_MAX = 5;

	/** Removes the flood: every water block in the area that wasn't there before the cast. */
	private static void drain(Flood f) {
		ServerLevel level = f.level();
		BlockPos c = f.center();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -f.reach(); dx <= f.reach(); dx++)
			for (int dz = -f.reach(); dz <= f.reach(); dz++)
				for (int dy = DY_MIN; dy <= DY_MAX; dy++) {
					m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (f.waterBefore().contains(m.asLong()) || !level.getChunkSource().hasChunk(m.getX() >> 4, m.getZ() >> 4))
						continue;
					BlockState s = level.getBlockState(m);
					if (s.is(Blocks.WATER)) // water blocks only (not waterlogged blocks)
						level.setBlock(m, Blocks.AIR.defaultBlockState(), 3); // with updates, so any stray flow beyond the area recedes
				}
	}

	// ------------------------------------------------------------------ Did I Say: launch + extinguish

	static void launchAt(ServerPlayer player, ServerPlayer target) {
		Flight f = new Flight();
		f.target = target.getUUID();
		f.ticksLeft = AbilityConfig.WATER_A2_MAX_FLIGHT_TICKS.get();
		FLIGHTS.put(player.getUUID(), f);
	}

	private static void tickFlight(ServerPlayer player, Flight f) {
		ServerPlayer target = player.server.getPlayerList().getPlayer(f.target);
		if (target == null || !Targeting.isValid(player, target) || f.ticksLeft-- <= 0
				|| player.distanceTo(target) <= AbilityConfig.WATER_A2_CONTACT_RANGE.get()) {
			FLIGHTS.remove(player.getUUID());
			player.setDeltaMovement(Vec3.ZERO);
			player.hurtMarked = true;
			player.fallDistance = 0;
			return;
		}
		Vec3 aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(player.position().add(0, player.getBbHeight() / 2, 0));
		player.setDeltaMovement(aim.normalize().scale(AbilityConfig.WATER_A2_SPEED.get()));
		player.hurtMarked = true;
		player.fallDistance = 0;
	}

	/** Puts out burning entities now and fire blocks over the next few seconds. */
	static void extinguish(ServerPlayer player, double radius) {
		ServerLevel level = player.serverLevel();
		AABB box = player.getBoundingBox().inflate(radius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isOnFire() && e.distanceTo(player) <= radius))
			e.clearFire();
		EXTINGUISH.add(new Extinguish(level, player.blockPosition(), (int) Math.round(radius)));
	}

	private static boolean runExtinguish(Extinguish job) {
		int budget = AbilityConfig.WATER_A2_BLOCKS_PER_TICK.get();
		int changes = 0;
		int side = 2 * job.radius + 1;
		long r2 = (long) job.radius * job.radius;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (job.cursor < job.total && budget-- > 0 && changes < MAX_FIRES_PER_TICK) {
			long i = job.cursor++;
			int dy = (int) (i / ((long) side * side)) - job.radius;
			int dx = (int) (i / side % side) - job.radius;
			int dz = (int) (i % side) - job.radius;
			if ((long) dx * dx + (long) dy * dy + (long) dz * dz > r2)
				continue;
			pos.set(job.center.getX() + dx, job.center.getY() + dy, job.center.getZ() + dz);
			if (!job.level.isInWorldBounds(pos) || !job.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4))
				continue;
			BlockState s = job.level.getBlockState(pos);
			if (s.is(BlockTags.FIRE)) {
				job.level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), 3);
				changes++;
			}
		}
		return job.cursor >= job.total;
	}

	// ------------------------------------------------------------------ ticks + cleanup

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (!(event.getLevel() instanceof ServerLevel level))
			return;
		if (!FLOODS.isEmpty()) {
			long now = level.getGameTime();
			FLOODS.removeIf(f -> {
				if (f.level() != level || now < f.drainTick())
					return false;
				drain(f);
				return true;
			});
		}
		if (!EXTINGUISH.isEmpty())
			EXTINGUISH.removeIf(job -> job.level == level && runExtinguish(job));
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			Flight f = FLIGHTS.get(player.getUUID());
			if (f != null)
				tickFlight(player, f);
		}
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		FLOODS.clear(); // note: a flood still standing at shutdown stays (saved with the world)
		EXTINGUISH.clear();
		SURGE_UNTIL.clear();
		LAST_FIRE_HIT.clear();
		FLIGHTS.clear();
	}

	private static void clear(UUID id) {
		SURGE_UNTIL.remove(id);
		LAST_FIRE_HIT.remove(id);
		FLIGHTS.remove(id);
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

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		SURGE_UNTIL.clear();
		LAST_FIRE_HIT.clear();
		FLIGHTS.clear();
		FLOODS.clear();
		EXTINGUISH.clear();
	}
}
