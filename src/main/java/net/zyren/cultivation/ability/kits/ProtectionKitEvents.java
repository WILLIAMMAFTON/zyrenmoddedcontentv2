package net.zyren.cultivation.ability.kits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.CastRules;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.ProtectionConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.CoreLife;
import net.zyren.cultivation.system.Corruption;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event side of the Protection kits (ProtectionKits). Damage-moving / healing / final hits from
 * inside damage events are deferred to the end of the server tick.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class ProtectionKitEvents {
	private static final AspectType PR = AspectType.PROTECTIVE;

	private ProtectionKitEvents() {
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

	// ================================================================== exclusions

	/** Damage types listed here ignore every Protection reduction / save. Empty by default. */
	static final TagKey<DamageType> DIVINE = TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "divine"));

	/** Set while the worthy-death payback hit is dealt: no Protection handling of it. */
	private static boolean payback;

	/** Space -> Abyssal attacker, a "divine" damage type, /kill + the void, or the worthy-death payback. */
	static boolean excluded(DamageSource src) {
		if (payback || src.is(DIVINE) || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
			return true;
		return src.getEntity() instanceof ServerPlayer a && KitResolver.formInUse(a) == SubAspectType.SPACE_ABYSSAL;
	}

	/** Victim -> game tick of their last excluded hit (invalid kill must not save from those). */
	private static final Map<UUID, Long> EXCLUDED_HIT = new HashMap<>();

	static boolean lastHitExcluded(ServerPlayer p) {
		Long t = EXCLUDED_HIT.get(p.getUUID());
		return t != null && t == now(p);
	}

	// ================================================================== cast rules

	/** Worthy death user -> tick until which invaild kill is locked. */
	private static final Map<UUID, Long> WORTHY_LOCK = new HashMap<>();

	static boolean worthyLocked(ServerPlayer p) {
		Long u = WORTHY_LOCK.get(p.getUUID());
		if (u == null)
			return false;
		if (now(p) >= u) {
			WORTHY_LOCK.remove(p.getUUID());
			return false;
		}
		return true;
	}

	static final CastRules.Rule RULES = new CastRules.Rule() {
		@Override
		public ActionResult refuse(ServerPlayer p, Ability a, long now) {
			if (a.slot() == AbilitySlot.REINFORCE && AbilityState.of(p).lethalGuard() == ProtectionKits.INVALID_KILL
					&& AspectData.of(p).getActive() == PR)
				return ActionResult.fail("Reinforce Body is locked while invaild kill is armed.");
			return null;
		}
	};

	// ================================================================== Resistance gift (no harmful effects, no corruption)

	private static final Map<UUID, Long> GIFTED = new HashMap<>();

	static boolean gifted(ServerPlayer p) {
		Long u = GIFTED.get(p.getUUID());
		if (u == null)
			return false;
		if (now(p) >= u) {
			GIFTED.remove(p.getUUID());
			return false;
		}
		return true;
	}

	private static boolean corruptionRuleAdded;

	static void registerCorruptionProtection() {
		if (corruptionRuleAdded)
			return;
		corruptionRuleAdded = true;
		Corruption.addProtection(ProtectionKitEvents::gifted);
	}

	static void giveGift(ServerPlayer p) {
		long until = now(p) + ProtectionConfig.HL_PB1_SECONDS.get() * 20L;
		int n = 0;
		for (ServerPlayer a : ProtectionKits.allies(p, ProtectionConfig.HL_PB1_RADIUS.get(), true)) {
			List<Holder<MobEffect>> bad = new ArrayList<>();
			for (MobEffectInstance e : a.getActiveEffects())
				if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
					bad.add(e.getEffect());
			bad.forEach(a::removeEffect);
			GIFTED.put(a.getUUID(), until);
			n++;
		}
		p.displayClientMessage(Component.literal("Resistance gift: " + n + " protected."), true);
	}

	@SubscribeEvent
	public static void onEffect(MobEffectEvent.Applicable event) {
		if (!GIFTED.isEmpty() && event.getEntity() instanceof ServerPlayer p && event.getEffectInstance() != null
				&& event.getEffectInstance().getEffect().value().getCategory() == MobEffectCategory.HARMFUL && gifted(p))
			event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
	}

	// ================================================================== Lady mercy charge

	static final PhysicalKitEvents.Charges MERCY_CHARGE = new PhysicalKitEvents.Charges("protection_healing_mercy", PR, () -> ProtectionKits.MERCY);

	// ================================================================== Lifeline / worthy death state

	static final Map<UUID, UUID> LINKS = new HashMap<>();
	private static final Map<UUID, Double> WORTHY_TOTAL = new HashMap<>();
	private static final Map<UUID, Boolean> WORTHY_BROKEN = new HashMap<>();

	static ServerPlayer linked(ServerPlayer p) {
		UUID id = LINKS.get(p.getUUID());
		ServerPlayer t = id == null ? null : p.server.getPlayerList().getPlayer(id);
		return t != null && t.isAlive() ? t : null;
	}

	static void startWorthy(ServerPlayer p) {
		WORTHY_TOTAL.put(p.getUUID(), 0.0);
		WORTHY_BROKEN.remove(p.getUUID());
		WORTHY_LOCK.put(p.getUUID(), Long.MAX_VALUE);
	}

	/** Ends: the user takes everything negated, in one hit that ignores their reductions and Reinforce. */
	static void endWorthy(ServerPlayer p) {
		WORTHY_LOCK.put(p.getUUID(), now(p) + ProtectionConfig.AB2_PB2_LOCK_AFTER.get() * 20L);
		WORTHY_BROKEN.remove(p.getUUID());
		Double total = WORTHY_TOTAL.remove(p.getUUID());
		if (total == null || total <= 0)
			return;
		float dmg = total.floatValue();
		later(() -> {
			if (!p.isAlive())
				return;
			ProtectionKits.removeReinforce(p);
			AspectType a = AspectData.of(p).getActive();
			if (a != null && a != PR)
				AbilityState.of(p).removeBuff(QiEffects.reinforceId(a));
			p.displayClientMessage(Component.literal("A worthy death: you take " + Math.round(dmg) + " damage.").withStyle(ChatFormatting.DARK_RED), false);
			payback = true;
			try {
				p.hurt(p.damageSources().magic(), dmg);
			} finally {
				payback = false;
			}
		});
	}

	// ================================================================== Healing passive

	static void passiveHeal(ServerPlayer p) {
		double perHp = ProtectionConfig.HL_QI_PER_HEART.get() / 2.0;
		float amount = ProtectionConfig.HL_HEAL_PER_SECOND.get().floatValue();
		if (amount <= 0)
			return;
		ServerPlayer target = null;
		float lowest = Float.MAX_VALUE;
		for (ServerPlayer a : ProtectionKits.allies(p, ProtectionConfig.HL_RADIUS.get(), false)) {
			float h = CoreLife.health(a);
			if (h < CoreLife.maxHealth(a) && h < lowest) {
				lowest = h;
				target = a;
			}
		}
		if (target == null && CoreLife.health(p) < CoreLife.maxHealth(p))
			target = p;
		if (target == null)
			return;
		float heal = Math.min(amount, CoreLife.maxHealth(target) - CoreLife.health(target));
		if (heal <= 0 || !QiCoreStorage.trySpendQi(p, heal * perHp, false))
			return;
		target.heal(heal);
	}

	// ================================================================== Intelligence

	private static final Map<String, Long> INTEL_SEEN = new HashMap<>();

	private static void intel(ServerPlayer victim, DamageSource src) {
		long now = now(victim);
		Entity owner = src.getEntity();
		String key = victim.getUUID() + "|" + (owner == null ? "none" : owner.getUUID().toString());
		Long last = INTEL_SEEN.get(key);
		if (last != null && now - last < ProtectionConfig.PB1_THROTTLE_TICKS.get())
			return;
		INTEL_SEEN.put(key, now);
		if (owner == null) {
			victim.displayClientMessage(Component.literal("Error: Insufficient power. Superfluous systems offline").withStyle(ChatFormatting.RED), false);
			return;
		}
		if (!(owner instanceof ServerPlayer a))
			return;
		AspectType asp = AspectData.of(a).getActive();
		SubAspectType sub = KitResolver.formInUse(a);
		String aspect = asp == null ? "no aspect" : asp.displayName() + (sub != null ? " → " + sub.displayName() : "");
		victim.displayClientMessage(Component.literal("[Intel] " + a.getGameProfile().getName() + ": " + Math.round(CoreLife.health(a)) + "/"
				+ Math.round(CoreLife.maxHealth(a)) + " hp · " + Math.round(QiData.of(a).getCoreAmount()) + " Qi · " + aspect).withStyle(ChatFormatting.AQUA), false);
	}

	// ================================================================== events

	/** Before anything: remember excluded hits; Lady mercy on allies. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onIncoming(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer victim))
			return;
		DamageSource src = event.getSource();
		if (excluded(src)) {
			EXCLUDED_HIT.put(victim.getUUID(), now(victim));
			return;
		}
		if (src.getEntity() instanceof ServerPlayer healer && healer != victim && src.getDirectEntity() == healer && !QiEffects.isDealingQiDamage()
				&& Targeting.isAllied(healer, victim) && MERCY_CHARGE.left(healer) > 0) {
			event.setCanceled(true);
			MERCY_CHARGE.use(healer);
			double chance = ProtectionConfig.HL_A1_CLEANSE_CHANCE.get();
			later(() -> {
				if (!victim.isAlive())
					return;
				victim.heal(CoreLife.maxHealth(victim) - CoreLife.health(victim));
				if (healer.getRandom().nextDouble() < chance) {
					List<Holder<MobEffect>> bad = new ArrayList<>();
					for (MobEffectInstance e : victim.getActiveEffects())
						if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
							bad.add(e.getEffect());
					if (!bad.isEmpty())
						victim.removeEffect(bad.get(healer.getRandom().nextInt(bad.size())));
				}
				victim.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, victim.getX(), victim.getY() + 1, victim.getZ(), 8, 0.4, 0.4, 0.4, 0);
			});
		}
	}

	/**
	 * Ally protection on the final damage (LOW, with the central reductions; lethal guards run at
	 * LOWEST after this): worthy death, defencive aura, guardians oath, tank, lifeline.
	 */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDamage(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof ServerPlayer victim) || event.getNewDamage() <= 0 || excluded(event.getSource()))
			return;
		float d = event.getNewDamage();
		UUID vid = victim.getUUID();

		for (ServerPlayer p : victim.serverLevel().players()) {
			if (d <= 0)
				break;
			if (p == victim || !Targeting.isAllied(p, victim) || AspectData.of(p).getActive() != PR)
				continue;
			double dist = p.distanceTo(victim);
			// a worthy death: everything negated, paid per heart
			if (WORTHY_TOTAL.containsKey(p.getUUID()) && !WORTHY_BROKEN.containsKey(p.getUUID()) && dist <= ProtectionConfig.AB2_RADIUS.get()
					&& running(p, ProtectionKits.WORTHY)) {
				if (QiCoreStorage.trySpendQi(p, d / 2.0 * ProtectionConfig.AB2_QI_PER_HEART.get(), false)) {
					WORTHY_TOTAL.merge(p.getUUID(), (double) d, Double::sum);
					d = 0;
					break;
				}
				WORTHY_BROKEN.put(p.getUUID(), true);
				p.displayClientMessage(Component.literal("A worthy death: out of Qi, allies are no longer protected."), true);
			}
			// defencive aura
			if (dist <= ProtectionConfig.A1_RADIUS.get() && running(p, ProtectionKits.AURA)) {
				float removed = d * ProtectionConfig.A1_REDUCTION.get().floatValue();
				if (QiCoreStorage.trySpendQi(p, removed * ProtectionConfig.A1_QI_PER_DAMAGE.get(), false))
					d -= removed;
				else
					later(() -> endAbility(p, ProtectionKits.AURA));
			}
			// guardians oath
			if (d > 0 && dist <= ProtectionConfig.AB1_RADIUS.get() && running(p, ProtectionKits.OATH)) {
				float removed = d * ProtectionConfig.AB1_REDUCTION.get().floatValue();
				if (QiCoreStorage.trySpendQi(p, removed / 2.0 * ProtectionConfig.AB1_QI_PER_HEART.get(), false))
					d -= removed;
				else
					later(() -> endAbility(p, ProtectionKits.OATH));
			}
			// lifeline: part of the linked ally's damage goes to the healer
			if (d > 0 && vid.equals(LINKS.get(p.getUUID())) && running(p, ProtectionKits.LIFELINE)) {
				float moved = d * ProtectionConfig.HL_PB2_FRACTION.get().floatValue();
				d -= moved;
				later(() -> {
					if (p.isAlive())
						p.hurt(p.damageSources().generic(), moved);
				});
			}
		}
		// tank (the user's own damage)
		if (d > 0 && AspectData.of(victim).getActive() == PR && running(victim, ProtectionKits.TANK)) {
			float removed = d * ProtectionConfig.A2_REDUCTION.get().floatValue();
			if (QiCoreStorage.trySpendQi(victim, removed * ProtectionConfig.A2_QI_PER_DAMAGE.get(), false))
				d -= removed;
			else
				later(() -> endAbility(victim, ProtectionKits.TANK));
		}
		event.setNewDamage(Math.max(0, d));
	}

	/** Deaths door: the first lethal hit on an ally (or the user) nearby is negated. Runs with the lethal guards. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLethal(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof ServerPlayer victim) || event.getNewDamage() <= 0 || excluded(event.getSource()))
			return;
		if (!CoreLife.isLethal(victim, event.getNewDamage()))
			return;
		for (ServerPlayer p : victim.serverLevel().players()) {
			if ((p != victim && !Targeting.isAllied(p, victim)) || AspectData.of(p).getActive() != PR || p.distanceTo(victim) > ProtectionConfig.HL_AB1_RADIUS.get()
					|| !running(p, ProtectionKits.DOOR))
				continue;
			event.setNewDamage(0);
			later(() -> endAbility(p, ProtectionKits.DOOR));
			victim.serverLevel().playSound(null, victim.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
			victim.displayClientMessage(Component.literal("Death's door: " + p.getGameProfile().getName() + " saved you."), true);
			if (p != victim)
				p.displayClientMessage(Component.literal("Death's door saved " + victim.getGameProfile().getName() + "."), true);
			return;
		}
	}

	/** After damage landed: intelegence. */
	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		if (event.getNewDamage() <= 0 || !(event.getEntity() instanceof ServerPlayer victim) || excluded(event.getSource()))
			return;
		if (AspectData.of(victim).getActive() == PR && running(victim, ProtectionKits.INTEL))
			intel(victim, event.getSource());
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

	static void leaveHealing(ServerPlayer p) {
		MERCY_CHARGE.clear(p);
		LINKS.remove(p.getUUID());
	}

	private static void clear(UUID id) {
		EXCLUDED_HIT.remove(id);
		WORTHY_LOCK.remove(id);
		WORTHY_TOTAL.remove(id);
		WORTHY_BROKEN.remove(id);
		GIFTED.remove(id);
		LINKS.remove(id);
		LINKS.values().removeIf(id::equals);
		MERCY_CHARGE.clear(id);
		INTEL_SEEN.keySet().removeIf(k -> k.startsWith(id.toString()));
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
		EXCLUDED_HIT.clear();
		WORTHY_LOCK.clear();
		WORTHY_TOTAL.clear();
		WORTHY_BROKEN.clear();
		GIFTED.clear();
		LINKS.clear();
		MERCY_CHARGE.clearAll();
		INTEL_SEEN.clear();
		payback = false;
	}

}
