package net.zyren.cultivation.ability.core;

import net.zyren.cultivation.data.AspectType;

/**
 * A timed modifier on a player, created by an ability.
 *
 * @param id                         unique per player; adding a buff with the same id replaces it
 * @param source                     aspect that created it (Nullify of that aspect removes it)
 * @param outgoingMultiplier         x on the player's total damage output (1 = none)
 * @param incomingReduction          fraction (0-1) of incoming damage removed (0 = none)
 * @param nullifiesIncomingQiEffects true for Reinforce Body: Qi effects delivered to the player are nullified
 * @param endTick                    game time when it expires
 */
public record Buff(String id, AspectType source, double outgoingMultiplier, double incomingReduction,
		boolean nullifiesIncomingQiEffects, long endTick) {
}
