package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * An aspect's passive. Runs only while its aspect is the active aspect and not nullified.
 *
 * Each server tick {@link PassiveRunner}:
 *  1. asks {@link #conditionMet} (e.g. "holding a sword", "in water or rain");
 *  2. if met, charges {@link #upkeepPerTick} (continuous cost, x Corrupted multiplier);
 *  3. if the player could pay, the passive is ACTIVE this tick; every second
 *     {@link #refreshEffects} re-applies its status effects (short durations, so they
 *     fall off within ~2 s of the passive stopping).
 * Hooks below are only called while the passive is active.
 */
public abstract class AspectPassive {
	/** Is the passive's own condition met (ignoring Qi)? */
	public abstract boolean conditionMet(ServerPlayer player);

	/** Continuous Qi upkeep per tick while the condition is met (before Corrupted multiplier). */
	public double upkeepPerTick(ServerPlayer player) {
		return 0;
	}

	/** Minimum Qi needed for the passive to be active (for per-use costs). */
	public double minimumQi(ServerPlayer player) {
		return 0;
	}

	/** Re-applies status effects; called once per second while active. */
	public void refreshEffects(ServerPlayer player) {
	}

	/** The player landed a melee hit while the passive is active. */
	public void onMeleeHit(ServerPlayer player, LivingEntity target) {
	}

	/** Multiplier on the player's total damage output while active. */
	public double outgoingMultiplier(ServerPlayer player, LivingEntity target, DamageSource source) {
		return 1.0;
	}

	/** Fraction (0-1) of incoming damage removed while active. */
	public double incomingReduction(ServerPlayer player, DamageSource source) {
		return 0.0;
	}

	/** Helper: Qi per tick for "X Qi every N seconds". */
	protected static double perSeconds(double qi, double seconds) {
		return qi / (seconds * 20.0);
	}
}
