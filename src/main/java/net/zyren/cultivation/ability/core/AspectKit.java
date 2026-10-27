package net.zyren.cultivation.ability.core;

import net.zyren.cultivation.data.AspectType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Everything one aspect can do: an optional passive plus abilities by slot. */
public final class AspectKit {
	private final AspectType aspect;
	private final AspectPassive passive;
	private final Map<AbilitySlot, Ability> abilities = new EnumMap<>(AbilitySlot.class);

	public AspectKit(AspectType aspect, AspectPassive passive, Ability... list) {
		this.aspect = aspect;
		this.passive = passive;
		for (Ability a : list) {
			if (a.aspect() != aspect)
				throw new IllegalArgumentException(a.name() + " belongs to " + a.aspect() + ", not " + aspect);
			abilities.put(a.slot(), a);
		}
	}

	public AspectType aspect() {
		return aspect;
	}

	/** May be null (e.g. Pure has no passive in the document). */
	public AspectPassive passive() {
		return passive;
	}

	public Ability ability(AbilitySlot slot) {
		return abilities.get(slot);
	}

	/** Abilities in slot order. */
	public Map<AbilitySlot, Ability> abilities() {
		return Collections.unmodifiableMap(abilities);
	}
}
