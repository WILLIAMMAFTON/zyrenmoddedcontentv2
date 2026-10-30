package net.zyren.cultivation.client;

import net.minecraft.client.Minecraft;

import net.zyren.cultivation.network.TargetPickMessage;

import java.util.List;

/** Client side of the target pick: opens / closes the screen. No client-only imports beyond Minecraft. */
public final class ClientTargetPick {
	private ClientTargetPick() {
	}

	public static void open(String title, int seconds, List<TargetPickMessage.Option> options) {
		Minecraft mc = Minecraft.getInstance();
		if (options.isEmpty()) {
			if (mc.screen instanceof TargetPickScreen)
				mc.setScreen(null);
			return;
		}
		mc.setScreen(new TargetPickScreen(title, seconds, options));
	}
}
