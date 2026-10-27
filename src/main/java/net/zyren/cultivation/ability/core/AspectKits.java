package net.zyren.cultivation.ability.core;

import net.zyren.cultivation.ability.kits.DarkKit;
import net.zyren.cultivation.ability.kits.EarthKit;
import net.zyren.cultivation.ability.kits.LightningKit;
import net.zyren.cultivation.ability.kits.FireKit;
import net.zyren.cultivation.ability.kits.LightKit;
import net.zyren.cultivation.ability.kits.PureKit;
import net.zyren.cultivation.ability.kits.ProtectiveKit;
import net.zyren.cultivation.ability.kits.WaterKit;
import net.zyren.cultivation.ability.kits.PhysicalKit;
import net.zyren.cultivation.data.AspectType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of aspect kits. Aspects without a kit yet (not implemented) simply have no
 * abilities or passive. Add a kit with one line in {@link #bootstrap()}.
 */
public final class AspectKits {
	private static final Map<AspectType, AspectKit> KITS = new EnumMap<>(AspectType.class);
	private static boolean bootstrapped;

	private AspectKits() {
	}

	public static synchronized void bootstrap() {
		if (bootstrapped)
			return;
		bootstrapped = true;
		// Warrior is now the sub-aspect Physical -> Warrior (2026-10-27); its old kit is no longer registered.
		register(PhysicalKit.create());
		register(DarkKit.create());
		register(EarthKit.create());
		register(LightningKit.create());
		register(FireKit.create());
		register(LightKit.create());
		register(PureKit.create());
		register(ProtectiveKit.create());
		register(WaterKit.create());
	}

	private static void register(AspectKit kit) {
		KITS.put(kit.aspect(), kit);
	}

	/** @return the kit, or null if the aspect has none (yet) */
	public static AspectKit get(AspectType aspect) {
		if (!bootstrapped)
			bootstrap();
		return aspect == null ? null : KITS.get(aspect);
	}
}
