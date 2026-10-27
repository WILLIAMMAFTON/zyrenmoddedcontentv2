package net.zyren.cultivation.ability.core;

import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.SubAspectTiers;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The ONE place that decides which ability / passive a player is using right now. Used by the
 * server (AbilityService, AbilityEvents) and the client (radial menu, HUD), so both always agree.
 *
 *   form in use = the active aspect's equipped sub-aspect, if the form is switched on AND the
 *                 player's aspect level still allows it (tier config can change at any time)
 *   ability     = form's override for the slot, else the normal kit's ability, else null
 *   passive     = form's passive, else the normal kit's passive, else null
 *
 * Works on both sides: everything it reads is a synced player variable or synced server config.
 */
public final class KitResolver {
	private KitResolver() {
	}

	/** The sub-aspect form whose overrides apply right now, or null (normal form). */
	public static SubAspectType formInUse(Player player) {
		SubAspectType sub = SubAspectData.of(player).activeForm();
		if (sub == null)
			return null;
		int level = AspectData.of(player).getLevel(sub.parent());
		return SubAspectTiers.levelAllows(sub, level) ? sub : null;
	}

	/** The ability in this slot right now (sub-aspect override first), or null. */
	public static Ability abilityFor(Player player, AbilitySlot slot) {
		if (slot == null)
			return null;
		AspectType active = AspectData.of(player).getActive();
		SubAspectKit sub = SubAspectKits.get(formInUse(player));
		Ability a = sub == null ? null : sub.override(slot);
		if (a != null)
			return a;
		AspectKit kit = AspectKits.get(active);
		a = kit == null ? null : kit.ability(slot);
		if (a == null && slot == AbilitySlot.REINFORCE && active != null && active != AspectType.LIGHT) {
			// User decision 2026-10-27: until each aspect's Reinforce Body is specified, a kit without
			// one uses Light's. (Its cooldown / buffs count as Light's: placeholder behaviour.)
			AspectKit light = AspectKits.get(AspectType.LIGHT);
			a = light == null ? null : light.ability(AbilitySlot.REINFORCE);
		}
		return a;
	}

	/** The passive in use right now (sub-aspect passive first), or null (also null below the passive's unlock level). */
	public static AspectPassive passiveFor(Player player) {
		AspectType active = AspectData.of(player).getActive();
		if (active == null || !UnlockSchedule.passiveUnlocked(player, active))
			return null;
		SubAspectKit sub = SubAspectKits.get(formInUse(player));
		if (sub != null && sub.passive() != null)
			return sub.passive();
		AspectKit kit = AspectKits.get(active);
		return kit == null ? null : kit.passive();
	}

	/** Every slot's current ability, in slot order (for the radial menu). */
	public static Map<AbilitySlot, Ability> abilities(Player player) {
		Map<AbilitySlot, Ability> out = new EnumMap<>(AbilitySlot.class);
		for (AbilitySlot slot : AbilitySlot.values()) {
			Ability a = abilityFor(player, slot);
			if (a != null)
				out.put(slot, a);
		}
		return Collections.unmodifiableMap(out);
	}

	/**
	 * True while {@code a} is still the ability in its slot. False once the player switched aspect
	 * or toggled form so that a different ability now sits there; armed hits, lethal guards and
	 * sustained abilities from the old form then stop (no carry-over / stacking between forms).
	 */
	public static boolean isCurrent(Player player, Ability a) {
		return a != null && abilityFor(player, a.slot()) == a;
	}
}
