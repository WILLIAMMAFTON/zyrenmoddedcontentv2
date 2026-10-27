package net.mcreator.zyrenmoddedcontentv2.item;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

public class PurifyingCrystalItem extends Item {
	public PurifyingCrystalItem() {
		super(new Item.Properties().durability(100).fireResistant().rarity(Rarity.EPIC));
	}

	@Override
	public float getDestroySpeed(ItemStack itemstack, BlockState state) {
		return 1100f;
	}
}