package net.zyren.cultivation.data;

import net.minecraft.world.entity.player.Player;

import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables;
import net.mcreator.zyrenmoddedcontentv2.network.ZyrenmoddedcontentV2ModVariables.PlayerVariables;

/**
 * The ONLY class that reads or writes the cultivation player variables.
 *
 * Storage is MCreator's own player_persistent variables, so MCreator procedures, GUIs and
 * overlays can read the same values. If the storage ever changes (renamed variable, custom
 * attachment), only this file needs updating.
 *
 * Every setter marks the variables for client sync. Call setters on the server side only.
 */
public final class QiData {
	private final PlayerVariables vars;

	private QiData(Player player) {
		this.vars = player.getData(ZyrenmoddedcontentV2ModVariables.PLAYER_VARIABLES);
	}

	public static QiData of(Player player) {
		return new QiData(player);
	}

	// ---- no_qi: pre-cultivation progress (NOT an aspect) ----
	public double getNoQi() {
		return vars.no_qi;
	}

	public void setNoQi(double value) {
		vars.no_qi = Math.max(0, value);
		dirty();
	}

	// ---- qi_core_level: development of the core (NOT an aspect level) ----
	public int getCoreLevel() {
		return (int) vars.Qi_core_level;
	}

	public void setCoreLevel(int value) {
		vars.Qi_core_level = Math.max(0, value);
		dirty();
	}

	// ---- qi_core_amount: usable Qi currently stored ----
	public double getCoreAmount() {
		return vars.qi_core_amount;
	}

	public void setCoreAmount(double value) {
		vars.qi_core_amount = Math.max(0, value);
		dirty();
	}

	// ---- qi_core_max: capacity of the core ----
	public double getCoreMax() {
		return vars.qi_core_max;
	}

	public void setCoreMax(double value) {
		vars.qi_core_max = Math.max(0, value);
		dirty();
	}

	// ---- qi_cultivated: cultivation progress (NOT the same as usable Qi) ----
	public double getCultivated() {
		return vars.qi_cultivated;
	}

	public void setCultivated(double value) {
		vars.qi_cultivated = Math.max(0, value);
		dirty();
	}

	// ---- qi_core_type: condition of the core (NOT an aspect) ----
	public CoreType getCoreType() {
		return CoreType.fromId(vars.qi_core_type);
	}

	public void setCoreType(CoreType type) {
		vars.qi_core_type = type.id();
		dirty();
	}

	/** Rewrites qi_core_type into its clean form (e.g. MCreator's quoted "pure" -> PURE). */
	public void normalize() {
		String clean = getCoreType().id();
		if (!clean.equals(vars.qi_core_type)) {
			vars.qi_core_type = clean;
			dirty();
		}
	}

	/** Puts the player back into the spec's initial state (section 3). */
	public void resetToInitialState() {
		vars.no_qi = 0;
		vars.Qi_core_level = 0;
		vars.qi_core_amount = 0;
		vars.qi_core_max = 0;
		vars.qi_cultivated = 0;
		vars.qi_core_type = CoreType.PURE.id();
		dirty();
	}

	private void dirty() {
		vars.markSyncDirty();
	}
}
