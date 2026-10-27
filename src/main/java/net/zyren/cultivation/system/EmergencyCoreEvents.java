package net.zyren.cultivation.system;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class EmergencyCoreEvents {
	private EmergencyCoreEvents() {
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer p && (p.serverLevel().getGameTime() + p.getId()) % 20 == 0)
			EmergencyCore.tick(p);
	}
}
