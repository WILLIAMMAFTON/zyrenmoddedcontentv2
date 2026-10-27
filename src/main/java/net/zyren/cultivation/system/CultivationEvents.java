package net.zyren.cultivation.system;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.QiData;

/**
 * Server-side game events for the cultivation systems:
 *  - login: clean up stored strings (quoted defaults, LIGHTNING alias, stale active aspect)
 *  - logout: drop any aspect switch in progress
 *  - player tick: finish aspect switches
 *  - taking damage / attacking: interrupt aspect switches
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class CultivationEvents {
	private CultivationEvents() {
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			QiData.of(player).normalize();
			AspectMigration.migrate(player); // old saves: Warrior -> Physical (+ Physical -> Warrior learned)
			AspectData.of(player).normalize();
			net.zyren.cultivation.data.SubAspectData.of(player).normalize(); // after AspectData: needs clean slots
		}
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AspectSwitching.discard(player);
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AspectSwitching.tick(player);
	}

	@SubscribeEvent
	public static void onDamaged(LivingIncomingDamageEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AspectSwitching.cancel(player, "Interrupted: you took damage.");
	}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			AspectSwitching.cancel(player, "Interrupted: you attacked.");
	}
}
