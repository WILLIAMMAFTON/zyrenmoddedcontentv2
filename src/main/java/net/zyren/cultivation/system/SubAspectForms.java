package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectCleanup;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.config.SubAspectConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.data.SubAspectData;
import net.zyren.cultivation.data.SubAspectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Switching between the normal form and the equipped sub-aspect form, and changing which
 * sub-aspect is equipped. Server side only; the keybind and the sub-aspect screen just send requests.
 *
 * Toggle (user decisions 2026-10-26): instant (no channel, no standing still), 15 s cooldown
 * (config form_toggle_cooldown_seconds). Changing the equipped sub-aspect while in sub-aspect form
 * drops back to normal form and also starts that cooldown.
 *
 * Every change goes through {@link #change}: it compares which ability sits in each slot before
 * and after, and ends everything the abilities that left started (buffs, armed hit, lethal guard,
 * sustained abilities, Reinforce extras, the sub kit's own extras). So nothing from one form can
 * keep running or stack in the other.
 */
public final class SubAspectForms {
	/** Player -> game time the toggle is ready again. In memory (like ability cooldowns). */
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private SubAspectForms() {
	}

	// ---------------------------------------------------------------- player actions

	/** The toggle key: normal form <-> equipped sub-aspect form. */
	public static ActionResult toggle(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator())
			return ActionResult.fail("You can't do that right now.");
		if (QiData.of(player).getCoreLevel() < 1 && !EmergencyCore.inGrace(player))
			return ActionResult.fail("You have no Qi Core.");
		AspectType active = AspectData.of(player).getActive();
		if (active == null)
			return ActionResult.fail("No aspect is active.");
		SubAspectData d = SubAspectData.of(player);
		SubAspectType eq = d.equipped(active);
		boolean turningOn = !d.isFormActive();
		if (turningOn) {
			if (eq == null)
				return ActionResult.fail("No " + active.displayName() + " sub-aspect equipped (K menu → Sub).");
			int req = SubAspectTiers.requiredLevel(eq);
			if (!SubAspectTiers.levelAllows(eq, AspectData.of(player).getLevel(active)))
				return ActionResult.fail(eq.fullName() + (req < 0 ? " is locked." : " needs " + active.displayName() + " level " + req + "."));
		}
		long now = player.serverLevel().getGameTime();
		long left = cooldownLeft(player, now);
		if (left > 0)
			return ActionResult.fail("Form switch on cooldown (" + (left + 19) / 20 + "s).");

		change(player, () -> d.setFormActive(turningOn));
		startCooldown(player, now);
		return ActionResult.ok(turningOn ? eq.fullName() + " form." : active.displayName() + ": normal form.");
	}

	/** The sub-aspect screen: equip a learned, unlocked sub-aspect of an owned aspect, or unequip (null). */
	public static ActionResult equip(ServerPlayer player, AspectType aspect, SubAspectType sub) {
		if (aspect == null || !AspectData.of(player).owns(aspect))
			return ActionResult.fail("You don't have that aspect.");
		SubAspectData d = SubAspectData.of(player);
		if (sub != null) {
			if (sub.parent() != aspect || !d.hasLearned(sub))
				return ActionResult.fail("You haven't learned " + sub.fullName() + ".");
			int req = SubAspectTiers.requiredLevel(sub);
			if (!SubAspectTiers.levelAllows(sub, AspectData.of(player).getLevel(aspect)))
				return ActionResult.fail(sub.fullName() + (req < 0 ? " is locked." : " needs " + aspect.displayName() + " level " + req + "."));
		}
		if (d.equipped(aspect) == sub)
			return ActionResult.ok("");
		boolean leavesForm = aspect == AspectData.of(player).getActive() && d.isFormActive();
		change(player, () -> d.equip(aspect, sub));
		if (leavesForm)
			startCooldown(player, player.serverLevel().getGameTime());
		return ActionResult.ok(sub == null ? aspect.displayName() + ": no sub-aspect equipped." : "Equipped " + sub.fullName() + "."
				+ (leavesForm ? " Back to normal form." : ""));
	}

	// ---------------------------------------------------------------- shared

	/**
	 * Runs a change to sub-aspect data and cleans up after it. Admin commands use this too, so an
	 * admin correction can't leave buffs from a removed sub-aspect behind.
	 */
	public static void change(ServerPlayer player, Runnable dataChange) {
		Map<AbilitySlot, Ability> before = KitResolver.abilities(player);
		dataChange.run();

		Set<Ability> gone = new HashSet<>();
		for (Ability a : before.values())
			if (!KitResolver.isCurrent(player, a))
				gone.add(a);
		long now = player.serverLevel().getGameTime();
		AbilityState state = AbilityState.of(player);
		state.endFrom(gone, now);
		for (Ability a : gone)
			if (a.slot() == AbilitySlot.REINFORCE && a.subAspect() == null)
				AspectCleanup.clearReinforceExtras(player, a.aspect()); // the normal Reinforce's Thorns / attributes / effects
		// The sub kit's own leave() clean-up runs from AbilityEvents on the next tick (it notices the
		// form change however it happened), so it is never run twice.
		state.markDirty(); // HUD / radial resync
	}

	private static long cooldownLeft(ServerPlayer player, long now) {
		Long ready = READY_AT.get(player.getUUID());
		return ready == null ? 0 : Math.max(0, ready - now);
	}

	private static void startCooldown(ServerPlayer player, long now) {
		int seconds;
		try {
			seconds = SubAspectConfig.FORM_TOGGLE_COOLDOWN_SECONDS.get();
		} catch (IllegalStateException notLoaded) {
			seconds = 0;
		}
		if (seconds > 0)
			READY_AT.put(player.getUUID(), now + seconds * 20L);
	}

	/** Dev/testing: the form toggle is ready again (/qi cooldowns clear). */
	public static void clearCooldown(ServerPlayer player) {
		READY_AT.remove(player.getUUID());
	}

	/** Shows a result above the hotbar (green / red). */
	public static void report(ServerPlayer player, ActionResult r) {
		if (r.message().isEmpty())
			return;
		player.displayClientMessage(Component.literal(r.message()).withStyle(r.success() ? ChatFormatting.AQUA : ChatFormatting.RED), true);
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		READY_AT.clear();
	}
}
