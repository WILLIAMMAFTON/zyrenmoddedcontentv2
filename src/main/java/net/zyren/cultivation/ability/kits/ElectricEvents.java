package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Clearable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilityService;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiCosts;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.ElectricConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.AspectMastery;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Event side of the Electric kits (ElectricKit + ElectricSubKits). Anything that would hurt or move an
 * entity from inside a damage event (lightning on the target, explosions, teleports, retaliation) is
 * deferred to the end of the server tick, so it never re-enters the damage pipeline mid-hit.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class ElectricEvents {
	private static final AspectType E = AspectType.ELECTRIC;

	private ElectricEvents() {
	}

	private static boolean running(ServerPlayer p, Ability a) {
		return KitParts.running(p, a);
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

		void clear(UUID id) {
			left.remove(id);
		}

		void clearAll() {
			left.clear();
		}
	}

	static final Charges FISTS = new Charges("electric_fists", () -> ElectricKit.FISTS);
	static final Charges BLADE = new Charges("electric_blade", () -> ElectricKit.BLADE);
	static final Charges HEATED = new Charges("electric_plasma_heated", () -> ElectricSubKits.HEATED);

	// ================================================================== deferred actions

	private static final List<Runnable> LATER = new ArrayList<>();

	private static void later(Runnable r) {
		LATER.add(r);
	}

	/** Lightning on a target after this hit resolves. */
	static void queueStrike(ServerPlayer from, LivingEntity target, double damage) {
		later(() -> {
			if (from.isAlive() && target.isAlive() && target.level() == from.level())
				LightningEvents.strike(from, target, damage);
		});
	}

	static void queueYeet(ServerPlayer from, LivingEntity target) {
		Vec3 to = ElectricSubKits.yeetSpot(from, target);
		later(() -> {
			if (!target.isAlive())
				return;
			target.teleportTo(to.x, to.y, to.z);
			target.fallDistance = 0;
		});
	}

	private static void queueBlast(ServerPlayer from, LivingEntity at) {
		Vec3 pos = at.position().add(0, at.getBbHeight() * 0.5, 0);
		later(() -> from.level().explode(from, pos.x, pos.y, pos.z, ElectricConfig.CH_A2_POWER.get().floatValue(),
				ElectricConfig.CH_A2_BREAKS_BLOCKS.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE));
	}

	/** Did I say something wrong: lightning + fire + damage. */
	private static void retaliate(ServerPlayer user, ServerPlayer t) {
		later(() -> {
			if (!Targeting.isValid(user, t))
				return;
			LightningEvents.strikeVisual(t.serverLevel(), t.position());
			if (QiEffects.canAffect(t, false))
				FireKit.ignite(t);
			QiEffects.dealDamage(user, t, ElectricConfig.PL_AB1_DAMAGE.get().floatValue());
		});
	}

	// ================================================================== world helpers

	/** Permanent cube crater (no drops, containers emptied, unbreakable blocks stay). */
	static void crater(ServerLevel level, BlockPos center, int size) {
		if (!ElectricConfig.CRATERS.get() || size <= 0)
			return;
		int lo = -(size / 2), hi = lo + size - 1;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(lo, lo, lo), center.offset(hi, hi, hi))) {
			if (!level.isInWorldBounds(pos))
				continue;
			BlockState s = level.getBlockState(pos);
			if (s.isAir() || s.getDestroySpeed(level, pos) < 0)
				continue;
			Clearable.tryClear(level.getBlockEntity(pos));
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	/** Whole server: jump to night (if it isn't night already). */
	static void makeNight(ServerLevel any) {
		ServerLevel world = any.getServer().overworld();
		long t = world.getDayTime(), inDay = t % 24000;
		if (inDay >= 13000 && inDay < 23000)
			return;
		world.setDayTime(t - inDay + (inDay < 13000 ? 13000 : 24000 + 13000));
	}

	static void landSafely(ServerPlayer p) {
		if (!p.onGround())
			FireEvents.noFallDamage(p);
	}

	/** Riding the lightning escape: a standable spot 20-45 blocks away with no unallied player within 20. */
	static BlockPos escapeSpot(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		int min = ElectricConfig.ER_A2_ESCAPE_MIN.get(), max = Math.max(min + 1, ElectricConfig.ER_A2_ESCAPE_MAX.get());
		double clear = ElectricConfig.ER_A2_ESCAPE_CLEAR.get();
		List<ServerPlayer> enemies = ElectricKit.unalliedWithin(p, max + clear + 1);
		var rnd = p.getRandom();
		for (int i = 0; i < 40; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2, dist = min + rnd.nextDouble() * (max - min);
			int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
			if (!level.getChunkSource().hasChunk(x >> 4, z >> 4))
				continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z), below = pos.below();
			if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(below).isEmpty()
					|| !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir())
				continue;
			Vec3 c = Vec3.atBottomCenterOf(pos);
			boolean safe = true;
			for (ServerPlayer e : enemies)
				if (e.position().distanceTo(c) <= clear) {
					safe = false;
					break;
				}
			if (safe)
				return pos;
		}
		return null;
	}

	// ================================================================== Plasma: nukes heated breath

	private static final class Breath {
		long endTick;
		Vec3 end;
	}

	private static final Map<UUID, Breath> BREATHS = new HashMap<>();

	static void startBreath(ServerPlayer p, int ticks) {
		Breath b = new Breath();
		b.endTick = p.serverLevel().getGameTime() + ticks;
		BREATHS.put(p.getUUID(), b);
	}

	static void breathTick(ServerPlayer p) {
		Breath b = BREATHS.get(p.getUUID());
		if (b == null)
			return;
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(ElectricConfig.PL_AB2_LENGTH.get()));
		HitResult hit = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
		b.end = end;
		float dmg = ElectricConfig.PL_AB2_DAMAGE.get().floatValue();
		for (ServerPlayer o : ElectricKit.unalliedWithin(p, ElectricConfig.PL_AB2_LENGTH.get() + 2)) {
			AABB box = o.getBoundingBox().inflate(0.6);
			if (box.contains(eye) || box.clip(eye, end).isPresent()) {
				QiEffects.dealDamage(p, o, dmg);
				if (QiEffects.canAffect(o, false))
					FireKit.ignite(o);
			}
		}
		Vec3 step = end.subtract(eye);
		int n = (int) Math.max(1, step.length() * 2);
		for (int i = 0; i <= n; i++) {
			Vec3 at = eye.add(step.scale(i / (double) n));
			p.serverLevel().sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.01);
		}
	}

	static void endBreath(ServerPlayer p) {
		Breath b = BREATHS.remove(p.getUUID());
		if (b == null || b.end == null || p.serverLevel().getGameTime() < b.endTick - 1)
			return; // stopped early (form change, nullify...): no crater
		crater(p.serverLevel(), BlockPos.containing(b.end), ElectricConfig.PL_AB2_CRATER.get());
		p.serverLevel().playSound(null, BlockPos.containing(b.end), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 3f, 0.6f);
	}

	static void leavePlasma(ServerPlayer p) {
		HEATED.clear(p.getUUID());
		AbilityState.of(p).removeBuff("electric_plasma_heated");
		BREATHS.remove(p.getUUID());
	}

	// ================================================================== Enhanced Receptors: modes + defense slots

	private static final Map<UUID, Integer> MODES = new HashMap<>();
	private static final Map<UUID, Long> MODE_SWITCHED = new HashMap<>();

	private static final class Slots {
		int count;
		long lastRecharge;
	}

	private static final Map<UUID, Slots> SLOTS = new HashMap<>();

	static int mode(ServerPlayer p) {
		return MODES.getOrDefault(p.getUUID(), 1);
	}

	/** Key 'Switch Receptor Mode' (SwitchModeRequest). */
	public static void switchMode(ServerPlayer p) {
		if (KitResolver.formInUse(p) != ElectricSubKits.RECEPTORS) {
			p.displayClientMessage(Component.literal("Only in the Electric → Enhanced Receptors form."), true);
			return;
		}
		long now = p.serverLevel().getGameTime();
		Long last = MODE_SWITCHED.get(p.getUUID());
		if (last != null && now - last < ElectricConfig.ER_MODE_SWITCH_TICKS.get())
			return;
		MODE_SWITCHED.put(p.getUUID(), now);
		int m = mode(p) == 1 ? 2 : 1;
		MODES.put(p.getUUID(), m);
		p.displayClientMessage(Component.literal(m == 1 ? "Receptors: mode 1 (storm empowerment)" : "Receptors: mode 2 (automatic defense, " + slots(p).count + " slots)"), true);
	}

	private static Slots slots(ServerPlayer p) {
		return SLOTS.computeIfAbsent(p.getUUID(), k -> {
			Slots s = new Slots();
			s.count = ElectricConfig.ER_SLOTS.get();
			s.lastRecharge = p.serverLevel().getGameTime();
			return s;
		});
	}

	private static double defenseChance(Ability incoming) {
		if (incoming == null)
			return ElectricConfig.ER_CHANCE_ABILITY.get(); // Qi damage outside a cast (auras, beams): counts as an ability
		AbilitySlot s = incoming.slot();
		return switch (s) {
			case ATTACK_1, HEAVY -> ElectricConfig.ER_CHANCE_ATTACK.get();
			case POWERBLAST_1, POWERBLAST_2 -> ElectricConfig.ER_CHANCE_POWERBLAST.get();
			case REINFORCE -> 0;
			default -> ElectricConfig.ER_CHANCE_ABILITY.get();
		};
	}

	private static void tryDefense(ServerPlayer victim, ServerPlayer attacker, DamageSource src) {
		if (!ElectricSubKits.receptorPassiveOn(victim) || mode(victim) != 2 || QiEffects.isReinforced(victim))
			return;
		long now = victim.serverLevel().getGameTime();
		Ability incoming;
		if (QiEffects.isDealingQiDamage())
			incoming = AbilityService.performing();
		else if (WaterEvents.isMelee(src, attacker) && AbilityState.of(attacker).armedAbility() != null)
			incoming = AbilityState.of(attacker).armedAbility();
		else
			return; // plain hits don't trigger the automatic defense
		Slots s = slots(victim);
		if (s.count <= 0 || victim.getRandom().nextDouble() >= defenseChance(incoming))
			return;
		double cost = QiCosts.effective(victim, ElectricConfig.ER_DEFENSE_COST.get());
		if (!QiCoreStorage.trySpendQi(victim, cost, false))
			return;
		if (s.count == ElectricConfig.ER_SLOTS.get())
			s.lastRecharge = now; // recharge clock starts at the first use
		s.count--;
		QiEffects.applyReinforce(victim, E, ElectricConfig.ER_DEFENSE_TICKS.get());
		victim.displayClientMessage(Component.literal("Automatic defense! (" + s.count + " slots left)"), true);
	}

	private static void rechargeSlots(ServerPlayer p, long now) {
		Slots s = SLOTS.get(p.getUUID());
		int max = ElectricConfig.ER_SLOTS.get();
		if (s == null || s.count >= max)
			return;
		long every = ElectricConfig.ER_SLOT_RECHARGE_SECONDS.get() * 20L;
		while (s.count < max && now - s.lastRecharge >= every) {
			s.count++;
			s.lastRecharge += every;
		}
	}

	// ---- I AM HIM: attacker of a hit that got through -> lightning ----

	private static final Map<UUID, UUID> HIM_PENDING = new HashMap<>();

	// ---- the reason we dont mess with me: storm fixed where cast ----

	private static final class Field {
		ServerLevel level;
		Vec3 center;
		long nextStrike;
	}

	private static final Map<UUID, Field> REASONS = new HashMap<>();

	static void startReason(ServerPlayer p) {
		Field f = new Field();
		f.level = p.serverLevel();
		f.center = p.position();
		f.nextStrike = p.serverLevel().getGameTime() + ElectricConfig.ER_AB2_INTERVAL_SECONDS.get() * 20L;
		REASONS.put(p.getUUID(), f);
	}

	static boolean insideReason(ServerPlayer p) {
		Field f = REASONS.get(p.getUUID());
		return f != null && p.level() == f.level && p.position().distanceTo(f.center) <= ElectricConfig.ER_AB2_RADIUS.get();
	}

	static void reasonTick(ServerPlayer p) {
		Field f = REASONS.get(p.getUUID());
		long now = p.serverLevel().getGameTime();
		if (f == null || now < f.nextStrike)
			return;
		f.nextStrike = now + ElectricConfig.ER_AB2_INTERVAL_SECONDS.get() * 20L;
		int r = ElectricConfig.ER_AB2_RADIUS.get();
		for (ServerPlayer o : f.level.players()) {
			if (!Targeting.isValid(p, o) || Targeting.isAllied(p, o) || o.position().distanceTo(f.center) > r || !ElectricKit.openSky(o))
				continue;
			LightningEvents.strike(p, o, ElectricConfig.ER_AB2_DAMAGE.get());
			if (QiEffects.canAffect(o, false))
				Motion.launchUp(o, ElectricConfig.ER_AB2_UP.get());
		}
	}

	static void endReason(ServerPlayer p) {
		REASONS.remove(p.getUUID());
	}

	static void leaveReceptors(ServerPlayer p) {
		HIM_PENDING.remove(p.getUUID());
		REASONS.remove(p.getUUID());
	}

	// ================================================================== Chaos: eye of the storm

	private static final Map<UUID, long[]> EYES = new HashMap<>(); // [next strike, next knock]

	static void startEye(ServerPlayer p) {
		long now = p.serverLevel().getGameTime();
		EYES.put(p.getUUID(), new long[] { now + ElectricConfig.CH_AB2_STRIKE_SECONDS.get() * 20L, now + ElectricConfig.CH_AB2_KNOCK_SECONDS.get() * 20L });
	}

	static void eyeTick(ServerPlayer p) {
		long[] t = EYES.get(p.getUUID());
		long now = p.serverLevel().getGameTime();
		if (t == null)
			return;
		if (now >= t[0]) {
			t[0] = now + ElectricConfig.CH_AB2_STRIKE_SECONDS.get() * 20L;
			List<ServerPlayer> near = ElectricKit.unalliedWithin(p, ElectricConfig.CH_AB2_STRIKE_RADIUS.get());
			for (int i = 0; i < Math.min(near.size(), ElectricConfig.CH_AB2_STRIKE_COUNT.get()); i++)
				LightningEvents.strike(p, near.get(i), ElectricKit.lightning());
		}
		if (now >= t[1]) {
			t[1] = now + ElectricConfig.CH_AB2_KNOCK_SECONDS.get() * 20L;
			double r = ElectricConfig.CH_AB2_KNOCK_RADIUS.get();
			for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> e != p && e.isAlive() && e.distanceTo(p) <= r)) {
				if (e instanceof ServerPlayer o && (Targeting.isAllied(p, o) || o.isSpectator()))
					continue;
				if (QiEffects.canAffect(e, false))
					Motion.push(e, p.position(), ElectricConfig.CH_AB2_KNOCK.get(), 1.5, 0.4, null);
			}
		}
	}

	static void endEye(ServerPlayer p) {
		EYES.remove(p.getUUID());
	}

	static void leaveChaos(ServerPlayer p) {
		EYES.remove(p.getUUID());
	}

	// ================================================================== events

	/** Outgoing hit effects. HIGH: after empowered hits (HIGHEST), before output multipliers (LOW). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (AspectData.of(attacker).getActive() != E || AbilityState.of(attacker).isNullified(E, attacker.serverLevel().getGameTime()))
			return;
		DamageSource src = event.getSource();
		boolean melee = WaterEvents.isMelee(src, attacker);
		boolean arrow = src.getDirectEntity() instanceof AbstractArrow && !QiEffects.isDealingQiDamage();
		if (!melee && !arrow)
			return;
		float dmg = event.getNewDamage();
		boolean sword = attacker.getMainHandItem().is(ItemTags.SWORDS);
		double lightning = ElectricKit.lightning();

		if (melee) {
			if (FISTS.left(attacker) > 0) {
				dmg *= ElectricConfig.A1_MULTIPLIER.get().floatValue();
				FISTS.use(attacker);
				AspectMastery.onAbilityHit(attacker, E, target);
			}
			if (sword && BLADE.left(attacker) > 0) {
				queueStrike(attacker, target, lightning);
				BLADE.use(attacker);
				AspectMastery.onAbilityHit(attacker, E, target);
			}
			if (sword && running(attacker, ElectricKit.MONSOON))
				queueStrike(attacker, target, lightning);
			if (HEATED.left(attacker) > 0) {
				dmg += ElectricConfig.PL_A1_EXTRA.get().floatValue();
				if (QiEffects.canAffect(target, false))
					FireKit.ignite(target);
				HEATED.use(attacker);
				AspectMastery.onAbilityHit(attacker, E, target);
			}
			if (running(attacker, ElectricSubKits.HIM) && FireKit.isWeapon(attacker.getMainHandItem()))
				dmg *= ElectricConfig.ER_AB1_WEAPON_MULTIPLIER.get().floatValue();
			if (running(attacker, ElectricSubKits.SMOKING))
				queueBlast(attacker, target);
			if (target instanceof ServerPlayer && running(attacker, ElectricSubKits.I_AM_CHAOS))
				queueStrike(attacker, target, lightning);
		}
		if (running(attacker, ElectricSubKits.BIGGER_BROTHER)) {
			if (QiEffects.canAffect(target, false))
				FireKit.ignite(target);
			queueStrike(attacker, target, lightning);
		}
		event.setNewDamage(dmg);
	}

	/** Incoming: automatic defense (first), I AM HIM blocks, Chaos immunity to own explosions. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer victim))
			return;
		DamageSource src = event.getSource();
		if (src.is(DamageTypeTags.IS_EXPLOSION) && src.getEntity() == victim && KitResolver.formInUse(victim) == ElectricSubKits.CHAOS) {
			event.setCanceled(true);
			return;
		}
		if (!(src.getEntity() instanceof ServerPlayer attacker) || attacker == victim)
			return;
		if (running(victim, ElectricSubKits.HIM)) {
			if (victim.getRandom().nextDouble() < ElectricConfig.ER_AB1_BLOCK_CHANCE.get()) {
				event.setCanceled(true);
				victim.displayClientMessage(Component.literal("Blocked."), true);
				return;
			}
			HIM_PENDING.put(victim.getUUID(), attacker.getUUID());
		}
		tryDefense(victim, attacker, src);
	}

	/** After damage landed: I AM HIM retaliation, did i say something wrong. */
	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer victim) || event.getNewDamage() <= 0)
			return;
		UUID pending = HIM_PENDING.remove(victim.getUUID());
		if (pending != null && event.getSource().getEntity() instanceof ServerPlayer him && him.getUUID().equals(pending) && running(victim, ElectricSubKits.HIM))
			queueStrike(victim, him, ElectricKit.lightning());
		if (!running(victim, ElectricSubKits.WRONG))
			return;
		var src = event.getSource().getEntity();
		ServerPlayer target = null;
		if (src instanceof ServerPlayer a && a != victim && !Targeting.isAllied(victim, a))
			target = a;
		else if (src == null || src == victim) {
			List<ServerPlayer> near = ElectricKit.unalliedWithin(victim, ElectricConfig.PL_AB1_FALLBACK_RANGE.get());
			target = near.isEmpty() ? null : near.get(0);
		} else {
			return; // a mob or an ally: keep waiting
		}
		AbilityState.of(victim).endSustained(ElectricSubKits.WRONG, victim.serverLevel().getGameTime());
		if (target != null) {
			retaliate(victim, target);
			victim.displayClientMessage(Component.literal("Did I say something wrong, " + target.getGameProfile().getName() + "?"), true);
		}
	}

	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && (running(p, ElectricSubKits.MOVEMENT) || running(p, ElectricSubKits.EYE)))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer p && (p.serverLevel().getGameTime() + p.getId()) % 20 == 0)
			rechargeSlots(p, p.serverLevel().getGameTime());
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (LATER.isEmpty())
			return;
		List<Runnable> run = new ArrayList<>(LATER);
		LATER.clear();
		run.forEach(Runnable::run);
	}

	// ================================================================== cleanup

	private static void clear(UUID id) {
		FISTS.clear(id);
		BLADE.clear(id);
		HEATED.clear(id);
		BREATHS.remove(id);
		HIM_PENDING.remove(id);
		REASONS.remove(id);
		EYES.remove(id);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer p)
			clear(p.getUUID());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		clear(id);
		MODES.remove(id);
		MODE_SWITCHED.remove(id);
		SLOTS.remove(id);
	}

	public static void resetAll() {
		FISTS.clearAll();
		BLADE.clearAll();
		HEATED.clearAll();
		LATER.clear();
		BREATHS.clear();
		MODES.clear();
		MODE_SWITCHED.clear();
		SLOTS.clear();
		HIM_PENDING.clear();
		REASONS.clear();
		EYES.clear();
	}
}
