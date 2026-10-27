package net.zyren.cultivation.ability.core;

import net.zyren.cultivation.data.SubAspectType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * What one sub-aspect changes about its parent aspect's kit.
 *
 * Anything left out falls back to the parent's normal kit (user rule 2026-10-26: "NONE -> use the
 * default kit's ability; if that doesn't exist either, nothing"). So a sub-aspect lists only the
 * slots it replaces, and a null passive means "keep the normal passive".
 *
 * Every ability must be built with the sub-aspect's PARENT as its aspect and this sub-aspect as
 * its {@link Ability#subAspect()}; that is checked here so a wrong registration fails at startup
 * instead of misbehaving in game.
 */
public final class SubAspectKit {
	private final SubAspectType sub;
	private final AspectPassive passive;
	private final Map<AbilitySlot, Ability> overrides = new EnumMap<>(AbilitySlot.class);
	private java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> onLeave = p -> {
	};

	/** @param passive replacement passive, or null to keep the normal kit's passive */
	public SubAspectKit(SubAspectType sub, AspectPassive passive, Ability... list) {
		this.sub = sub;
		this.passive = passive;
		for (Ability a : list) {
			if (a.aspect() != sub.parent() || a.subAspect() != sub)
				throw new IllegalArgumentException(a.name() + " must be built with aspect " + sub.parent() + " and sub-aspect " + sub);
			if (overrides.put(a.slot(), a) != null)
				throw new IllegalArgumentException(sub + " has two abilities in slot " + a.slot());
		}
	}

	/**
	 * Extra clean-up when the player leaves this form (toggle off, equip change, aspect switch):
	 * anything the kit attached outside the normal buff system (Thorns grants, TimedAttributes ids,
	 * tagged items...). Buffs, armed hits, lethal guards and sustained abilities are ended automatically.
	 */
	public SubAspectKit onLeave(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> cleanup) {
		this.onLeave = cleanup;
		return this;
	}

	public void leave(net.minecraft.server.level.ServerPlayer player) {
		onLeave.accept(player);
	}

	public SubAspectType sub() {
		return sub;
	}

	/** Replacement passive, or null (= normal passive). */
	public AspectPassive passive() {
		return passive;
	}

	/** Replacement for a slot, or null (= normal kit's ability). */
	public Ability override(AbilitySlot slot) {
		return overrides.get(slot);
	}

	public Map<AbilitySlot, Ability> overrides() {
		return Collections.unmodifiableMap(overrides);
	}
}
