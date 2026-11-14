package net.zyren.cultivation.ability.kits;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.CastRules;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiCosts;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.SpaceConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreState;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.network.SensedPlayersMessage;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.CoreBreak;
import net.zyren.cultivation.system.CoreLife;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Event side of the Space kits (SpaceKits). Anything that hurts or moves an entity from inside a
 * damage event is deferred to the end of the server tick.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class SpaceKitEvents {
	private static final AspectType S = AspectType.SPACE;

	private SpaceKitEvents() {
	}

	private static boolean running(ServerPlayer p, Ability a) {
		return KitParts.running(p, a);
	}

	private static long now(ServerPlayer p) {
		return p.serverLevel().getGameTime();
	}

	private static void endAbility(ServerPlayer p, Ability a) {
		AbilityState.of(p).endSustained(a, now(p));
	}

	private static final List<Runnable> LATER = new ArrayList<>();

	private static void later(Runnable r) {
		LATER.add(r);
	}

	static void poof(ServerPlayer p) {
		p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
		p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.1f);
	}

	record Point(ServerLevel level, Vec3 pos) {
	}

	// ================================================================== "next hit" charges (with optional time limit)

	static final class Arm {
		private record State(int left, long until) {
		}

		private final String buff;
		private final Supplier<Ability> owner;
		private final Map<UUID, State> map = new HashMap<>();

		Arm(String buff, Supplier<Ability> owner) {
			this.buff = buff;
			this.owner = owner;
		}

		/** @param ticks time limit (0 = until used) */
		void start(ServerPlayer p, int hits, int ticks) {
			long until = ticks > 0 ? now(p) + ticks : Long.MAX_VALUE;
			AbilityState.of(p).addBuff(new Buff(buff, S, 1.0, 0, false, until));
			map.put(p.getUUID(), new State(hits, until));
		}

		int left(ServerPlayer p) {
			State s = map.get(p.getUUID());
			if (s == null)
				return 0;
			AbilityState st = AbilityState.of(p);
			if (now(p) >= s.until() || !st.hasBuff(buff, now(p)) || !KitResolver.isCurrent(p, owner.get()) || st.isNullified(S, now(p))) {
				map.remove(p.getUUID());
				return 0;
			}
			return s.left();
		}

		void use(ServerPlayer p) {
			State s = map.get(p.getUUID());
			if (s == null)
				return;
			if (s.left() > 1)
				map.put(p.getUUID(), new State(s.left() - 1, s.until()));
			else {
				map.remove(p.getUUID());
				AbilityState.of(p).removeBuff(buff);
			}
		}

		void clear(UUID id) {
			map.remove(id);
		}

		void clearAll() {
			map.clear();
		}
	}

	static final Arm BLADE_ARM = new Arm("space_blade", () -> SpaceKits.BLADE);
	static final Arm ALT_ARM = new Arm("space_alt_reality", () -> SpaceKits.ALT);
	static final Arm VOID_ARM = new Arm("space_void_attack", () -> SpaceKits.VOID);
	static final Arm FOLD_ARM = new Arm("space_fold_punch", () -> SpaceKits.FOLD);
	static final Arm SPIKE_ARM = new Arm("space_gravity_spike", () -> SpaceKits.SPIKE);

	// ================================================================== cast rules (free Rift Step in an Event Horizon)

	private static final Set<UUID> HORIZONS = new HashSet<>();
	private static final Map<UUID, Long> LAST_FREE_STEP = new HashMap<>();

	/** Inside a running Event Horizon of yours, or of an allied player (as an Energy user). */
	static boolean inHorizon(ServerPlayer p) {
		if (HORIZONS.isEmpty() || KitResolver.formInUse(p) != SpaceKits.ENERGY)
			return false;
		for (UUID id : HORIZONS) {
			ServerPlayer o = p.server.getPlayerList().getPlayer(id);
			if (o == null || o.level() != p.level() || !running(o, SpaceKits.HORIZON))
				continue;
			if ((o == p || Targeting.isAllied(o, p)) && o.distanceTo(p) <= SpaceConfig.EN_AB2_RADIUS.get())
				return true;
		}
		return false;
	}

	static final CastRules.Rule RULES = new CastRules.Rule() {
		@Override
		public ActionResult refuse(ServerPlayer p, Ability a, long now) {
			if (a == SpaceKits.RIFT_STEP && inHorizon(p)) {
				Long last = LAST_FREE_STEP.get(p.getUUID());
				if (last != null && now - last < SpaceConfig.EN_AB2_FREE_GAP_SECONDS.get() * 20L)
					return ActionResult.fail("Rift step: wait " + (SpaceConfig.EN_AB2_FREE_GAP_SECONDS.get() * 20L - (now - last) + 19) / 20 + "s.");
			}
			return null;
		}

		@Override
		public boolean free(ServerPlayer p, Ability a, long now) {
			return a == SpaceKits.RIFT_STEP && inHorizon(p);
		}

		@Override
		public void used(ServerPlayer p, Ability a, long now) {
			if (a == SpaceKits.RIFT_STEP && inHorizon(p))
				LAST_FREE_STEP.put(p.getUUID(), now);
		}
	};

	// ================================================================== DEFAULT: chorus fruit (temporary item)

	static final String TEMP_CHORUS = "space_chorus";

	/** AirSubEvents removes temp items a player isn't entitled to. */
	static boolean chorusEntitled(ServerPlayer p) {
		return AspectData.of(p).getActive() == S && SpaceKits.on(p, SpaceKits.PASSIVE);
	}

	private static final Map<UUID, Long> CHORUS_GIVEN = new HashMap<>();

	private static void tickChorus(ServerPlayer p) {
		if (!chorusEntitled(p))
			return;
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++)
			if (inv.getItem(i).is(Items.CHORUS_FRUIT))
				return;
		long now = now(p);
		Long last = CHORUS_GIVEN.get(p.getUUID());
		if (last != null && now - last < SpaceConfig.PASSIVE_CHORUS_SECONDS.get() * 20L)
			return;
		ItemStack fruit = new ItemStack(Items.CHORUS_FRUIT);
		CustomData.update(DataComponents.CUSTOM_DATA, fruit, tag -> tag.putString(AirSubEvents.TEMP, TEMP_CHORUS));
		if (inv.add(fruit))
			CHORUS_GIVEN.put(p.getUUID(), now);
	}

	// ================================================================== DEFAULT: mind scanning / scans

	private static final Map<String, Long> SCAN_SEEN = new HashMap<>();

	static String describe(ServerPlayer o) {
		AspectType a = AspectData.of(o).getActive();
		SubAspectType sub = KitResolver.formInUse(o);
		String aspect = a == null ? "no aspect" : a.displayName() + (sub != null ? " → " + sub.displayName() : "");
		return Math.round(CoreLife.health(o)) + "/" + Math.round(CoreLife.maxHealth(o)) + " hp · " + Math.round(QiData.of(o).getCoreAmount()) + " Qi · " + aspect;
	}

	// ================================================================== DEFAULT: spatial anchor

	private record Anchor(ResourceKey<Level> dim, Vec3 pos, long until) {
	}

	private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();

	static void setAnchor(ServerPlayer p) {
		ANCHORS.put(p.getUUID(), new Anchor(p.level().dimension(), p.position(), now(p) + SpaceConfig.PB1_SECONDS.get() * 20L));
		p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.2, p.getZ(), 20, 0.3, 0.1, 0.3, 0.02);
	}

	private static Anchor anchor(ServerPlayer p) {
		Anchor a = ANCHORS.get(p.getUUID());
		if (a != null && now(p) >= a.until()) {
			ANCHORS.remove(p.getUUID());
			p.displayClientMessage(Component.literal("Your spatial anchor faded."), true);
			return null;
		}
		return a;
	}

	private static boolean returnToAnchor(ServerPlayer p) {
		Anchor a = anchor(p);
		if (a == null)
			return false;
		ServerLevel level = p.server.getLevel(a.dim());
		if (level == null)
			return false;
		ANCHORS.remove(p.getUUID());
		poof(p);
		if (level == p.serverLevel())
			p.teleportTo(a.pos().x, a.pos().y, a.pos().z);
		else
			p.teleportTo(level, a.pos().x, a.pos().y, a.pos().z, p.getYRot(), p.getXRot());
		p.fallDistance = 0;
		p.displayClientMessage(Component.literal("Returned to your spatial anchor."), true);
		return true;
	}

	/** Key 'Space Anchor Recall' (SpaceActionRequest). */
	public static void recallAnchor(ServerPlayer p) {
		if (anchor(p) == null) {
			p.displayClientMessage(Component.literal("You have no spatial anchor."), true);
			return;
		}
		if (PhysicalKitEvents.mobilityLocked(p)) {
			p.displayClientMessage(Component.literal("You can't move like that right now."), true);
			return;
		}
		returnToAnchor(p);
	}

	// ================================================================== DEFAULT: relocation / blink plans

	static final Map<UUID, BlockPos> PLANNED = new HashMap<>();

	private record Dest(ServerLevel level, BlockPos pos) {
	}

	private static final Map<UUID, Dest> RELOC = new HashMap<>();

	/** Nearest safe spot within range, else the bed (respawn point). */
	static boolean planRelocation(ServerPlayer p) {
		BlockPos s = SpaceWorld.nearestSafe(p, SpaceConfig.PB2_RANGE.get());
		if (s != null) {
			RELOC.put(p.getUUID(), new Dest(p.serverLevel(), s));
			return true;
		}
		BlockPos bed = p.getRespawnPosition();
		ServerLevel bedLevel = bed == null ? null : p.server.getLevel(p.getRespawnDimension());
		if (bedLevel == null)
			return false;
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++)
				for (int dy = -1; dy <= 2; dy++) {
					BlockPos c = bed.offset(dx, dy, dz);
					if (SpaceWorld.standable(bedLevel, c)) {
						RELOC.put(p.getUUID(), new Dest(bedLevel, c));
						return true;
					}
				}
		return false;
	}

	static boolean relocate(ServerPlayer p) {
		Dest d = RELOC.remove(p.getUUID());
		if (d == null && planRelocation(p))
			d = RELOC.remove(p.getUUID());
		if (d == null)
			return false;
		poof(p);
		SpaceWorld.teleport(p, d.level(), d.pos());
		return true;
	}

	// ================================================================== DEFAULT: Hi there

	static final Map<UUID, Integer> HI_TICKS = new HashMap<>();

	static void hiThereTick(ServerPlayer p) {
		int t = HI_TICKS.merge(p.getUUID(), 1, Integer::sum);
		if (t % Math.max(1, SpaceConfig.AB2_INTERVAL.get()) != 0)
			return;
		List<ServerPlayer> near = SpaceKits.unalliedWithin(p, p.position(), SpaceConfig.AB2_RADIUS.get());
		if (near.isEmpty() || PhysicalKitEvents.mobilityLocked(p))
			return;
		ServerPlayer target = near.get(p.getRandom().nextInt(near.size()));
		Vec3 at = SpaceWorld.behind(p, target, 2);
		if (at == null)
			return;
		poof(p);
		p.teleportTo(at.x, at.y, at.z);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		p.fallDistance = 0;
		p.displayClientMessage(Component.literal("Hi there, " + target.getGameProfile().getName() + "."), true);
	}

	// ================================================================== DIMENSIONAL: passive travel + modes + nexus

	private static final Map<UUID, Integer> DM_MODES = new HashMap<>();
	private static final Map<UUID, Long> DM_USED = new HashMap<>(), DM_MODE_SWITCHED = new HashMap<>();

	static int mode(ServerPlayer p) {
		return DM_MODES.getOrDefault(p.getUUID(), 1);
	}

	private static ResourceKey<Level> destination(ServerPlayer p, int mode) {
		ResourceKey<Level> here = p.level().dimension();
		if (here == Level.NETHER)
			return mode == 1 ? Level.OVERWORLD : Level.END;
		if (here == Level.END)
			return mode == 1 ? Level.NETHER : Level.OVERWORLD;
		return mode == 1 ? Level.NETHER : Level.END;
	}

	/** The mode a nexus point replaces from this dimension (Overworld / Nether: the End option; End: the Nether option). */
	private static int replacedMode(ServerPlayer p) {
		return p.level().dimension() == Level.END ? 1 : 2;
	}

	private static String modeName(ServerPlayer p, int mode) {
		if (mode == replacedMode(p) && nexusFor(p) != null)
			return "nexus point";
		ResourceKey<Level> d = destination(p, mode);
		return d == Level.NETHER ? "the Nether" : d == Level.END ? "the End" : "the Overworld";
	}

	/** Mode key (V) while in the Dimensional form. */
	public static void switchMode(ServerPlayer p) {
		long now = now(p);
		Long last = DM_MODE_SWITCHED.get(p.getUUID());
		if (last != null && now - last < 10)
			return;
		DM_MODE_SWITCHED.put(p.getUUID(), now);
		int m = mode(p) == 1 ? 2 : 1;
		DM_MODES.put(p.getUUID(), m);
		p.displayClientMessage(Component.literal("Dimensional: mode " + m + " → " + modeName(p, m)), true);
	}

	static final class Nexus {
		ServerLevel level;
		Vec3 pos;
		int uses;
	}

	static final Map<UUID, Nexus> NEXUS = new HashMap<>();

	static void placeNexus(ServerPlayer p) {
		Nexus n = new Nexus();
		n.level = p.serverLevel();
		n.pos = p.position();
		n.uses = SpaceConfig.DM_AB1_USES.get();
		NEXUS.put(p.getUUID(), n);
	}

	static void nexusTick(ServerPlayer p) {
		Nexus n = NEXUS.get(p.getUUID());
		if (n == null || n.uses <= 0) {
			endAbility(p, SpaceKits.NEXUS);
			return;
		}
		n.level.sendParticles(ParticleTypes.REVERSE_PORTAL, n.pos.x, n.pos.y + 1, n.pos.z, 25, 0.3, 1, 0.3, 0.05);
	}

	/** An allied (or own) nexus with uses left, or null. */
	private static Map.Entry<UUID, Nexus> nexusFor(ServerPlayer p) {
		for (Map.Entry<UUID, Nexus> en : NEXUS.entrySet()) {
			ServerPlayer owner = p.server.getPlayerList().getPlayer(en.getKey());
			if (owner == null || en.getValue().uses <= 0 || !running(owner, SpaceKits.NEXUS))
				continue;
			if (owner == p || Targeting.isAllied(owner, p))
				return en;
		}
		return null;
	}

	/** Sneak + right-click air with an empty hand (SpaceActionRequest). */
	public static void dimensionalJump(ServerPlayer p) {
		if (!p.isShiftKeyDown() || !p.getMainHandItem().isEmpty() || AspectData.of(p).getActive() != S || KitResolver.formInUse(p) != SpaceKits.DIMENSIONAL
				|| !SpaceKits.on(p, SpaceKits.DM_PASSIVE))
			return;
		long now = now(p);
		Long last = DM_USED.get(p.getUUID());
		if (last != null && now - last < SpaceConfig.DM_PASSIVE_GAP_TICKS.get())
			return;
		if (PhysicalKitEvents.mobilityLocked(p)) {
			p.displayClientMessage(Component.literal("You can't move like that right now."), true);
			return;
		}
		double cost = QiCosts.effective(p, SpaceConfig.DM_PASSIVE_COST.get());
		int m = mode(p);
		Map.Entry<UUID, Nexus> nexus = m == replacedMode(p) ? nexusFor(p) : null;
		ServerLevel dest;
		BlockPos to;
		if (nexus != null) {
			dest = nexus.getValue().level;
			to = BlockPos.containing(nexus.getValue().pos);
		} else {
			dest = p.server.getLevel(destination(p, m));
			if (dest == null) {
				p.displayClientMessage(Component.literal("That dimension isn't available."), true);
				return;
			}
			to = null;
		}
		if (!QiCoreStorage.trySpendQi(p, cost)) {
			p.displayClientMessage(Component.literal("Not enough Qi (" + Math.round(cost) + ")."), true);
			return;
		}
		DM_USED.put(p.getUUID(), now);
		if (to == null)
			to = SpaceWorld.arrival(p, dest);
		else
			nexus.getValue().uses--;
		poof(p);
		SpaceWorld.teleport(p, dest, to);
	}

	// ================================================================== DIMENSIONAL: rift

	static final Map<UUID, Point> RIFTS = new HashMap<>();
	private static final Map<UUID, Integer> RIFT_TICKS = new HashMap<>();

	static void riftTick(ServerPlayer p) {
		Point r = RIFTS.get(p.getUUID());
		if (r == null || r.level() != p.level())
			return;
		int t = RIFT_TICKS.merge(p.getUUID(), 1, Integer::sum);
		Vec3 c = r.pos();
		double inner = SpaceConfig.DM_AB2_INNER.get(), outer = SpaceConfig.DM_AB2_OUTER.get();
		for (LivingEntity e : PhysicalDefaultKit.around(p, c, outer, false)) {
			if (!QiEffects.canAffect(e, false))
				continue;
			double d = e.position().distanceTo(c);
			if (d <= inner) {
				Motion.pullToward(e, c, d, 0.4);
				if (t % 2 == 0)
					QiEffects.dealDamage(p, e, SpaceConfig.DM_AB2_DAMAGE.get().floatValue());
			} else
				Motion.pullToward(e, c, 1.0, 0.1); // very slow draw
		}
		List<SensedPlayersMessage.Entry> hud = new ArrayList<>();
		for (ServerPlayer o : SpaceKits.unalliedWithin(p, c, SpaceConfig.DM_AB2_DRAIN_RADIUS.get())) {
			QiData q = QiData.of(o);
			if (q.getCoreLevel() >= 1)
				q.setCoreAmount(Math.max(0, q.getCoreAmount() - SpaceConfig.DM_AB2_DRAIN.get()));
			hud.add(new SensedPlayersMessage.Entry(o.getGameProfile().getName(), cut(describe(o)), o.getX(), o.getZ()));
		}
		SensedPlayersMessage.send(p, hud);
		r.level().sendParticles(ParticleTypes.PORTAL, c.x, c.y + 1, c.z, 60, inner, 1, inner, 0.5);
		r.level().sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 1, c.z, 10, 0.3, 0.3, 0.3, 0.01);
	}

	private static String cut(String s) {
		return s.length() > 64 ? s.substring(0, 63) + "…" : s;
	}

	// ================================================================== ABYSSAL

	static final Map<UUID, UUID> DRAINING = new HashMap<>();

	static void slowDeathTick(ServerPlayer p) {
		UUID id = DRAINING.get(p.getUUID());
		ServerPlayer t = id == null ? null : p.server.getPlayerList().getPlayer(id);
		if (t == null || !t.isAlive() || t.level() != p.level() || t.distanceTo(p) > SpaceConfig.AB_A2_RANGE.get()) {
			endAbility(p, SpaceKits.SLOW_DEATH);
			return;
		}
		QiData q = QiData.of(t);
		double take = Math.min(SpaceConfig.AB_A2_DRAIN.get(), q.getCoreAmount());
		if (take <= 0) {
			endAbility(p, SpaceKits.SLOW_DEATH);
			return;
		}
		q.setCoreAmount(q.getCoreAmount() - take);
		p.heal((float) (take / Math.max(0.0001, SpaceConfig.AB_A2_QI_PER_HEALTH.get())));
		p.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL, t.getX(), t.getY() + 1, t.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
		if (CoreLife.health(p) >= CoreLife.maxHealth(p)) {
			QiEffects.dealDamage(p, t, SpaceConfig.AB_A2_FINAL.get().floatValue());
			endAbility(p, SpaceKits.SLOW_DEATH);
		}
	}

	static void suctionTick(ServerPlayer p) {
		List<SensedPlayersMessage.Entry> hud = new ArrayList<>();
		double glow = SpaceConfig.AB_AB1_GLOW_RADIUS.get(), slow = SpaceConfig.AB_AB1_SLOW_RADIUS.get(), drainR = SpaceConfig.AB_AB1_DRAIN_RADIUS.get();
		double gained = 0;
		for (ServerPlayer o : p.serverLevel().players()) {
			if (!Targeting.isValid(p, o) || o.distanceTo(p) > glow)
				continue;
			QiEffects.applyEffect(o, MobEffects.GLOWING, 1, 40, false);
			AspectType a = AspectData.of(o).getActive();
			SubAspectType sub = KitResolver.formInUse(o);
			hud.add(new SensedPlayersMessage.Entry(o.getGameProfile().getName(), a == null ? "no aspect" : a.displayName() + (sub != null ? " → " + sub.displayName() : ""),
					o.getX(), o.getZ()));
			if (Targeting.isAllied(p, o))
				continue;
			if (o.distanceTo(p) <= slow)
				QiEffects.applyEffect(o, MobEffects.MOVEMENT_SLOWDOWN, SpaceConfig.AB_AB1_SLOW_LEVEL.get(), 30, false);
			if (o.distanceTo(p) <= drainR) {
				QiData q = QiData.of(o);
				double take = Math.min(SpaceConfig.AB_AB1_DRAIN.get(), q.getCoreAmount());
				if (take > 0) {
					q.setCoreAmount(q.getCoreAmount() - take);
					gained += take;
				}
			}
		}
		if (gained > 0)
			QiCoreStorage.addQi(p, gained * SpaceConfig.AB_AB1_GAIN.get());
		SensedPlayersMessage.send(p, hud);
	}

	private static final Map<UUID, Long> CLEANSE_AT = new HashMap<>();
	private static final Set<UUID> ERASED = new HashSet<>();

	private static boolean tainted(ServerPlayer o) {
		CoreState s = QiData.of(o).getCoreState();
		return s == CoreState.CORRUPTED || s == CoreState.POISONED;
	}

	static ActionResult err(ServerPlayer p) {
		double killR = SpaceConfig.AB_AB2_KILL_RADIUS.get(), resetR = SpaceConfig.AB_AB2_RESET_RADIUS.get(), cleanR = SpaceConfig.AB_AB2_CLEANSE_RADIUS.get();
		int killed = 0, reset = 0, cleansed = 0;
		for (ServerPlayer o : new ArrayList<>(p.serverLevel().players())) {
			if (o == p || !o.isAlive() || o.isSpectator())
				continue;
			double d = o.distanceTo(p);
			if (d > cleanR)
				continue;
			boolean shifted = running(o, SpaceKits.SHIFT); // dimentional shift: unkillable, even by this
			if (d <= killR && !shifted) {
				ERASED.add(o.getUUID());
				o.getInventory().clearContent();
				o.kill();
				killed++;
				continue;
			}
			if (d <= resetR) {
				QiEffects.dealDamageBypassingReinforce(p, o, SpaceConfig.AB_AB2_DAMAGE.get().floatValue());
				if (tainted(o))
					QiData.of(o).setCoreState(CoreState.PURE); // the reset cleanses too (no Corrupted-core crater)
				if (CoreBreak.breakCore(o, "ERR"))
					reset++;
				continue;
			}
			if (tainted(o)) {
				QiEffects.nullifyAll(o, SpaceConfig.AB_AB2_CLEANSE_SECONDS.get() * 20);
				CLEANSE_AT.put(o.getUUID(), now(p) + SpaceConfig.AB_AB2_CLEANSE_SECONDS.get() * 20L);
				cleansed++;
			}
		}
		p.serverLevel().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
		p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2f, 0.5f);
		return ActionResult.ok("ERR: " + killed + " erased, " + reset + " reset, " + cleansed + " being cleansed.");
	}

	/** ERR: items of erased players are deleted, never dropped. */
	@SubscribeEvent
	public static void onDrops(LivingDropsEvent event) {
		if (!ERASED.isEmpty() && event.getEntity() instanceof ServerPlayer p && ERASED.remove(p.getUUID()))
			event.setCanceled(true);
	}

	// ================================================================== ENERGY

	private static final Set<UUID> SENSING = new HashSet<>();
	private static final Map<UUID, Vec3> STILL_POS = new HashMap<>();
	private static final Map<UUID, Integer> STILL_TICKS = new HashMap<>();
	private static final Map<UUID, Long> PHASE_UNTIL = new HashMap<>();

	private static void tickEnergy(ServerPlayer p, boolean second) {
		boolean on = AspectData.of(p).getActive() == S && SpaceKits.on(p, SpaceKits.EN_PASSIVE);
		if (second) {
			List<SensedPlayersMessage.Entry> list = new ArrayList<>();
			if (on)
				for (ServerPlayer o : p.serverLevel().players())
					if (Targeting.isValid(p, o) && o.distanceTo(p) <= SpaceConfig.EN_SENSE_RADIUS.get())
						list.add(new SensedPlayersMessage.Entry(o.getGameProfile().getName(), "nearby", o.getX(), o.getZ()));
			if (on || SENSING.contains(p.getUUID())) {
				if (!(AspectData.of(p).getActive() == S && (running(p, SpaceKits.RIFT) || running(p, SpaceKits.SUCTION)))) {
					SensedPlayersMessage.send(p, list);
					if (on)
						SENSING.add(p.getUUID());
					else
						SENSING.remove(p.getUUID());
				}
			}
		}
		if (!on)
			return;
		// phase: stand still a while, then walking into a 1-block wall passes through it
		Vec3 pos = p.position(), last = STILL_POS.put(p.getUUID(), pos);
		long now = now(p);
		if (last != null && last.distanceToSqr(pos) < 1.0E-4) {
			int t = STILL_TICKS.merge(p.getUUID(), 1, Integer::sum);
			if (t == SpaceConfig.EN_STILL_TICKS.get()) {
				PHASE_UNTIL.put(p.getUUID(), now + SpaceConfig.EN_PHASE_TICKS.get());
				p.displayClientMessage(Component.literal("Phase ready: walk into a thin wall."), true);
			}
		} else
			STILL_TICKS.remove(p.getUUID());
		Long until = PHASE_UNTIL.get(p.getUUID());
		if (until != null && now < until && p.horizontalCollision) {
			Vec3 dir = PhysicalDefaultKit.horizontal(p.getLookAngle());
			if (dir == Vec3.ZERO)
				return;
			Vec3 through = pos.add(dir.scale(1.6));
			Vec3 snapped = new Vec3(Math.floor(through.x) + 0.5, pos.y, Math.floor(through.z) + 0.5);
			if (p.serverLevel().noCollision(p, p.getBoundingBox().move(snapped.subtract(pos)))) {
				PHASE_UNTIL.remove(p.getUUID());
				p.teleportTo(snapped.x, snapped.y, snapped.z);
				p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
			}
		} else if (until != null && now >= until)
			PHASE_UNTIL.remove(p.getUUID());
	}

	/** Rift Step: blink along the look, stopping just before walls; landing next to a player hurts them. */
	static ActionResult riftStep(ServerPlayer p) {
		Vec3 dir = p.getLookAngle().normalize();
		Vec3 start = p.position(), at = start;
		double max = SpaceConfig.EN_PB1_DISTANCE.get();
		for (double d = 0.25; d <= max + 1.0E-6; d += 0.25) {
			Vec3 next = start.add(dir.scale(d));
			if (!p.serverLevel().noCollision(p, p.getBoundingBox().move(next.subtract(start))))
				break;
			at = next;
		}
		if (at.distanceToSqr(start) < 0.25) {
			BlockPos open = SpaceWorld.nearestSafe(p, 6);
			if (open == null)
				return ActionResult.fail("No room to step.");
			at = Vec3.atBottomCenterOf(open);
		}
		p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.8, 0.3, 0.05);
		p.teleportTo(at.x, at.y, at.z);
		p.fallDistance = 0;
		for (ServerPlayer o : SpaceKits.unalliedWithin(p, p.position(), 1.5)) {
			QiEffects.dealDamage(p, o, SpaceConfig.EN_PB1_LAND_DAMAGE.get().floatValue());
			QiEffects.applyEffect(o, MobEffects.BLINDNESS, 1, SpaceConfig.EN_PB1_BLIND_TICKS.get(), false);
		}
		return ActionResult.ok("Rift step.");
	}

	static final Map<UUID, Point> WELLS = new HashMap<>();

	static void wellTick(ServerPlayer p) {
		Point w = WELLS.get(p.getUUID());
		if (w == null || w.level() != p.level())
			return;
		double r = SpaceConfig.EN_PB2_RADIUS.get();
		for (ServerPlayer o : SpaceKits.unalliedWithin(p, w.pos(), r)) {
			if (!QiEffects.canAffect(o, false))
				continue;
			Motion.pullToward(o, w.pos(), 0.8, 0.15);
			TimedAttributes.apply(o, Attributes.JUMP_STRENGTH, "space_anchor_well", -SpaceConfig.EN_PB2_JUMP_CUT.get(),
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 15);
		}
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			w.level().sendParticles(ParticleTypes.PORTAL, w.pos().x + Math.cos(a) * r, w.pos().y + 0.2, w.pos().z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	/** Movement interrupted: dashes / throws / elytra. */
	private static void stopMovement(LivingEntity e) {
		Motion.cancel(e);
		if (e instanceof ServerPlayer sp) {
			if (sp.isFallFlying())
				sp.stopFallFlying();
			PhysicalKitEvents.cancelFlight(sp);
		}
	}

	static ActionResult shear(ServerPlayer p) {
		Vec3 dir = PhysicalDefaultKit.horizontal(p.getLookAngle());
		if (dir == Vec3.ZERO)
			dir = PhysicalDefaultKit.horizontal(Vec3.directionFromRotation(0, p.getYRot()));
		Vec3 side = new Vec3(-dir.z, 0, dir.x), o = p.position();
		double len = SpaceConfig.EN_AB1_LENGTH.get(), half = SpaceConfig.EN_AB1_WIDTH.get() / 2.0;
		int lock = SpaceConfig.EN_AB1_LOCK_SECONDS.get() * 20, n = 0;
		for (LivingEntity e : PhysicalDefaultKit.around(p, o, len + half + 1, false)) {
			Vec3 rel = e.position().subtract(o);
			double along = rel.dot(dir), across = Math.abs(rel.dot(side));
			if (along < 0 || along > len || across > half + e.getBbWidth() / 2 || Math.abs(rel.y) > 4)
				continue;
			QiEffects.dealDamage(p, e, SpaceConfig.EN_AB1_DAMAGE.get().floatValue());
			if (QiEffects.canAffect(e, false)) {
				Stun.apply(e, lock);
				stopMovement(e);
				if (e instanceof ServerPlayer sp)
					PhysicalKitEvents.lockMobility(sp, lock);
			}
			n++;
		}
		for (double d = 1; d <= len; d += 0.5)
			for (double s = -half; s <= half; s += 1)
				p.serverLevel().sendParticles(ParticleTypes.END_ROD, o.x + dir.x * d + side.x * s, o.y + 1, o.z + dir.z * d + side.z * s, 1, 0, 0.3, 0, 0);
		return ActionResult.ok("Dimensional shear: " + n + " hit.");
	}

	private static final Map<UUID, Integer> HORIZON_TICKS = new HashMap<>();

	static void horizonTick(ServerPlayer p) {
		HORIZONS.add(p.getUUID());
		int t = HORIZON_TICKS.merge(p.getUUID(), 1, Integer::sum);
		double r = SpaceConfig.EN_AB2_RADIUS.get();
		for (ServerPlayer o : SpaceKits.unalliedWithin(p, p.position(), r)) {
			if (t % 2 == 0)
				QiEffects.dealDamage(p, o, SpaceConfig.EN_AB2_DAMAGE.get().floatValue());
			PhysicalKitEvents.lockMobility(o, 30);
			PhysicalKitEvents.breakReinforce(o, 2);
		}
		for (int i = 0; i < 40; i++) {
			double a = p.getRandom().nextDouble() * Math.PI * 2, b = p.getRandom().nextDouble() * Math.PI - Math.PI / 2;
			p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX() + Math.cos(a) * Math.cos(b) * r, p.getY() + 1 + Math.sin(b) * r,
					p.getZ() + Math.sin(a) * Math.cos(b) * r, 1, 0, 0, 0, 0);
		}
	}

	static void horizonEnd(ServerPlayer p) {
		HORIZONS.remove(p.getUUID());
		HORIZON_TICKS.remove(p.getUUID());
		if (!p.isAlive())
			return;
		Vec3 c = p.position();
		for (ServerPlayer o : SpaceKits.unalliedWithin(p, c, SpaceConfig.EN_AB2_RADIUS.get())) {
			QiEffects.dealDamage(p, o, SpaceConfig.EN_AB2_FINAL.get().floatValue());
			if (QiEffects.canAffect(o, false))
				Motion.pullToward(o, c, o.position().distanceTo(c), 0.8);
		}
		p.serverLevel().sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 3, 1, 1, 1, 0);
	}

	// ================================================================== events: outgoing

	/** Next-hit charges. HIGH: after empowered hits (HIGHEST), before output multipliers (LOW). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (AspectData.of(attacker).getActive() != S || AbilityState.of(attacker).isNullified(S, now(attacker)))
			return;
		if (!WaterEvents.isMelee(event.getSource(), attacker))
			return;
		float dmg = event.getNewDamage();
		if (BLADE_ARM.left(attacker) > 0) {
			float hp = target instanceof ServerPlayer tp ? CoreLife.health(tp) : target.getHealth();
			dmg += (float) Math.min(hp * SpaceConfig.A1_FRACTION.get(), SpaceConfig.A1_CAP.get());
			BLADE_ARM.use(attacker);
		}
		if (ALT_ARM.left(attacker) > 0) {
			ALT_ARM.use(attacker);
			int ticks = SpaceConfig.DM_A2_SECONDS.get() * 20;
			later(() -> {
				if (!target.isAlive() || !QiEffects.canAffect(target, false))
					return;
				SpaceKits.hidden(target, MobEffects.WEAKNESS, 255, ticks);
				SpaceKits.hidden(target, MobEffects.DIG_SLOWDOWN, 1, ticks);
				SpaceKits.hidden(target, MobEffects.DAMAGE_RESISTANCE, 255, ticks);
				SpaceKits.hidden(target, MobEffects.INVISIBILITY, 1, ticks);
				SpaceKits.hidden(target, MobEffects.BLINDNESS, 1, ticks);
			});
		}
		if (VOID_ARM.left(attacker) > 0) {
			dmg += (attacker.getMainHandItem().isEmpty() ? SpaceConfig.AB_A1_FIST.get() : SpaceConfig.AB_A1_WEAPON.get()).floatValue();
			VOID_ARM.use(attacker);
			int lvl = SpaceConfig.AB_A1_SLOW_LEVEL.get(), ticks = SpaceConfig.AB_A1_SLOW_SECONDS.get() * 20;
			later(() -> QiEffects.applyEffect(target, MobEffects.MOVEMENT_SLOWDOWN, lvl, ticks, false));
		}
		if (FOLD_ARM.left(attacker) > 0) {
			FOLD_ARM.use(attacker);
			Vec3 look = attacker.getLookAngle();
			Vec3 dir = Math.abs(attacker.getXRot()) > 60 ? new Vec3(0, 1, 0) : look.normalize();
			later(() -> fold(attacker, target, dir));
		}
		if (SPIKE_ARM.left(attacker) > 0) {
			SPIKE_ARM.use(attacker);
			if (!target.onGround()) {
				dmg += SpaceConfig.EN_A2_AIR_DAMAGE.get().floatValue();
				later(() -> {
					if (target.isAlive() && QiEffects.canAffect(target, false))
						Motion.slam(target, 2.0, 60);
				});
			} else {
				dmg += SpaceConfig.EN_A2_GROUND_DAMAGE.get().floatValue();
				int root = SpaceConfig.EN_A2_ROOT_TICKS.get();
				later(() -> {
					if (target.isAlive() && QiEffects.canAffect(target, false))
						Stun.apply(target, root); // Reinforce blocks only the root
				});
			}
		}
		event.setNewDamage(dmg);
	}

	private static void fold(ServerPlayer attacker, LivingEntity t, Vec3 dir) {
		if (!t.isAlive() || !QiEffects.canAffect(t, false))
			return;
		Vec3 start = t.position(), at = start;
		boolean blocked = false;
		double max = SpaceConfig.EN_A1_DISTANCE.get();
		for (double d = 0.25; d <= max + 1.0E-6; d += 0.25) {
			Vec3 next = start.add(dir.scale(d));
			if (!t.level().noCollision(t, t.getBoundingBox().move(next.subtract(start)))) {
				blocked = true;
				break;
			}
			at = next;
		}
		t.teleportTo(at.x, at.y, at.z);
		if (t.level() instanceof ServerLevel sl)
			sl.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 15, 0.3, 0.5, 0.3, 0.05);
		if (blocked) {
			QiEffects.dealDamage(attacker, t, SpaceConfig.EN_A1_WALL_DAMAGE.get().floatValue());
			Stun.apply(t, SpaceConfig.EN_A1_ROOT_TICKS.get());
		}
	}

	/** Mind scanning: info on what you hit. */
	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		if (event.getNewDamage() <= 0)
			return;
		LivingEntity hurt = event.getEntity();
		// spatial anchor: at low health, back to the anchor
		if (hurt instanceof ServerPlayer v && ANCHORS.containsKey(v.getUUID()) && CoreLife.health(v) <= SpaceConfig.PB1_RETURN_HEALTH.get() && v.isAlive())
			later(() -> {
				if (v.isAlive())
					returnToAnchor(v);
			});
		if (!(event.getSource().getEntity() instanceof ServerPlayer user) || user == hurt || !running(user, SpaceKits.MIND))
			return;
		long now = now(user);
		String key = user.getUUID() + "|" + hurt.getUUID();
		Long last = SCAN_SEEN.get(key);
		if (last != null && now - last < SpaceConfig.A2_THROTTLE_TICKS.get())
			return;
		SCAN_SEEN.put(key, now);
		String info = hurt instanceof ServerPlayer o ? o.getGameProfile().getName() + ": " + describe(o)
				: hurt.getName().getString() + ": " + Math.round(hurt.getHealth()) + "/" + Math.round(hurt.getMaxHealth()) + " hp";
		user.displayClientMessage(Component.literal("[Scan] " + info).withStyle(ChatFormatting.LIGHT_PURPLE), false);
	}

	// ================================================================== events: incoming

	/** Energy: no fall damage. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && event.getSource().is(DamageTypes.FALL) && AspectData.of(p).getActive() == S
				&& SpaceKits.on(p, SpaceKits.EN_PASSIVE))
			event.setCanceled(true);
	}

	/** Lethal hits: dimentional shift (can't die, even from Abyssal), then the spatial anchor saves you. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLethal(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || event.getNewDamage() <= 0 || !CoreLife.isLethal(p, event.getNewDamage()))
			return;
		if (AspectData.of(p).getActive() == S && running(p, SpaceKits.SHIFT)) {
			event.setNewDamage(0);
			return;
		}
		if (anchor(p) != null) {
			event.setNewDamage(0);
			later(() -> {
				if (p.isAlive())
					returnToAnchor(p);
			});
		}
	}

	// ================================================================== ticking

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		long phase = now(p) + p.getId();
		boolean second = phase % 20 == 0;
		if (second)
			tickChorus(p);
		tickEnergy(p, second);
		Long cleanse = CLEANSE_AT.get(p.getUUID());
		if (cleanse != null && now(p) >= cleanse) {
			CLEANSE_AT.remove(p.getUUID());
			QiData.of(p).setCoreState(CoreState.PURE);
			net.zyren.cultivation.data.CorruptionData.of(p).setCorruption(0);
			p.displayClientMessage(Component.literal("Your core has been cleansed.").withStyle(ChatFormatting.AQUA), false);
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (LATER.isEmpty())
			return;
		List<Runnable> run = new ArrayList<>(LATER);
		LATER.clear();
		run.forEach(Runnable::run);
	}

	// ================================================================== form leave / cleanup

	static void leaveDimensional(ServerPlayer p) {
		ALT_ARM.clear(p.getUUID());
		RIFTS.remove(p.getUUID());
		NEXUS.remove(p.getUUID());
	}

	static void leaveAbyssal(ServerPlayer p) {
		VOID_ARM.clear(p.getUUID());
		DRAINING.remove(p.getUUID());
	}

	static void leaveEnergy(ServerPlayer p) {
		FOLD_ARM.clear(p.getUUID());
		SPIKE_ARM.clear(p.getUUID());
		WELLS.remove(p.getUUID());
		HORIZONS.remove(p.getUUID());
		PHASE_UNTIL.remove(p.getUUID());
	}

	private static void clear(UUID id) {
		for (Arm a : new Arm[] { BLADE_ARM, ALT_ARM, VOID_ARM, FOLD_ARM, SPIKE_ARM })
			a.clear(id);
		LAST_FREE_STEP.remove(id);
		HORIZONS.remove(id);
		HORIZON_TICKS.remove(id);
		CHORUS_GIVEN.remove(id);
		ANCHORS.remove(id);
		PLANNED.remove(id);
		RELOC.remove(id);
		HI_TICKS.remove(id);
		DM_MODES.remove(id);
		DM_USED.remove(id);
		DM_MODE_SWITCHED.remove(id);
		NEXUS.remove(id);
		RIFTS.remove(id);
		RIFT_TICKS.remove(id);
		DRAINING.remove(id);
		DRAINING.values().removeIf(id::equals);
		SENSING.remove(id);
		STILL_POS.remove(id);
		STILL_TICKS.remove(id);
		PHASE_UNTIL.remove(id);
		WELLS.remove(id);
		SCAN_SEEN.keySet().removeIf(k -> k.startsWith(id.toString()));
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && !event.isCanceled())
			clear(p.getUUID()); // CLEANSE_AT / ERASED survive the death on purpose
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		clear(event.getEntity().getUUID());
	}

	public static void resetAll() {
		LATER.clear();
		for (Arm a : new Arm[] { BLADE_ARM, ALT_ARM, VOID_ARM, FOLD_ARM, SPIKE_ARM })
			a.clearAll();
		HORIZONS.clear();
		HORIZON_TICKS.clear();
		LAST_FREE_STEP.clear();
		CHORUS_GIVEN.clear();
		SCAN_SEEN.clear();
		ANCHORS.clear();
		PLANNED.clear();
		RELOC.clear();
		HI_TICKS.clear();
		DM_MODES.clear();
		DM_USED.clear();
		DM_MODE_SWITCHED.clear();
		NEXUS.clear();
		RIFTS.clear();
		RIFT_TICKS.clear();
		DRAINING.clear();
		CLEANSE_AT.clear();
		ERASED.clear();
		SENSING.clear();
		STILL_POS.clear();
		STILL_TICKS.clear();
		PHASE_UNTIL.clear();
		WELLS.clear();
	}

	static AABB box(Vec3 c, double r) {
		return new AABB(c, c).inflate(r);
	}
}
