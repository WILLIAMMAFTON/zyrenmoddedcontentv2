package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
import net.zyren.cultivation.ability.core.QiCosts;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.FireConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.AspectMastery;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Event side of Fire's sub-aspects (FireSubKits): hit effects, the Body charge, Heating Touch's
 * aura, Volcanic explosions and lava buffs, Inferno's fire-coverage rules.
 * Every effect checks that its ability is still the CURRENT one (KitResolver.isCurrent), so a form
 * toggle / aspect switch ends it; buff-backed effects also end on nullify (buffs are removed).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class FireSubEvents {
	private static final AspectType F = AspectType.FIRE;

	private FireSubEvents() {
	}

	// ================================================================== shared helpers

	/** Inside a fire block (normal or soul fire) - "standing on fire". */
	static boolean standingInFire(Player p) {
		return p.level().getBlockStatesIfLoaded(p.getBoundingBox().deflate(1.0E-3)).anyMatch(s -> s.is(BlockTags.FIRE));
	}

	/** "Fire covering a 3 block radius": share of floor spots within the radius that hold fire. */
	static boolean coveredByFire(Player p) {
		int r = FireConfig.INF_COVER_RADIUS.get();
		Level level = p.level();
		BlockPos feet = p.blockPosition();
		int floor = 0, burning = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r)
					continue;
				for (int dy = -1; dy <= 1; dy++) { // small steps in the ground count too
					pos.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
					BlockState s = level.getBlockState(pos);
					if (s.is(BlockTags.FIRE)) {
						floor++;
						burning++;
						break;
					}
					if (s.isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
						floor++;
						break;
					}
				}
			}
		return floor > 0 && burning >= floor * FireConfig.INF_COVER_FRACTION.get();
	}

	/** Water / Air users, or Space users in the Matter form. */
	static boolean isWaterAirOrMatter(ServerPlayer p) {
		return isWaterOrAir(p) || KitResolver.formInUse(p) == net.zyren.cultivation.data.SubAspectType.SPACE_MATTER;
	}

	static boolean isWaterOrAir(ServerPlayer p) {
		AspectType a = AspectData.of(p).getActive();
		return a == AspectType.WATER || a == AspectType.AIR;
	}

	private static boolean active(ServerPlayer p, Ability a) {
		return AbilityState.of(p).isSustained(a) && KitResolver.isCurrent(p, a);
	}

	private static boolean buffed(ServerPlayer p, String buff, Ability a, long now) {
		return AbilityState.of(p).hasBuff(buff, now) && KitResolver.isCurrent(p, a) && !AbilityState.of(p).isNullified(F, now);
	}

	private static boolean isMelee(DamageSource src, ServerPlayer attacker) {
		return src.getDirectEntity() == attacker && src.is(DamageTypes.PLAYER_ATTACK) && !QiEffects.isDealingQiDamage();
	}

	// ================================================================== BODY

	/** Fire-active targets hit by Body Attack 1 in the Overworld: sent to the Nether at the end of the tick. */
	private static final Set<UUID> TO_NETHER = new HashSet<>();

	static void sendToNether(ServerPlayer t) {
		TO_NETHER.add(t.getUUID());
	}

	private static void doSendToNether(ServerPlayer t) {
		ServerLevel nether = t.server.getLevel(Level.NETHER);
		if (nether == null || t.level().dimension() != Level.OVERWORLD || !t.isAlive())
			return;
		BlockPos scaled = BlockPos.containing(t.getX() / 8.0, 64, t.getZ() / 8.0);
		for (BlockPos p : BlockPos.withinManhattan(scaled, 16, 48, 16)) {
			if (p.getY() <= nether.getMinBuildHeight() + 1 || p.getY() >= 126)
				continue;
			BlockState feet = nether.getBlockState(p), head = nether.getBlockState(p.above()), below = nether.getBlockState(p.below());
			if (feet.isAir() && head.isAir() && below.isFaceSturdy(nether, p.below(), Direction.UP) && below.getFluidState().isEmpty()) {
				t.teleportTo(nether, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, t.getYRot(), t.getXRot());
				t.displayClientMessage(net.minecraft.network.chat.Component.literal("You were cast into the Nether!"), true);
				return;
			}
		}
	}

	// ---- Fission blade (sword/axe hits): Sharpness II + Fire Aspect, simulated ----

	// ---- a line straight from hell: the charge ----

	private static final class Charge {
		Vec3 dir;
		double range, travelled, damage;
		Vec3 last;
		int stuck;
		final Set<UUID> hit = new HashSet<>();
	}

	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	/** Fire immunity from the charge's own hellfire, until this game time. */
	private static final Map<UUID, Long> HELLFIRE_IMMUNE = new HashMap<>();

	static boolean isCharging(ServerPlayer p) {
		return CHARGES.containsKey(p.getUUID());
	}

	static void startCharge(ServerPlayer p, int range, double damage) {
		Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0E-4)
			dir = new Vec3(0, 0, 1);
		Charge c = new Charge();
		c.dir = dir.normalize();
		c.range = range;
		c.damage = damage;
		c.last = p.position();
		CHARGES.put(p.getUUID(), c);
		HELLFIRE_IMMUNE.put(p.getUUID(), Long.MAX_VALUE);
	}

	private static void tickCharge(ServerPlayer p, Charge c) {
		double speed = FireConfig.BODY_AB1_SPEED.get();
		Vec3 now = p.position();
		double moved = now.subtract(c.last).multiply(1, 0, 1).length();
		c.travelled += moved;
		c.last = now;
		c.stuck = moved < speed * 0.3 && c.travelled > 0 ? c.stuck + 1 : 0;
		ServerLevel level = p.serverLevel();
		// strike unallied players along the path (once each)
		for (ServerPlayer o : level.players())
			if (Targeting.isValid(p, o) && !Targeting.isAllied(p, o) && !c.hit.contains(o.getUUID()) && o.distanceTo(p) <= 1.8) {
				c.hit.add(o.getUUID());
				QiEffects.dealDamage(p, o, (float) c.damage);
				if (QiEffects.canAffect(o, false))
					FireKit.ignite(o);
			}
		level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
		if (c.travelled >= c.range || c.stuck >= 2 || !p.isAlive()) { // done, or a solid block stopped us
			endCharge(p);
			return;
		}
		p.setDeltaMovement(c.dir.x * speed, Math.min(p.getDeltaMovement().y, 0), c.dir.z * speed);
		p.hurtMarked = true;
	}

	private static void endCharge(ServerPlayer p) {
		if (CHARGES.remove(p.getUUID()) != null) {
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
			HELLFIRE_IMMUNE.put(p.getUUID(), p.serverLevel().getGameTime() + 60); // a moment to put the flames out
		}
	}

	// ---- Heating Touch ----

	/** Caster -> players their aura has nullified (first touch shows the message, later refreshes are silent). */
	private static final Map<UUID, Set<UUID>> TOUCHED = new HashMap<>();

	private static void tickHeatingTouch(ServerPlayer p, long now) {
		int r = FireConfig.BODY_AB2_RADIUS.get();
		Set<UUID> touched = TOUCHED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (ServerPlayer o : p.serverLevel().players()) {
			if (!Targeting.isValid(p, o) || Targeting.isAllied(p, o) || o.distanceTo(p) > r || !isWaterOrAir(o))
				continue;
			if (touched.add(o.getUUID())) {
				QiEffects.nullifyAll(o, 40); // bypasses Reinforce (nullify doesn't check it)
			} else {
				AbilityState st = AbilityState.of(o);
				for (AspectType a : AspectType.values())
					st.nullify(a, now + 40); // silent refresh while they stay inside
			}
		}
	}

	static void stopHeatingTouch(ServerPlayer p) {
		TOUCHED.remove(p.getUUID());
	}

	static void leaveBody(ServerPlayer p) {
		endCharge(p);
		stopHeatingTouch(p);
	}

	// ================================================================== VOLCANIC

	private record Blast(ServerLevel level, UUID attacker, double x, double y, double z) {
	}

	private static final List<Blast> BLASTS = new ArrayList<>();
	/** victim id + tick: one explosion per victim per tick even if both Volcanic attacks are on. */
	private static final Set<String> BLASTED = new HashSet<>();

	private static void queueBlast(ServerPlayer attacker, LivingEntity at) {
		if (BLASTED.add(at.getId() + "@" + attacker.serverLevel().getGameTime()))
			BLASTS.add(new Blast(attacker.serverLevel(), attacker.getUUID(), at.getX(), at.getY() + at.getBbHeight() * 0.5, at.getZ()));
	}

	private static boolean isNetherite(ItemStack s) {
		return s.getItem() instanceof TieredItem t && t.getTier() == Tiers.NETHERITE;
	}

	// ================================================================== INFERNO

	private static final Map<UUID, Integer> HEAT_CHARGES = new HashMap<>();
	private static final String HEAT_BUFF = "fire_inferno_heat";
	/** Covered-by-fire result of the last second (passive, heart, unbound). */
	private static final Set<UUID> COVERED = new HashSet<>();

	static void startHeat(ServerPlayer p, int hits) {
		AbilityState.of(p).addBuff(new Buff(HEAT_BUFF, F, 1.0, 0, false, Long.MAX_VALUE));
		HEAT_CHARGES.put(p.getUUID(), hits);
	}

	private static int heatCharges(ServerPlayer p) {
		Integer n = HEAT_CHARGES.get(p.getUUID());
		if (n == null)
			return 0;
		if (!buffed(p, HEAT_BUFF, FireSubKits.HEAT, p.serverLevel().getGameTime())) {
			HEAT_CHARGES.remove(p.getUUID());
			return 0;
		}
		return n;
	}

	private static void endHeat(ServerPlayer p) {
		HEAT_CHARGES.remove(p.getUUID());
		AbilityState.of(p).removeBuff(HEAT_BUFF);
	}

	/** Called once a second by the Inferno passive while it's active. */
	static void refreshInfernoPassive(ServerPlayer p) {
		boolean covered = coveredByFire(p);
		if (covered)
			COVERED.add(p.getUUID());
		else
			COVERED.remove(p.getUUID());
		if (!covered)
			return;
		boolean armored = FireKit.hasArmor(p);
		EffectHelper.applyPassive(p, MobEffects.REGENERATION, armored ? 1 : 2);
		int hearts = armored ? FireConfig.INF_HEARTS_ARMORED.get() : FireConfig.INF_HEARTS.get();
		TimedAttributes.apply(p, Attributes.MAX_HEALTH, "fire_inferno_hearts", hearts * 2.0, AttributeModifier.Operation.ADD_VALUE, 50);
	}

	static void surroundWithFire(ServerPlayer p) {
		int r = FireConfig.INF_COVER_RADIUS.get();
		ServerLevel level = p.serverLevel();
		BlockPos feet = p.blockPosition();
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r)
					continue;
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos pos = feet.offset(dx, dy, dz);
					if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
						level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
						break;
					}
				}
			}
	}

	static void leaveInferno(ServerPlayer p) {
		endHeat(p);
		COVERED.remove(p.getUUID());
		TimedAttributes.remove(p, "fire_inferno_hearts");
	}

	/** Largest Water/Air Qi-attack reduction the Inferno form gives right now (they don't stack). */
	private static double infernoReduction(ServerPlayer victim) {
		if (!COVERED.contains(victim.getUUID()) || KitResolver.formInUse(victim) != FireSubKits.INFERNO)
			return 0;
		double r = 0;
		if (AbilityState.of(victim).passiveActive() && FireSubKits.isInfernoPassive(KitResolver.passiveFor(victim)))
			r = Math.max(r, FireConfig.INF_PASSIVE_REDUCTION.get());
		if (active(victim, FireSubKits.HEART))
			r = Math.max(r, FireConfig.INF_AB1_REDUCTION.get());
		if (active(victim, FireSubKits.UNBOUND))
			r = Math.max(r, FireConfig.INF_AB2_REDUCTION.get());
		return r;
	}

	// ================================================================== events

	/** Hit effects. HIGH: after empowered hits (HIGHEST), before output multipliers. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (!isMelee(event.getSource(), attacker) || AspectData.of(attacker).getActive() != F)
			return;
		long now = attacker.serverLevel().getGameTime();
		ItemStack held = attacker.getMainHandItem();
		float dmg = event.getNewDamage();

		// Body: Fission blade
		if (buffed(attacker, FireSubKits.BodyFission.BUFF, FireSubKits.FISSION, now) && (held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES))) {
			dmg += FireConfig.BODY_A2_SHARPNESS_BONUS.get().floatValue();
			if (QiEffects.canAffect(target, false))
				target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), FireConfig.BODY_A2_FIRE_TICKS.get()));
		}

		// Volcanic: fists of doom (any item in hand)
		if (!held.isEmpty() && buffed(attacker, FireSubKits.VolcDoomFists.BUFF, FireSubKits.DOOM_FISTS, now)) {
			dmg += FireConfig.VOLC_A2_EXTRA.get().floatValue();
			if (QiEffects.canAffect(target, false)) {
				FireKit.ignite(target);
				EffectHelper.apply(target, MobEffects.CONFUSION, 1, FireConfig.VOLC_A2_NAUSEA_SECONDS.get() * 20, true);
			}
			queueBlast(attacker, target);
			AspectMastery.onAbilityHit(attacker, F, target);
		}

		// Volcanic: the world will burn -> netherite weapons x2
		if (isNetherite(held) && buffed(attacker, FireSubKits.VolcWorldBurn.BUFF, FireSubKits.WORLD_BURN, now))
			dmg *= FireConfig.VOLC_AB2_NETHERITE_MULTIPLIER.get().floatValue();

		// Inferno: the inferno's heat (bare fists)
		int heat = heatCharges(attacker);
		if (heat > 0 && !FireKit.isWeapon(held)) {
			dmg = (FireKit.hasArmor(target) ? FireConfig.INF_A1_DAMAGE_ARMORED.get() : FireConfig.INF_A1_DAMAGE.get()).floatValue();
			if (QiEffects.canAffect(target, false)) {
				FireKit.ignite(target);
				if (target instanceof ServerPlayer tp && isWaterOrAir(tp))
					EffectHelper.apply(tp, MobEffects.CONFUSION, 1, 100, true);
			}
			AspectMastery.onAbilityHit(attacker, F, target);
			if (--heat > 0)
				HEAT_CHARGES.put(attacker.getUUID(), heat);
			else
				endHeat(attacker);
		}

		// Inferno: heart / unbound while covered by fire -> hits set fire; unbound: fists +5 vs armour
		boolean covered = COVERED.contains(attacker.getUUID());
		if (covered && (active(attacker, FireSubKits.HEART) || active(attacker, FireSubKits.UNBOUND)) && QiEffects.canAffect(target, false))
			FireKit.ignite(target);
		if (covered && active(attacker, FireSubKits.UNBOUND) && !FireKit.isWeapon(held) && FireKit.hasArmor(target))
			dmg += FireConfig.INF_AB2_ARMORED_BONUS.get().floatValue();

		event.setNewDamage(dmg);
	}

	/** Volcanic explosive magma: ANY damage the user deals (not explosions) makes an explosion on the target. */
	@SubscribeEvent
	public static void onDamageDealt(LivingDamageEvent.Post event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (event.getSource().is(DamageTypeTags.IS_EXPLOSION) || event.getNewDamage() <= 0)
			return;
		if (!buffed(attacker, FireSubKits.VolcExplosive.BUFF, FireSubKits.EXPLOSIVE, attacker.serverLevel().getGameTime()))
			return;
		if (QiEffects.canAffect(target, false))
			FireKit.ignite(target);
		queueBlast(attacker, target);
	}

	/** Incoming: own explosions / own hellfire ignored; Inferno reductions vs Water/Air Qi attacks. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer victim))
			return;
		DamageSource src = event.getSource();
		long now = victim.serverLevel().getGameTime();
		if (src.is(DamageTypeTags.IS_EXPLOSION) && src.getEntity() == victim && KitResolver.formInUse(victim) == FireSubKits.VOLCANIC) {
			event.setCanceled(true); // immune to your own Volcanic explosions
			return;
		}
		Long immune = HELLFIRE_IMMUNE.get(victim.getUUID());
		if (immune != null && now < immune && src.is(DamageTypeTags.IS_FIRE)) {
			event.setCanceled(true); // the charge's own hellfire
			return;
		}
		if (src.getEntity() instanceof ServerPlayer attacker && attacker != victim && isWaterOrAir(attacker)
				&& (QiEffects.isDealingQiDamage() || AbilityState.of(attacker).empoweredFor(victim, now) != null)) {
			double r = infernoReduction(victim);
			if (r > 0)
				event.setAmount((float) (event.getAmount() * (1 - r)));
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		Charge c = CHARGES.get(p.getUUID());
		if (c != null)
			tickCharge(p, c);
		long now = p.serverLevel().getGameTime();
		if ((now + p.getId()) % 10 == 0 && active(p, FireSubKits.HEATING_TOUCH))
			tickHeatingTouch(p, now);
		if ((now + p.getId()) % 20 != 0)
			return;

		// once a second
		if (active(p, FireSubKits.DOOM) && p.isInLava()) {
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, 3);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 3, 50, false);
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 2, 50, false);
			QiCoreStorage.addQi(p, FireConfig.VOLC_AB1_QI_PER_MINUTE.get() / 60.0);
		}
		if (heatCharges(p) > 0 && FireKit.isWeapon(p.getMainHandItem())) {
			endHeat(p);
			QiCoreStorage.addQi(p, QiCosts.effective(p, FireConfig.INF_A1_REFUND.get()));
			p.displayClientMessage(net.minecraft.network.chat.Component.literal("The inferno's heat fades: you took up a weapon (Qi partly refunded)."), true);
		}
		boolean heart = active(p, FireSubKits.HEART), unbound = active(p, FireSubKits.UNBOUND);
		if ((heart || unbound) && !(AbilityState.of(p).passiveActive() && FireSubKits.isInfernoPassive(KitResolver.passiveFor(p)))) {
			// the passive normally measures coverage; measure it here when the passive isn't running
			if (coveredByFire(p))
				COVERED.add(p.getUUID());
			else
				COVERED.remove(p.getUUID());
		}
		if (heart && standingInFire(p)) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 2, 50, false);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, 1, 50, false);
		}
		if (unbound && COVERED.contains(p.getUUID())) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, 3, 50, false);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, 2, 50, false);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, 2, 50, false);
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (!TO_NETHER.isEmpty()) {
			for (UUID id : new ArrayList<>(TO_NETHER)) {
				ServerPlayer t = event.getServer().getPlayerList().getPlayer(id);
				if (t != null)
					doSendToNether(t);
			}
			TO_NETHER.clear();
		}
		if (!BLASTS.isEmpty()) {
			List<Blast> now = new ArrayList<>(BLASTS);
			BLASTS.clear();
			BLASTED.clear();
			float power = FireConfig.VOLC_EXPLOSION_POWER.get().floatValue();
			Level.ExplosionInteraction mode = FireConfig.VOLC_EXPLOSION_BREAKS_BLOCKS.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
			for (Blast b : now) {
				ServerPlayer src = event.getServer().getPlayerList().getPlayer(b.attacker());
				b.level().explode(src, b.x(), b.y(), b.z(), power, mode);
			}
		}
	}

	// ================================================================== cleanup

	private static void clear(UUID id) {
		CHARGES.remove(id);
		HELLFIRE_IMMUNE.remove(id);
		TOUCHED.remove(id);
		HEAT_CHARGES.remove(id);
		COVERED.remove(id);
		TO_NETHER.remove(id);
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
		CHARGES.clear();
		HELLFIRE_IMMUNE.clear();
		TOUCHED.clear();
		HEAT_CHARGES.clear();
		COVERED.clear();
		TO_NETHER.clear();
		BLASTS.clear();
		BLASTED.clear();
	}
}
