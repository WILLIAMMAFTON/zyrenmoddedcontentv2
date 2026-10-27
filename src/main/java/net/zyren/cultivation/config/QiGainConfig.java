package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Qi gain table (agreed by the user with ChatGPT, 2026-10-07).
 * File: <world>/serverconfig/zyrenmoddedcontent_v2-qigain-server.toml
 *
 * Rules:
 *  - Every value is a gain. "usable" goes to the usable Qi pool (qi_core_amount),
 *    "cultivated" to qi_cultivated.
 *  - Before the core forms (core level 0), a source's CULTIVATED amount is added to no_qi instead.
 *  - Usable Qi that doesn't fit in a full pool becomes cultivated Qi.
 *  - Crafting, smelting, cooking, brewing and enchanting pay once per action (per tick), not per item.
 */
public final class QiGainConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.BooleanValue ENABLED;
	public static final ModConfigSpec.BooleanValue OVERFLOW_TO_CULTIVATED;
	public static final ModConfigSpec.BooleanValue SHOW_MESSAGES;

	public static final Source COOKED_FOOD, ENCHANTED_GOLDEN_APPLE, GOLDEN_APPLE, GOLDEN_CARROT, GLISTERING_MELON,
			CRAFTED, KILL_UNARMED, KILL_WEAPON, FISHING, TAMING, SMELTING, ENCHANTING, BREWING, BOSS_KILL,
			ORE_DIAMOND, ORE_GOLD, ORE_IRON, ORE_REDSTONE, ORE_LAPIS;

	public static final ModConfigSpec.DoubleValue QI_PLAYER_KILL_NO_QI;
	public static final ModConfigSpec.DoubleValue QI_PLAYER_KILL_CULTIVATED;
	public static final ModConfigSpec.BooleanValue ORES_IGNORE_SILK_TOUCH;
	public static final ModConfigSpec.DoubleValue INFUSE_BASE_COST;
	public static final ModConfigSpec.DoubleValue INFUSED_CULTIVATED_SHARE;

	/** One gain source: usable + cultivated amounts. */
	public record Source(ModConfigSpec.DoubleValue usable, ModConfigSpec.DoubleValue cultivated) {
		public double usableQi() {
			return usable.get();
		}

		public double cultivatedQi() {
			return cultivated.get();
		}
	}

	private static Source source(ModConfigSpec.Builder b, String name, String comment, double usable, double cultivated) {
		b.comment(comment).push(name);
		ModConfigSpec.DoubleValue u = b.defineInRange("usable", usable, 0.0, Double.MAX_VALUE);
		ModConfigSpec.DoubleValue c = b.defineInRange("cultivated", cultivated, 0.0, Double.MAX_VALUE);
		b.pop();
		return new Source(u, c);
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("general");
		ENABLED = b.define("enabled", true);
		OVERFLOW_TO_CULTIVATED = b.comment("Usable Qi that doesn't fit in a full pool becomes cultivated Qi (decided).").define("overflow_to_cultivated", true);
		SHOW_MESSAGES = b.comment("Show '+X Qi' in the action bar when Qi is gained.").define("show_messages", true);
		b.pop();

		b.push("sources");
		COOKED_FOOD = source(b, "cooked_food", "Taking food you cooked out of a furnace/smoker (once per action).", 15, 10);
		ENCHANTED_GOLDEN_APPLE = source(b, "enchanted_golden_apple", "Eating one. Its 130 cultivated is exclusive to it.", 1590, 130);
		GOLDEN_APPLE = source(b, "golden_apple", "Eating one.", 900, 100);
		GOLDEN_CARROT = source(b, "golden_carrot", "Eating one.", 900, 100);
		GLISTERING_MELON = source(b, "glistering_melon", "Listed golden food. NOTE: not edible in vanilla, so this never triggers unless something makes it edible.", 900, 100);
		CRAFTED = source(b, "crafted", "Crafting (once per action).", 20, 15);
		KILL_UNARMED = source(b, "kill_unarmed", "Killing any mob or player with an empty hand.", 100, 59);
		KILL_WEAPON = source(b, "kill_weapon", "Killing any mob or player with a held item or projectile.", 50, 24);
		FISHING = source(b, "fishing", "Each catch.", 50, 50);
		TAMING = source(b, "taming", "Taming an animal, or feeding an animal food it eats (breeding/growing).", 30, 0);
		SMELTING = source(b, "smelting", "Taking non-food items out of a furnace/blast furnace/smoker (once per action).", 15, 0);
		ENCHANTING = source(b, "enchanting", "Enchanting at an enchanting table.", 35, 49);
		BREWING = source(b, "brewing", "Taking brewed potions out of a brewing stand (once per action).", 12, 15);
		BOSS_KILL = source(b, "boss_kill", "Killing the Wither, Ender Dragon, Warden or Elder Guardian (replaces the normal kill reward).", 10000, 10000);
		ORE_DIAMOND = source(b, "ore_diamond", "Mining a diamond ore (any variant).", 10, 140);
		ORE_GOLD = source(b, "ore_gold", "Mining a gold ore (any variant).", 3, 140);
		ORE_IRON = source(b, "ore_iron", "Mining an iron ore (any variant).", 4, 140);
		ORE_REDSTONE = source(b, "ore_redstone", "Mining a redstone ore (any variant).", 14, 140);
		ORE_LAPIS = source(b, "ore_lapis", "Mining a lapis ore (any variant).", 100, 140);
		ORES_IGNORE_SILK_TOUCH = b.comment("Silk-touched ores give nothing (stops place-and-mine farming).").define("ores_ignore_silk_touch", true);
		b.pop();

		b.comment("Killing a Qi-infused player (formed core) WITHOUT using Qi (no Qi ability or empowered hit on them in the last few seconds).",
				"Before your core forms: no_qi_gain. After: usable = the victim's max Qi, plus cultivated (excess usable becomes cultivated).").push("qi_player_kill");
		QI_PLAYER_KILL_NO_QI = b.defineInRange("no_qi_gain", 100.0, 0.0, Double.MAX_VALUE);
		QI_PLAYER_KILL_CULTIVATED = b.defineInRange("cultivated", 10000.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.comment("Qi-infused items (custom data 'contained_qi' on the item). /qi infuse <amount> stores Qi in the held food.",
				"Eating before the core forms: fills no_qi up to the amount needed; the rest goes to the new core's usable pool.",
				"Eating after: a share goes to cultivated Qi, the rest to usable.").push("infused_items");
		INFUSE_BASE_COST = b.comment("Infusing costs this + the amount stored (usable Qi).").defineInRange("infuse_base_cost", 10.0, 0.0, Double.MAX_VALUE);
		INFUSED_CULTIVATED_SHARE = b.comment("Share of contained Qi that becomes cultivated Qi when eaten after the core forms (decided: 50%).").defineInRange("cultivated_share", 0.5, 0.0, 1.0);
		b.pop();

		SPEC = b.build();
	}

	private QiGainConfig() {
	}
}
