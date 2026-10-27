package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * COMMON config (loaded at game start, not per world): <game>/config/zyrenmoddedcontent_v2-loot-common.toml.
 * Loot tables are built BEFORE a world's server config loads, so anything loot needs lives here.
 */
public final class LootConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.ConfigValue<String> CRYSTAL_ITEM;
	public static final ModConfigSpec.DoubleValue CRYSTAL_LOOT_CHANCE;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();
		b.push("purifying_crystal");
		CRYSTAL_ITEM = b.comment("The Purifying Crystal item.").define("item", "zyrenmoddedcontent_v2:purifying_crystal");
		CRYSTAL_LOOT_CHANCE = b.comment("Chance per End City / Ancient City chest. PLACEHOLDER.").defineInRange("loot_chance", 0.1, 0.0, 1.0);
		b.pop();
		SPEC = b.build();
	}

	private LootConfig() {
	}
}
