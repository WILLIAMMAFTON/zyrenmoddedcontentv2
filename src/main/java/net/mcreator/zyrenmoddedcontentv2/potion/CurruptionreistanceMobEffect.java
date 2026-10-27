package net.mcreator.zyrenmoddedcontentv2.potion;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public class CurruptionreistanceMobEffect extends InstantenousMobEffect {
	public CurruptionreistanceMobEffect() {
		super(MobEffectCategory.BENEFICIAL, -13434829);
		this.withSoundOnAdded(BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("block.amethyst_block.resonate")));
	}
}