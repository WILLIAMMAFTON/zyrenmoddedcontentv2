package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectType;

/**
 * Ends what an aspect left on the player (user decisions 2026-10-08):
 *  - Nullify also removes what that aspect's Reinforce Body attached (Thorns grants, Earth's
 *    knockback resistance, Physical's Regeneration/Resistance 255), not just the Reinforce buff.
 *  - Switching aspects ends the old aspects' buffs (output multipliers, Reinforce) and extras.
 */
public final class AspectCleanup {
	private AspectCleanup() {
	}

	/** Removes the extras an aspect's Reinforce Body attached. */
	public static void clearReinforceExtras(ServerPlayer p, AspectType aspect) {
		if (aspect == null)
			return;
		switch (aspect) {
			case DARK -> Thorns.revoke(p, "dark_reinforce");
			case EARTH -> TimedAttributes.remove(p, "earth_reinforce_kb");
			case ELECTRIC -> Thorns.revoke(p, "lightning_reinforce");
			case PHYSICAL -> {
				Thorns.revoke(p, "physical_reinforce");
				removeIfLevel(p, MobEffects.REGENERATION, AbilityConfig.PHYSICAL_REINFORCE_REGEN_LEVEL.get());
				removeIfLevel(p, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.PHYSICAL_REINFORCE_RESISTANCE_LEVEL.get());
			}
			default -> {
			}
		}
	}

	/** Switching to {@code newAspect}: every other aspect's buffs and Reinforce extras end. */
	public static void onSwitched(ServerPlayer p, AspectType newAspect) {
		AbilityState.of(p).removeBuffsExcept(newAspect);
		for (AspectType a : AspectType.values())
			if (a != newAspect)
				clearReinforceExtras(p, a);
	}

	private static void removeIfLevel(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int level) {
		var inst = p.getEffect(effect);
		if (inst != null && inst.getAmplifier() == Math.min(level, EffectHelper.MAX_LEVEL) - 1)
			p.removeEffect(effect);
	}
}
