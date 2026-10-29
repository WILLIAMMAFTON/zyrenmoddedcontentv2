package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiCostDebuffs;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.WaterConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.network.RevealAspectsMessage;
import net.zyren.cultivation.system.AspectMastery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Event side of Water's sub-aspects (WaterSubKits). Every effect checks that its ability is still the
 * CURRENT one (KitResolver.isCurrent), so a form toggle / aspect switch ends it; buff-backed effects
 * also end on nullify (buffs are removed).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class WaterSubEvents {
	private static final AspectType W = AspectType.WATER;
	static final String MOON_KB = "water_moon_kb";
	private static final String RIPTIDE_SPEED = "water_moon_riptide";

	private WaterSubEvents() {
	}

	private static boolean running(ServerPlayer p, Ability a) {
		return AbilityState.of(p).isSustained(a) && KitResolver.isCurrent(p, a);
	}

	private static boolean buffed(ServerPlayer p, String buff, Ability a, long now) {
		return AbilityState.of(p).hasBuff(buff, now) && KitResolver.isCurrent(p, a) && !AbilityState.of(p).isNullified(W, now);
	}

	private static boolean inFreezingForm(ServerPlayer p) {
		return KitResolver.formInUse(p) == WaterSubKits.FREEZING;
	}

	// ================================================================== FREEZING

	private static List<String> biomeRaw;
	private static Set<ResourceLocation> biomes = Set.of();

	static boolean inColdBiome(ServerPlayer p) {
		List<? extends String> raw = WaterConfig.FRZ_BIOMES.get();
		if (!raw.equals(biomeRaw)) {
			Set<ResourceLocation> s = new HashSet<>();
			for (String id : raw) {
				ResourceLocation rl = ResourceLocation.tryParse(id.trim());
				if (rl != null)
					s.add(rl);
			}
			biomes = s;
			biomeRaw = new ArrayList<>(raw);
		}
		return p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> biomes.contains(k.location())).orElse(false);
	}

	/** Players in the Nether in Freezing form: when they arrived (Nausea after 5 min). */
	private static final Map<UUID, Long> NETHER_SINCE = new HashMap<>();

	private static void tickNetherDebuff(ServerPlayer p, long now) {
		if (!inFreezingForm(p) || p.level().dimension() != Level.NETHER) {
			NETHER_SINCE.remove(p.getUUID());
			return;
		}
		long since = NETHER_SINCE.computeIfAbsent(p.getUUID(), k -> now);
		EffectHelper.apply(p, MobEffects.WEAKNESS, WaterConfig.FRZ_NETHER_WEAKNESS.get(), 50, true);
		if (now - since >= WaterKit.seconds(WaterConfig.FRZ_NETHER_NAUSEA_AFTER.get()))
			EffectHelper.apply(p, MobEffects.CONFUSION, 1, 200, true);
	}

	/** Only stone-type blocks (natural stone + cobblestone, as Volcanic) block Freezing Blast's sight. */
	private static boolean stoneClear(ServerPlayer from, ServerPlayer to) {
		Vec3 a = from.getEyePosition(), b = to.getEyePosition();
		Vec3 step = b.subtract(a);
		double len = step.length();
		if (len < 1.0E-3)
			return true;
		step = step.scale(0.25 / len);
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		Vec3 pos = a;
		for (double d = 0; d < len; d += 0.25, pos = pos.add(step)) {
			m.set(pos.x, pos.y, pos.z);
			if (FireWorld.isStoneType(from.level().getBlockState(m)))
				return false;
		}
		return true;
	}

	/** Nearest unallied player within range whose line of sight isn't blocked by stone. */
	static ServerPlayer blastTarget(ServerPlayer p) {
		ServerPlayer best = null;
		for (ServerPlayer o : WaterKit.unalliedWithin(p, WaterConfig.FRZ_PB1_RANGE.get()))
			if ((best == null || o.distanceToSqr(p) < best.distanceToSqr(p)) && stoneClear(p, o))
				best = o;
		return best;
	}

	// ---- WINTERY DEATH ----

	private static final Map<UUID, Set<UUID>> WINTERY_HIT = new HashMap<>();

	private static void tickWintery(ServerPlayer p, long now) {
		Set<UUID> hit = WINTERY_HIT.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (ServerPlayer o : WaterKit.unalliedWithin(p, WaterConfig.FRZ_AB1_RADIUS.get())) {
			if (inFreezingForm(o))
				continue;
			if (QiEffects.canAffect(o, false))
				EffectHelper.apply(o, MobEffects.BLINDNESS, 1, 40, true);
			if (AspectData.of(o).getActive() == AspectType.FIRE) {
				if (hit.add(o.getUUID()))
					QiEffects.dealDamage(p, o, WaterConfig.FRZ_AB1_FIRE_DAMAGE.get().floatValue()); // once per cast
				if (QiEffects.canAffect(o, false))
					QiCostDebuffs.apply(o, WaterConfig.FRZ_AB1_COST_MULTIPLIER.get(), 40, now);
			}
		}
	}

	static void stopWintery(ServerPlayer p) {
		WINTERY_HIT.remove(p.getUUID());
	}

	// ---- FREEZING APOCALYPSE ----

	private static final Map<UUID, Set<UUID>> APOCALYPSE_NULLIFIED = new HashMap<>();

	private static void tickApocalypse(ServerPlayer p, long now) {
		Set<UUID> done = APOCALYPSE_NULLIFIED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (ServerPlayer o : WaterKit.unalliedWithin(p, WaterConfig.FRZ_AB2_RADIUS.get())) {
			if (inFreezingForm(o) || !QiEffects.canAffect(o, false))
				continue;
			// vanilla frostbite: frozen screen, slowed, freeze damage (leather armour still protects)
			o.setTicksFrozen(Math.max(o.getTicksFrozen(), o.getTicksRequiredToFreeze() + 30));
			AspectType a = AspectData.of(o).getActive();
			if (a == AspectType.FIRE || a == AspectType.ELECTRIC) {
				if (done.add(o.getUUID()))
					QiEffects.nullify(o, a, 40);
				else
					AbilityState.of(o).nullify(a, now + 40); // silent refresh while inside
			}
		}
	}

	static void stopApocalypse(ServerPlayer p) {
		APOCALYPSE_NULLIFIED.remove(p.getUUID());
	}

	private static final class SnowJob {
		final ServerLevel level;
		final int cx, cz, r;
		int dx, dz;

		SnowJob(ServerLevel level, BlockPos c, int r) {
			this.level = level;
			this.cx = c.getX();
			this.cz = c.getZ();
			this.r = r;
			this.dx = -r;
			this.dz = -r;
		}

		/** @return true when finished */
		boolean step(int budget) {
			BlockState snow = Blocks.SNOW.defaultBlockState();
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			while (budget-- > 0) {
				if (dx > r)
					return true;
				int x = cx + dx, z = cz + dz;
				if (dx * dx + dz * dz <= r * r && level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
					m.set(x, y, z);
					if (level.getBlockState(m).isAir() && snow.canSurvive(level, m))
						level.setBlock(m, snow, Block.UPDATE_CLIENTS);
				}
				if (++dz > r) {
					dz = -r;
					dx++;
				}
			}
			return false;
		}
	}

	private static final List<SnowJob> SNOW = new ArrayList<>();

	static void startSnow(ServerLevel level, BlockPos center, int radius) {
		SNOW.add(new SnowJob(level, center, radius));
	}

	static void leaveFreezing(ServerPlayer p) {
		NETHER_SINCE.remove(p.getUUID());
		WINTERY_HIT.remove(p.getUUID());
		APOCALYPSE_NULLIFIED.remove(p.getUUID());
	}

	// ================================================================== BODY

	private static final Map<UUID, Integer> BLOOD = new HashMap<>();
	/** Players with the Enhanced power attack-damage bonus (removed when its buff ends early). */
	private static final Set<UUID> POWERED = new HashSet<>();

	static void startBlood(ServerPlayer p, int hits) {
		AbilityState.of(p).addBuff(new Buff(WaterSubKits.InfusedBlood.BUFF, W, 1.0, 0, false, Long.MAX_VALUE));
		BLOOD.put(p.getUUID(), hits);
	}

	static int bloodCharges(ServerPlayer p) {
		Integer n = BLOOD.get(p.getUUID());
		if (n == null)
			return 0;
		if (!buffed(p, WaterSubKits.InfusedBlood.BUFF, WaterSubKits.INFUSED_BLOOD, p.serverLevel().getGameTime())) {
			BLOOD.remove(p.getUUID());
			return 0;
		}
		return n;
	}

	static void trackPower(ServerPlayer p) {
		POWERED.add(p.getUUID());
	}

	private static void tickPower(ServerPlayer p, long now) {
		if (POWERED.contains(p.getUUID()) && !buffed(p, WaterSubKits.EnhancedPower.BUFF, WaterSubKits.POWER, now)) {
			POWERED.remove(p.getUUID());
			TimedAttributes.remove(p, WaterSubKits.EnhancedPower.BUFF); // form change / nullify ended it early
		}
	}

	static void leaveBody(ServerPlayer p) {
		BLOOD.remove(p.getUUID());
		AbilityState.of(p).removeBuff(WaterSubKits.InfusedBlood.BUFF);
		POWERED.remove(p.getUUID());
		TimedAttributes.remove(p, WaterSubKits.EnhancedPower.BUFF);
	}

	// ================================================================== MOON

	/** Players currently shown aspect labels by the Moon passive (so they get one clearing message). */
	private static final Set<UUID> SIGHT = new HashSet<>();

	private static void tickSight(ServerPlayer p) {
		if (LightEvents.isRevealing(p))
			return; // Light's reveal owns the labels while it runs
		if (!WaterSubKits.moonPassiveOn(p)) {
			if (SIGHT.remove(p.getUUID()))
				RevealAspectsMessage.send(p, Map.of());
			return;
		}
		Map<Integer, String> labels = new HashMap<>();
		int r = WaterConfig.MOON_SIGHT_RADIUS.get();
		for (ServerPlayer o : p.serverLevel().players()) {
			if (o == p || !o.isAlive() || o.isSpectator() || o.distanceTo(p) > r)
				continue;
			var form = KitResolver.formInUse(o);
			AspectType a = AspectData.of(o).getActive();
			labels.put(o.getId(), form != null ? form.fullName() : a == null ? "No aspect" : a.displayName());
		}
		RevealAspectsMessage.send(p, labels);
		SIGHT.add(p.getUUID());
	}

	private static final Map<UUID, Long> NO_SPRINT = new HashMap<>();

	static void noSprint(ServerPlayer t, int ticks) {
		NO_SPRINT.merge(t.getUUID(), t.serverLevel().getGameTime() + ticks, Math::max);
	}

	private record Field(ServerLevel level, Vec3 center) {
	}

	private static final Map<UUID, Field> FIELDS = new HashMap<>();

	static void startHighTide(ServerPlayer p) {
		FIELDS.put(p.getUUID(), new Field(p.serverLevel(), p.position()));
	}

	private static int fieldRadius(ServerPlayer p) {
		return (WaterKit.isNight(p.level()) ? WaterConfig.MOON_AB1_RADIUS_NIGHT : WaterConfig.MOON_AB1_RADIUS).get();
	}

	private static List<ServerPlayer> inField(ServerPlayer p, Field f) {
		List<ServerPlayer> out = new ArrayList<>();
		int r = fieldRadius(p);
		for (ServerPlayer o : WaterKit.unalliedWithin(p, Double.MAX_VALUE))
			if (o.level() == f.level() && o.position().distanceTo(f.center()) <= r)
				out.add(o);
		return out;
	}

	private static void tickHighTide(ServerPlayer p, Field f) {
		double pull = (WaterKit.isNight(p.level()) ? WaterConfig.MOON_AB1_PULL_NIGHT : WaterConfig.MOON_AB1_PULL).get();
		for (ServerPlayer o : inField(p, f))
			if (QiEffects.canAffect(o, false))
				WaterEvents.pullToward(o, f.center(), pull);
		int r = fieldRadius(p);
		for (int i = 0; i < 24; i++) { // ring of splashes marking the edge
			double ang = Math.PI * 2 * i / 24;
			f.level().sendParticles(ParticleTypes.SPLASH, f.center().x + Math.cos(ang) * r, f.center().y + 0.2, f.center().z + Math.sin(ang) * r, 4, 0.2, 0.1, 0.2, 0.05);
		}
	}

	/** Second press: everyone inside is pushed out from the centre. @return how many */
	static int highTidePush(ServerPlayer p) {
		Field f = FIELDS.get(p.getUUID());
		if (f == null)
			return 0;
		double push = (WaterKit.isNight(p.level()) ? WaterConfig.MOON_AB1_PUSH_NIGHT : WaterConfig.MOON_AB1_PUSH).get();
		int n = 0;
		for (ServerPlayer o : inField(p, f))
			if (QiEffects.canAffect(o, false)) {
				WaterEvents.push(o, f.center(), push);
				n++;
			}
		return n;
	}

	static void stopHighTide(ServerPlayer p) {
		FIELDS.remove(p.getUUID());
	}

	static void leaveMoon(ServerPlayer p) {
		TimedAttributes.remove(p, MOON_KB);
		FIELDS.remove(p.getUUID());
		if (SIGHT.remove(p.getUUID()) && !LightEvents.isRevealing(p))
			RevealAspectsMessage.send(p, Map.of());
	}

	// ================================================================== events

	/** Hit effects. HIGH: after empowered hits (HIGHEST), before output multipliers (LOW). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (!WaterEvents.isMelee(event.getSource(), attacker) || AspectData.of(attacker).getActive() != W)
			return;
		long now = attacker.serverLevel().getGameTime();
		float dmg = event.getNewDamage();

		// Body: infused blood (unarmed only)
		int blood = bloodCharges(attacker);
		if (blood > 0 && attacker.getMainHandItem().isEmpty()) {
			dmg += WaterConfig.BODY_A1_EXTRA.get().floatValue();
			attacker.heal(WaterConfig.BODY_A1_HEAL.get().floatValue());
			AspectMastery.onAbilityHit(attacker, W, target);
			if (--blood > 0) {
				BLOOD.put(attacker.getUUID(), blood);
			} else {
				BLOOD.remove(attacker.getUUID());
				AbilityState.of(attacker).removeBuff(WaterSubKits.InfusedBlood.BUFF);
			}
		}

		// Body: enhanced power -> Knockback IV while the passive is on
		if (buffed(attacker, WaterSubKits.EnhancedPower.BUFF, WaterSubKits.POWER, now) && WaterSubKits.bodyPassiveOn(attacker))
			QiEffects.knockback(target, attacker, WaterConfig.BODY_A2_KNOCKBACK.get(), false);

		// Body: I AM HIM -> +damage while the passive is on
		if (running(attacker, WaterSubKits.HIM) && WaterSubKits.bodyPassiveOn(attacker))
			dmg += WaterConfig.BODY_AB2_EXTRA.get().floatValue();

		event.setNewDamage(dmg);
	}

	/** Freezing passive: immune to suffocation and freeze damage while it's on. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer victim))
			return;
		DamageSource src = event.getSource();
		if ((src.is(DamageTypes.IN_WALL) || src.is(DamageTypeTags.IS_FREEZING)) && WaterSubKits.freezingPassiveOn(victim))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		long now = p.serverLevel().getGameTime();
		long phase = now + p.getId();

		Long ns = NO_SPRINT.get(p.getUUID());
		if (ns != null) {
			if (now >= ns) {
				NO_SPRINT.remove(p.getUUID());
			} else if (p.isSprinting()) {
				// sprint can't be blocked from the server, so it gives no speed: x 1/1.3 cancels sprint's +30%
				TimedAttributes.apply(p, Attributes.MOVEMENT_SPEED, RIPTIDE_SPEED, 1 / 1.3 - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 3);
				p.setSprinting(false);
			}
		}

		if (phase % 10 == 0) {
			if (running(p, WaterSubKits.WINTERY))
				tickWintery(p, now);
			if (running(p, WaterSubKits.APOCALYPSE))
				tickApocalypse(p, now);
			if (AspectData.of(p).getActive() == W || SIGHT.contains(p.getUUID()))
				tickSight(p);
		}

		Field f = FIELDS.get(p.getUUID());
		if (f != null && running(p, WaterSubKits.HIGH_TIDE) && phase % (WaterKit.seconds(WaterConfig.MOON_AB1_PULSE_SECONDS.get())) == 0)
			tickHighTide(p, f);

		if (phase % 20 != 0)
			return;
		// once a second
		tickNetherDebuff(p, now);
		tickPower(p, now);
		if (running(p, WaterSubKits.INFUSED) && p.isInWater()) {
			EffectHelper.apply(p, MobEffects.REGENERATION, WaterConfig.BODY_AB1_REGEN.get(), 30, false);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, WaterConfig.BODY_AB1_RESISTANCE.get(), 30, false);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, WaterConfig.BODY_AB1_SPEED.get(), 30, false);
		}
		if (running(p, WaterSubKits.HIM) && WaterSubKits.bodyPassiveOn(p)) {
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, WaterConfig.BODY_AB2_RESISTANCE.get(), 30, false);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, WaterConfig.BODY_AB2_SPEED.get(), 30, false);
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (!SNOW.isEmpty() && SNOW.get(0).step(WaterConfig.SNOW_COLUMNS_PER_TICK.get()))
			SNOW.remove(0);
	}

	// ================================================================== cleanup

	private static void clear(UUID id) {
		NETHER_SINCE.remove(id);
		WINTERY_HIT.remove(id);
		APOCALYPSE_NULLIFIED.remove(id);
		BLOOD.remove(id);
		POWERED.remove(id);
		SIGHT.remove(id);
		NO_SPRINT.remove(id);
		FIELDS.remove(id);
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
		NETHER_SINCE.clear();
		WINTERY_HIT.clear();
		APOCALYPSE_NULLIFIED.clear();
		BLOOD.clear();
		POWERED.clear();
		SIGHT.clear();
		NO_SPRINT.clear();
		FIELDS.clear();
		SNOW.clear();
		biomeRaw = null;
	}
}
