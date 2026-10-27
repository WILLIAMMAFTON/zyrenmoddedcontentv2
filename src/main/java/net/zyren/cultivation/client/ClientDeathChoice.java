package net.zyren.cultivation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Client side of the death-choice GUI. While a choice is pending the GUI is (re)opened whenever
 * the player is alive and it isn't showing, so it can't be skipped.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2", value = Dist.CLIENT)
public final class ClientDeathChoice {
	private static List<String> options = List.of();

	private ClientDeathChoice() {
	}

	public static void set(List<String> opts) {
		options = List.copyOf(opts);
		Minecraft mc = Minecraft.getInstance();
		if (options.isEmpty() && mc.screen instanceof DeathChoiceScreen)
			mc.setScreen(null);
	}

	/** Called by the GUI after a click: wait for the server instead of reopening. */
	static void clear() {
		options = List.of();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (options.isEmpty())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !mc.player.isAlive() || mc.screen instanceof DeathChoiceScreen || mc.screen instanceof DeathScreen)
			return;
		mc.setScreen(new DeathChoiceScreen(options));
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		options = List.of();
	}
}
