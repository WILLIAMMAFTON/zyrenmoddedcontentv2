package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every balancing value for the cultivation system lives here, and nowhere else.
 *
 * Values marked PLACEHOLDER are NOT finalized in the design spec. Their defaults are neutral
 * (0) so they don't introduce mechanics nobody designed; change them in the config file
 * when the numbers are decided.
 */
public final class CultivationConfig {
	public static final ModConfigSpec SPEC;

	// ---- Stage 1: initial cultivation / first breakthrough ----
	public static final ModConfigSpec.DoubleValue FIRST_BREAKTHROUGH_NO_QI_REQUIRED;
	public static final ModConfigSpec.DoubleValue FIRST_BREAKTHROUGH_CORE_MAX;
	public static final ModConfigSpec.DoubleValue FIRST_BREAKTHROUGH_QI_REWARD;

	// ---- Core progression beyond level 1: one config line per level (server-configurable) ----
	public static final ModConfigSpec.BooleanValue CORE_PROGRESSION_ENABLED;
	public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> CORE_LEVELS;
	public static final ModConfigSpec.DoubleValue EXTRA_ASPECT_MULTIPLIER;

	// ---- Advancements ----
	public static final ModConfigSpec.IntValue ADV_PURE_SUFFERING_LEVEL;
	public static final ModConfigSpec.IntValue ADV_FAILURE_MINUTES;
	public static final ModConfigSpec.IntValue ADV_WHAT_A_WASTE_MINUTES;
	public static final ModConfigSpec.IntValue ADV_NO_QI_NO_PROBLEM_MINUTES;
	public static final ModConfigSpec.DoubleValue ADV_WHERE_DID_IT_GO_QI;
	public static final ModConfigSpec.BooleanValue CRATER_ENABLED;
	public static final ModConfigSpec.IntValue DEATH_CHOICE_TIMEOUT_TICKS;
	public static final ModConfigSpec.IntValue CRATER_RADIUS_PER_LEVEL, CRATER_MAX_RADIUS, CRATER_BLOCKS_PER_TICK;
	public static final ModConfigSpec.DoubleValue CRATER_CENTER_DAMAGE;
	public static final ModConfigSpec.IntValue EMERGENCY_WINDOW_TICKS, EMERGENCY_MIN_CORE_LEVEL, ANNIHILATION_GRACE_TICKS_PER_LEVEL, ANNIHILATION_GRACE_MAX_TICKS;

	// ---- Aspect mastery XP (practice-based aspect levels; slot aspects only, not Pure) ----
	public static final ModConfigSpec.BooleanValue MASTERY_ENABLED;
	public static final ModConfigSpec.DoubleValue MASTERY_XP_BASE;
	public static final ModConfigSpec.DoubleValue MASTERY_XP_GROWTH;
	public static final ModConfigSpec.IntValue MASTERY_MAX_LEVEL;
	public static final ModConfigSpec.DoubleValue MASTERY_XP_PER_QI;
	public static final ModConfigSpec.DoubleValue MASTERY_XP_PER_HIT;
	public static final ModConfigSpec.DoubleValue MASTERY_XP_PER_KILL;
	public static final ModConfigSpec.IntValue MASTERY_KILL_WINDOW_TICKS;
	public static final ModConfigSpec.DoubleValue MASTERY_XP_PER_SECOND_FLAVOUR;
	public static final ModConfigSpec.DoubleValue MASTERY_XP_PER_ACTION_FLAVOUR;

	// ---- Stage 2: aspects ----
	public static final ModConfigSpec.IntValue ASPECT_CHOICE_MIN_CORE_LEVEL;
	public static final ModConfigSpec.IntValue NEW_ASPECT_LEVEL;
	public static final ModConfigSpec.IntValue PURE_LEVEL_ON_CORE_FORMATION;
	public static final ModConfigSpec.IntValue ASPECT_SWITCH_TICKS;

	// ---- Later stages: values given by the spec, not used by any system yet ----
	public static final ModConfigSpec.DoubleValue PURE_VS_CORRUPTED_DAMAGE_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue CORRUPTED_DAMAGE_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue CORRUPTED_QI_COST_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue PURE_CORRUPTION_CURE_CHANCE;
	public static final ModConfigSpec.DoubleValue CORRUPTED_DEFENSE_THRESHOLD;
	public static final ModConfigSpec.IntValue CORE_HURT_LEVEL_PENALTY;

