package net.zyren.cultivation.ability.kits;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiCosts;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.Thorns;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.CoreBreak;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightning's world interactions: lightning strikes, the passive's lethal save, Attack 1's
 * charged fists, Powerblast 1 flight, Ability 1's storm and Nuclear Winter.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class LightningEvents {
	private static final class Winter {
		long endTick;
		boolean broken;
	}

	private static final Map<UUID, Long> CHARGED_FISTS = new HashMap<>();
	private static final Map<UUID, Winter> WINTERS = new HashMap<>();

	private LightningEvents() {
	}

	// ------------------------------------------------------------------ strikes

	/** A lightning bolt that only looks and sounds like one (used for "lightning strikes you"). */
	static void strikeVisual(ServerLevel level, Vec3 pos) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt == null)
			return;
		bolt.moveTo(pos);
		bolt.setVisualOnly(true);
		level.addFreshEntity(bolt);
	}

	/** Lightning on a target: visual bolt + Qi damage attributed to the user. */
	static void strike(ServerPlayer user, LivingEntity target, double damage) {
		strikeVisual(user.serverLevel(), target.position());
		QiEffects.dealDamage(user, target, (float) damage);
	}

	// ------------------------------------------------------------------ timed states

	static void chargeFists(ServerPlayer player, int ticks) {
		CHARGED_FISTS.put(player.getUUID(), player.serverLevel().getGameTime() + ticks);
	}

	/** Creative-style flight via NeoForge's flight attribute; TimedAttributes removes it on expiry/death/logout. */
	static void grantFlight(ServerPlayer player, int ticks) {
		TimedAttributes.apply(player, NeoForgeMod.CREATIVE_FLIGHT, "lightning_flight", 1, AttributeModifier.Operation.ADD_VALUE, ticks);
	}

	static boolean nuclearWinterActive(ServerPlayer player) {
		Winter w = WINTERS.get(player.getUUID());
		return w != null && player.serverLevel().getGameTime() < w.endTick;
	}

	private static boolean brokenCore(ServerPlayer player) {
		Winter w = WINTERS.get(player.getUUID());
		return w != null && w.broken;
	}

	static ActionResult startNuclearWinter(ServerPlayer player) {
		int ticks = AbilityConfig.ELEC_ULT_DURATION.get();
		double required = QiCosts.effective(player, AbilityConfig.ELEC_ULT_COST.get());
		double have = QiData.of(player).getCoreAmount();
		boolean broken = have < required;
		double paid = Math.min(have, required);
		QiCoreStorage.trySpendQi(player, paid); // "uses as much Qi as possible"
		AbilityState.of(player).recordPaid(paid); // paid here, not by the service: record it like any cast
		net.zyren.cultivation.system.AspectMastery.onQiSpent(player, LightningKit.L, paid);

		// Force global stormy weather with lightning.
		player.server.overworld().setWeatherParameters(0, ticks, true, true);
		if (player.serverLevel() != player.server.overworld())
			player.serverLevel().setWeatherParameters(0, ticks, true, true);

		Winter w = new Winter();
		w.endTick = player.serverLevel().getGameTime() + ticks;
		w.broken = broken;
		WINTERS.put(player.getUUID(), w);
		long now = player.serverLevel().getGameTime();
		AbilityState.of(player).addBuff(new Buff("lightning_nuclear_winter", LightningKit.L, AbilityConfig.ELEC_ULT_OUTPUT.get(), 0, false, now + ticks));

		if (broken) {
			QiData.of(player).setCoreAmount(AbilityConfig.ELEC_ULT_BROKEN_QI.get());
			player.displayClientMessage(Component.literal("Your Core shatters into the storm! 5000 Qi until the weather ends... then nothing."), false);
			return ActionResult.ok("NUCLEAR WINTER (Core broken).");
		}
		return ActionResult.ok("Nuclear Winter!");
	}

	private static void endNuclearWinter(ServerPlayer player) {
		Winter w = WINTERS.remove(player.getUUID());
		if (w == null)
			return;
		if (w.broken) {
			// "When weather ends, remove all remaining Qi and cultivation stats."
			QiData.of(player).setCoreAmount(0);
			CoreBreak.breakCore(player, "the storm took your Core");
		} else {
			player.displayClientMessage(Component.literal("Nuclear Winter ends."), true);
		}
	}

	// ------------------------------------------------------------------ ticks

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		UUID id = player.getUUID();
		long now = player.serverLevel().getGameTime();

		Winter w = WINTERS.get(id);
		if (w != null && player.isAlive()) { // dead: ended on respawn
			// The weather FLAG, not isThundering(): that one only turns true once the thunder level has
			// faded in (~1-2 s), which ended the winter on the first tick when cast in clear weather.
			boolean stormOver = now >= w.endTick || !player.server.overworld().getLevelData().isThundering();
			if (w.broken && QiData.of(player).getCoreLevel() < 1)
				w.broken = false; // the Core was already broken/annihilated by something else: nothing left to take
			if (stormOver)
				endNuclearWinter(player);
			else if (w.broken && QiData.of(player).getCoreAmount() != AbilityConfig.ELEC_ULT_BROKEN_QI.get())
				QiData.of(player).setCoreAmount(AbilityConfig.ELEC_ULT_BROKEN_QI.get()); // "Maintain 5000 Qi"
		}

		// Ability 1: lightning on every non-allied player in range, every 3 s.
		if (AbilityState.of(player).isSustained(LightningKit.ABILITY1) && now % AbilityConfig.ELEC_ABILITY1_INTERVAL.get() == 0) {
			double r = AbilityConfig.ELEC_ABILITY1_RADIUS.get();
			for (ServerPlayer t : player.serverLevel().players())
				if (Targeting.isValid(player, t) && !Targeting.isAllied(player, t) && t.distanceTo(player) <= r)
					strike(player, t, LightningKit.lightningDamage());
		}
	}

	// ------------------------------------------------------------------ damage

	@SubscribeEvent
	public static void onDamage(LivingDamageEvent.Pre event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity())
			return;
		if (AspectData.of(attacker).getActive() != LightningKit.L
				|| AbilityState.of(attacker).isNullified(LightningKit.L, attacker.serverLevel().getGameTime()))
			return; // nullified Lightning: no x5 broken-core melee / storm bonus
		double mult = 1.0;
		boolean melee = event.getSource().is(DamageTypes.PLAYER_ATTACK) && event.getSource().getDirectEntity() == attacker && !QiEffects.isDealingQiDamage();
		if (melee && nuclearWinterActive(attacker) && brokenCore(attacker))
			mult *= AbilityConfig.ELEC_ULT_BROKEN_MELEE.get(); // "5x total melee damage output"
		if (AbilityState.of(attacker).isSustained(LightningKit.ABILITY1) && !LightningKit.passiveOn(attacker) && !nuclearWinterActive(attacker))
			mult *= AbilityConfig.ELEC_ABILITY1_OUTPUT.get(); // "+50% final output; does not stack with Nuclear Winter or passive"
		if (mult != 1.0)
			event.setNewDamage((float) (event.getNewDamage() * mult));
	}

	/** Attack 1: charged melee hits burn and stun. */
	@SubscribeEvent
	public static void onHit(LivingDamageEvent.Post event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity())
			return;
		Long until = CHARGED_FISTS.get(attacker.getUUID());
		boolean melee = event.getSource().is(DamageTypes.PLAYER_ATTACK) && event.getSource().getDirectEntity() == attacker && !QiEffects.isDealingQiDamage();
		if (until == null || !melee || attacker.serverLevel().getGameTime() >= until || AspectData.of(attacker).getActive() != LightningKit.L
				|| AbilityState.of(attacker).isNullified(LightningKit.L, attacker.serverLevel().getGameTime()))
			return;
		LivingEntity target = event.getEntity();
		if (!QiEffects.canAffect(target, false))
			return;
		target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), AbilityConfig.ELEC_ATTACK1_FIRE_TICKS.get()));
		Stun.apply(target, AbilityConfig.ELEC_ATTACK1_STUN_TICKS.get());
	}

	/** Storm Conduit lethal save: runs last, on fully modified final damage. */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPossiblyLethal(LivingDamageEvent.Pre event) {
		if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() < player.getHealth() + player.getAbsorptionAmount())
			return;
		// Like the Totem of Undying: no saving from /kill or a Corrupted Core's death ("Player dies").
		if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))
			return;
		if (!LightningKit.passiveOn(player) || nuclearWinterActive(player))
			return;
		double cost = QiCosts.effective(player, AbilityConfig.ELEC_SAVE_COST.get());
		if (!QiCoreStorage.trySpendQi(player, cost, false))
			return;
		event.setNewDamage(0);
		int ticks = AbilityConfig.ELEC_SAVE_TICKS.get();
		strikeVisual(player.serverLevel(), player.position());
		EffectHelper.apply(player, MobEffects.REGENERATION, 255, ticks, true);
		Thorns.grant(player, "lightning_lethal_save", AbilityConfig.ELEC_SAVE_THORNS.get(), ticks);
		player.displayClientMessage(Component.literal("The storm refuses to let you fall."), true);
	}

	// ------------------------------------------------------------------ cleanup

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			CHARGED_FISTS.remove(player.getUUID());
		}
	}

	/**
	 * Death ends Nuclear Winter (user decision 2026-10-07): the 5000 Qi lock and x5 melee don't
	 * survive respawn. Ended on respawn, after the death penalty has been applied to the dying
	 * player; a broken-core winter still takes the Core when it ends.
	 */
	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered())
			endNuclearWinter(player);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			CHARGED_FISTS.remove(player.getUUID());
			// The winter isn't saved: logging out (or the server stopping) ends it now, so a broken
			// Core can't escape its price by relogging and keep the 5000 Qi.
			endNuclearWinter(player);
		}
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		CHARGED_FISTS.clear();
		WINTERS.clear();
	}
}
