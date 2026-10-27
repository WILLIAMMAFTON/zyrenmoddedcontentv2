/*
 *	MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.zyrenmoddedcontentv2.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.registries.Registries;

import net.mcreator.zyrenmoddedcontentv2.potion.SickeningwarmthMobEffect;
import net.mcreator.zyrenmoddedcontentv2.potion.CurruptionreistanceMobEffect;
import net.mcreator.zyrenmoddedcontentv2.ZyrenmoddedcontentV2Mod;

public class ZyrenmoddedcontentV2ModMobEffects {
	public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(Registries.MOB_EFFECT, ZyrenmoddedcontentV2Mod.MODID);
	public static final DeferredHolder<MobEffect, MobEffect> CURRUPTIONREISTANCE = REGISTRY.register("curruptionreistance", CurruptionreistanceMobEffect::new);
	public static final DeferredHolder<MobEffect, MobEffect> SICKENINGWARMTH = REGISTRY.register("sickeningwarmth", SickeningwarmthMobEffect::new);
}