	/** "level:cultivated:maxQi" with level >= 1 and non-negative numbers. */
	static boolean validLevelLine(Object o) {
		if (!(o instanceof String s))
			return false;
		String[] parts = s.trim().split(":");
		if (parts.length != 3)
			return false;
		try {
			return Integer.parseInt(parts[0].trim()) >= 1 && Double.parseDouble(parts[1].trim()) >= 0 && Double.parseDouble(parts[2].trim()) >= 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.comment("Initial cultivation: filling no_qi to form the Qi Core (qi_core_level 0 -> 1)").push("first_breakthrough");
		FIRST_BREAKTHROUGH_NO_QI_REQUIRED = b
				.comment("no_qi needed for the first breakthrough. Spec value: 100.")
				.defineInRange("no_qi_required", 100.0, 1.0, Double.MAX_VALUE);
		FIRST_BREAKTHROUGH_CORE_MAX = b
				.comment("PLACEHOLDER (not finalized): qi_core_max set at the first breakthrough.")
				.defineInRange("initial_core_max", 0.0, 0.0, Double.MAX_VALUE);
		FIRST_BREAKTHROUGH_QI_REWARD = b
				.comment("PLACEHOLDER (not finalized): Qi added to qi_core_amount at the first breakthrough (capped by qi_core_max).")
				.defineInRange("initial_qi_reward", 0.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.comment("Core level 2+ breakthroughs, configured per level by the server.",
				"Each entry is \"level:cultivated_qi_to_level_up:max_qi\" describing life AT that level, e.g. [\"1:100:200\", \"2:200:500\"]:",
				"at level 1 the Qi pool is 200 and 100 qi_cultivated takes you to level 2; at level 2 the pool is 500 and 200 takes you to 3.",
				"Level 0 -> 1 (forming the core) uses no_qi only (first_breakthrough.no_qi_required); the level 1 line's max_qi is the starting pool.",
				"Reaching a level sets qi_core_max to that level's max_qi. A level that isn't listed can't be reached",
				"(except with /qi breakthrough force). qi_cultivated is checked, not consumed.")
				.push("core_progression");
		CORE_PROGRESSION_ENABLED = b
				.comment("Automatic core level-ups from qi_cultivated.")
				.define("enabled", true);
		CORE_LEVELS = b
				.comment("User's default progression (2026-10-07).")
				.defineListAllowEmpty("levels", java.util.List.of(
						"1:100:200", "2:200:500", "3:350:1000", "4:500:1500", "5:600:1750",
						"6:750:2000", "7:950:2500", "8:1200:3000", "9:1500:3500", "10:1900:4250",
						"11:2400:5000", "12:3000:6000", "13:3750:7000", "14:4700:8250", "15:5900:9500",
						"16:7400:11000", "17:9250:12750", "18:11500:14500", "19:14500:16500", "20:18500:19000"),
						() -> "21:0:0", CultivationConfig::validLevelLine);
		EXTRA_ASPECT_MULTIPLIER = b
				.comment("PLACEHOLDER (not finalized): extra cost per owned slot aspect. 1 = no effect.")
				.defineInRange("extra_aspect_multiplier", 1.0, 1.0, Double.MAX_VALUE);
		b.pop();

		b.comment("Aspect mastery: slot aspects gain XP through practice and level up between breakthroughs.",
				"XP needed for level L -> L+1 = xp_base * xp_growth^(L-1). All amounts are PLACEHOLDERS to balance.").push("aspect_mastery");
		MASTERY_ENABLED = b.define("enabled", true);
		MASTERY_XP_BASE = b.comment("PLACEHOLDER: XP for level 1 -> 2.").defineInRange("xp_base", 100.0, 1.0, Double.MAX_VALUE);
		MASTERY_XP_GROWTH = b.comment("PLACEHOLDER: growth per level.").defineInRange("xp_growth", 1.5, 1.0, 100.0);
		MASTERY_MAX_LEVEL = b.comment("Highest level mastery can reach. 0 = no cap (breakthrough choices can still raise levels).").defineInRange("max_level", 0, 0, Integer.MAX_VALUE);
		MASTERY_XP_PER_QI = b.comment("Source 1: XP per Qi spent on that aspect's abilities and passive. PLACEHOLDER.").defineInRange("xp_per_qi_spent", 1.0, 0.0, Double.MAX_VALUE);
		MASTERY_XP_PER_HIT = b.comment("Source 2: XP when that aspect's ability or empowered hit damages a living target. PLACEHOLDER.").defineInRange("xp_per_ability_hit", 5.0, 0.0, Double.MAX_VALUE);
		MASTERY_XP_PER_KILL = b.comment("Source 3: XP for a kill made with that aspect's ability or empowered hit. PLACEHOLDER.").defineInRange("xp_per_kill", 25.0, 0.0, Double.MAX_VALUE);
		MASTERY_KILL_WINDOW_TICKS = b.comment("A death counts as an aspect kill if your ability hit them within this many ticks.").defineInRange("kill_window_ticks", 100, 1, Integer.MAX_VALUE);
		MASTERY_XP_PER_SECOND_FLAVOUR = b.comment("Source 4a: XP per second doing the aspect's thing while it is ACTIVE (Fire in fire/lava, Lightning in storms,",
				"Water underwater/in rain, Dark at night or in darkness, Light in daylight under open sky). PLACEHOLDER.").defineInRange("flavour_xp_per_second", 0.2, 0.0, Double.MAX_VALUE);
		MASTERY_XP_PER_ACTION_FLAVOUR = b.comment("Source 4b: XP per aspect action, for any OWNED aspect (Earth mining stone, Warrior sword hits, Physical unarmed hits,",
				"Lightning struck by lightning, Protective taking a hit while active). PLACEHOLDER.").defineInRange("flavour_xp_per_action", 1.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.comment("Advancement thresholds").push("advancements");
		ADV_PURE_SUFFERING_LEVEL = b.comment("'Pure suffering': killed by a Corrupted-core player at this core level or higher (decided: 10).").defineInRange("pure_suffering_min_core_level", 10, 1, 1000);
		ADV_FAILURE_MINUTES = b.comment("'A whole new level of failure': killed by a player within this many minutes after your core broke. Placeholder 10.").defineInRange("whole_new_failure_minutes", 10, 1, 10000);
		ADV_WHAT_A_WASTE_MINUTES = b.comment("'why would u do that?': kill a player whose core broke within this many minutes. User: 10.").defineInRange("what_a_waste_minutes", 10, 1, 10000);
		ADV_NO_QI_NO_PROBLEM_MINUTES = b.comment("'no Qi no problem': kill a player with a core within this many minutes after your core broke. User: 20.").defineInRange("no_qi_no_problem_minutes", 20, 1, 10000);
		ADV_WHERE_DID_IT_GO_QI = b.comment("'Were did it all go!?': a food item holding at least this much Qi after infusing. User: 14k.").defineInRange("where_did_it_go_qi", 14000.0, 1.0, Double.MAX_VALUE);
		b.pop();

		b.comment("Qi aspects").push("aspects");
		ASPECT_CHOICE_MIN_CORE_LEVEL = b
				.comment("Reaching this core level (and each level after) grants one aspect choice. Decided: 2 (forming the core gives none).")
				.defineInRange("choice_min_core_level", 2, 1, Integer.MAX_VALUE);
		NEW_ASPECT_LEVEL = b
				.comment("Level a newly gained slot aspect starts at (decided 2026-10-26: 0).")
				.defineInRange("new_aspect_level", 0, 0, Integer.MAX_VALUE);
		PURE_LEVEL_ON_CORE_FORMATION = b
				.comment("PLACEHOLDER: Pure level given when the core forms (Pure is always available).")
				.defineInRange("pure_level_on_core_formation", 1, 0, Integer.MAX_VALUE);
		ASPECT_SWITCH_TICKS = b
				.comment("PLACEHOLDER: how long switching the active aspect takes, in ticks (20 = 1 second). 0 = instant.")
				.defineInRange("switch_ticks", 100, 0, Integer.MAX_VALUE);
		b.pop();

		b.comment("Combat and core-damage values from the spec. Reserved for later stages; nothing reads these yet.").push("combat");
		PURE_VS_CORRUPTED_DAMAGE_MULTIPLIER = b
				.comment("Damage multiplier when a Pure-aspect attack hits a corrupted core. Spec value: 5.")
				.defineInRange("pure_vs_corrupted_damage_multiplier", 5.0, 0.0, Double.MAX_VALUE);
		CORRUPTED_DAMAGE_MULTIPLIER = b
				.comment("Damage multiplier for Qi used from a corrupted core. Spec value: 2.")
				.defineInRange("corrupted_damage_multiplier", 2.0, 0.0, Double.MAX_VALUE);
		CORRUPTED_QI_COST_MULTIPLIER = b
				.comment("Qi cost multiplier for Qi used from a corrupted core. Spec value: 3.")
				.defineInRange("corrupted_qi_cost_multiplier", 3.0, 0.0, Double.MAX_VALUE);
		PURE_CORRUPTION_CURE_CHANCE = b
				.comment("Chance (0-1) that a Pure-aspect hit cures corruption. Spec value: 0.5.")
				.defineInRange("pure_corruption_cure_chance", 0.5, 0.0, 1.0);
		CORRUPTED_DEFENSE_THRESHOLD = b
				.comment("Fraction (0-1) of incoming Qi a defender must use to stop a Qi attack. Spec value: 0.7.")
				.defineInRange("corrupted_defense_threshold", 0.7, 0.0, 1.0);
		CORE_HURT_LEVEL_PENALTY = b
				.comment("qi_core_level lost when a corrupted core is hurt but not broken. Spec value: 2.")
				.defineInRange("core_hurt_level_penalty", 2, 0, Integer.MAX_VALUE);
		b.pop();

		b.comment("Death penalty (claude/death-penalty.md).").push("death_penalty");
		DEATH_CHOICE_TIMEOUT_TICKS = b.comment("A tied death's 'Choose an Aspect to Lose' screen: if no choice is made this long after it",
				"opens, one tied option is picked at random and the player is kicked for idling (user decision 2026-10-08). PLACEHOLDER 60 s. 0 = never.")
				.defineInRange("choice_timeout_ticks", 1200, 0, Integer.MAX_VALUE);
		b.pop();

		b.comment("Emergency Core Breakdown / Core Annihilation (claude/core-destruction.md). The player's toggle is in the cultivation menu (default OFF).").push("emergency_core");
		EMERGENCY_WINDOW_TICKS = b.comment("A second breakdown within this long of the last one annihilates the Core. Spec: 30 minutes.")
				.defineInRange("repeat_window_ticks", 36000, 1, Integer.MAX_VALUE);
		EMERGENCY_MIN_CORE_LEVEL = b.comment("Lowest core level that can break down (level 1 can't be reduced without destroying the core).")
				.defineInRange("min_core_level", 2, 2, Integer.MAX_VALUE);
		ANNIHILATION_GRACE_TICKS_PER_LEVEL = b.comment("After Annihilation, aspects stay usable for this long per level of the player's HIGHEST aspect. Decided: 5 min.")
				.defineInRange("grace_ticks_per_level", 6000, 0, Integer.MAX_VALUE);
		ANNIHILATION_GRACE_MAX_TICKS = b.comment("Cap on that time. Spec: 20 minutes.")
				.defineInRange("grace_max_ticks", 24000, 0, Integer.MAX_VALUE);
		b.pop();

		b.comment("A Corrupted core breaking explodes into a full-sphere crater centred on the player.").push("corrupted_core_explosion");
		CRATER_ENABLED = b.define("enabled", true);
		CRATER_RADIUS_PER_LEVEL = b.comment("Radius added per core level. Decided: 30.").defineInRange("radius_per_level", 30, 0, 10000);
		CRATER_MAX_RADIUS = b.comment("Radius cap so big cores can't freeze the server; 0 = no cap. PLACEHOLDER (user: no preference).",
				"Unloaded chunks are skipped, so very large craters are cut off where the world isn't loaded.").defineInRange("max_radius", 128, 0, 100000);
		CRATER_CENTER_DAMAGE = b.comment("Explosion damage at the centre, falling to 0 at the edge. PLACEHOLDER.").defineInRange("center_damage", 100.0, 0.0, Double.MAX_VALUE);
		CRATER_BLOCKS_PER_TICK = b.comment("Positions processed per server tick while digging the crater (performance).").defineInRange("blocks_per_tick", 40000, 100, 10000000);
		b.pop();

		SPEC = b.build();
	}

	private CultivationConfig() {
	}
}
