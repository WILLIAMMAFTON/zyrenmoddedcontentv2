package net.zyren.cultivation.client;

import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import net.zyren.cultivation.system.CoreLife;

/** CORE core type: hides the heart bar while Qi replaces health (the Qi HUD shows "Life Qi"). */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2", value = Dist.CLIENT)
public final class CoreLifeHud {
	private CoreLifeHud() {
	}

	@SubscribeEvent
	public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
		if (!event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH))
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && CoreLife.active(mc.player))
			event.setCanceled(true);
	}
}
