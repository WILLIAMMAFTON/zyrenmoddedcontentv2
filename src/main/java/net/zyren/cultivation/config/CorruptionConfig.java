package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Corruption, Poisoned cores and Corrupted-core combat.
 * File: <world>/serverconfig/zyrenmoddedcontent_v2-corruption-server.toml
 * Sources: claude/Poisoned_Corrupted_Core_Source_of_Truth.md + claude/corruption-decisions.md.
 * Values marked PLACEHOLDER were not decided by the user.
 */
public final class CorruptionConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.BooleanValue ENABLED;
	public static final ModConfigSpec.DoubleValue MAX, NO_RETURN_FRACTION, PAST_POINT_RATE;
	public static final ModConfigSpec.DoubleValue RATE_END, RATE_END_CITY, RATE_SCULK, RATE_ANCIENT_CITY;
	public static final ModConfigSpec.IntValue END_DELAY_TICKS, SCULK_DELAY_TICKS, SCULK_RADIUS, SCULK_MIN_BLOCKS;
	public static final ModConfigSpec.DoubleValue CLEANSE_QI_FRACTION, CLEANSE_PER_QI;
	public static final ModConfigSpec.IntValue MEDITATION_TICKS, MEDITATION_AFTER_TICKS;
	public static final ModConfigSpec.DoubleValue MEDITATION_MULTIPLIER, MEDITATION_MOVE_TOLERANCE;

	public static final ModConfigSpec.DoubleValue CRYSTAL_REDUCTION, CRYSTAL_CORRUPTION_PER_DURABILITY, CRYSTAL_QI_PER_DURABILITY;

	public static final ModConfigSpec.ConfigValue<String> RESISTANCE_EFFECT, RESISTANCE_POTION;
	public static final ModConfigSpec.DoubleValue RESISTANCE_REDUCTION, RESISTANCE_COST_MULTIPLIER;
	public static final ModConfigSpec.IntValue RESISTANCE_TICKS;

	public static final ModConfigSpec.DoubleValue WARDEN_HIT, CHORUS_FRUIT;

	public static final ModConfigSpec.ConfigValue<String> POISONED_POTION;
	public static final ModConfigSpec.IntValue POTION_LOW_LEVEL_MAX, POTION_LEVEL_LOSS;
	public static final ModConfigSpec.DoubleValue POTION_QI_LOSS;

	public static final ModConfigSpec.DoubleValue POISONED_LEAK_PER_MIN;
	public static final ModConfigSpec.IntValue DETECT_LEVEL, DETECT_RANGE, DETECT_COOLDOWN_TICKS, HUD_LEVEL, HUD_RANGE;
	public static final ModConfigSpec.IntValue PROXIMITY_RANGE, PROXIMITY_TICKS, PROXIMITY_EFFECT_TICKS;
	public static final ModConfigSpec.DoubleValue PURE_VS_POISONED_CHANCE, PURE_VS_POISONED_CURE_SHARE;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("corruption");
		ENABLED = b.define("enabled", true);
		MAX = b.comment("Corruption points to become CORRUPTED. Decided: 1000.").defineInRange("max", 1000.0, 1.0, Double.MAX_VALUE);
		NO_RETURN_FRACTION = b.comment("Point of no return as a share of max. Spec: 50% (= 500 by default).").defineInRange("point_of_no_return", 0.5, 0.0, 1.0);
		PAST_POINT_RATE = b.comment("Past the point of no return, corruption rises this much per minute on its own. Decided: 50.").defineInRange("past_point_per_minute", 50.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.comment("Area corruption per minute. Only the HIGHEST applicable rate counts (decided).").push("areas");
		RATE_END = b.comment("The End, after end_delay_ticks continuously in the End. Decided: 10.").defineInRange("end_per_minute", 10.0, 0.0, Double.MAX_VALUE);
		RATE_END_CITY = b.comment("Inside an End City (immediate). Decided: 15.").defineInRange("end_city_per_minute", 15.0, 0.0, Double.MAX_VALUE);
		RATE_SCULK = b.comment("Near sculk, after sculk_delay_ticks continuously near it. Decided: 20.").defineInRange("sculk_per_minute", 20.0, 0.0, Double.MAX_VALUE);
		RATE_ANCIENT_CITY = b.comment("Inside an Ancient City (immediate). Decided: 35.").defineInRange("ancient_city_per_minute", 35.0, 0.0, Double.MAX_VALUE);
		END_DELAY_TICKS = b.comment("Spec: 3 in-game days.").defineInRange("end_delay_ticks", 72000, 0, Integer.MAX_VALUE);
		SCULK_DELAY_TICKS = b.comment("Spec: 100,000 ticks.").defineInRange("sculk_delay_ticks", 100000, 0, Integer.MAX_VALUE);
		SCULK_RADIUS = b.comment("'Near sculk' = this many sculk blocks within this radius. PLACEHOLDER.").defineInRange("sculk_radius", 6, 1, 16);
		SCULK_MIN_BLOCKS = b.comment("PLACEHOLDER.").defineInRange("sculk_min_blocks", 8, 1, 4096);
		b.pop();

		b.push("cleansing");
		CLEANSE_QI_FRACTION = b.comment("Share of current usable Qi channelled. Spec: 50%. Only at or below the point of no return.").defineInRange("qi_fraction", 0.5, 0.0, 1.0);
		CLEANSE_PER_QI = b.comment("Corruption removed per Qi channelled. Decided: 1.").defineInRange("corruption_per_qi", 1.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.push("meditation");
		MEDITATION_TICKS = b.comment("Spec: 3 minutes.").defineInRange("duration_ticks", 3600, 1, Integer.MAX_VALUE);
		MEDITATION_MULTIPLIER = b.comment("Corruption rate multiplier while meditating and afterwards. Spec: 3x.").defineInRange("corruption_multiplier", 3.0, 0.0, 100.0);
		MEDITATION_AFTER_TICKS = b.comment("The multiplier lasts this long after meditation ends. Spec: 3 minutes.").defineInRange("after_ticks", 3600, 0, Integer.MAX_VALUE);
		MEDITATION_MOVE_TOLERANCE = b.comment("Moving further than this (blocks) from where you started stops meditation.").defineInRange("move_tolerance", 0.3, 0.01, 10.0);
		b.pop();

		b.comment("Item id and loot chance are in the COMMON config zyrenmoddedcontent_v2-loot-common.toml.").push("purifying_crystal");
		CRYSTAL_REDUCTION = b.comment("Share of corruption blocked while it has durability. Decided: 75%.").defineInRange("reduction", 0.75, 0.0, 1.0);
		CRYSTAL_CORRUPTION_PER_DURABILITY = b.comment("Corruption blocked per durability point lost. Decided: 10.").defineInRange("corruption_per_durability", 10.0, 0.01, Double.MAX_VALUE);
		CRYSTAL_QI_PER_DURABILITY = b.comment("Recharge cost. Decided: 2 Qi per durability point.").defineInRange("qi_per_durability", 2.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.push("core_resistance");
		RESISTANCE_EFFECT = b.define("effect", "zyrenmoddedcontent_v2:curruptionreistance");
		RESISTANCE_POTION = b.define("potion", "zyrenmoddedcontent_v2:potionofcurruptionresistance");
		RESISTANCE_TICKS = b.comment("Drinking the potion grants the effect for this long. Decided: 3600.").defineInRange("duration_ticks", 3600, 1, Integer.MAX_VALUE);
		RESISTANCE_REDUCTION = b.comment("Share of corruption blocked. Decided: 80%.").defineInRange("reduction", 0.8, 0.0, 1.0);
		RESISTANCE_COST_MULTIPLIER = b.comment("Ability Qi costs while active. Spec: x2.").defineInRange("ability_cost_multiplier", 2.0, 1.0, 100.0);
		b.pop();

		b.push("sources");
		WARDEN_HIT = b.comment("Per Warden hit. Default chosen (user: no preference).").defineInRange("warden_hit", 50.0, 0.0, Double.MAX_VALUE);
		CHORUS_FRUIT = b.comment("Eating chorus fruit (\"Corri's fruit\"). Default chosen.").defineInRange("chorus_fruit", 100.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.comment("Poisoned Qi potion: no core -> Poisoned core; Pure core: level 1 -> Poisoned, levels 2..low_level_max -> lose levels,",
				"higher -> lose qi_loss usable Qi (or 1 level if you have less).").push("poisoned_potion");
		POISONED_POTION = b.define("potion", "zyrenmoddedcontent_v2:potion_of_weaknessfake");
		POTION_LOW_LEVEL_MAX = b.defineInRange("low_level_max", 5, 1, 1000);
		POTION_LEVEL_LOSS = b.comment("Levels lost at levels 2..low_level_max. Assumed 1 (confirm).").defineInRange("level_loss", 1, 1, 1000);
		POTION_QI_LOSS = b.defineInRange("qi_loss", 500.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.push("poisoned_core");
		POISONED_LEAK_PER_MIN = b.comment("Spec: 4 Qi per minute.").defineInRange("qi_leak_per_minute", 4.0, 0.0, Double.MAX_VALUE);
		DETECT_LEVEL = b.comment("Spec: core level 5+ get a warning.").defineInRange("warning_core_level", 5, 1, 1000);
		DETECT_RANGE = b.comment("PLACEHOLDER.").defineInRange("warning_range", 32, 1, 512);
		DETECT_COOLDOWN_TICKS = b.comment("Between warnings. PLACEHOLDER.").defineInRange("warning_cooldown_ticks", 1200, 20, Integer.MAX_VALUE);
		HUD_LEVEL = b.comment("Spec: core level 20 sees Poisoned players on the HUD.").defineInRange("hud_core_level", 20, 1, 1000);
		HUD_RANGE = b.comment("PLACEHOLDER.").defineInRange("hud_range", 64, 1, 512);
		PROXIMITY_RANGE = b.comment("'Near' for Nausea + Poison on Pure-core players. PLACEHOLDER.").defineInRange("proximity_range", 8, 1, 128);
		PROXIMITY_TICKS = b.comment("Spec: 1,500 ticks continuously near.").defineInRange("proximity_ticks", 1500, 1, Integer.MAX_VALUE);
		PROXIMITY_EFFECT_TICKS = b.comment("Nausea/Poison length, refreshed while still near. PLACEHOLDER.").defineInRange("proximity_effect_ticks", 100, 1, Integer.MAX_VALUE);
		PURE_VS_POISONED_CHANCE = b.comment("A Pure-aspect Qi hit on a Poisoned core: chance something happens. Spec: 70% (the other 30% is unspecified = nothing).").defineInRange("pure_hit_chance", 0.7, 0.0, 1.0);
		PURE_VS_POISONED_CURE_SHARE = b.comment("Within that chance: share that cures (rest turns it Corrupted). PLACEHOLDER 50/50.").defineInRange("cure_share", 0.5, 0.0, 1.0);
		b.pop();

		SPEC = b.build();
	}

	private CorruptionConfig() {
	}
}
