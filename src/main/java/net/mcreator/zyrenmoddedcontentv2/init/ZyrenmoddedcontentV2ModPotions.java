/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zyrenmoddedcontentv2.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.registries.Registries;

import net.mcreator.zyrenmoddedcontentv2.ZyrenmoddedcontentV2Mod;

public class ZyrenmoddedcontentV2ModPotions {
	public static final DeferredRegister<Potion> REGISTRY = DeferredRegister.create(Registries.POTION, ZyrenmoddedcontentV2Mod.MODID);
	public static final DeferredHolder<Potion, Potion> POTIONOFCURRUPTIONRESISTANCE = REGISTRY.register("potionofcurruptionresistance",
			() -> new Potion(new MobEffectInstance(ZyrenmoddedcontentV2ModMobEffects.CURRUPTIONREISTANCE, 3600, 0, false, true)));
	public static final DeferredHolder<Potion, Potion> POTION_OF_WEAKNESSFAKE = REGISTRY.register("potion_of_weaknessfake", () -> new Potion(new MobEffectInstance(ZyrenmoddedcontentV2ModMobEffects.SICKENINGWARMTH, 3600, 0, false, false)));
}