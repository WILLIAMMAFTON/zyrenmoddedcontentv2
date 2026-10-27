package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import net.zyren.cultivation.config.QiGainConfig;
import net.zyren.cultivation.data.QiData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies Qi gains from activities (table in {@link QiGainConfig}).
 *
 *  - Core level 0: the source's CULTIVATED amount goes to no_qi (may form the core).
 *  - Core level 1+: usable -> qi_core_amount; what doesn't fit becomes cultivated (config);
 *    cultivated -> qi_cultivated (may trigger core breakthroughs).
 *
 * Also owns Qi-infused items: custom data "contained_qi" on an item stack.
 */
public final class QiGain {
	public static final String CONTAINED_QI = "contained_qi";

	/** "player|source" -> last tick it paid (once-per-action sources). */
	private static final Map<String, Long> LAST_ACTION = new HashMap<>();

	private QiGain() {
	}

	/** Grants one source's amounts. */
	public static void grant(ServerPlayer player, QiGainConfig.Source source, String label) {
		grant(player, source.usableQi(), source.cultivatedQi(), label);
	}

	/** Grants a source at most once per tick per player (one action = one gain, however many items). */
	public static void grantOncePerAction(ServerPlayer player, QiGainConfig.Source source, String label) {
		String key = player.getUUID() + "|" + label;
		long now = player.serverLevel().getGameTime();
		Long last = LAST_ACTION.get(key);
		if (last != null && last == now)
			return;
		LAST_ACTION.put(key, now);
		if (LAST_ACTION.size() > 4096)
			LAST_ACTION.values().removeIf(t -> now - t > 20);
		grant(player, source, label);
	}

	public static void grant(ServerPlayer player, double usable, double cultivated, String label) {
		if (!QiGainConfig.ENABLED.get() || player.isSpectator())
			return;
		QiData data = QiData.of(player);
		if (data.getCoreLevel() < 1) {
			if (cultivated > 0 && QiCultivation.addNoQi(player, cultivated))
				message(player, "+" + fmt(cultivated) + " no_qi (" + label + ")");
			return;
		}
		double toCultivated = cultivated;
		double added = usable > 0 ? QiCoreStorage.addQi(player, usable) : 0;
		if (QiGainConfig.OVERFLOW_TO_CULTIVATED.get())
			toCultivated += Math.max(0, usable - added);
		if (toCultivated > 0)
			QiCultivation.addQiCultivated(player, toCultivated);
		if (added > 0 || toCultivated > 0)
			message(player, (added > 0 ? "+" + fmt(added) + " Qi" : "") + (added > 0 && toCultivated > 0 ? "  " : "")
					+ (toCultivated > 0 ? "+" + fmt(toCultivated) + " cultivated" : "") + " (" + label + ")");
	}

	// ---- Qi-infused items ----

	public static double containedQi(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getDouble(CONTAINED_QI);
	}

	public static void setContainedQi(ItemStack stack, double amount) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (amount > 0)
				tag.putDouble(CONTAINED_QI, amount);
			else
				tag.remove(CONTAINED_QI);
		});
	}

	/**
	 * Eating an item that holds Qi.
	 *  - No core yet: fills no_qi up to what's needed to form the core; whatever is left goes to
	 *    the new core's usable pool (overflow -> cultivated).
	 *  - Core formed: the cultivated share goes to qi_cultivated, the rest to the usable pool.
	 */
	public static void consumeInfused(ServerPlayer player, double contained) {
		if (contained <= 0 || !QiGainConfig.ENABLED.get())
			return;
		QiData data = QiData.of(player);
		boolean hadCore = data.getCoreLevel() >= 1;
		CultivationAdvancements.onAteInfused(player, false);
		double rest = contained;
		if (data.getCoreLevel() < 1) {
			double needed = Math.max(0, QiBreakthrough.noQiRequired() - data.getNoQi());
			double toNoQi = Math.min(rest, needed);
			if (toNoQi > 0)
				QiCultivation.addNoQi(player, toNoQi);
			rest -= toNoQi;
			if (!hadCore && data.getCoreLevel() >= 1)
				CultivationAdvancements.onAteInfused(player, true);
			if (toNoQi > 0)
				message(player, "+" + fmt(toNoQi) + " no_qi (infused food)");
			if (rest <= 0 || data.getCoreLevel() < 1)
				return;
			grant(player, rest, 0, "infused food");
			return;
		}
		double share = QiGainConfig.INFUSED_CULTIVATED_SHARE.get();
		grant(player, rest * (1 - share), rest * share, "infused food");
	}

	private static void message(ServerPlayer player, String text) {
		if (QiGainConfig.SHOW_MESSAGES.get())
			player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.AQUA), true);
	}

	public static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
	}

	public static void forget(UUID id) {
		String prefix = id + "|";
		LAST_ACTION.keySet().removeIf(k -> k.startsWith(prefix));
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		LAST_ACTION.clear();
	}
}
