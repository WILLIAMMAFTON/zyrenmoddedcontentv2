/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zyrenmoddedcontentv2.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

import net.minecraft.world.item.Item;

import net.mcreator.zyrenmoddedcontentv2.item.SubaspectbookItem;
import net.mcreator.zyrenmoddedcontentv2.item.PurifyingCrystalItem;
import net.mcreator.zyrenmoddedcontentv2.item.AspecttomeItem;
import net.mcreator.zyrenmoddedcontentv2.ZyrenmoddedcontentV2Mod;

public class ZyrenmoddedcontentV2ModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(ZyrenmoddedcontentV2Mod.MODID);
	public static final DeferredItem<Item> PURIFYING_CRYSTAL;
	public static final DeferredItem<Item> ASPECT_TOME;
	public static final DeferredItem<Item> SUB_ASPECT_BOOK;
	static {
		PURIFYING_CRYSTAL = REGISTRY.register("purifying_crystal", PurifyingCrystalItem::new);
		ASPECT_TOME = REGISTRY.register("aspect_tome", AspecttomeItem::new);
		SUB_ASPECT_BOOK = REGISTRY.register("sub_aspect_book", SubaspectbookItem::new);
	}
	// Start of user code block custom items
	// End of user code block custom items
}