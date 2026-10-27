package net.zyren.cultivation.ability.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.network.AbilityStateMessage;
import net.zyren.cultivation.system.QiCoreStorage;

/**
 * Game events that drive the ability framework:
 *  - player tick: passive (condition, continuous upkeep, effects), expiry, Corrupted/Pure rule, client sync
 *  - damage pipeline (final damage): empowered hit, total-damage-output multipliers, incoming reductions
 *  - melee hits: passive on-hit hooks (e.g. Warrior's Qi per sword use)
 *  - last damaged player tracking (targeting)
 *  - death / logout cleanup
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class AbilityEvents {
	private AbilityEvents() {
	}

	// ------------------------------------------------------------------ tick

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		long now = player.serverLevel().getGameTime();
		AbilityState state = AbilityState.of(player);
		state.expireBuffs(now);
		state.empowered(now); // expires a timed-out armed hit
		var ended = state.expireNullify(now);
		if (!ended.isEmpty()) {
			String msg = ended.size() >= AspectType.values().length ? "Your Qi flows again."
					: ended.size() == 1 ? "Your " + ended.get(0).displayName() + " aspect is no longer nullified."
					: "Your aspects are no longer nullified.";
			player.displayClientMessage(Component.literal(msg).withStyle(net.minecraft.ChatFormatting.GREEN), false);
		}

		enforceCorruptedPureRule(player);
		checkFormChange(player, state, now);
		runPassive(player, state, now);
		runSustained(player, state, now);

		AspectType active = AspectData.of(player).getActive();
		if (active != state.lastSyncedActive) {
			state.lastSyncedActive = active;
			state.dirty = true;
		}
		if (state.dirty) {
			state.dirty = false;
			AbilityStateMessage.send(player, state, now);
		}
	}

	/**
	 * Catches EVERY way of leaving a sub-aspect form (toggle, equip change, aspect switch, death,
	 * admin command, level / tier config no longer allowing it): whatever that form's abilities
	 * started ends, and the sub kit's own clean-up runs. SubAspectForms already does this
	 * immediately for toggles; this is the safety net for everything else.
	 */
	private static void checkFormChange(ServerPlayer player, AbilityState state, long now) {
		net.zyren.cultivation.data.SubAspectType form = KitResolver.formInUse(player);
		if (form == state.lastForm)
			return;
		SubAspectKit old = SubAspectKits.get(state.lastForm);
		if (old != null) {
			state.endFrom(new java.util.HashSet<>(old.overrides().values()), now);
			old.leave(player);
		}
		state.lastForm = form;
		state.dirty = true;
	}

	/** "Corrupted Core cannot use Pure aspect." Deactivates Pure if the core becomes corrupted. */
	private static void enforceCorruptedPureRule(ServerPlayer player) {
		AspectData a = AspectData.of(player);
		if (a.getActive() == AspectType.PURE && QiData.of(player).getCoreType() != CoreType.PURE) {
			a.setActive(null);
			player.displayClientMessage(Component.literal("A " + QiData.of(player).getCoreType().id().charAt(0)
					+ QiData.of(player).getCoreType().id().substring(1).toLowerCase() + " Core cannot channel Pure Qi."), false);
		}
	}

	/**
	 * Passive: evaluated every second; upkeep is charged continuously in 1-second chunks while
	 * the condition holds ("Passive Qi costs are continuous while their conditions are active").
	 */
	private static void runPassive(ServerPlayer player, AbilityState state, long now) {
		if (now % 20 != player.getId() % 20)
			return; // once per second, spread across players
		boolean wasActive = state.passiveActive;
		boolean active = false;
		AspectType aspect = AspectData.of(player).getActive();
		AspectPassive passive = KitResolver.passiveFor(player);
		if (passive != state.lastPassive) {
			// The passive in use changed (aspect switch or form toggle): the old one isn't running any more.
			state.lastPassive = passive;
			wasActive = false;
			state.dirty = true;
		}
		if (passive != null && (QiData.of(player).getCoreLevel() >= 1 || net.zyren.cultivation.system.EmergencyCore.inGrace(player)) && !state.isNullified(aspect, now) && passive.conditionMet(player)) {
			double upkeep = QiCosts.effective(player, passive.upkeepPerTick(player) * 20) * (1 - state.passiveCostCut(now));
			double minimum = QiCosts.effective(player, passive.minimumQi(player));
			QiData q = QiData.of(player);
			if (q.getCoreAmount() < Math.max(upkeep, minimum) && upkeep > 0)
				net.zyren.cultivation.system.CultivationAdvancements.onOutOfQi(player);
			if (q.getCoreAmount() >= Math.max(upkeep, minimum))
				active = upkeep <= 0 || QiCoreStorage.trySpendQi(player, upkeep, false);
			if (active && upkeep > 0)
				net.zyren.cultivation.system.AspectMastery.onQiSpent(player, aspect, upkeep);
		}
		state.passiveActive = active;
		if (active)
			passive.refreshEffects(player);
		if (active != wasActive)
			state.dirty = true;
	}

	/**
	 * Sustained abilities: stop when expired, when their aspect isn't active or is nullified,
	 * or when the per-second Qi upkeep can't be paid. Upkeep is charged once per second.
	 */
	private static void runSustained(ServerPlayer player, AbilityState state, long now) {
		var map = state.sustainedMap();
		if (map.isEmpty())
			return;
		boolean secondTick = now % 20 == player.getId() % 20;
		AspectType active = AspectData.of(player).getActive();
		var it = map.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			Ability a = e.getKey();
			// Stops when it's no longer the ability in its slot: other aspect active, or the form changed.
			boolean stop = now >= e.getValue() || a.aspect() != active || !KitResolver.isCurrent(player, a) || state.isNullified(a.aspect(), now);
			String reason = now >= e.getValue() ? " ended." : " stopped.";
			if (!stop && secondTick && a.sustainQiPerSecond() > 0) {
				double upkeep = QiCosts.effective(player, a.sustainQiPerSecond());
				if (QiCoreStorage.trySpendQi(player, upkeep, false)) {
					net.zyren.cultivation.system.AspectMastery.onQiSpent(player, a.aspect(), upkeep);
				} else {
					stop = true;
					reason = " stopped: out of Qi.";
					net.zyren.cultivation.system.CultivationAdvancements.onOutOfQi(player);
				}
			}
			if (stop) {
				it.remove();
				a.onStop(player);
				state.dirty = true;
				player.displayClientMessage(Component.literal(a.name() + reason), true);
			}
		}
	}

	private static AspectPassive activePassive(ServerPlayer player) {
		if (!AbilityState.of(player).passiveActive)
			return null;
		return KitResolver.passiveFor(player);
	}

	// ------------------------------------------------------------------ damage

	/**
	 * Empowered Attack 1 / Heavy, applied FIRST (HIGHEST): several set a fixed amount (e.g. "next
	 * hit = 50"), so they must come before the Pure L5 bonus (HIGH), Corrupted/Pure core rules
	 * (NORMAL) and output multipliers (LOW) — otherwise those were silently thrown away.
	 */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onEmpoweredHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		DamageSource source = event.getSource();
		if (target.level().isClientSide() || event.getNewDamage() <= 0 || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (QiEffects.isDealingQiDamage() || source.getDirectEntity() != attacker || !source.is(DamageTypes.PLAYER_ATTACK))
			return;
		long now = attacker.serverLevel().getGameTime();
		AbilityState as = AbilityState.of(attacker);
		Ability armed = as.consumeEmpowered(now);
		if (armed != null && armed.aspect() == AspectData.of(attacker).getActive() && KitResolver.isCurrent(attacker, armed) && !as.isNullified(armed.aspect(), now)) {
			event.setNewDamage(armed.onEmpoweredHit(attacker, target, event.getNewDamage()));
			as.recordEmpoweredHit(armed, target, now);
			net.zyren.cultivation.system.AspectMastery.onAbilityHit(attacker, armed.aspect(), target);
		}
	}

	/**
	 * Runs on the FINAL calculated damage (after armor etc.), per the document:
	 * "Damage multipliers apply to final calculated damage." All multipliers multiply.
	 */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onDamage(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide())
			return;
		DamageSource source = event.getSource();
		float damage = event.getNewDamage();
		if (damage <= 0)
			return;

		Entity attackerEntity = source.getEntity();
		if (attackerEntity instanceof ServerPlayer attacker && attacker != target) {
			long now = attacker.serverLevel().getGameTime();
			AbilityState as = AbilityState.of(attacker);

			// Total damage output multipliers (buffs + active passive)
			double mult = 1.0;
			for (Buff b : as.buffs())
				if (b.endTick() > now)
					mult *= b.outgoingMultiplier();
			AspectPassive passive = activePassive(attacker);
			if (passive != null)
				mult *= passive.outgoingMultiplier(attacker, target, source);
			damage *= (float) mult;
		}

		if (target instanceof ServerPlayer victim && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			long now = victim.serverLevel().getGameTime();
			double keep = 1.0;
			// Reinforce Body counts once (the strongest), even if several were cast on different aspects.
			double reinforceKeep = 1.0;
			boolean bypass = QiEffects.isBypassingReinforce() && !source.is(DamageTypes.THORNS); // reflected thorns don't inherit the bypass
			for (Buff b : AbilityState.of(victim).buffs()) {
				if (b.endTick() <= now)
					continue;
				if (b.nullifiesIncomingQiEffects()) {
					if (!bypass)
						reinforceKeep = Math.min(reinforceKeep, 1.0 - b.incomingReduction());
				} else {
					keep *= 1.0 - b.incomingReduction();
				}
			}
			keep *= reinforceKeep;
			AspectPassive passive = activePassive(victim);
			if (passive != null)
				keep *= 1.0 - passive.incomingReduction(victim, source);
			damage *= (float) Math.max(0.0, keep);
		}

		event.setNewDamage(damage);
	}

	/**
	 * Lethal guards (e.g. Warrior Ability 2): runs last, on the fully modified final damage.
	 * If the hit would kill and the guard's aspect is active, the guard fires once.
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPossiblyLethal(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		AbilityState state = AbilityState.of(player);
		Ability guard = state.lethalGuard();
		if (guard == null || event.getNewDamage() < player.getHealth() + player.getAbsorptionAmount())
			return;
		// Like the Totem of Undying: no saving from /kill or a Corrupted Core's death ("Player dies").
		if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
			return;
		long now = player.serverLevel().getGameTime();
		if (guard.aspect() != AspectData.of(player).getActive() || !KitResolver.isCurrent(player, guard) || state.isNullified(guard.aspect(), now))
			return;
		state.setLethalGuard(null);
		if (guard.onLethal(player))
			event.setNewDamage(0);
	}

	@SubscribeEvent
	public static void onDamaged(LivingDamageEvent.Post event) {
		if (event.getNewDamage() > 0 && event.getSource().getEntity() instanceof ServerPlayer attacker
				&& event.getEntity() instanceof ServerPlayer victim && attacker != victim)
			AbilityState.of(attacker).setLastDamagedPlayer(victim.getUUID());
	}

	@SubscribeEvent
	public static void onMeleeHit(AttackEntityEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity target) {
			AspectPassive passive = activePassive(player);
			if (passive != null)
				passive.onMeleeHit(player, target);
		}
	}

	// ------------------------------------------------------------------ lifecycle

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AbilityState.of(player).clearTransient(player);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AbilityState.of(player).clearTransient(player);
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AbilityState.of(player).markDirty();
	}
}
