package net.zyren.cultivation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;

import net.zyren.cultivation.ability.core.AspectKits;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.config.CultivationConfig;

/**
 * Entry point for the cultivation system.
 *
 * Call {@link #init(IEventBus)} once from the MCreator mod constructor, inside the
 * "Start of user code block mod init" section of ZyrenmoddedcontentV2Mod.java:
 *
 *     net.zyren.cultivation.CultivationSetup.init(modEventBus);
 *
 * Everything else (commands, etc.) registers itself through @EventBusSubscriber.
 *
 * This package lives outside net.mcreator.zyrenmoddedcontentv2 on purpose, so MCreator's
 * code regeneration never touches it.
 */
public final class CultivationSetup {
	private CultivationSetup() {
	}

	public static void init(IEventBus modEventBus) {
		// SERVER config: one file per world, at <world>/serverconfig/zyrenmoddedcontent_v2-cultivation-server.toml.
		// Synced to clients automatically.
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, CultivationConfig.SPEC, "zyrenmoddedcontent_v2-cultivation-server.toml");
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, AbilityConfig.SPEC, "zyrenmoddedcontent_v2-abilities-server.toml");
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, net.zyren.cultivation.config.QiGainConfig.SPEC, "zyrenmoddedcontent_v2-qigain-server.toml");
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, net.zyren.cultivation.config.CorruptionConfig.SPEC, "zyrenmoddedcontent_v2-corruption-server.toml");
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, net.zyren.cultivation.config.SubAspectConfig.SPEC, "zyrenmoddedcontent_v2-subaspects-server.toml");
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, net.zyren.cultivation.config.UnlockConfig.SPEC, "zyrenmoddedcontent_v2-progression-server.toml");
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, net.zyren.cultivation.config.FireConfig.SPEC, "zyrenmoddedcontent_v2-fire-server.toml");
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, net.zyren.cultivation.config.WaterConfig.SPEC, "zyrenmoddedcontent_v2-water-server.toml");
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.SERVER, net.zyren.cultivation.config.AirConfig.SPEC, "zyrenmoddedcontent_v2-air-server.toml");
		// COMMON config: loaded at game start (loot tables are built before server configs load).
		ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.COMMON, net.zyren.cultivation.config.LootConfig.SPEC, "zyrenmoddedcontent_v2-loot-common.toml");
		AspectKits.bootstrap();
	}
}
