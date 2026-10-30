package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import com.mojang.datafixers.util.Pair;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiCostDebuffs;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.SearchThrottle;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.WaterConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.AspectMastery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Event side of the Water DEFAULT kit (WaterKit), plus helpers the sub-aspects share:
 * pushes / pulls (velocity-based, so solid blocks stop them), temporary floods that drain, the
 * passive's 10 s re-activation cooldown, Water fists, Water seeker, the two auras, Reinforce fall
 * immunity. Everything checks its ability is still CURRENT (KitResolver.isCurrent), so a form
 * toggle / aspect switch ends it.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class WaterEvents {
	static final AspectType W = AspectType.WATER;

	private WaterEvents() {
	}

	// ================================================================== passive: re-activation cooldown

	private static final Set<UUID> PASSIVE_ON = new HashSet<>();
	private static final Map<UUID, Long> PASSIVE_LOCK = new HashMap<>();

	/** In water (rain doesn't count), and not within 10 s of last leaving it. Called once a second. */
	static boolean passiveCondition(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = p.serverLevel().getGameTime();
		if (!p.isInWater()) {
			if (PASSIVE_ON.remove(id))
				PASSIVE_LOCK.put(id, now + WaterKit.seconds(WaterConfig.PASSIVE_COOLDOWN.get()));
			return false;
		}
		Long lock = PASSIVE_LOCK.get(id);
		if (lock != null && now < lock)
			return false;
		PASSIVE_LOCK.remove(id);
		PASSIVE_ON.add(id);
		return true;
	}

	// ================================================================== Reinforce: fall immunity

	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && AspectData.of(player).getActive() == W
				&& AbilityState.of(player).hasBuff(QiEffects.reinforceId(W), player.serverLevel().getGameTime()))
			event.setCanceled(true);
	}

	// ================================================================== pushes and pulls (shared core Motion)

	/** Throw {@code target} horizontally away from {@code from} for about {@code blocks}. */
	static void push(LivingEntity target, Vec3 from, double blocks) {
		net.zyren.cultivation.ability.core.Motion.push(target, from, blocks, WaterConfig.PUSH_SPEED.get(), 0, null);
	}

	/** Drag {@code target} up to {@code blocks} toward {@code anchor} (stops next to it, or at a solid block). */
	static void pullToward(LivingEntity target, Entity anchor, double blocks) {
		net.zyren.cultivation.ability.core.Motion.pullToward(target, anchor, blocks, WaterConfig.PULL_SPEED.get());
	}

	/** Drag {@code target} up to {@code blocks} toward a fixed point. */
	static void pullToward(LivingEntity target, Vec3 point, double blocks) {
		net.zyren.cultivation.ability.core.Motion.pullToward(target, point, blocks, WaterConfig.PULL_SPEED.get());
	}

	// ================================================================== temporary floods

	private record Flood(ServerLevel level, long drainTick, BlockPos center, int reach, Set<Long> waterBefore) {
	}

	private static final List<Flood> FLOODS = new ArrayList<>();
	private static final int FLOW_MARGIN = 8, DY_MIN = -16, DY_MAX = 5;

	/** One-block-high water on the ground around the player, drained after {@code ticks}. @return blocks placed */
	static int flood(ServerPlayer player, double radius, int ticks) {
		ServerLevel level = player.serverLevel();
		BlockPos c = player.blockPosition();
		int r = (int) Math.ceil(radius);
		// Remember the water that was already here: placed sources can spread; the drain removes everything else.
		int reach = r + FLOW_MARGIN;
		Set<Long> before = new HashSet<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -reach; dx <= reach; dx++)
			for (int dz = -reach; dz <= reach; dz++) {
				if (!level.getChunkSource().hasChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4))
					continue;
				for (int dy = DY_MIN; dy <= DY_MAX; dy++) {
					m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (level.getFluidState(m).is(FluidTags.WATER))
						before.add(m.asLong());
				}
			}
		int placed = 0;
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > radius * radius || !level.getChunkSource().hasChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4))
					continue;
				for (int dy = 4; dy >= -6; dy--) { // first air block resting on solid ground near your height
					BlockPos p = c.offset(dx, dy, dz);
					BlockPos below = p.below();
					if (level.getBlockState(p).isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
						level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
						placed++;
						break;
					}
				}
			}
		FLOODS.add(new Flood(level, level.getGameTime() + ticks, c, reach, before));
		return placed;
	}

	/** Removes every water block in the area that wasn't there before the flood. */
	private static void drain(Flood f) {
		ServerLevel level = f.level();
		BlockPos c = f.center();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -f.reach(); dx <= f.reach(); dx++)
			for (int dz = -f.reach(); dz <= f.reach(); dz++) {
				if (!level.getChunkSource().hasChunk((c.getX() + dx) >> 4, (c.getZ() + dz) >> 4))
					continue;
				for (int dy = DY_MIN; dy <= DY_MAX; dy++) {
					m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (f.waterBefore().contains(m.asLong()))
						continue;
					if (level.getBlockState(m).is(Blocks.WATER)) // water blocks only (not waterlogged blocks)
						level.setBlock(m, Blocks.AIR.defaultBlockState(), 3);
				}
			}
	}

	// ================================================================== Attack 1: water fists

	static final String FISTS_BUFF = "water_fists";
	private static final Map<UUID, Integer> FIST_CHARGES = new HashMap<>();

	static void startFists(ServerPlayer p, int hits) {
		AbilityState.of(p).addBuff(new Buff(FISTS_BUFF, W, 1.0, 0, false, Long.MAX_VALUE));
		FIST_CHARGES.put(p.getUUID(), hits);
	}

	static int fistCharges(ServerPlayer p) {
		Integer n = FIST_CHARGES.get(p.getUUID());
		if (n == null)
			return 0;
		long now = p.serverLevel().getGameTime();
		if (!AbilityState.of(p).hasBuff(FISTS_BUFF, now) || !KitResolver.isCurrent(p, WaterKit.FISTS) || AbilityState.of(p).isNullified(W, now)) {
			FIST_CHARGES.remove(p.getUUID());
			return 0;
		}
		return n;
	}

	static boolean isWeapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.TRIDENT) || s.is(Items.MACE);
	}

	static boolean isMelee(net.minecraft.world.damagesource.DamageSource src, ServerPlayer attacker) {
		return src.getDirectEntity() == attacker && src.is(DamageTypes.PLAYER_ATTACK) && !QiEffects.isDealingQiDamage();
	}

	/** After empowered hits (HIGHEST), before output multipliers (LOW). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (!isMelee(event.getSource(), attacker) || AspectData.of(attacker).getActive() != W)
			return;
		int charges = fistCharges(attacker);
		if (charges <= 0)
			return;
		ItemStack held = attacker.getMainHandItem();
		double extra;
		if (held.isEmpty())
			extra = WaterConfig.A1_UNARMED_EXTRA.get();
		else if (isWeapon(held))
			extra = WaterConfig.A1_WEAPON_EXTRA.get();
		else
			return; // other items: no bonus, no charge used
		event.setNewDamage(event.getNewDamage() + (float) extra);
		AspectMastery.onAbilityHit(attacker, W, target);
		if (--charges > 0) {
			FIST_CHARGES.put(attacker.getUUID(), charges);
		} else {
			FIST_CHARGES.remove(attacker.getUUID());
			AbilityState.of(attacker).removeBuff(FISTS_BUFF);
		}
	}

	// ================================================================== Powerblast 2: water seeker

	private record Seek(ServerLevel level, double x, double y, double z, String what) {
	}

	private static final Map<UUID, Seek> SEEKS = new HashMap<>();

	/** Picks the destination (checkUse). Refused, no Qi spent, if there is nowhere to go. */
	static ActionResult planSeek(ServerPlayer p) {
		SEEKS.remove(p.getUUID());
		BlockPos water = nearestSafeWater(p);
		if (water != null) {
			SEEKS.put(p.getUUID(), new Seek(p.serverLevel(), water.getX() + 0.5, water.getY(), water.getZ() + 0.5, "water"));
			return ActionResult.ok("");
		}
		ServerPlayer ally = null;
		int ar = WaterConfig.PB2_ALLY_RADIUS.get();
		for (ServerPlayer o : p.serverLevel().players())
			if (o != p && Targeting.isValid(p, o) && Targeting.isAllied(p, o) && AspectData.of(o).getActive() == W && o.distanceTo(p) <= ar
					&& (ally == null || o.distanceToSqr(p) < ally.distanceToSqr(p)))
				ally = o;
		if (ally != null) {
			SEEKS.put(p.getUUID(), new Seek(p.serverLevel(), ally.getX(), ally.getY(), ally.getZ(), ally.getGameProfile().getName()));
			return ActionResult.ok("");
		}
		if (SearchThrottle.blocked(p, "water_seeker"))
			return ActionResult.fail("No water anywhere near. Try again in a few seconds.");
		ServerLevel level = p.serverLevel();
		Pair<BlockPos, Holder<Biome>> ocean = level.findClosestBiome3d(b -> b.is(BiomeTags.IS_OCEAN), p.blockPosition(),
				WaterConfig.PB2_OCEAN_RADIUS.get(), 32, 64);
		if (ocean == null) {
			SearchThrottle.failed(p, "water_seeker", 100);
			return ActionResult.fail("No water, no Water ally and no ocean within reach.");
		}
		BlockPos o = ocean.getFirst();
		level.getChunk(o.getX() >> 4, o.getZ() >> 4); // load it so the surface height is real
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, o.getX(), o.getZ());
		SEEKS.put(p.getUUID(), new Seek(level, o.getX() + 0.5, y, o.getZ() + 0.5, "the ocean"));
		return ActionResult.ok("");
	}

	static ActionResult executeSeek(ServerPlayer p) {
		Seek s = SEEKS.remove(p.getUUID());
		if (s == null)
			return ActionResult.fail("Lost track of the water.");
		if (s.level() == p.serverLevel())
			p.teleportTo(s.x(), s.y(), s.z());
		else
			p.teleportTo(s.level(), s.x(), s.y(), s.z(), p.getYRot(), p.getXRot());
		p.fallDistance = 0;
		p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_SPLASH, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
		return ActionResult.ok("Water seeker: to " + s.what() + ".");
	}

	/**
	 * Nearest loaded water SOURCE within the radius (vertical search limited, see config) with no unallied
	 * player within enemy_clear_radius of it and no solid block above it. Searches outward in square rings
	 * and stops once no closer spot can exist.
	 */
	private static BlockPos nearestSafeWater(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		int r = WaterConfig.PB2_WATER_RADIUS.get(), v = WaterConfig.PB2_VERTICAL.get();
		double clear = WaterConfig.PB2_ENEMY_CLEAR_RADIUS.get();
		List<ServerPlayer> enemies = WaterKit.unalliedWithin(p, r + clear + 1);
		BlockPos c = p.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int ring = 0; ring <= r; ring++) {
			if (best != null && (double) ring * ring > bestD)
				break; // every remaining spot is farther away than the best one
			for (int dx = -ring; dx <= ring; dx++)
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring)
						continue;
					int x = c.getX() + dx, z = c.getZ() + dz;
					if (!level.getChunkSource().hasChunk(x >> 4, z >> 4))
						continue;
					for (int dy = -v; dy <= v; dy++) {
						double d = (double) dx * dx + (double) dy * dy + (double) dz * dz;
						if (d > (double) r * r || d >= bestD || ring == 0 && dy == 0)
							continue;
						m.set(x, c.getY() + dy, z);
						FluidState f = level.getFluidState(m);
						if (!f.is(FluidTags.WATER) || !f.isSource())
							continue;
						BlockState above = level.getBlockState(m.above());
						if (above.isSuffocating(level, m.above()))
							continue;
						Vec3 spot = Vec3.atCenterOf(m);
						boolean safe = true;
						for (ServerPlayer e : enemies)
							if (e.position().distanceTo(spot) <= clear) {
								safe = false;
								break;
							}
						if (!safe)
							continue;
						best = m.immutable();
						bestD = d;
					}
				}
		}
		return best;
	}

	// ================================================================== Ability 1 / 2 auras

	/** Caster -> players their Oceanic Doom has nullified (first time shows the message, later refreshes are silent). */
	private static final Map<UUID, Set<UUID>> DOOMED = new HashMap<>();

	private static boolean running(ServerPlayer p, net.zyren.cultivation.ability.core.Ability a) {
		return AbilityState.of(p).isSustained(a) && KitResolver.isCurrent(p, a);
	}

	private static void tickMyArea(ServerPlayer p, long now) {
		int r = WaterConfig.AB1_RADIUS.get();
		double discount = WaterConfig.AB1_WATER_DISCOUNT.get();
		for (ServerPlayer o : p.serverLevel().players()) {
			if (!o.isAlive() || o.isSpectator() || !Targeting.isAllied(p, o) || o.distanceTo(p) > r)
				continue;
			EffectHelper.apply(o, MobEffects.DAMAGE_BOOST, WaterConfig.AB1_STRENGTH.get(), 50, true);
			EffectHelper.applyPassive(o, MobEffects.REGENERATION, WaterConfig.AB1_REGEN.get());
			EffectHelper.apply(o, MobEffects.MOVEMENT_SPEED, WaterConfig.AB1_SPEED.get(), 50, true);
			if (AspectData.of(o).getActive() == W)
				QiCostDebuffs.discount(o, discount, 40, now);
		}
	}

	private static void tickOceanicDoom(ServerPlayer p, long now) {
		int r = WaterConfig.AB2_NULLIFY_RADIUS.get();
		Set<UUID> doomed = DOOMED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (ServerPlayer o : WaterKit.unalliedWithin(p, r)) {
			AspectType a = AspectData.of(o).getActive();
			boolean fire = a == AspectType.FIRE;
			boolean earth = a == AspectType.EARTH && QiEffects.canAffect(o, false); // Reinforce protects Earth, not Fire
			if (!fire && !earth)
				continue;
			if (doomed.add(o.getUUID()))
				QiEffects.nullify(o, a, 40);
			else
				AbilityState.of(o).nullify(a, now + 40); // silent refresh while they stay inside
		}
	}

	static void endOceanicDoom(ServerPlayer p) {
		DOOMED.remove(p.getUUID());
		TimedAttributes.remove(p, WaterKit.OceanicDoom.FLIGHT);
		var str = p.getEffect(MobEffects.DAMAGE_BOOST);
		if (str != null && str.getAmplifier() == WaterConfig.AB2_STRENGTH.get() - 1)
			p.removeEffect(MobEffects.DAMAGE_BOOST);
		if (!p.onGround())
			FireEvents.noFallDamage(p); // flight ended mid-air
	}

	// ================================================================== ticks

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		long now = p.serverLevel().getGameTime();
		if ((now + p.getId()) % 10 == 0 && running(p, WaterKit.OCEANIC_DOOM))
			tickOceanicDoom(p, now);
		if ((now + p.getId()) % 20 == 0 && running(p, WaterKit.MY_AREA))
			tickMyArea(p, now);
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (!(event.getLevel() instanceof ServerLevel level) || FLOODS.isEmpty())
			return;
		long now = level.getGameTime();
		FLOODS.removeIf(f -> {
			if (f.level() != level || now < f.drainTick())
				return false;
			drain(f);
			return true;
		});
	}

	// ================================================================== cleanup

	private static void clear(UUID id) {
		FIST_CHARGES.remove(id);
		SEEKS.remove(id);
		DOOMED.remove(id);
		PASSIVE_ON.remove(id);
		PASSIVE_LOCK.remove(id);
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

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. A flood standing at shutdown stays (saved with the world). */
	public static void resetAll() {
		FIST_CHARGES.clear();
		SEEKS.clear();
		DOOMED.clear();
		PASSIVE_ON.clear();
		PASSIVE_LOCK.clear();
		FLOODS.clear();
	}
}
