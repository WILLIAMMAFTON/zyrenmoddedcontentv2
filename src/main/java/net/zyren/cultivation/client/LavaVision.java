package net.zyren.cultivation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FogType;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;

/** Fire passive "can see through lava": while Fire is active and its passive is on, lava fog is pushed far away. */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2", value = Dist.CLIENT)
public final class LavaVision {
	private LavaVision() {
	}

	@SubscribeEvent
	public static void onFog(ViewportEvent.RenderFog event) {
		if (event.getType() != FogType.LAVA)
			return;
		var player = Minecraft.getInstance().player;
		if (player == null || AspectData.of(player).getActive() != AspectType.FIRE || !ClientAbilityState.passiveActive()
				|| !net.zyren.cultivation.ability.kits.FireKit.isDefaultPassive(net.zyren.cultivation.ability.core.KitResolver.passiveFor(player)))
			return; // only the DEFAULT Fire passive gives lava vision (Body / Inferno replace it)
		event.setNearPlaneDistance(0f);
		event.setFarPlaneDistance(24f);
		event.setCanceled(true); // required for the new distances to apply
	}
}
