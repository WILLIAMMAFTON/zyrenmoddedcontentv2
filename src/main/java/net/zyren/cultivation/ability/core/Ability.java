package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.List;

/**
 * One activatable ability of an aspect kit.
 *
 * {@link AbilityService} does all the shared checks (aspect active, not nullified, cooldown,
 * Qi incl. the Corrupted multiplier), pays the Qi, then calls {@link #perform}. Abilities only
 * describe what they do.
 *
 * EMPOWER abilities (Attack 1 / Heavy style) return {@link Kind#EMPOWER}: paying arms the
 * player's next melee hit, and {@link #onEmpoweredHit} runs when it lands.
 */
public abstract class Ability {
	public enum Kind {
		/** Takes effect when cast. */
		INSTANT,
		/** Arms the next melee hit. */
		EMPOWER,
		/** Runs for {@link #sustainTicks()} with optional Qi upkeep; ends early if Qi runs out or the aspect stops being active. */
		SUSTAINED,
		/** Stays armed until the user would take lethal damage, then {@link #onLethal} runs. */
		LETHAL_GUARD
	}

	private final AspectType aspect;
	private final AbilitySlot slot;
	private final String name;
	private final net.zyren.cultivation.data.SubAspectType subAspect;

	/** A normal-kit ability. */
	protected Ability(AspectType aspect, AbilitySlot slot, String name) {
		this(aspect, slot, name, null);
	}

	/**
	 * A sub-aspect ability. {@code aspect} must be the sub-aspect's PARENT: the cooldown key, Qi /
	 * mastery credit and nullification all go through the parent, so the slot's cooldown is
	 * shared between the normal and the sub-aspect form (decided 2026-10-26).
	 */
	protected Ability(AspectType aspect, AbilitySlot slot, String name, net.zyren.cultivation.data.SubAspectType subAspect) {
		if (subAspect != null && subAspect.parent() != aspect)
			throw new IllegalArgumentException(name + ": " + subAspect + " is not a sub-aspect of " + aspect);
		this.aspect = aspect;
		this.slot = slot;
		this.name = name;
		this.subAspect = subAspect;
	}

	public final AspectType aspect() {
		return aspect;
	}

	public final AbilitySlot slot() {
		return slot;
	}

	/** The sub-aspect this ability belongs to, or null for a normal-kit ability. */
	public final net.zyren.cultivation.data.SubAspectType subAspect() {
		return subAspect;
	}

	/** Name from the document, or the slot name when the document leaves it unnamed. */
	public final String name() {
		return name;
	}

	public Kind kind() {
		return Kind.INSTANT;
	}

	/**
	 * Aspect level needed to use (and see) this ability; 0 = always, -1 = never. Default: the unlock
	 * schedule for this slot (UnlockConfig). Pure abilities override it with Pure's own levels.
	 */
	public int requiredLevel() {
		return UnlockSchedule.level(aspect, slot);
	}

	/** Works on both sides (aspect levels are synced player variables). */
	public final boolean isUnlocked(Player player) {
		int req = requiredLevel();
		return req == 0 || req > 0 && net.zyren.cultivation.data.AspectData.of(player).getLevel(aspect) >= req;
	}

	/**
	 * "Can break core on use" (ability sheet). True = when the player can't pay, the Emergency Core
	 * Breakdown system may sacrifice a core level to pay for it (if their toggle is ON). Default:
	 * Ability 1 / Ability 2 / Ultimate, as before the sheet existed.
	 */
	public boolean canBreakCore() {
		return slot == AbilitySlot.ABILITY_1 || slot == AbilitySlot.ABILITY_2 || slot == AbilitySlot.ULTIMATE;
	}

	/** Qi cost before the Corrupted-core multiplier. May depend on conditions. Negative = unset. */
	public abstract double baseCost(Player player);

	public int cooldownTicks() {
		return 0;
	}

	/** Cost shown in the radial menu (defaults to {@link #baseCost}). */
	public double displayCost(Player player) {
		return baseCost(player);
	}

	/**
	 * True for costs defined as a share of current Qi (e.g. "50% of remaining Qi"): the final
	 * cost, after the Corrupted multiplier, is capped at the Qi the player has.
	 */
	public boolean costCappedAtCurrentQi() {
		return false;
	}

	/**
	 * Names of values the document leaves TBD that this ability can't work without and that are
	 * still unset in the config. Non-empty = ability unavailable ("Not finalized").
	 */
	public List<String> missingValues() {
		return List.of();
	}

	/**
	 * Pressing the ability again while it's running (e.g. Moon High Tide: second press pushes enemies
	 * out). Checked before the cooldown / Qi checks. Return null for "not a recast" (normal use), or
	 * the result of the recast (free, no cooldown change).
	 */
	public ActionResult onRecast(ServerPlayer player) {
		return null;
	}

	/** Extra preconditions checked before paying (e.g. a target exists). */
	public ActionResult checkUse(ServerPlayer player) {
		return ActionResult.ok("");
	}

	/** Runs after Qi is paid. For EMPOWER abilities this is optional (default: nothing). */
	public ActionResult perform(ServerPlayer player) {
		return ActionResult.ok(name + " ready: your next hit is empowered.");
	}

	/** EMPOWER only: called when the empowered hit lands. Returns the new (final) damage. */
	public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
		return damage;
	}

	// ---- SUSTAINED ----

	/** SUSTAINED only: how long it runs. */
	public int sustainTicks() {
		return 0;
	}

	/** SUSTAINED only: Qi per second while running (before Corrupted multiplier). */
	public double sustainQiPerSecond() {
		return 0;
	}

	/** SUSTAINED only: called once when it ends for any reason (expired, no Qi, death, switch...). */
	public void onStop(ServerPlayer player) {
	}

	// ---- LETHAL_GUARD ----

	/** LETHAL_GUARD only: the user is about to take lethal damage. Return true if saved (damage is cancelled). */
	public boolean onLethal(ServerPlayer player) {
		return false;
	}

	protected static List<String> unset(Object... namesAndValues) {
		// pairs: name, Number value; collects names whose value < 0
		java.util.ArrayList<String> out = new java.util.ArrayList<>();
		for (int i = 0; i + 1 < namesAndValues.length; i += 2)
			if (((Number) namesAndValues[i + 1]).doubleValue() < 0)
				out.add((String) namesAndValues[i]);
		return out;
	}
}
