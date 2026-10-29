/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zyrenmoddedcontentv2.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import net.mcreator.zyrenmoddedcontentv2.ZyrenmoddedcontentV2Mod;

@EventBusSubscriber
public class ZyrenmoddedcontentV2ModTabs {
	public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZyrenmoddedcontentV2Mod.MODID);
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ZYREN_MODDED_ITEMS = REGISTRY.register("zyren_modded_items",
			() -> CreativeModeTab.builder().title(Component.translatable("item_group.zyrenmoddedcontent_v2.zyren_modded_items")).icon(() -> new ItemStack(Blocks.BARRIER)).displayItems((parameters, tabData) -> {
				tabData.accept(ZyrenmoddedcontentV2ModItems.PURIFYING_CRYSTAL.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.ASPECT_TOME.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.SUB_ASPECT_BOOK.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSED_LITE_STEEL_ARMOR_HELMET.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSED_LITE_STEEL_ARMOR_CHESTPLATE.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSED_LITE_STEEL_ARMOR_LEGGINGS.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSED_LITE_STEEL_ARMOR_BOOTS.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_ARMOR_HELMET.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_ARMOR_CHESTPLATE.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_ARMOR_LEGGINGS.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_ARMOR_BOOTS.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_SWORD.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSEDSTEELARMOR_HELMET.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSEDSTEELARMOR_CHESTPLATE.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSEDSTEELARMOR_LEGGINGS.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSEDSTEELARMOR_BOOTS.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.LIGHTSTEELARMOR_HELMET.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.LIGHTSTEELARMOR_CHESTPLATE.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.LIGHTSTEELARMOR_LEGGINGS.get());
				tabData.accept(ZyrenmoddedcontentV2ModItems.LIGHTSTEELARMOR_BOOTS.get());
			}).withSearchBar().build());

	@SubscribeEvent
	public static void buildTabContentsVanilla(BuildCreativeModeTabContentsEvent tabData) {
		if (tabData.getTabKey() == CreativeModeTabs.COMBAT) {
			tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSED_LITE_STEEL_ARMOR_HELMET.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSED_LITE_STEEL_ARMOR_CHESTPLATE.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSED_LITE_STEEL_ARMOR_LEGGINGS.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSED_LITE_STEEL_ARMOR_BOOTS.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_ARMOR_HELMET.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_ARMOR_CHESTPLATE.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_ARMOR_LEGGINGS.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.STEEL_ARMOR_BOOTS.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSEDSTEELARMOR_HELMET.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSEDSTEELARMOR_CHESTPLATE.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSEDSTEELARMOR_LEGGINGS.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.QIINFUSEDSTEELARMOR_BOOTS.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.LIGHTSTEELARMOR_HELMET.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.LIGHTSTEELARMOR_CHESTPLATE.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.LIGHTSTEELARMOR_LEGGINGS.get());
			tabData.accept(ZyrenmoddedcontentV2ModItems.LIGHTSTEELARMOR_BOOTS.get());
		}
	}
}