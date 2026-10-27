package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Physical's world interactions: environment conditions, Bomb explosions and craters, the
 * Yeet throw and the Sky High jump. Kept out of {@link PhysicalKit} so each piece stays small.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class PhysicalEvents {
	private static final Map<UUID, Long> LAST_STRIKE = new HashMap<>();
	private static final Map<UUID, YeetFlight> YEETS = new HashMap<>();
	private static final Map<UUID, SkyJump> SKY = new HashMap<>();
	private static final Map<UUID, Long> PASSIVE_OFF_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> DOMAIN_KNOCKBACK_UNTIL = new HashMap<>();
	/** Blocks broken per tick by Yeet, so a throw through a mountain can't stall the server. */
	private static final int MAX_BLOCKS_PER_TICK = 48;

	private record YeetFlight(UUID target, int ticksLeft) {
	}

	private static final class SkyJump {
		boolean falling;
		int ticks;
		double lastY;
		int stuckTicks;
	}

	private PhysicalEvents() {
	}

	// ------------------------------------------------------------------ environment

	/** Number of environmental conditions (lava, lightning strike, underwater): 0-3. */
	static int environmentConditions(ServerPlayer player) {
		int n = 0;
		if (player.isInLava())
			n++;
		if (recentlyStruck(player))
			n++;
		if (player.isUnderWater())
			n++;
		return n;
	}

	static boolean recentlyStruck(ServerPlayer player) {
		Long t = LAST_STRIKE.get(player.getUUID());
		return t != null && player.serverLevel().getGameTime() - t <= AbilityConfig.PHYSICAL_LIGHTNING_WINDOW_TICKS.get();
	}

	@SubscribeEvent
	public static void onStruck(EntityStruckByLightningEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			LAST_STRIKE.put(player.getUUID(), player.serverLevel().getGameTime());
	}

	// ------------------------------------------------------------------ Bomb

	/** Every melee hit explodes while Bomb is active. */
	@SubscribeEvent
	public static void onHitLanded(LivingDamageEvent.Post event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player)
			return;
		boolean melee = event.getSource().is(DamageTypes.PLAYER_ATTACK) && event.getSource().getDirectEntity() == player && !QiEffects.isDealingQiDamage();
		Long kbUntil = DOMAIN_KNOCKBACK_UNTIL.get(player.getUUID());
		if (melee && kbUntil != null && player.serverLevel().getGameTime() < kbUntil && isPhysicalActive(player)) // off while nullified/switched
			QiEffects.knockback(event.getEntity(), player, AbilityConfig.PHYSICAL_ABILITY2_KNOCKBACK_LEVEL.get(), false);
		if (melee && PhysicalKit.isBombActive(player) && isPhysicalActive(player)) {
			Vec3 p = event.getEntity().position();
			explode(player, p, AbilityConfig.PHYSICAL_HEAVY_EXPLOSION_POWER.get().floatValue());
		}
	}

	/** Bomb: the user takes no damage from their own explosions. */
	@SubscribeEvent
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getSource().is(DamageTypeTags.IS_EXPLOSION)
				&& event.getSource().getEntity() == player && PhysicalKit.isBombActive(player))
			event.setCanceled(true);
	}

	/**
	 * Heavy/Bomb explosions are Qi abilities: record them as Qi used on the target (no hit XP), so
	 * kill credit and the "killed a cultivator without using Qi" reward see them. (The user's own
	 * explosion is direct-sourced by the player; TNT/crystals are not.)
	 */
	@SubscribeEvent
	public static void onExplosionHit(LivingDamageEvent.Post event) {
		if (event.getNewDamage() > 0 && event.getSource().is(DamageTypeTags.IS_EXPLOSION)
				&& event.getSource().getDirectEntity() instanceof ServerPlayer player && event.getSource().getEntity() == player
				&& event.getEntity() != player && AspectData.of(player).getActive() == PhysicalKit.P)
			net.zyren.cultivation.system.AspectMastery.recordQiHit(player, PhysicalKit.P, event.getEntity());
	}

	private static void explode(ServerPlayer player, Vec3 at, float power) {
		if (power <= 0)
			return;
		Level.ExplosionInteraction mode = AbilityConfig.PHYSICAL_HEAVY_BREAKS_BLOCKS.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
		player.serverLevel().explode(player, at.x, at.y, at.z, power, mode);
	}

	// ------------------------------------------------------------------ landings

	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		float distance = event.getDistance();

		// Sky High landing: hit whoever you land on, no fall damage for the user.
		SkyJump jump = SKY.get(player.getUUID());
		if (jump != null && jump.falling) {
			SKY.remove(player.getUUID());
			event.setDamageMultiplier(0);
			float damage = (float) (AbilityConfig.PHYSICAL_PB2_DAMAGE_PER_BLOCK.get() * Mth.floor(distance));
			double r = AbilityConfig.PHYSICAL_PB2_HIT_RADIUS.get();
			AABB box = player.getBoundingBox().inflate(r, 1.0, r);
			boolean hit = false;
			for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, box,
					o -> Targeting.isValid(player, o) && !Targeting.isAllied(player, o)))
				hit |= QiEffects.dealDamage(player, other, damage);
			if (hit) // balancing: the user's own Physical passive is nullified for a while
				PASSIVE_OFF_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + AbilityConfig.PHYSICAL_PB2_PASSIVE_NULLIFY_TICKS.get());
			player.serverLevel().sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 6, 1, 0.2, 1, 0);
		}

		// Bomb: falls become craters sized by the fall instead of fall damage.
		if (PhysicalKit.isBombActive(player) && isPhysicalActive(player)) {
			event.setDamageMultiplier(0);
			int radius = Math.min(AbilityConfig.PHYSICAL_HEAVY_MAX_CRATER_RADIUS.get(),
					Mth.floor(distance / AbilityConfig.PHYSICAL_HEAVY_BLOCKS_PER_CRATER_RADIUS.get()));
			if (radius >= 1)
				explode(player, player.position(), radius);
		}
	}

	// ------------------------------------------------------------------ Yeet / Sky High

	/** True while the user's own Physical passive is nullified after a Sky High hit. */
	static boolean passiveSuppressed(ServerPlayer player) {
		Long until = PASSIVE_OFF_UNTIL.get(player.getUUID());
		return until != null && player.serverLevel().getGameTime() < until;
	}

	static void startDomainKnockback(ServerPlayer player, int ticks) {
		if (ticks > 0)
			DOMAIN_KNOCKBACK_UNTIL.put(player.getUUID(), player.serverLevel().getGameTime() + ticks);
	}

	static void startYeet(ServerPlayer player, ServerPlayer target) {
		YEETS.put(player.getUUID(), new YeetFlight(target.getUUID(), AbilityConfig.PHYSICAL_PB1_MAX_FLIGHT_TICKS.get()));
	}

	static void startSkyHigh(ServerPlayer player) {
		SkyJump j = new SkyJump();
		j.lastY = player.getY();
		SKY.put(player.getUUID(), j);
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		YeetFlight y = YEETS.get(player.getUUID());
		if (y != null)
			tickYeet(player, y);
		SkyJump j = SKY.get(player.getUUID());
		if (j != null)
			tickSky(player, j);
	}

	private static void tickYeet(ServerPlayer player, YeetFlight flight) {
		ServerPlayer target = player.server.getPlayerList().getPlayer(flight.target());
		if (target == null || !Targeting.isValid(player, target) || flight.ticksLeft() <= 0) {
			YEETS.remove(player.getUUID());
			return;
		}
		if (player.distanceTo(target) <= AbilityConfig.PHYSICAL_PB1_CONTACT_RANGE.get()) {
			YEETS.remove(player.getUUID());
			QiEffects.dealDamage(player, target, AbilityConfig.PHYSICAL_PB1_DAMAGE.get().floatValue());
			player.setDeltaMovement(Vec3.ZERO);
			player.hurtMarked = true;
			return;
		}
		Vec3 aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(player.position().add(0, player.getBbHeight() / 2, 0));
		Vec3 velocity = aim.normalize().scale(AbilityConfig.PHYSICAL_PB1_SPEED.get());
		breakBlocksAlong(player, player.getBoundingBox().expandTowards(velocity).inflate(0.2));
		player.setDeltaMovement(velocity);
		player.hurtMarked = true;
		player.fallDistance = 0;
		YEETS.put(player.getUUID(), new YeetFlight(flight.target(), flight.ticksLeft() - 1));
	}

	/** "Break all blocks in path": everything breakable (not bedrock or other unbreakable blocks). */
	private static void breakBlocksAlong(ServerPlayer player, AABB box) {
		ServerLevel level = player.serverLevel();
		int broken = 0;
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
				Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
			if (broken >= MAX_BLOCKS_PER_TICK)
				return;
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty())
				continue;
			if (state.getDestroySpeed(level, pos) < 0)
				continue; // unbreakable
			level.destroyBlock(pos.immutable(), true, player);
			broken++;
		}
	}

	private static void tickSky(ServerPlayer player, SkyJump j) {
		j.ticks++;
		if (j.ticks > 1200) { // safety: give up after 60 s
			SKY.remove(player.getUUID());
			return;
		}
		if (j.falling) {
			// Landing in water/lava (or flying) fires no fall event: end the jump here, or the next
			// ordinary fall within 60 s would count as the Sky High landing.
			if (player.isInWater() || player.isInLava() || player.getAbilities().flying || player.onClimbable())
				SKY.remove(player.getUUID());
			return;
		}
		int skyLimit = player.serverLevel().getMaxBuildHeight();
		j.stuckTicks = player.getY() - j.lastY < 0.1 ? j.stuckTicks + 1 : 0;
		j.lastY = player.getY();
		if (player.getY() >= skyLimit - 2 || j.stuckTicks > 10) {
			j.falling = true; // reached the sky limit (or a ceiling): now fall
			player.fallDistance = 0;
			return;
		}
		player.setDeltaMovement(0, AbilityConfig.PHYSICAL_PB2_ASCEND_SPEED.get(), 0);
		player.hurtMarked = true;
		player.fallDistance = 0;
	}

	// ------------------------------------------------------------------ cleanup

	private static boolean isPhysicalActive(ServerPlayer player) {
		return AspectData.of(player).getActive() == AspectType.PHYSICAL
				&& !net.zyren.cultivation.ability.core.AbilityState.of(player).isNullified(AspectType.PHYSICAL, player.serverLevel().getGameTime());
	}

	private static void clear(UUID id) {
		YEETS.remove(id);
		SKY.remove(id);
		LAST_STRIKE.remove(id);
		DOMAIN_KNOCKBACK_UNTIL.remove(id);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			clear(player.getUUID());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity().getUUID());
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		LAST_STRIKE.clear();
		YEETS.clear();
		SKY.clear();
		PASSIVE_OFF_UNTIL.clear();
		DOMAIN_KNOCKBACK_UNTIL.clear();
	}
}
