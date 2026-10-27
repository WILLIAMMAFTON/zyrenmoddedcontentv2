package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Expensive world searches (Fire Emergency Leave scans ~900k blocks; Water "Say Hi to the Fishies"
 * runs a 2000-block biome search) cost no Qi and no cooldown when they find nothing, so they could
 * be spammed to lag the server. A search may run at most once per {@code ticks} per player.
 */
public final class SearchThrottle {
	private static final Map<String, Long> NEXT = new HashMap<>();

	private SearchThrottle() {
	}

	/** @return true if a recent search for this player found nothing and the wait isn't over */
	public static boolean blocked(ServerPlayer player, String key) {
		Long next = NEXT.get(key + "|" + player.getUUID());
		return next != null && player.serverLevel().getGameTime() < next;
	}

	/** A search found nothing: the next one may run after {@code ticks}. */
	public static void failed(ServerPlayer player, String key, int ticks) {
		NEXT.put(key + "|" + player.getUUID(), player.serverLevel().getGameTime() + ticks);
	}

	public static void forget(UUID id) {
		NEXT.keySet().removeIf(k -> k.endsWith("|" + id));
	}

	public static void resetAll() {
		NEXT.clear();
	}
}
