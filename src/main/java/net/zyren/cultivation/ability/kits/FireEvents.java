package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.SearchThrottle;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.FireConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.network.SensedPlayersMessage;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fire DEFAULT kit: everything that happens outside a single cast (FireKit).
 *   - Attack 1 charges (tied to the buff "fire_fists", so form change / nullify / switch end them)
 *   - critical-hit tracking for ENHANCED FLAMES
 *   - Burning retreat planning (also used by METEOR for free)
 *   - Hell on Earth Qi discount, METEOR no-fall
 *   - passive sensing of Earth->Magma / Electric->Plasma users (HUD packet)
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class FireEvents {
	static final String FISTS_BUFF = "fire_fists";

	private static final Map<UUID, Integer> FIST_CHARGES = new HashMap<>();
	private static final Map<UUID, Long> LAST_CRIT = new HashMap<>();
	private static final Map<UUID, Long> HELL_DISCOUNT_UNTIL = new HashMap<>();
	private static final Set<UUID> NO_FALL = new HashSet<>();
	private static final Map<UUID, Plan> PLANS = new HashMap<>();
	/** Players who were last sent a non-empty sensing list (so an empty one clears their HUD once). */
	private static final Set<UUID> SENSING = new HashSet<>();

	private FireEvents() {
	}

	// ------------------------------------------------------------------ Attack 1: fists of flames

	static void startFists(ServerPlayer player, int hits) {
		int limit = FireConfig.A1_TIME_LIMIT.get();
		long end = limit > 0 ? player.serverLevel().getGameTime() + limit * 20L : Long.MAX_VALUE;
		AbilityState.of(player).addBuff(new Buff(FISTS_BUFF, AspectType.FIRE, 1.0, 0, false, end));
		FIST_CHARGES.put(player.getUUID(), hits);
	}

	/** Charges left (0 if the buff ended for any reason: time limit, form change, nullify, switch, death). */
	static int fistCharges(ServerPlayer player) {
		Integer n = FIST_CHARGES.get(player.getUUID());
		if (n == null)
			return 0;
		if (!AbilityState.of(player).hasBuff(FISTS_BUFF, player.serverLevel().getGameTime()) || !KitResolver.isCurrent(player, FireKit.FISTS)) {
			FIST_CHARGES.remove(player.getUUID());
			return 0;
		}
		return n;
	}

	/** After the empowered-hit handler (HIGHEST), before multipliers. Unarmed (non-weapon) melee only. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (event.getSource().getDirectEntity() != attacker || !event.getSource().is(DamageTypes.PLAYER_ATTACK) || QiEffects.isDealingQiDamage())
			return;
		int charges = fistCharges(attacker);
		if (charges <= 0 || FireKit.isWeapon(attacker.getMainHandItem()))
			return;
		long now = attacker.serverLevel().getGameTime();
		if (AbilityState.of(attacker).isNullified(AspectType.FIRE, now))
			return;
		event.setNewDamage(FireConfig.A1_DAMAGE.get().floatValue());
		if (QiEffects.canAffect(target, false))
			FireKit.ignite(target);
		net.zyren.cultivation.system.AspectMastery.onAbilityHit(attacker, AspectType.FIRE, target);
		if (--charges > 0) {
			FIST_CHARGES.put(attacker.getUUID(), charges);
			return;
		}
		FIST_CHARGES.remove(attacker.getUUID());
		AbilityState st = AbilityState.of(attacker);
		st.removeBuff(FISTS_BUFF);
		st.startCooldown(FireKit.FISTS, now, FireKit.FISTS.realCooldownTicks()); // "only apply cooldown after 3rd attack"
		st.markDirty();
	}

	// ------------------------------------------------------------------ crits (ENHANCED FLAMES)

	@SubscribeEvent
	public static void onCrit(CriticalHitEvent event) {
		if (event.isCriticalHit() && event.getEntity() instanceof ServerPlayer p)
			LAST_CRIT.put(p.getUUID(), p.serverLevel().getGameTime());
	}

	static boolean wasCritThisTick(ServerPlayer p) {
		Long t = LAST_CRIT.get(p.getUUID());
		return t != null && t == p.serverLevel().getGameTime();
	}

	// ------------------------------------------------------------------ Hell on Earth discount

	static void setHellDiscount(Player p, long until) {
		HELL_DISCOUNT_UNTIL.put(p.getUUID(), until);
	}

	/** Qi taken off Fire abilities right now (Fire-active players under Hell on Earth). */
	static double hellDiscount(Player p) {
		Long until = HELL_DISCOUNT_UNTIL.get(p.getUUID());
		if (until == null || p.level().getGameTime() >= until || AspectData.of(p).getActive() != AspectType.FIRE)
			return 0;
		return FireConfig.AB1_FIRE_DISCOUNT.get();
	}

	// ------------------------------------------------------------------ METEOR: no fall damage

	static void noFallDamage(ServerPlayer p) {
		NO_FALL.add(p.getUUID());
	}

	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && NO_FALL.remove(p.getUUID()))
			event.setCanceled(true);
	}

	// ------------------------------------------------------------------ Powerblast 1: Burning retreat

	enum PlanKind {
		/** (a) flame / lava / campfire nearby, no unallied player near it */
		LOCAL,
		/** (b) from the Overworld into the Nether (next to a portal, else a flame there); +30 Qi */
		NETHER,
		/** (c) nothing found: heal only */
		FALLBACK
	}

	record Plan(PlanKind kind, ServerLevel level, BlockPos pos) {
	}

	static Plan pendingPlan(ServerPlayer p) {
		return PLANS.get(p.getUUID());
	}

	/** Decides where Burning retreat goes (called in checkUse, so the cost can depend on it). */
	static void planRetreat(ServerPlayer player) {
		ServerLevel here = player.serverLevel();
		BlockPos local = SearchThrottle.blocked(player, "fire_retreat") ? null : findFlame(player, here, player.blockPosition(), true);
		if (local != null) {
			PLANS.put(player.getUUID(), new Plan(PlanKind.LOCAL, here, local));
			return;
		}
		SearchThrottle.failed(player, "fire_retreat", 20);
		if (here.dimension() == Level.OVERWORLD) {
			ServerLevel nether = player.server.getLevel(Level.NETHER);
			if (nether != null) {
				BlockPos scaled = BlockPos.containing(player.getX() / 8.0, Math.max(nether.getMinBuildHeight() + 1, Math.min(120, player.getY())), player.getZ() / 8.0);
				BlockPos dest = findPortalSpot(nether, scaled);
				if (dest == null)
					dest = findFlame(player, nether, scaled, false); // "restart the search" in the Nether
				if (dest != null) {
					PLANS.put(player.getUUID(), new Plan(PlanKind.NETHER, nether, dest));
					return;
				}
			}
		}
		PLANS.put(player.getUUID(), new Plan(PlanKind.FALLBACK, here, null));
	}

	static ActionResult executeRetreat(ServerPlayer player) {
		Plan plan = PLANS.remove(player.getUUID());
		if (plan == null || plan.kind() == PlanKind.FALLBACK) {
			player.heal(FireConfig.PB1_FALLBACK_HEAL.get().floatValue());
			return ActionResult.ok("No flames to retreat to: you burn your wounds closed instead.");
		}
		teleportOnto(player, plan.level(), plan.pos());
		player.heal(FireConfig.PB1_HEAL.get().floatValue());
		return ActionResult.ok(plan.kind() == PlanKind.NETHER ? "You retreat through the flames into the Nether." : "You retreat into the flames.");
	}

	/** Nearest lava / campfire / fire within the search radius with no unallied player near it. */
	private static BlockPos findFlame(ServerPlayer user, ServerLevel level, BlockPos from, boolean skipOwnSpot) {
		int r = FireConfig.PB1_RADIUS.get();
		int clear = FireConfig.PB1_ENEMY_CLEAR_RADIUS.get();
		Optional<BlockPos> found = BlockPos.findClosestMatch(from, r, r, p -> {
			if (skipOwnSpot && p.distSqr(from) <= 4)
				return false;
			if (!level.isInWorldBounds(p))
				return false;
			BlockState s = level.getBlockState(p);
			if (!(s.is(BlockTags.CAMPFIRES) || s.is(BlockTags.FIRE) || s.getFluidState().is(FluidTags.LAVA)))
				return false;
			for (ServerPlayer o : level.players())
				if (o != user && o.isAlive() && !o.isSpectator() && !Targeting.isAllied(user, o) && o.blockPosition().distSqr(p) <= (double) clear * clear)
					return false;
			return true;
		});
		return found.map(BlockPos::immutable).orElse(null);
	}

	/** A standing spot beside the nearest nether portal around {@code scaled}, or null. */
	private static BlockPos findPortalSpot(ServerLevel nether, BlockPos scaled) {
		int r = FireConfig.PB1_PORTAL_SEARCH_RADIUS.get();
		PoiManager poi = nether.getPoiManager();
		poi.ensureLoadedAndValid(nether, scaled, r);
		Optional<BlockPos> portal = poi.getInSquare(h -> h.is(PoiTypes.NETHER_PORTAL), scaled, r, PoiManager.Occupancy.ANY)
				.map(PoiRecord::getPos)
				.min(Comparator.comparingDouble(p -> p.distSqr(scaled)));
		if (portal.isEmpty())
			return null;
		BlockPos base = portal.get();
		for (BlockPos p : BlockPos.withinManhattan(base, 3, 3, 3)) {
			BlockState feet = nether.getBlockState(p), head = nether.getBlockState(p.above()), below = nether.getBlockState(p.below());
			if (feet.isAir() && head.isAir() && below.isFaceSturdy(nether, p.below(), Direction.UP))
				return p.immutable();
		}
		return base; // inside the portal itself, as a last resort
	}

	private static void teleportOnto(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		var shape = state.getCollisionShape(level, pos);
		double top = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
		if (level == player.serverLevel())
			player.teleportTo(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5);
		else
			player.teleportTo(level, pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
		player.fallDistance = 0;
	}

	// ------------------------------------------------------------------ passive sensing

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || (p.serverLevel().getGameTime() + p.getId()) % 20 != 0)
			return;
		List<SensedPlayersMessage.Entry> list = new ArrayList<>();
		if (AspectData.of(p).getActive() == AspectType.FIRE && AbilityState.of(p).passiveActive()
				&& FireKit.isDefaultPassive(KitResolver.passiveFor(p))) {
			Set<SubAspectType> sensed = sensedForms();
			int r = FireConfig.SENSE_RADIUS.get(), nameR = FireConfig.SENSE_NAME_RADIUS.get();
			for (ServerPlayer o : p.serverLevel().players()) {
				if (o == p || !o.isAlive() || o.isSpectator() || o.distanceTo(p) > r)
					continue;
				SubAspectType form = KitResolver.formInUse(o);
				if (form == null || !sensed.contains(form))
					continue;
				String name = o.distanceTo(p) <= nameR ? o.getGameProfile().getName() : "";
				list.add(new SensedPlayersMessage.Entry(name, form.fullName(), o.getX(), o.getZ()));
			}
		}
		if (!list.isEmpty() || SENSING.contains(p.getUUID())) {
			SensedPlayersMessage.send(p, list);
			if (list.isEmpty())
				SENSING.remove(p.getUUID());
			else
				SENSING.add(p.getUUID());
		}
	}

	private static Set<SubAspectType> sensedForms() {
		Set<SubAspectType> out = new HashSet<>();
		for (String id : FireConfig.SENSED_SUB_ASPECTS.get()) {
			SubAspectType s = SubAspectType.fromId(id);
			if (s != null)
				out.add(s);
		}
		return out;
	}

	// ------------------------------------------------------------------ cleanup

	private static void clear(UUID id) {
		FIST_CHARGES.remove(id);
		LAST_CRIT.remove(id);
		PLANS.remove(id);
		SENSING.remove(id);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer p) {
			clear(p.getUUID());
			NO_FALL.remove(p.getUUID());
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity().getUUID());
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		FIST_CHARGES.clear();
		LAST_CRIT.clear();
		HELL_DISCOUNT_UNTIL.clear();
		NO_FALL.clear();
		PLANS.clear();
		SENSING.clear();
		FireWorld.resetAll();
		FireSubEvents.resetAll();
	}
}
