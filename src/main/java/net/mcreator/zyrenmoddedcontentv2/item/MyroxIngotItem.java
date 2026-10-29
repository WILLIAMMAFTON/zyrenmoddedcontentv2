package net.mcreator.zyrenmoddedcontentv2.item;

import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item;

public class MyroxIngotItem extends Item {
	public MyroxIngotItem() {
		super(new Item.Properties().stacksTo(32).rarity(Rarity.RARE));
	}
}