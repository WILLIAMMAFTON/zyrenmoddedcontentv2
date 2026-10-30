package net.zyren.cultivation.ability.core;

import net.zyren.cultivation.data.SubAspectType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of sub-aspect kits. A sub-aspect without a kit (not built yet) changes nothing: the
 * resolver simply uses the normal kit. Add a kit with one line in {@link #bootstrap()}.
 */
public final class SubAspectKits {
	private static final Map<SubAspectType, SubAspectKit> KITS = new EnumMap<>(SubAspectType.class);
	private static boolean bootstrapped;

	private SubAspectKits() {
	}

	public static synchronized void bootstrap() {
		if (bootstrapped)
			return;
		bootstrapped = true;
		register(net.zyren.cultivation.ability.kits.FireSubKits.body());
		register(net.zyren.cultivation.ability.kits.FireSubKits.volcanic());
		register(net.zyren.cultivation.ability.kits.FireSubKits.inferno());
		register(net.zyren.cultivation.ability.kits.WaterSubKits.freezing());
		register(net.zyren.cultivation.ability.kits.WaterSubKits.body());
		register(net.zyren.cultivation.ability.kits.WaterSubKits.moon());
		register(net.zyren.cultivation.ability.kits.AirSubKits.sky());
		register(net.zyren.cultivation.ability.kits.AirSubKits.storm());
		register(net.zyren.cultivation.ability.kits.AirSubKits.gale());
		register(net.zyren.cultivation.ability.kits.ElectricSubKits.plasma());
		register(net.zyren.cultivation.ability.kits.ElectricSubKits.receptors());
		register(net.zyren.cultivation.ability.kits.ElectricSubKits.chaos());
	}

	private static void register(SubAspectKit kit) {
		if (KITS.put(kit.sub(), kit) != null)
			throw new IllegalStateException("Two kits registered for " + kit.sub());
	}

	/** @return the kit, or null if the sub-aspect has none (yet) */
	public static SubAspectKit get(SubAspectType sub) {
		if (!bootstrapped)
			bootstrap();
		return sub == null ? null : KITS.get(sub);
	}
}
