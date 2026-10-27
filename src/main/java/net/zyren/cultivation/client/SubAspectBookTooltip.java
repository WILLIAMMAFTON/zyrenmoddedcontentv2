package net.zyren.cultivation.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.SubAspectBooks;
import net.zyren.cultivation.system.SubAspectTiers;

/** "Teaches Fire → Volcanic (Lv 5)" / "Teaches a random Fire sub-aspect" on books and tomes. */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2", value = Dist.CLIENT)
public final class SubAspectBookTooltip {
	private SubAspectBookTooltip() {
	}

	@SubscribeEvent
	public static void onTooltip(ItemTooltipEvent event) {
		ItemStack stack = event.getItemStack();
		var player = Minecraft.getInstance().player;
		if (SubAspectBooks.isBook(stack)) {
			SubAspectType sub = SubAspectBooks.bookSub(stack);
			if (sub == null) {
				event.getToolTip().add(Component.literal("Blank").withStyle(ChatFormatting.GRAY));
				return;
			}
			int req = SubAspectTiers.requiredLevel(sub);
			event.getToolTip().add(Component.literal("Teaches " + sub.fullName() + (req >= 0 ? " (Lv " + req + ")" : " (locked)"))
					.withStyle(ChatFormatting.AQUA));
			if (player != null && SubAspectData.of(player).knows(sub))
				event.getToolTip().add(Component.literal("Already learned").withStyle(ChatFormatting.DARK_GRAY));
		} else if (SubAspectBooks.isTome(stack)) {
			AspectType a = SubAspectBooks.tomeAspect(stack);
			event.getToolTip().add(a == null ? Component.literal("Blank").withStyle(ChatFormatting.GRAY)
					: Component.literal("Teaches a random " + a.displayName() + " sub-aspect").withStyle(ChatFormatting.AQUA));
		}
	}
}
