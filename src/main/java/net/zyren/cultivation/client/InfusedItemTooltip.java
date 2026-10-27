package net.zyren.cultivation.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import net.zyren.cultivation.system.QiGain;

/** Shows "Contains X Qi" on Qi-infused items. */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2", value = Dist.CLIENT)
public final class InfusedItemTooltip {
	private InfusedItemTooltip() {
	}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		double qi = QiGain.containedQi(event.getItemStack());
		if (qi > 0)
			event.getToolTip().add(Component.literal("Contains " + QiGain.fmt(qi) + " Qi").withStyle(ChatFormatting.AQUA));
	}
}
