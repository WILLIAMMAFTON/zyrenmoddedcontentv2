package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;

/**
 * Core TYPE handling (Cursed / Elemental / Void / Core / Soul). See claude/core-types.md.
 *
 * - Assigned at random (config weights, default equal) the first time a player joins; players from
 *   older saves get one at their next login.
 * - Permanent in normal play: core breaks, annihilation, death and /qi reset never touch it.
 * - Changes will come from items / actions that are not designed yet. Every change MUST go through
 *   {@link #set} so those future rules (and anything that reacts to a change) live in one place.
 *
 * Labels only for now: no type has any gameplay effect.
 */
public final class CoreTypes {
	private CoreTypes() {
	}

	/** Gives a random core type if the player has none. @return true if one was assigned. */
	public static boolean ensureAssigned(ServerPlayer player) {
		if (QiData.of(player).getCoreType() != null)
			return false;
		CoreType rolled = roll(player.getRandom());
		set(player, rolled, "first join");
		if (CultivationConfig.CORE_TYPE_ANNOUNCE.get())
			player.displayClientMessage(Component.literal("You were born with a " + rolled.displayName() + " core.").withStyle(ChatFormatting.GOLD), false);
		return true;
	}

	/**
	 * The single entry point for changing a player's core type (admin command now; items/actions later).
	 *
	 * @param source short description of what caused the change (for future hooks / logging)
	 * @return the previous type (null if none)
	 */
	public static CoreType set(ServerPlayer player, CoreType type, String source) {
		QiData q = QiData.of(player);
		CoreType old = q.getCoreType();
		if (old == type)
			return old;
		q.setCoreType(type);
		// Becoming CORE with a formed core: fill the pool (it's now the player's health).
		if (type == CoreType.CORE)
			CoreLife.fillIfActive(player);
		// Becoming CURSED: corruption already on the meter turns into Cursed Qi.
		if (type == CoreType.CURSED)
			CursedQi.convertMeter(player);
		return old;
	}

	/**
	 * Core types whose normal activity sources give no usable Qi (cultivated / no_qi still count).
	 * SOUL now; BLOOD will join ("limits qi gain same as soul").
	 */
	public static boolean cutsNormalUsableQi(net.minecraft.world.entity.player.Player player) {
		CoreType t = QiData.of(player).getCoreType();
		return t == CoreType.SOUL || t == CoreType.BLOOD;
	}

	/** New random type. May roll the same type again (pure random, equal chances by default). */
	public static CoreType reroll(ServerPlayer player, String source) {
		CoreType rolled = roll(player.getRandom());
		set(player, rolled, source);
		return rolled;
	}

	/** Weighted random pick. If every weight is 0, falls back to equal chances. */
	public static CoreType roll(RandomSource random) {
		CoreType[] all = CoreType.values();
		double[] w = new double[all.length];
		double total = 0;
		for (int i = 0; i < all.length; i++) {
			w[i] = Math.max(0, weight(all[i]));
			total += w[i];
		}
		if (total <= 0)
			return all[random.nextInt(all.length)];
		double r = random.nextDouble() * total;
		for (int i = 0; i < all.length; i++) {
			r -= w[i];
			if (r < 0 && w[i] > 0)
				return all[i];
		}
		for (int i = all.length - 1; i >= 0; i--)
			if (w[i] > 0)
				return all[i];
		return all[0];
	}

	private static double weight(CoreType t) {
		return switch (t) {
			case CURSED -> CultivationConfig.CORE_TYPE_WEIGHT_CURSED.get();
			case ELEMENTAL -> CultivationConfig.CORE_TYPE_WEIGHT_ELEMENTAL.get();
			case VOID -> CultivationConfig.CORE_TYPE_WEIGHT_VOID.get();
			case CORE -> CultivationConfig.CORE_TYPE_WEIGHT_CORE.get();
			case SOUL -> CultivationConfig.CORE_TYPE_WEIGHT_SOUL.get();
			case BLOOD -> CultivationConfig.CORE_TYPE_WEIGHT_BLOOD.get();
		};
	}
}
