package net.zyren.cultivation.system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.data.QiData;

/**
 * Basic operations on usable Qi (qi_core_amount), always kept within [0, qi_core_max].
 *
 * Corrupted cost multipliers, Doom Blow (spend ALL) and core breaking are separate systems
 * for later stages and must not be folded into this class.
 */
public final class QiCoreStorage {
	private QiCoreStorage() {
	}

	/** Adds Qi, capped at qi_core_max. @return the amount actually added */
	public static double addQi(Player player, double amount) {
		if (player.level().isClientSide() || amount <= 0)
			return 0;
		QiData data = QiData.of(player);
		double before = data.getCoreAmount();
		double after = Math.min(before + amount, data.getCoreMax());
		if (after <= before)
			return 0;
		data.setCoreAmount(after);
		return after - before;
	}

	public static boolean hasQi(Player player, double amount) {
		return QiData.of(player).getCoreAmount() >= amount;
	}

	/** Spends Qi only if the full amount is available. Interrupts an aspect switch. @return true if spent */
	public static boolean trySpendQi(Player player, double amount) {
		return trySpendQi(player, amount, true);
	}

	/**
	 * @param interruptsSwitch false for passive upkeep (a passive draining Qi in the background
	 *                         must not cancel the player's aspect switch)
	 */
	public static boolean trySpendQi(Player player, double amount, boolean interruptsSwitch) {
		if (player.level().isClientSide() || amount < 0)
			return false;
		QiData data = QiData.of(player);
		if (data.getCoreAmount() < amount)
			return false;
		data.setCoreAmount(data.getCoreAmount() - amount);
		// Using Qi interrupts an aspect switch in progress.
		if (interruptsSwitch && player instanceof ServerPlayer sp)
			AspectSwitching.cancel(sp, "Interrupted: you used Qi.");
		return true;
	}

	/** Sets the core capacity, trimming current Qi if it no longer fits. */
	public static void setCoreMax(Player player, double max) {
		if (player.level().isClientSide())
			return;
		QiData data = QiData.of(player);
		data.setCoreMax(max);
		if (data.getCoreAmount() > data.getCoreMax())
			data.setCoreAmount(data.getCoreMax());
	}
}
