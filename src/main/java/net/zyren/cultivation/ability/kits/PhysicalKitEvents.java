package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilityService;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectCleanup;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.CastRules;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.PhysicalConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.network.SensedPlayersMessage;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.AspectMastery;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static net.zyren.cultivation.ability.kits.PhysicalSubKits.isSword;

/**
 * Event side of the Physical kits (PhysicalDefaultKit + PhysicalSubKits). Anything that hurts or
 * moves an entity from inside a damage event is deferred to the end of the server tick.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class PhysicalKitEvents {
	private static final AspectType P = AspectType.PHYSICAL;

	private PhysicalKitEvents() {
	}

	private static boolean running(ServerPlayer p, Ability a) {
		return KitParts.running(p, a);
	}

	private static long now(ServerPlayer p) {
		return p.serverLevel().getGameTime();
	}

	private static boolean until(Map<UUID, Long> map, ServerPlayer p) {
		Long u = map.get(p.getUUID());
		if (u == null)
			return false;
		if (now(p) >= u) {
			map.remove(p.getUUID());
			return false;
		}
		return true;
	}

	private static void endAbility(ServerPlayer p, Ability a) {
		AbilityState.of(p).endSustained(a, now(p));
	}

	// ================================================================== deferred actions

	private static final List<Runnable> LATER = new ArrayList<>();

	private static void later(Runnable r) {
		LATER.add(r);
	}

	// ================================================================== cast rules: locks + enhanced reflexes

	/** Movement lock / Reinforce lock (my fist, ur fase; thousand cuts). */
	private static final Map<UUID, Long> MOBILITY_LOCK = new HashMap<>(), REINFORCE_LOCK = new HashMap<>();
	private static final Map<UUID, Long> LAST_FREE = new HashMap<>();

	static boolean mobilityLocked(ServerPlayer p) {
		return until(MOBILITY_LOCK, p);
	}

	static boolean isMobility(Ability a) {
		String n = a.name().toLowerCase(Locale.ROOT);
		for (String s : PhysicalConfig.MOBILITY_ABILITIES.get())
			if (s.toLowerCase(Locale.ROOT).equals(n))
				return true;
		return false;
	}

	private static boolean reflexFree(ServerPlayer p, Ability a) {
		return (a == PhysicalDefaultKit.DASH || a.slot() == AbilitySlot.REINFORCE) && running(p, PhysicalDefaultKit.REFLEXES);
	}

	static final CastRules.Rule RULES = new CastRules.Rule() {
		@Override
		public ActionResult refuse(ServerPlayer p, Ability a, long now) {
			if (a.slot() == AbilitySlot.REINFORCE && until(REINFORCE_LOCK, p))
				return ActionResult.fail("Reinforce Body is locked (" + (REINFORCE_LOCK.get(p.getUUID()) - now + 19) / 20 + "s).");
			if (isMobility(a) && mobilityLocked(p))
				return ActionResult.fail("You can't move like that right now (" + (MOBILITY_LOCK.get(p.getUUID()) - now + 19) / 20 + "s).");
			if (reflexFree(p, a)) {
				Long last = LAST_FREE.get(p.getUUID());
				if (last != null && now - last < PhysicalConfig.AB1_GAP_TICKS.get())
					return ActionResult.fail("Too fast.");
			}
			return null;
		}

		@Override
		public boolean free(ServerPlayer p, Ability a, long now) {
			return reflexFree(p, a);
		}

		@Override
		public void used(ServerPlayer p, Ability a, long now) {
			if (reflexFree(p, a))
				LAST_FREE.put(p.getUUID(), now);
		}
	};

	/** Removes the active aspect's Reinforce Body and locks it. */
	static void breakReinforce(ServerPlayer t, int seconds) {
		AspectType a = AspectData.of(t).getActive();
		if (a != null) {
			AbilityState.of(t).removeBuff(QiEffects.reinforceId(a));
			AspectCleanup.clearReinforceExtras(t, a);
		}
		REINFORCE_LOCK.put(t.getUUID(), now(t) + seconds * 20L);
	}

	@SubscribeEvent
	public static void onPearl(EntityTeleportEvent.EnderPearl event) {
		if (mobilityLocked(event.getPlayer()))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onChorus(EntityTeleportEvent.ChorusFruit event) {
		if (event.getEntityLiving() instanceof ServerPlayer p && mobilityLocked(p))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
		if (event.getEntity() instanceof ServerPlayer p && event.getItemStack().is(Items.ENDER_PEARL) && mobilityLocked(p)) {
			event.setCanceled(true);
			p.displayClientMessage(Component.literal("You can't teleport right now."), true);
		}
	}

	// ================================================================== movement tracking (DASH / sidestep direction)

	private record Move(Vec3 dir, int strafe, long dirTick, long strafeTick, Vec3 lastPos) {
	}

	private static final Map<UUID, Move> MOVES = new HashMap<>();

	/** Horizontal direction moved within the last second, or null. */
	static Vec3 recentMove(ServerPlayer p) {
		Move m = MOVES.get(p.getUUID());
		return m != null && m.dir() != null && now(p) - m.dirTick() <= 20 ? m.dir() : null;
	}

	/** +1 right / -1 left: last strafe within the last second, else 0. */
	static int recentStrafe(ServerPlayer p) {
		Move m = MOVES.get(p.getUUID());
		return m != null && now(p) - m.strafeTick() <= 20 ? m.strafe() : 0;
	}

	private static void trackMove(ServerPlayer p) {
		Move old = MOVES.get(p.getUUID());
		Vec3 pos = p.position();
		long now = now(p);
		if (old == null || old.lastPos() == null) {
			MOVES.put(p.getUUID(), new Move(null, 0, 0, 0, pos));
			return;
		}
		Vec3 v = new Vec3(pos.x - old.lastPos().x, 0, pos.z - old.lastPos().z);
		if (v.lengthSqr() < 0.0004 || v.lengthSqr() > 4) { // standing still, or teleported
			MOVES.put(p.getUUID(), new Move(old.dir(), old.strafe(), old.dirTick(), old.strafeTick(), pos));
			return;
		}
		Vec3 look = PhysicalDefaultKit.horizontal(Vec3.directionFromRotation(0, p.getYRot()));
		Vec3 right = new Vec3(-look.z, 0, look.x);
		double side = v.dot(right);
		int strafe = old.strafe();
		long strafeTick = old.strafeTick();
		if (Math.abs(side) > 0.02) {
			strafe = side > 0 ? 1 : -1;
			strafeTick = now;
		}
		MOVES.put(p.getUUID(), new Move(v.normalize(), strafe, now, strafeTick, pos));
	}

	// ================================================================== hit charges ("next N hits")

	static final class Charges {
		private final String buff;
		private final AspectType aspect;
		private final Supplier<Ability> owner;
		private final Map<UUID, Integer> left = new HashMap<>();

		Charges(String buff, Supplier<Ability> owner) {
			this(buff, P, owner);
		}

		/** Shared with other kits (e.g. Protection -> Healing lady mercy). */
		Charges(String buff, AspectType aspect, Supplier<Ability> owner) {
			this.buff = buff;
			this.aspect = aspect;
			this.owner = owner;
		}

		void start(ServerPlayer p, int hits) {
			AbilityState.of(p).addBuff(new Buff(buff, aspect, 1.0, 0, false, Long.MAX_VALUE));
			left.put(p.getUUID(), hits);
		}

		int left(ServerPlayer p) {
			Integer n = left.get(p.getUUID());
			if (n == null)
				return 0;
			AbilityState st = AbilityState.of(p);
			if (!st.hasBuff(buff, now(p)) || !KitResolver.isCurrent(p, owner.get()) || st.isNullified(aspect, now(p))) {
				left.remove(p.getUUID());
				return 0;
			}
			return n;
		}

		void use(ServerPlayer p) {
			int n = left(p) - 1;
			if (n > 0)
				left.put(p.getUUID(), n);
			else {
				left.remove(p.getUUID());
				AbilityState.of(p).removeBuff(buff);
			}
		}

		void clear(ServerPlayer p) {
			left.remove(p.getUUID());
			AbilityState.of(p).removeBuff(buff);
		}

		void clear(UUID id) {
			left.remove(id);
		}

		void clearAll() {
			left.clear();
		}
	}

	static final Charges HARDENED = new Charges("physical_body_hardened", () -> PhysicalSubKits.HARDENED);

	// ================================================================== DEFAULT: EAT MY FIST / my fist, ur fase

	private static final Map<UUID, Integer> COMBO = new HashMap<>();

	static void resetCombo(ServerPlayer p) {
		COMBO.remove(p.getUUID());
	}

	private record Armed(UUID target, long until) {
	}

	private static final Map<UUID, Armed> MY_FIST = new HashMap<>();

	static void armMyFist(ServerPlayer p, ServerPlayer t) {
		MY_FIST.put(p.getUUID(), new Armed(t.getUUID(), now(p) + PhysicalConfig.AB2_WINDOW_SECONDS.get() * 20L));
	}

	private static boolean receptorsDefending(ServerPlayer t) {
		return ElectricSubKits.receptorPassiveOn(t) && ElectricEvents.mode(t) == 2;
	}

	private static void myFistHit(ServerPlayer p, ServerPlayer t) {
		float dmg = PhysicalConfig.AB2_DAMAGE.get().floatValue();
		if (receptorsDefending(t))
			QiEffects.dealDamage(p, t, dmg); // Electric -> Enhanced Receptors' automatic defense still works
		else
			QiEffects.dealDamageBypassingReinforce(p, t, dmg);
		float stone = PhysicalConfig.AB2_STONE_DAMAGE.get().floatValue();
		Motion.push(t, p.position(), PhysicalConfig.AB2_THROW.get(), 1.5, 0.3, (e, state) -> {
			if (EarthKit.isStone(state))
				QiEffects.dealDamageBypassingReinforce(p, e, stone);
		});
		int lock = PhysicalConfig.AB2_LOCK_SECONDS.get();
		breakReinforce(t, lock);
		MOBILITY_LOCK.put(t.getUUID(), now(t) + lock * 20L);
		t.displayClientMessage(Component.literal("Reinforce Body and movement abilities locked for " + lock + " s!"), true);
		p.serverLevel().playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.5f, 0.6f);
	}

	// ================================================================== DEFAULT: deflecting blow

	static final Map<UUID, Integer> DEFLECTS = new HashMap<>();

	// ================================================================== flights (jumpin blow, boulder, dashing fists)

	private static final class Flight {
		ServerPlayer p;
		LivingEntity t;
		int delay, ticks, breakLeft;
		double speed;
		float damage;
		Runnable onContact;
	}

	private static final List<Flight> FLIGHTS = new ArrayList<>();

	/** The user flies (teleport steps) at the target after {@code delay} ticks; contact = damage + onContact. Breaks up to breakBlocks blocks. */
	static void fly(ServerPlayer p, LivingEntity t, int delay, double speed, int breakBlocks, float damage, Runnable onContact) {
		FLIGHTS.removeIf(f -> f.p == p);
		Flight f = new Flight();
		f.p = p;
		f.t = t;
		f.delay = delay;
		f.speed = speed;
		f.breakLeft = breakBlocks;
		f.damage = damage;
		f.onContact = onContact;
		FLIGHTS.add(f);
	}

	private static boolean contact(LivingEntity a, LivingEntity b) {
		return a.getBoundingBox().inflate(0.6).intersects(b.getBoundingBox());
	}

	private static boolean boulderStops(ServerLevel level, BlockPos pos, BlockState s) {
		return s.getDestroySpeed(level, pos) < 0 || s.hasBlockEntity() || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)
				|| s.is(Blocks.END_PORTAL_FRAME) || s.is(Blocks.REINFORCED_DEEPSLATE) || s.is(Blocks.RESPAWN_ANCHOR);
	}

	/** @return true when finished */
	private static boolean tickFlight(Flight f) {
		ServerPlayer p = f.p;
		LivingEntity t = f.t;
		if (!p.isAlive() || !t.isAlive() || p.level() != t.level() || Stun.isStunned(p))
			return true;
		if (f.delay > 0) {
			f.delay--;
			return false;
		}
		if (++f.ticks > 80) {
			noFallUntilLanded(p);
			return true;
		}
		if (contact(p, t)) {
			hitFlight(f);
			return true;
		}
		ServerLevel level = p.serverLevel();
		Vec3 d = t.position().subtract(p.position());
		double dist = d.length();
		Vec3 step = d.scale(Math.min(f.speed, dist) / Math.max(dist, 1.0E-6));
		AABB box = p.getBoundingBox().move(step);
		if (!level.noCollision(p, box)) {
			if (f.breakLeft <= 0) {
				noFallUntilLanded(p);
				return true;
			}
			for (BlockPos bp : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1.0E-4, box.maxY - 1.0E-4, box.maxZ - 1.0E-4))) {
				BlockState s = level.getBlockState(bp);
				if (s.isAir() || s.getCollisionShape(level, bp).isEmpty())
					continue;
				if (boulderStops(level, bp, s) || f.breakLeft <= 0) {
					noFallUntilLanded(p);
					return true;
				}
				level.destroyBlock(bp, false, p);
				f.breakLeft--;
			}
		}
		p.teleportTo(p.getX() + step.x, p.getY() + step.y, p.getZ() + step.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		p.fallDistance = 0;
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
		if (contact(p, t)) {
			hitFlight(f);
			return true;
		}
		return false;
	}

	private static void hitFlight(Flight f) {
		if (f.damage > 0)
			QiEffects.dealDamage(f.p, f.t, f.damage);
		if (f.onContact != null)
			f.onContact.run();
		noFallUntilLanded(f.p);
	}

	// ================================================================== no fall damage until landing

	/** Player -> tick it was set. Cleared by the cancelled fall, by standing on the ground 5+ ticks later, or after 20 s. */
	private static final Map<UUID, Long> NO_FALL = new HashMap<>();

	static void noFallUntilLanded(ServerPlayer p) {
		NO_FALL.put(p.getUUID(), now(p));
	}

	private static boolean noFall(ServerPlayer p) {
		Long set = NO_FALL.get(p.getUUID());
		if (set == null)
			return false;
		if (now(p) - set > 400) {
			NO_FALL.remove(p.getUUID());
			return false;
		}
		return true;
	}

	// ================================================================== WARRIOR

	private static final class ParryState {
		long cast;
		boolean parried;
	}

	private static final Map<UUID, ParryState> PARRIES = new HashMap<>();
	private static final Set<UUID> CRIT_READY = new HashSet<>();
	private static final Map<UUID, Long> LAST_PARRY = new HashMap<>(), SIDESTEP_BONUS = new HashMap<>(), BANNER_BUFF = new HashMap<>();
	static final Map<UUID, Integer> STACKS = new HashMap<>();
	private static final Map<UUID, Float> ATTACK_SCALE = new HashMap<>();
	private static final Map<UUID, Boolean> LAST_CRIT = new HashMap<>();

	static void startParry(ServerPlayer p) {
		ParryState s = new ParryState();
		s.cast = now(p);
		PARRIES.put(p.getUUID(), s);
	}

	/** Window over: no parry -> the whiff cooldown (counted from the cast). */
	static void endParry(ServerPlayer p) {
		ParryState s = PARRIES.remove(p.getUUID());
		if (s == null || s.parried)
			return;
		long now = now(p);
		long left = PhysicalConfig.WR_A2_WHIFF_COOLDOWN.get() * 20L - (now - s.cast);
		if (left > 0)
			AbilityState.of(p).startCooldown(PhysicalSubKits.PARRY, now, (int) left);
	}

	private static void flowingSuccess(ServerPlayer p, Ability other) {
		if (!running(p, PhysicalSubKits.FLOWING))
			return;
		QiCoreStorage.addQi(p, PhysicalConfig.WR_AB1_REFUND.get());
		AbilityState.of(p).startCooldown(other, now(p), 0);
	}

	private static void parried(ServerPlayer victim, LivingEntity attacker) {
		long now = now(victim);
		ParryState s = PARRIES.get(victim.getUUID());
		if (s != null)
			s.parried = true;
		CRIT_READY.add(victim.getUUID());
		LAST_PARRY.put(victim.getUUID(), now);
		if (running(victim, PhysicalSubKits.FLOWING))
			STACKS.merge(victim.getUUID(), 1, (a, b) -> Math.min(PhysicalConfig.WR_AB1_MAX_STACKS.get(), a + b));
		flowingSuccess(victim, PhysicalSubKits.SIDESTEP);
		int stagger = PhysicalConfig.WR_A2_STAGGER_TICKS.get();
		later(() -> {
			if (attacker.isAlive()) {
				EffectHelper.apply(attacker, MobEffects.MOVEMENT_SLOWDOWN, 2, stagger, true);
				EffectHelper.apply(attacker, MobEffects.WEAKNESS, 1, stagger, true);
			}
			endAbility(victim, PhysicalSubKits.PARRY);
		});
		victim.serverLevel().playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1.3f);
		victim.displayClientMessage(Component.literal("Parried! Next sword hit is a critical."), true);
	}

	static void onSidestep(ServerPlayer p) {
		long now = now(p);
		Long last = LAST_PARRY.get(p.getUUID());
		if (last != null && now - last <= PhysicalConfig.WR_PB1_PARRY_WINDOW_SECONDS.get() * 20L)
			SIDESTEP_BONUS.put(p.getUUID(), now + 200);
		flowingSuccess(p, PhysicalSubKits.PARRY);
	}

	private record Banner(ServerLevel level, BlockPos pos, String group) {
	}

	private static final Map<UUID, Banner> BANNERS = new HashMap<>();

	static void plantBanner(ServerPlayer p, int ticks) {
		removeBanner(p);
		ServerLevel level = p.serverLevel();
		BlockPos pos = p.blockPosition();
		String group = "war_banner_" + p.getUUID();
		if (EarthWorld.temp(level, pos, Blocks.RED_BANNER.defaultBlockState(), ticks + 40, group, true))
			BANNERS.put(p.getUUID(), new Banner(level, pos.immutable(), group));
	}

	static void bannerTick(ServerPlayer p) {
		Banner b = BANNERS.get(p.getUUID());
		if (b == null || !b.level().getBlockState(b.pos()).is(BlockTags.BANNERS)) {
			endAbility(p, PhysicalSubKits.BANNER);
			return;
		}
		Vec3 c = Vec3.atBottomCenterOf(b.pos());
		int r = PhysicalConfig.WR_PB2_RADIUS.get();
		long until = now(p) + 30;
		for (ServerPlayer o : b.level().players())
			if (!o.isSpectator() && Targeting.isAllied(p, o) && AspectData.of(o).getActive() == P && o.position().distanceTo(c) <= r) {
				TimedAttributes.apply(o, Attributes.KNOCKBACK_RESISTANCE, "physical_war_banner", PhysicalConfig.WR_PB2_KB_RESIST.get(),
						AttributeModifier.Operation.ADD_VALUE, 30);
				BANNER_BUFF.put(o.getUUID(), until);
			}
		b.level().sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y + 1.5, c.z, 6, r / 3.0, 0.5, r / 3.0, 0);
	}

	static void removeBanner(ServerPlayer p) {
		Banner b = BANNERS.remove(p.getUUID());
		if (b != null)
			EarthWorld.revertGroup(b.group());
	}

	/** One hit from an unallied player breaks a war banner. */
	@SubscribeEvent
	public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
		if (BANNERS.isEmpty() || !(event.getEntity() instanceof ServerPlayer hitter))
			return;
		for (Map.Entry<UUID, Banner> en : new ArrayList<>(BANNERS.entrySet())) {
			Banner b = en.getValue();
			if (b.level() != hitter.level() || !b.pos().equals(event.getPos()))
				continue;
			ServerPlayer owner = hitter.server.getPlayerList().getPlayer(en.getKey());
			if (owner != null && Targeting.isAllied(owner, hitter))
				return;
			event.setCanceled(true);
			b.level().removeBlock(b.pos(), false);
			b.level().playSound(null, b.pos(), SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
			if (owner != null) {
				endAbility(owner, PhysicalSubKits.BANNER);
				owner.displayClientMessage(Component.literal("Your war banner was broken!"), true);
			} else
				BANNERS.remove(en.getKey());
			return;
		}
	}

	// bleeding (thousand cuts)

	private static final class Bleed {
		LivingEntity e;
		UUID source;
		long until, next;
	}

	private static final List<Bleed> BLEEDS = new ArrayList<>();

	static void bleed(ServerPlayer from, LivingEntity e, int seconds) {
		BLEEDS.removeIf(b -> b.e == e);
		Bleed b = new Bleed();
		b.e = e;
		b.source = from.getUUID();
		b.next = now(from) + 20;
		b.until = now(from) + seconds * 20L;
		BLEEDS.add(b);
	}

	// ================================================================== BODY

	private static final class Punch {
		ServerPlayer p;
		LivingEntity t;
		int left;
		long next;
	}

	private static final List<Punch> PUNCHES = new ArrayList<>();

	static void startPunches(ServerPlayer p, LivingEntity t) {
		PUNCHES.removeIf(x -> x.p == p);
		Punch x = new Punch();
		x.p = p;
		x.t = t;
		x.left = PhysicalConfig.BD_PB2_PUNCHES.get();
		x.next = now(p);
		PUNCHES.add(x);
	}

	private static boolean tickPunch(Punch x) {
		ServerPlayer p = x.p;
		LivingEntity t = x.t;
		if (!p.isAlive() || !t.isAlive() || p.level() != t.level() || Stun.isStunned(p) || x.left <= 0)
			return true;
		double dist = p.distanceTo(t);
		if (dist > PhysicalConfig.BD_PB2_FOLLOW.get() + 1 || !p.hasLineOfSight(t)) {
			p.displayClientMessage(Component.literal("Combo broken (" + x.left + " punches lost)."), true);
			return true;
		}
		if (dist > 1.8) { // follow
			Vec3 to = t.position().subtract(p.position());
			Vec3 at = t.position().subtract(to.normalize().scale(1.4));
			if (p.serverLevel().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) {
				p.teleportTo(at.x, at.y, at.z);
				p.fallDistance = 0;
			}
		}
		long now = now(p);
		if (now < x.next)
			return false;
		x.next = now + PhysicalConfig.BD_PB2_INTERVAL_TICKS.get();
		p.swing(InteractionHand.MAIN_HAND, true);
		QiEffects.dealDamage(p, t, PhysicalConfig.BD_PB2_DAMAGE.get().floatValue());
		p.serverLevel().sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1, t.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
		x.left--;
		return x.left <= 0;
	}

	static final Map<UUID, Integer> KRATOS_JOBS = new HashMap<>();

	static void startKratos(ServerPlayer p) {
		KRATOS_JOBS.put(p.getUUID(), 0);
	}

	private static void kratosLanded(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		int r = PhysicalConfig.BD_AB2_CRATER_RADIUS.get(), depth = PhysicalConfig.BD_AB2_CRATER_DEPTH.get();
		int cx = p.getBlockX(), cy = p.getBlockY(), cz = p.getBlockZ();
		if (r > 0 && depth > 0) {
			Iterator<BlockPos> it = BlockPos.betweenClosed(cx - r, cy - depth, cz - r, cx + r, cy - 1, cz + r).iterator();
			BlockState air = Blocks.AIR.defaultBlockState();
			EarthWorld.spread(it, pos -> {
				int dx = pos.getX() - cx, dz = pos.getZ() - cz;
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r)
					return;
				int here = Math.max(1, (int) Math.ceil(depth * Math.sqrt(1 - (d / r) * (d / r))));
				if (cy - pos.getY() <= here)
					EarthWorld.replace(level, pos, air);
			}, null);
		}
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4f, 0.5f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
		int ticks = PhysicalConfig.BD_AB2_SECONDS.get() * 20;
		for (LivingEntity e : PhysicalDefaultKit.around(p, p.position(), PhysicalConfig.BD_AB2_FLING_RADIUS.get(), false)) {
			if (!QiEffects.canAffect(e, false))
				continue;
			Motion.launchUp(e, PhysicalConfig.BD_AB2_FLING.get());
			EffectHelper.apply(e, MobEffects.CONFUSION, 1, ticks, true);
			EffectHelper.apply(e, MobEffects.BLINDNESS, 1, ticks, true);
		}
	}

	// ================================================================== INSTINCT

	private static final class Dashes {
		int count;
		long lastRegen, lastTrigger = Long.MIN_VALUE / 2;
	}

	private static final Map<UUID, Dashes> DASHES = new HashMap<>();
	private static final Map<UUID, Long> DODGING = new HashMap<>();

	private static boolean infused(ServerPlayer p) {
		return running(p, PhysicalSubKits.INFUSED);
	}

	private static int maxDashes(ServerPlayer p) {
		return infused(p) ? PhysicalConfig.IN_AB2_MAX_DASHES.get() : PhysicalConfig.IN_DODGE_MAX.get();
	}

	private static Dashes dashes(ServerPlayer p) {
		return DASHES.computeIfAbsent(p.getUUID(), k -> {
			Dashes d = new Dashes();
			d.count = maxDashes(p);
			d.lastRegen = now(p);
			return d;
		});
	}

	/** Once a second from the Instinct passive. */
	static void regenDashes(ServerPlayer p) {
		Dashes d = dashes(p);
		int max = maxDashes(p);
		long now = now(p);
		if (d.count >= max) {
			d.count = Math.min(d.count, max);
			d.lastRegen = now;
			return;
		}
		int every = (infused(p) ? PhysicalConfig.IN_AB2_REGEN_SECONDS.get() : PhysicalConfig.IN_DODGE_REGEN_SECONDS.get()) * 20;
		int amount = infused(p) ? PhysicalConfig.IN_AB2_REGEN_DASHES.get() : 1;
		if (now - d.lastRegen >= every) {
			d.count = Math.min(max, d.count + amount);
			d.lastRegen = now;
		}
	}

	private static final class Streak {
		int target, count;
	}

	static final Map<UUID, Streak> STREAKS = new HashMap<>();
	static final Map<UUID, Integer> SHIELD_BLOCKS = new HashMap<>(), UNCHAINED_HITS = new HashMap<>();
	static final Map<UUID, UUID> GUIDED_TARGET = new HashMap<>();
	private static boolean guidedEcho;

	static ServerPlayer guidedTarget(ServerPlayer p) {
		UUID id = GUIDED_TARGET.get(p.getUUID());
		ServerPlayer t = id == null ? null : p.server.getPlayerList().getPlayer(id);
		return t != null && Targeting.isValid(p, t) ? t : null;
	}

	private static String activeAbilities(ServerPlayer o) {
		int mask = AbilityState.of(o).activeSlotsMask();
		List<String> names = new ArrayList<>();
		for (AbilitySlot s : AbilitySlot.values())
			if ((mask & (1 << s.ordinal())) != 0) {
				Ability a = KitResolver.abilityFor(o, s);
				if (a != null)
					names.add(a.name());
			}
		return names.isEmpty() ? "none" : String.join(", ", names);
	}

	/** Awareness of the hunter: HUD list, more detail the closer they are. */
	static void senseHunters(ServerPlayer p) {
		List<SensedPlayersMessage.Entry> list = new ArrayList<>();
		int detect = PhysicalConfig.IN_PB2_DETECT.get(), aspectR = PhysicalConfig.IN_PB2_ASPECT.get(), subR = PhysicalConfig.IN_PB2_SUB.get(),
				fullR = PhysicalConfig.IN_PB2_FULL.get();
		for (ServerPlayer o : p.serverLevel().players()) {
			if (!Targeting.isValid(p, o))
				continue;
			double d = o.distanceTo(p);
			if (d > detect)
				continue;
			String label = "a player", name = "";
			if (d <= aspectR) {
				AspectType a = AspectData.of(o).getActive();
				String aspect = a == null ? "no aspect" : a.displayName();
				if (d <= subR) {
					SubAspectType sub = KitResolver.formInUse(o);
					if (sub != null)
						aspect += " → " + sub.displayName();
					QiEffects.applyEffect(o, MobEffects.GLOWING, 1, 40, false);
				}
				label = aspect + " · " + Math.round(o.getHealth()) + "hp";
				if (d <= fullR)
					label += " · " + Math.round(QiData.of(o).getCoreAmount()) + " Qi · " + activeAbilities(o);
				name = o.getGameProfile().getName();
			}
			if (label.length() > 64)
				label = label.substring(0, 63) + "…";
			list.add(new SensedPlayersMessage.Entry(name, label, o.getX(), o.getZ()));
		}
		SensedPlayersMessage.send(p, list);
	}

	// ================================================================== events: outgoing

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		if (event.getEntity() instanceof ServerPlayer p)
			ATTACK_SCALE.put(p.getUUID(), p.getAttackStrengthScale(0.5f));
	}

	/** Parry's guaranteed critical; remembers whether this swing was a crit (Measured strike). */
	@SubscribeEvent
	public static void onCrit(CriticalHitEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		if (CRIT_READY.contains(p.getUUID()) && isSword(p.getMainHandItem()) && KitResolver.formInUse(p) == PhysicalSubKits.WARRIOR
				&& AspectData.of(p).getActive() == P) {
			CRIT_READY.remove(p.getUUID());
			event.setCriticalHit(true);
			event.setDamageMultiplier(Math.max(event.getDamageMultiplier(), PhysicalConfig.WR_A2_CRIT_MULTIPLIER.get().floatValue()));
		}
		LAST_CRIT.put(p.getUUID(), event.isCriticalHit());
	}

	/** Outgoing melee bonuses. HIGH: after empowered hits (HIGHEST), before output multipliers (LOW). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (AspectData.of(attacker).getActive() != P || AbilityState.of(attacker).isNullified(P, now(attacker)))
			return;
		if (!WaterEvents.isMelee(event.getSource(), attacker))
			return;
		float dmg = event.getNewDamage();
		boolean unarmed = PhysicalDefaultKit.unarmed(attacker), sword = isSword(attacker.getMainHandItem());
		UUID id = attacker.getUUID();

		if (running(attacker, PhysicalDefaultKit.EAT_MY_FIST)) {
			int n = COMBO.getOrDefault(id, 0) + 1;
			dmg += PhysicalConfig.A1_PER_HIT.get().floatValue() * n;
			COMBO.put(id, n);
		}
		Armed armed = MY_FIST.get(id);
		if (armed != null && target instanceof ServerPlayer tp && tp.getUUID().equals(armed.target())) {
			MY_FIST.remove(id);
			if (now(attacker) <= armed.until() && KitResolver.isCurrent(attacker, PhysicalDefaultKit.MY_FIST)) {
				later(() -> myFistHit(attacker, tp));
				AspectMastery.onAbilityHit(attacker, P, target);
			}
		}
		if (sword) {
			if (running(attacker, PhysicalSubKits.MEASURED) && ATTACK_SCALE.getOrDefault(id, 0f) >= 0.9f) {
				dmg += PhysicalConfig.WR_A1_BONUS.get().floatValue();
				if (Boolean.TRUE.equals(LAST_CRIT.get(id)))
					dmg += PhysicalConfig.WR_A1_CRIT_BONUS.get().floatValue();
			}
			if (until(SIDESTEP_BONUS, attacker) && KitResolver.formInUse(attacker) == PhysicalSubKits.WARRIOR) {
				SIDESTEP_BONUS.remove(id);
				dmg += PhysicalConfig.WR_PB1_PARRY_BONUS.get().floatValue();
			}
			if (running(attacker, PhysicalSubKits.FLOWING))
				dmg += PhysicalConfig.WR_AB1_STACK_DAMAGE.get().floatValue() * STACKS.getOrDefault(id, 0);
			if (until(BANNER_BUFF, attacker))
				dmg += PhysicalConfig.WR_PB2_DAMAGE.get().floatValue();
		}
		if (unarmed && HARDENED.left(attacker) > 0) {
			dmg += PhysicalConfig.BD_A1_EXTRA.get().floatValue();
			HARDENED.use(attacker);
			AspectMastery.onAbilityHit(attacker, P, target);
		}
		if (running(attacker, PhysicalSubKits.COMBO)) {
			Streak s = STREAKS.computeIfAbsent(id, k -> new Streak());
			if (s.target != target.getId()) {
				s.target = target.getId();
				s.count = 0;
			}
			s.count++;
		}
		event.setNewDamage(dmg);
	}

	/** Enhanced combo: after N hits in a row on one target, total damage output x(1+boost). */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onHitBoost(LivingDamageEvent.Pre event) {
		if (STREAKS.isEmpty() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity())
			return;
		Streak s = STREAKS.get(attacker.getUUID());
		if (s == null || !running(attacker, PhysicalSubKits.COMBO))
			return;
		boolean melee = WaterEvents.isMelee(event.getSource(), attacker);
		int need = PhysicalConfig.IN_A1_HITS.get();
		if (melee ? s.count > need : s.count >= need)
			event.setNewDamage(event.getNewDamage() * (1 + PhysicalConfig.IN_A1_BOOST.get().floatValue()));
	}

	// ================================================================== events: incoming

	private static boolean isProjectile(DamageSource src) {
		return src.getDirectEntity() instanceof Projectile || src.is(DamageTypeTags.IS_PROJECTILE);
	}

	private static void reflect(ServerPlayer victim, Entity attacker, float amount) {
		if (!(attacker instanceof LivingEntity le) || amount <= 0)
			return;
		later(() -> {
			if (le.isAlive() && victim.isAlive())
				QiEffects.dealDamage(victim, le, amount);
		});
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer victim))
			return;
		DamageSource src = event.getSource();
		UUID id = victim.getUUID();
		long now = now(victim);

		if (src.is(DamageTypes.FALL)) {
			if (noFall(victim) || running(victim, PhysicalSubKits.KRATOS)) {
				NO_FALL.remove(id);
				event.setCanceled(true);
			}
			return;
		}
		if (AspectData.of(victim).getActive() != P)
			return;
		Entity attacker = src.getEntity(), direct = src.getDirectEntity();
		boolean qi = QiEffects.isDealingQiDamage();
		boolean melee = attacker instanceof LivingEntity && attacker != victim && direct == attacker && !qi;
		boolean projectile = isProjectile(src);

		// Sidestep: projectiles miss while hidden
		if (projectile && CastRules.hiddenFromTargeting(victim)) {
			event.setCanceled(true);
			return;
		}
		boolean fromSomeone = attacker instanceof LivingEntity && attacker != victim;

		// Instinct: unchained awareness of the parrying blade (any hit from someone)
		Integer chained = UNCHAINED_HITS.get(id);
		if (chained != null && chained > 0 && fromSomeone && running(victim, PhysicalSubKits.UNCHAINED)) {
			event.setCanceled(true);
			reflect(victim, attacker, event.getAmount());
			UNCHAINED_HITS.put(id, chained - 1);
			if (chained - 1 <= 0)
				later(() -> endAbility(victim, PhysicalSubKits.UNCHAINED));
			victim.serverLevel().playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 0.7f);
			return;
		}

		// DEFAULT: deflecting blow
		Integer deflects = DEFLECTS.get(id);
		if (deflects != null && deflects > 0 && fromSomeone && !src.is(DamageTypeTags.IS_EXPLOSION) && running(victim, PhysicalDefaultKit.DEFLECT)) {
			Ability incoming = qi ? AbilityService.performing() : null;
			boolean blockable = melee || projectile || qi && (incoming == null || incoming.slot() != AbilitySlot.ABILITY_2);
			if (blockable) {
				event.setCanceled(true);
				if (victim.getRandom().nextDouble() < PhysicalConfig.PB2_REFLECT_CHANCE.get())
					reflect(victim, attacker, event.getAmount());
				DEFLECTS.put(id, deflects - 1);
				if (deflects - 1 <= 0)
					later(() -> endAbility(victim, PhysicalDefaultKit.DEFLECT));
				victim.serverLevel().playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1f);
				return;
			}
		}

		// WARRIOR: parry (unallied melee hit, or an Attack-slot move)
		ParryState ps = PARRIES.get(id);
		if (fromSomeone && ps != null && !ps.parried && running(victim, PhysicalSubKits.PARRY) && !(attacker instanceof ServerPlayer ap && Targeting.isAllied(victim, ap))) {
			Ability incoming = qi ? AbilityService.performing() : null;
			boolean attackMove = qi && incoming != null && (incoming.slot() == AbilitySlot.ATTACK_1 || incoming.slot() == AbilitySlot.HEAVY);
			if (melee || attackMove) {
				event.setCanceled(true);
				parried(victim, (LivingEntity) attacker);
				return;
			}
		}

		// INSTINCT: survival instinct (shield auto-block of melee / arrows)
		Integer shield = SHIELD_BLOCKS.get(id);
		if (shield != null && shield > 0 && (melee || projectile) && running(victim, PhysicalSubKits.SURVIVAL)) {
			InteractionHand hand = victim.getMainHandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK) ? InteractionHand.MAIN_HAND
					: victim.getOffhandItem().canPerformAction(ItemAbilities.SHIELD_BLOCK) ? InteractionHand.OFF_HAND : null;
			if (hand != null) {
				event.setCanceled(true);
				ItemStack st = victim.getItemInHand(hand);
				int wear = 1 + (int) Math.floor(event.getAmount());
				st.hurtAndBreak(wear, victim, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
				victim.serverLevel().playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 1f);
				SHIELD_BLOCKS.put(id, shield - 1);
				if (shield - 1 <= 0)
					later(() -> endAbility(victim, PhysicalSubKits.SURVIVAL));
				return;
			}
		}

		// INSTINCT passive: dodge dash
		if (PhysicalSubKits.instinctPassiveOn(victim)) {
			float cut = (float) (1 - PhysicalConfig.IN_DODGE_REDUCTION.get());
			if (until(DODGING, victim)) {
				event.setAmount(event.getAmount() * cut);
				return;
			}
			if (!fromSomeone)
				return;
			Dashes d = dashes(victim);
			if (d.count <= 0 || now - d.lastTrigger < PhysicalConfig.IN_DODGE_GAP_SECONDS.get() * 20L
					|| victim.getRandom().nextDouble() >= PhysicalConfig.IN_DODGE_CHANCE.get())
				return;
			if (d.count >= maxDashes(victim))
				d.lastRegen = now; // regen clock starts at the first use
			d.count--;
			d.lastTrigger = now;
			DODGING.put(id, now + PhysicalConfig.IN_DODGE_TICKS.get());
			event.setAmount(event.getAmount() * cut);
			Vec3 away = victim.position().subtract(attacker.position());
			later(() -> {
				if (victim.isAlive())
					PhysicalDefaultKit.slide(victim, away, PhysicalConfig.IN_DODGE_BLOCKS.get(), false);
			});
		}
	}

	/** After damage landed: combo resets, Qi-guided echo. */
	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		if (event.getNewDamage() <= 0)
			return;
		LivingEntity hurt = event.getEntity();
		if (hurt instanceof ServerPlayer v) {
			COMBO.remove(v.getUUID()); // EAT MY FIST: hurt -> combo reset
			if (STACKS.containsKey(v.getUUID()))
				STACKS.remove(v.getUUID()); // flowing stance: hit -> stacks cleared
			Streak s = STREAKS.get(v.getUUID());
			if (s != null && event.getSource().getEntity() != null && event.getSource().getEntity().getId() == s.target)
				s.count = 0;
		}
		// Qi guided attacks: Qi damage landing within reach of the marked target also hits it
		if (!guidedEcho && !GUIDED_TARGET.isEmpty() && QiEffects.isDealingQiDamage() && event.getSource().getEntity() instanceof ServerPlayer user
				&& running(user, PhysicalSubKits.GUIDED)) {
			ServerPlayer t = guidedTarget(user);
			if (t != null && t != hurt && hurt.getBoundingBox().inflate(PhysicalConfig.IN_PB1_REACH.get()).intersects(t.getBoundingBox())) {
				float amount = event.getNewDamage();
				later(() -> {
					guidedEcho = true;
					try {
						if (t.isAlive())
							QiEffects.dealDamage(user, t, amount);
					} finally {
						guidedEcho = false;
					}
				});
			}
		}
	}

	// ================================================================== ticking

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		trackMove(p);
		if ((p.tickCount & 3) == 0 && !isSword(p.getMainHandItem()))
			PhysicalSubKits.removeWarriorAttributes(p); // no lingering bonus after switching off the sword
		if (p.onGround() && !NO_FALL.isEmpty()) {
			Long set = NO_FALL.get(p.getUUID());
			if (set != null && now(p) - set > 5)
				NO_FALL.remove(p.getUUID()); // landed without a fall-damage event
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (!LATER.isEmpty()) {
			List<Runnable> run = new ArrayList<>(LATER);
			LATER.clear();
			run.forEach(Runnable::run);
		}
		if (!FLIGHTS.isEmpty()) {
			List<Flight> done = new ArrayList<>();
			for (Flight f : new ArrayList<>(FLIGHTS))
				if (tickFlight(f))
					done.add(f);
			FLIGHTS.removeAll(done);
		}
		if (!PUNCHES.isEmpty())
			PUNCHES.removeIf(PhysicalKitEvents::tickPunch);
		if (!KRATOS_JOBS.isEmpty())
			for (UUID id : new ArrayList<>(KRATOS_JOBS.keySet())) {
				ServerPlayer p = event.getServer().getPlayerList().getPlayer(id);
				if (p == null) {
					KRATOS_JOBS.remove(id);
					continue;
				}
				int t = KRATOS_JOBS.merge(id, 1, Integer::sum);
				if (t > 8 && p.onGround()) {
					KRATOS_JOBS.remove(id);
					kratosLanded(p);
				}
			}
		if (!BLEEDS.isEmpty() && event.getServer().getTickCount() % 5 == 0) {
			long now = event.getServer().overworld().getGameTime();
			BLEEDS.removeIf(b -> {
				if (!b.e.isAlive() || now >= b.until)
					return true;
				if (now < b.next)
					return false;
				b.next = now + 20;
				float dmg = PhysicalConfig.WR_AB2_BLEED_DAMAGE.get().floatValue();
				ServerPlayer src = event.getServer().getPlayerList().getPlayer(b.source);
				if (src != null && src.level() == b.e.level())
					QiEffects.dealDamage(src, b.e, dmg);
				else
					b.e.hurt(b.e.damageSources().generic(), dmg);
				if (b.e.level() instanceof ServerLevel sl)
					sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, b.e.getX(), b.e.getY() + 1, b.e.getZ(), 3, 0.2, 0.3, 0.2, 0.05);
				return false;
			});
		}
	}

	// ================================================================== form leave / cleanup

	static void leaveWarrior(ServerPlayer p) {
		PhysicalSubKits.removeWarriorAttributes(p);
		CRIT_READY.remove(p.getUUID());
		SIDESTEP_BONUS.remove(p.getUUID());
		STACKS.remove(p.getUUID());
		removeBanner(p);
	}

	static void leaveBody(ServerPlayer p) {
		HARDENED.clear(p);
		TimedAttributes.remove(p, PhysicalSubKits.BD_STEP);
		PUNCHES.removeIf(x -> x.p == p);
		KRATOS_JOBS.remove(p.getUUID());
	}

	static void leaveInstinct(ServerPlayer p) {
		STREAKS.remove(p.getUUID());
		DODGING.remove(p.getUUID());
		GUIDED_TARGET.remove(p.getUUID());
	}

	private static void clear(UUID id) {
		LAST_FREE.remove(id);
		MOBILITY_LOCK.remove(id);
		REINFORCE_LOCK.remove(id);
		MOVES.remove(id);
		HARDENED.clear(id);
		COMBO.remove(id);
		MY_FIST.remove(id);
		DEFLECTS.remove(id);
		FLIGHTS.removeIf(f -> f.p.getUUID().equals(id));
		NO_FALL.remove(id);
		PARRIES.remove(id);
		CRIT_READY.remove(id);
		LAST_PARRY.remove(id);
		SIDESTEP_BONUS.remove(id);
		BANNER_BUFF.remove(id);
		STACKS.remove(id);
		ATTACK_SCALE.remove(id);
		LAST_CRIT.remove(id);
		Banner b = BANNERS.remove(id);
		if (b != null)
			EarthWorld.revertGroup(b.group());
		BLEEDS.removeIf(x -> x.e.getUUID().equals(id));
		PUNCHES.removeIf(x -> x.p.getUUID().equals(id));
		KRATOS_JOBS.remove(id);
		DASHES.remove(id);
		DODGING.remove(id);
		STREAKS.remove(id);
		SHIELD_BLOCKS.remove(id);
		UNCHAINED_HITS.remove(id);
		GUIDED_TARGET.remove(id);
		CastRules.clear(id);
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

	public static void resetAll() {
		LATER.clear();
		LAST_FREE.clear();
		MOBILITY_LOCK.clear();
		REINFORCE_LOCK.clear();
		MOVES.clear();
		HARDENED.clearAll();
		COMBO.clear();
		MY_FIST.clear();
		DEFLECTS.clear();
		FLIGHTS.clear();
		NO_FALL.clear();
		PARRIES.clear();
		CRIT_READY.clear();
		LAST_PARRY.clear();
		SIDESTEP_BONUS.clear();
		BANNER_BUFF.clear();
		STACKS.clear();
		ATTACK_SCALE.clear();
		LAST_CRIT.clear();
		BANNERS.clear();
		BLEEDS.clear();
		PUNCHES.clear();
		KRATOS_JOBS.clear();
		DASHES.clear();
		DODGING.clear();
		STREAKS.clear();
		SHIELD_BLOCKS.clear();
		UNCHAINED_HITS.clear();
		GUIDED_TARGET.clear();
		guidedEcho = false;
	}
}
