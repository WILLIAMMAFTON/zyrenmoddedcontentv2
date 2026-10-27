package net.zyren.cultivation.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/** Light Heavy: shows "[Aspect]" after revealed players' names, above their heads. */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2", value = Dist.CLIENT)
public final class AspectRevealRenderer {
	private AspectRevealRenderer() {
	}

	@SubscribeEvent
	public static void onNameTag(RenderNameTagEvent event) {
		String label = ClientAspectReveal.labelFor(event.getEntity().getId());
		if (label == null)
			return;
		event.setContent(event.getContent().copy()
				.append(Component.literal(" [" + label + "]").withStyle(ChatFormatting.GOLD)));
		event.setCanRender(TriState.TRUE);
	}
}
