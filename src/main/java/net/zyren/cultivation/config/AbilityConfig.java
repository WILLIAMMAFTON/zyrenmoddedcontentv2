package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Ability numbers, in their own server config file:
 *   <world>/serverconfig/zyrenmoddedcontent_v2-abilities-server.toml
 *
 * Conventions (so nothing is invented):
 *  - Values given by the source-of-truth document are the defaults.
 *  - UNSET = -1: a value the document leaves TBD or doesn't give, that the ability can't work
 *    without. The ability shows as "Not finalized" and can't be used until it is set.
 *  - Missing cooldowns default to 0 (no cooldown) and are listed in the checklist.
 */
public final class AbilityConfig {
	public static final double UNSET = -1;

	public static final ModConfigSpec SPEC;

	// ---- Framework ----
	public static final ModConfigSpec.IntValue EMPOWER_WINDOW_TICKS;
	public static final ModConfigSpec.DoubleValue LOOKED_AT_RANGE;
	public static final ModConfigSpec.DoubleValue REINFORCE_DAMAGE_REDUCTION;

	// ---- Warrior ----
	public static final ModConfigSpec.DoubleValue WARRIOR_PASSIVE_QI_PER_SECOND;
	public static final ModConfigSpec.DoubleValue WARRIOR_PASSIVE_QI_PER_SWORD_USE;
	public static final ModConfigSpec.IntValue WARRIOR_PASSIVE_SPEED_LEVEL;
	public static final ModConfigSpec.IntValue WARRIOR_PASSIVE_STRENGTH_LEVEL;

	public static final ModConfigSpec.DoubleValue WARRIOR_ATTACK1_COST;
	public static final ModConfigSpec.DoubleValue WARRIOR_ATTACK1_MULTIPLIER;
	public static final ModConfigSpec.IntValue WARRIOR_ATTACK1_COOLDOWN;

	public static final ModConfigSpec.DoubleValue WARRIOR_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue WARRIOR_HEAVY_MULTIPLIER;
	public static final ModConfigSpec.BooleanValue WARRIOR_HEAVY_FULL_HEAL;
	public static final ModConfigSpec.IntValue WARRIOR_HEAVY_COOLDOWN;

	public static final ModConfigSpec.DoubleValue WARRIOR_REINFORCE_COST;
	public static final ModConfigSpec.IntValue WARRIOR_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue WARRIOR_REINFORCE_COOLDOWN;

	public static final ModConfigSpec.DoubleValue WARRIOR_PB1_COST;
	public static final ModConfigSpec.DoubleValue WARRIOR_PB1_MULTIPLIER;
	public static final ModConfigSpec.IntValue WARRIOR_PB1_COOLDOWN;

	public static final ModConfigSpec.DoubleValue WARRIOR_PB2_COST;
	public static final ModConfigSpec.IntValue WARRIOR_PB2_KNOCKBACK_LEVEL;
	public static final ModConfigSpec.IntValue WARRIOR_PB2_COOLDOWN;

	public static final ModConfigSpec.DoubleValue WARRIOR_ABILITY1_COST;
	public static final ModConfigSpec.IntValue WARRIOR_ABILITY1_DURATION;
	public static final ModConfigSpec.DoubleValue WARRIOR_ABILITY1_QI_PER_SECOND;
	public static final ModConfigSpec.DoubleValue WARRIOR_ABILITY1_ATTACK_SPEED_BONUS;
	public static final ModConfigSpec.IntValue WARRIOR_ABILITY1_COOLDOWN;

	public static final ModConfigSpec.DoubleValue WARRIOR_ABILITY2_COST;
	public static final ModConfigSpec.DoubleValue WARRIOR_ABILITY2_DISTANCE;
	public static final ModConfigSpec.IntValue WARRIOR_ABILITY2_COOLDOWN;

	// ---- Physical ----
	public static final ModConfigSpec.DoubleValue PHYSICAL_PASSIVE_QI_PER_MINUTE;
	public static final ModConfigSpec.IntValue PHYSICAL_PASSIVE_SPEED_LEVEL;
	public static final ModConfigSpec.IntValue PHYSICAL_PASSIVE_STRENGTH_LEVEL;
	public static final ModConfigSpec.DoubleValue PHYSICAL_PASSIVE_UNARMED_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue PHYSICAL_ENV_MULT_1;
	public static final ModConfigSpec.DoubleValue PHYSICAL_ENV_MULT_2;
	public static final ModConfigSpec.DoubleValue PHYSICAL_ENV_MULT_3;
	public static final ModConfigSpec.IntValue PHYSICAL_LIGHTNING_WINDOW_TICKS;

	public static final ModConfigSpec.DoubleValue PHYSICAL_ATTACK1_COST;
	public static final ModConfigSpec.IntValue PHYSICAL_ATTACK1_DURATION;
	public static final ModConfigSpec.DoubleValue PHYSICAL_ATTACK1_QI_PER_SECOND;
	public static final ModConfigSpec.DoubleValue PHYSICAL_ATTACK1_ATTACK_SPEED;
	public static final ModConfigSpec.IntValue PHYSICAL_ATTACK1_COOLDOWN;

	public static final ModConfigSpec.DoubleValue PHYSICAL_HEAVY_COST;
	public static final ModConfigSpec.IntValue PHYSICAL_HEAVY_DURATION;
	public static final ModConfigSpec.DoubleValue PHYSICAL_HEAVY_EXPLOSION_POWER;
	public static final ModConfigSpec.BooleanValue PHYSICAL_HEAVY_BREAKS_BLOCKS;
	public static final ModConfigSpec.DoubleValue PHYSICAL_HEAVY_BLOCKS_PER_CRATER_RADIUS;
	public static final ModConfigSpec.IntValue PHYSICAL_HEAVY_MAX_CRATER_RADIUS;
	public static final ModConfigSpec.IntValue PHYSICAL_HEAVY_COOLDOWN;

	public static final ModConfigSpec.DoubleValue PHYSICAL_REINFORCE_COST;
	public static final ModConfigSpec.IntValue PHYSICAL_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue PHYSICAL_REINFORCE_REGEN_LEVEL;
	public static final ModConfigSpec.IntValue PHYSICAL_REINFORCE_RESISTANCE_LEVEL;
	public static final ModConfigSpec.IntValue PHYSICAL_REINFORCE_COOLDOWN;

	public static final ModConfigSpec.DoubleValue PHYSICAL_PB1_COST;
	public static final ModConfigSpec.DoubleValue PHYSICAL_PB1_RANGE, PHYSICAL_PB1_DAMAGE;
	public static final ModConfigSpec.DoubleValue PHYSICAL_PB1_SPEED;
	public static final ModConfigSpec.IntValue PHYSICAL_PB1_MAX_FLIGHT_TICKS;
	public static final ModConfigSpec.DoubleValue PHYSICAL_PB1_CONTACT_RANGE;
	public static final ModConfigSpec.IntValue PHYSICAL_PB1_COOLDOWN;

	public static final ModConfigSpec.DoubleValue PHYSICAL_PB2_COST;
	public static final ModConfigSpec.DoubleValue PHYSICAL_PB2_ASCEND_SPEED;
	public static final ModConfigSpec.DoubleValue PHYSICAL_PB2_DAMAGE_PER_BLOCK;
	public static final ModConfigSpec.DoubleValue PHYSICAL_PB2_HIT_RADIUS;
	public static final ModConfigSpec.IntValue PHYSICAL_PB2_COOLDOWN;
	public static final ModConfigSpec.IntValue PHYSICAL_PB2_PASSIVE_NULLIFY_TICKS;

	public static final ModConfigSpec.DoubleValue PHYSICAL_ABILITY1_QI_FRACTION;
	public static final ModConfigSpec.IntValue PHYSICAL_ABILITY1_COOLDOWN;

	public static final ModConfigSpec.DoubleValue PHYSICAL_ABILITY2_COST;
	public static final ModConfigSpec.IntValue PHYSICAL_ABILITY2_KNOCKBACK_LEVEL;
	public static final ModConfigSpec.IntValue PHYSICAL_ABILITY2_STRENGTH_LEVEL;
	public static final ModConfigSpec.IntValue PHYSICAL_ABILITY2_STRENGTH_DURATION;
	public static final ModConfigSpec.IntValue PHYSICAL_ABILITY2_KNOCKBACK_DURATION;
	public static final ModConfigSpec.IntValue PHYSICAL_ABILITY2_COOLDOWN;

	// ---- Dark ----
	public static final ModConfigSpec.DoubleValue DARK_PASSIVE_QI_PER_MINUTE;
	public static final ModConfigSpec.IntValue DARK_NIGHT_START;
	public static final ModConfigSpec.IntValue DARK_NIGHT_END;
	public static final ModConfigSpec.DoubleValue DARK_NIGHT_OUTPUT_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue DARK_NIGHT_INCOMING_DIVISOR;
	public static final ModConfigSpec.IntValue DARK_EFFECT_TICKS;
	public static final ModConfigSpec.IntValue DARK_DARKNESS_LIGHT;
	public static final ModConfigSpec.DoubleValue DARK_ATTACK1_COST;
	public static final ModConfigSpec.DoubleValue DARK_ATTACK1_BLADE_MULTIPLIER;
	public static final ModConfigSpec.IntValue DARK_ATTACK1_KNOCKBACK;
	public static final ModConfigSpec.IntValue DARK_ATTACK1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue DARK_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue DARK_HEAVY_DARK_MULTIPLIER;
	public static final ModConfigSpec.IntValue DARK_HEAVY_KNOCKBACK;
	public static final ModConfigSpec.IntValue DARK_HEAVY_COOLDOWN;
	public static final ModConfigSpec.DoubleValue DARK_REINFORCE_COST;
	public static final ModConfigSpec.IntValue DARK_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue DARK_REINFORCE_THORNS;
	public static final ModConfigSpec.IntValue DARK_REINFORCE_COOLDOWN;
	public static final ModConfigSpec.DoubleValue DARK_PB1_COST;
	public static final ModConfigSpec.IntValue DARK_PB1_COOLDOWN;
	public static final ModConfigSpec.IntValue DARK_PB1_DURATION;
	public static final ModConfigSpec.DoubleValue DARK_PB1_MAX_HEALTH;
	public static final ModConfigSpec.IntValue DARK_PB1_STRENGTH;
	public static final ModConfigSpec.IntValue DARK_PB1_THORNS;
	public static final ModConfigSpec.DoubleValue DARK_PB2_COST;
	public static final ModConfigSpec.IntValue DARK_PB2_COOLDOWN;
	public static final ModConfigSpec.DoubleValue DARK_PB2_HEIGHT;
	public static final ModConfigSpec.DoubleValue DARK_ABILITY1_COST;
	public static final ModConfigSpec.DoubleValue DARK_ABILITY1_HEALTH_THRESHOLD;
	public static final ModConfigSpec.DoubleValue DARK_ABILITY1_HEAL;
	public static final ModConfigSpec.IntValue DARK_ABILITY1_ESCAPE_RANGE;
	public static final ModConfigSpec.IntValue DARK_ABILITY1_PLAYER_RANGE;
	public static final ModConfigSpec.DoubleValue DARK_ULT_COST;
	public static final ModConfigSpec.IntValue DARK_ULT_COOLDOWN;
	public static final ModConfigSpec.DoubleValue DARK_ULT_RADIUS;
	public static final ModConfigSpec.IntValue DARK_ULT_UNLUCK;
	public static final ModConfigSpec.IntValue DARK_ULT_LIGHT_WEAKNESS;
	public static final ModConfigSpec.DoubleValue DARK_ULT_LIGHT_HEALTH_REDUCTION;
	public static final ModConfigSpec.IntValue DARK_ULT_LIGHT_DURATION;

	// ---- Earth ----
	public static final ModConfigSpec.DoubleValue EARTH_PASSIVE_QI_PER_15S;
	public static final ModConfigSpec.IntValue EARTH_PASSIVE_HASTE;
	public static final ModConfigSpec.IntValue EARTH_PASSIVE_FORTUNE;
	public static final ModConfigSpec.IntValue EARTH_EFFECT_TICKS;
	public static final ModConfigSpec.DoubleValue EARTH_ATTACK1_COST;
	public static final ModConfigSpec.DoubleValue EARTH_ATTACK1_MULTIPLIER;
	public static final ModConfigSpec.IntValue EARTH_ATTACK1_KNOCKBACK;
	public static final ModConfigSpec.IntValue EARTH_ATTACK1_SLOWNESS;
	public static final ModConfigSpec.IntValue EARTH_ATTACK1_SLOWNESS_TICKS;
	public static final ModConfigSpec.IntValue EARTH_ATTACK1_STUN_TICKS;
	public static final ModConfigSpec.IntValue EARTH_ATTACK1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue EARTH_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue EARTH_HEAVY_RADIUS;
	public static final ModConfigSpec.DoubleValue EARTH_HEAVY_HEIGHT;
	public static final ModConfigSpec.DoubleValue EARTH_HEAVY_DAMAGE;
	public static final ModConfigSpec.IntValue EARTH_HEAVY_COOLDOWN;
	public static final ModConfigSpec.DoubleValue EARTH_REINFORCE_COST;
	public static final ModConfigSpec.IntValue EARTH_REINFORCE_DURATION;
	public static final ModConfigSpec.DoubleValue EARTH_REINFORCE_KB_RESIST;
	public static final ModConfigSpec.IntValue EARTH_REINFORCE_LOCK_TICKS;
	public static final ModConfigSpec.IntValue EARTH_REINFORCE_STONE_RESISTANCE;
	public static final ModConfigSpec.IntValue EARTH_REINFORCE_COOLDOWN;
	public static final ModConfigSpec.DoubleValue EARTH_PB1_COST;
	public static final ModConfigSpec.IntValue EARTH_PB1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue EARTH_PB1_DAMAGE;
	public static final ModConfigSpec.IntValue EARTH_PB1_KNOCKBACK;
	public static final ModConfigSpec.IntValue EARTH_PB1_STUN_TICKS;
	public static final ModConfigSpec.DoubleValue EARTH_PB2_COST;
	public static final ModConfigSpec.IntValue EARTH_PB2_COOLDOWN;
	public static final ModConfigSpec.DoubleValue EARTH_PB2_RADIUS;
	public static final ModConfigSpec.IntValue EARTH_PB2_SLOWNESS;
	public static final ModConfigSpec.DoubleValue EARTH_PB2_LAUNCH_HEIGHT;
	public static final ModConfigSpec.DoubleValue EARTH_ABILITY1_COST;
	public static final ModConfigSpec.IntValue EARTH_ABILITY1_COOLDOWN;
	public static final ModConfigSpec.IntValue EARTH_ABILITY1_RADIUS;
	public static final ModConfigSpec.IntValue EARTH_ABILITY1_RISE;
	public static final ModConfigSpec.IntValue EARTH_ABILITY1_RESISTANCE;
	public static final ModConfigSpec.DoubleValue EARTH_ABILITY1_ALLY_RANGE;
	public static final ModConfigSpec.IntValue EARTH_ABILITY1_ALLY_REGEN;
	public static final ModConfigSpec.DoubleValue EARTH_ULT_COST;
	public static final ModConfigSpec.IntValue EARTH_ULT_COOLDOWN;
	public static final ModConfigSpec.DoubleValue EARTH_ULT_RADIUS;
	public static final ModConfigSpec.IntValue EARTH_ULT_RAISE_MIN;
	public static final ModConfigSpec.IntValue EARTH_ULT_RAISE_MAX;
	public static final ModConfigSpec.IntValue EARTH_ULT_REGEN_TICKS;
	public static final ModConfigSpec.IntValue EARTH_ULT_RESISTANCE;
	public static final ModConfigSpec.IntValue EARTH_ULT_RESISTANCE_TICKS;

	// ---- Lightning (ELECTRIC) ----
	public static final ModConfigSpec.DoubleValue ELEC_PASSIVE_QI_PER_3S;
	public static final ModConfigSpec.IntValue ELEC_PASSIVE_SPEED;
	public static final ModConfigSpec.IntValue ELEC_PASSIVE_REGEN;
	public static final ModConfigSpec.DoubleValue ELEC_PASSIVE_OUTPUT;
	public static final ModConfigSpec.DoubleValue ELEC_PASSIVE_CREEPER_RANGE;
	public static final ModConfigSpec.DoubleValue ELEC_SAVE_COST;
	public static final ModConfigSpec.IntValue ELEC_SAVE_TICKS;
	public static final ModConfigSpec.IntValue ELEC_SAVE_THORNS;
	public static final ModConfigSpec.DoubleValue ELEC_LIGHTNING_DAMAGE;
	public static final ModConfigSpec.DoubleValue ELEC_ATTACK1_COST;
	public static final ModConfigSpec.DoubleValue ELEC_ATTACK1_PASSIVE_DISCOUNT;
	public static final ModConfigSpec.IntValue ELEC_ATTACK1_DURATION;
	public static final ModConfigSpec.IntValue ELEC_ATTACK1_FIRE_TICKS;
	public static final ModConfigSpec.IntValue ELEC_ATTACK1_STUN_TICKS;
	public static final ModConfigSpec.IntValue ELEC_ATTACK1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue ELEC_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue ELEC_HEAVY_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue ELEC_HEAVY_STRIKE_MULTIPLIER;
	public static final ModConfigSpec.IntValue ELEC_HEAVY_COOLDOWN;
	public static final ModConfigSpec.DoubleValue ELEC_REINFORCE_COST;
	public static final ModConfigSpec.IntValue ELEC_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue ELEC_REINFORCE_THORNS;
	public static final ModConfigSpec.IntValue ELEC_REINFORCE_THORNS_TICKS;
	public static final ModConfigSpec.IntValue ELEC_REINFORCE_COOLDOWN;
	public static final ModConfigSpec.DoubleValue ELEC_PB1_COST;
	public static final ModConfigSpec.IntValue ELEC_PB1_DURATION;
	public static final ModConfigSpec.IntValue ELEC_PB1_SPEED;
	public static final ModConfigSpec.IntValue ELEC_PB1_HASTE;
	public static final ModConfigSpec.IntValue ELEC_PB1_RESISTANCE;
	public static final ModConfigSpec.IntValue ELEC_PB1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue ELEC_PB2_COST;
	public static final ModConfigSpec.DoubleValue ELEC_PB2_PASSIVE_DISCOUNT;
	public static final ModConfigSpec.DoubleValue ELEC_PB2_STRIKE_MULTIPLIER;
	public static final ModConfigSpec.IntValue ELEC_PB2_COOLDOWN;
	public static final ModConfigSpec.DoubleValue ELEC_ABILITY1_COST;
	public static final ModConfigSpec.IntValue ELEC_ABILITY1_DURATION;
	public static final ModConfigSpec.IntValue ELEC_ABILITY1_INTERVAL;
	public static final ModConfigSpec.DoubleValue ELEC_ABILITY1_RADIUS;
	public static final ModConfigSpec.IntValue ELEC_ABILITY1_REGEN;
	public static final ModConfigSpec.IntValue ELEC_ABILITY1_SPEED;
	public static final ModConfigSpec.DoubleValue ELEC_ABILITY1_OUTPUT;
	public static final ModConfigSpec.IntValue ELEC_ABILITY1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue ELEC_ULT_COST;
	public static final ModConfigSpec.IntValue ELEC_ULT_DURATION;
	public static final ModConfigSpec.DoubleValue ELEC_ULT_OUTPUT;
	public static final ModConfigSpec.DoubleValue ELEC_ULT_BROKEN_MELEE;
	public static final ModConfigSpec.DoubleValue ELEC_ULT_BROKEN_QI;
	public static final ModConfigSpec.IntValue ELEC_ULT_COOLDOWN;

	// ---- Fire ----
	public static final ModConfigSpec.DoubleValue FIRE_PASSIVE_QI_PER_SECOND;
	public static final ModConfigSpec.DoubleValue FIRE_ATTACK1_COST;
	public static final ModConfigSpec.DoubleValue FIRE_ATTACK1_FIST_DAMAGE;
	public static final ModConfigSpec.DoubleValue FIRE_ATTACK1_SWORD_REDUCTION;
	public static final ModConfigSpec.IntValue FIRE_ATTACK1_KNOCKBACK;
	public static final ModConfigSpec.IntValue FIRE_ATTACK1_FIRE_TICKS;
	public static final ModConfigSpec.IntValue FIRE_ATTACK1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue FIRE_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue FIRE_HEAVY_DAMAGE;
	public static final ModConfigSpec.IntValue FIRE_HEAVY_KNOCKBACK;
	public static final ModConfigSpec.IntValue FIRE_HEAVY_FIRE_TICKS;
	public static final ModConfigSpec.DoubleValue FIRE_HEAVY_SPEED;
	public static final ModConfigSpec.IntValue FIRE_HEAVY_MAX_CHASE_TICKS;
	public static final ModConfigSpec.DoubleValue FIRE_HEAVY_CONTACT_RANGE;
	public static final ModConfigSpec.DoubleValue FIRE_HEAVY_SEARCH_HEIGHT;
	public static final ModConfigSpec.IntValue FIRE_HEAVY_MAX_SEARCH_TICKS;
	public static final ModConfigSpec.DoubleValue FIRE_HEAVY_MISS_REFUND;
	public static final ModConfigSpec.IntValue FIRE_HEAVY_COOLDOWN;
	public static final ModConfigSpec.DoubleValue FIRE_REINFORCE_COST;
	public static final ModConfigSpec.IntValue FIRE_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue FIRE_REINFORCE_COOLDOWN;
	public static final ModConfigSpec.DoubleValue FIRE_PB1_COST;
	public static final ModConfigSpec.DoubleValue FIRE_PB1_RADIUS;
	public static final ModConfigSpec.DoubleValue FIRE_PB1_DAMAGE;
	public static final ModConfigSpec.IntValue FIRE_PB1_FIRE_TICKS;
	public static final ModConfigSpec.IntValue FIRE_PB1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue FIRE_PB2_COST;
	public static final ModConfigSpec.DoubleValue FIRE_PB2_RADIUS;
	public static final ModConfigSpec.DoubleValue FIRE_PB2_DAMAGE;
	public static final ModConfigSpec.IntValue FIRE_PB2_FIRE_TICKS;
	public static final ModConfigSpec.IntValue FIRE_PB2_BLOCKS_PER_TICK;
	public static final ModConfigSpec.IntValue FIRE_PB2_COOLDOWN;
	public static final ModConfigSpec.DoubleValue FIRE_ABILITY1_QI_PER_SECOND;
	public static final ModConfigSpec.DoubleValue FIRE_ABILITY1_QI_PER_SECOND_BURNING;
	public static final ModConfigSpec.DoubleValue FIRE_ABILITY1_HEAL_PER_SECOND;
	public static final ModConfigSpec.IntValue FIRE_ABILITY1_MAX_TICKS;
	public static final ModConfigSpec.IntValue FIRE_ABILITY1_COOLDOWN;
	public static final ModConfigSpec.DoubleValue FIRE_ABILITY2_COST;
	public static final ModConfigSpec.IntValue FIRE_ABILITY2_RADIUS;
	public static final ModConfigSpec.IntValue FIRE_ABILITY2_COOLDOWN;

	// ---- Light ----
	public static final ModConfigSpec.DoubleValue LIGHT_PASSIVE_QI_PER_30MIN;
	public static final ModConfigSpec.DoubleValue LIGHT_ATTACK1_COST;
	public static final ModConfigSpec.DoubleValue LIGHT_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue LIGHT_REINFORCE_COST;
	public static final ModConfigSpec.DoubleValue LIGHT_REINFORCE_ASCEND_SPEED;
	public static final ModConfigSpec.DoubleValue LIGHT_PB1_COST;
	public static final ModConfigSpec.DoubleValue LIGHT_PB1_RADIUS;
	public static final ModConfigSpec.DoubleValue LIGHT_PB1_DISTANCE;
	public static final ModConfigSpec.DoubleValue LIGHT_PB1_SPEED;
	public static final ModConfigSpec.DoubleValue LIGHT_PB2_COST;
	public static final ModConfigSpec.DoubleValue LIGHT_PB2_RADIUS;
	public static final ModConfigSpec.DoubleValue LIGHT_ABILITY1_COST;
	public static final ModConfigSpec.DoubleValue LIGHT_ABILITY1_MIN_DISTANCE;
	public static final ModConfigSpec.DoubleValue LIGHT_ABILITY1_MAX_DISTANCE;
	public static final ModConfigSpec.DoubleValue LIGHT_ULT_COST;
	public static final ModConfigSpec.DoubleValue LIGHT_ULT_RADIUS;
	public static final ModConfigSpec.IntValue LIGHT_DAY_START;
	public static final ModConfigSpec.IntValue LIGHT_DAY_END;
	public static final ModConfigSpec.IntValue LIGHT_PASSIVE_RESISTANCE;
	public static final ModConfigSpec.IntValue LIGHT_PASSIVE_SPEED;
	public static final ModConfigSpec.IntValue LIGHT_ATTACK1_STRENGTH;
	public static final ModConfigSpec.IntValue LIGHT_ATTACK1_TICKS;
	public static final ModConfigSpec.IntValue LIGHT_ATTACK1_COOLDOWN;
	public static final ModConfigSpec.IntValue LIGHT_HEAVY_TICKS;
	public static final ModConfigSpec.IntValue LIGHT_HEAVY_COOLDOWN;
	public static final ModConfigSpec.IntValue LIGHT_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue LIGHT_REINFORCE_REGEN_TICKS;
	public static final ModConfigSpec.IntValue LIGHT_REINFORCE_FLIGHT_TICKS;
	public static final ModConfigSpec.IntValue LIGHT_REINFORCE_FEATHER_FALLING;
	public static final ModConfigSpec.IntValue LIGHT_REINFORCE_COOLDOWN;
	public static final ModConfigSpec.IntValue LIGHT_PB1_COOLDOWN;
	public static final ModConfigSpec.IntValue LIGHT_PB2_NULLIFY_TICKS;
	public static final ModConfigSpec.IntValue LIGHT_PB2_COOLDOWN;
	public static final ModConfigSpec.IntValue LIGHT_ABILITY1_MIN_LIGHT;
	public static final ModConfigSpec.IntValue LIGHT_ABILITY1_COOLDOWN;
	public static final ModConfigSpec.IntValue LIGHT_ULT_EFFECT_TICKS;
	public static final ModConfigSpec.IntValue LIGHT_ULT_RESISTANCE;
	public static final ModConfigSpec.IntValue LIGHT_ULT_HASTE;
	public static final ModConfigSpec.IntValue LIGHT_ULT_SPEED;
	public static final ModConfigSpec.IntValue LIGHT_ULT_WEAKNESS;
	public static final ModConfigSpec.IntValue LIGHT_ULT_COOLDOWN;

	// ---- Pure ----
	public static final ModConfigSpec.DoubleValue PURE_PASSIVE_QI_PER_MIN;
	public static final ModConfigSpec.DoubleValue PURE_COST_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue PURE_L3_GAIN_PER_MIN;
	public static final ModConfigSpec.DoubleValue PURE_L4_GAIN_PER_MIN;
	public static final ModConfigSpec.DoubleValue PURE_L4_REDUCTION;
	public static final ModConfigSpec.DoubleValue PURE_L5_GAIN_PER_MIN;
	public static final ModConfigSpec.DoubleValue PURE_L5_ABSORB_FRACTION;
	public static final ModConfigSpec.DoubleValue PURE_L5_DAMAGE_PER_QI;
	public static final ModConfigSpec.DoubleValue PURE_ATTACK_COST;
	public static final ModConfigSpec.DoubleValue PURE_ATTACK_DAMAGE;
	public static final ModConfigSpec.DoubleValue PURE_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue PURE_HEAVY_RADIUS;
	public static final ModConfigSpec.DoubleValue PURE_HEAVY_VERTICAL;
	public static final ModConfigSpec.DoubleValue PURE_HEAVY_DAMAGE;
	public static final ModConfigSpec.DoubleValue PURE_REINFORCE_COST;
	public static final ModConfigSpec.DoubleValue PURE_REINFORCE_RADIUS;
	public static final ModConfigSpec.DoubleValue PURE_PB1_COST;
	public static final ModConfigSpec.DoubleValue PURE_PB1_OUTPUT;
	public static final ModConfigSpec.DoubleValue PURE_PB1_STRIKE_MULTIPLIER;
	public static final ModConfigSpec.DoubleValue PURE_PB2_COST;
	public static final ModConfigSpec.DoubleValue PURE_PB2_DAMAGE;
	public static final ModConfigSpec.DoubleValue PURE_A1_COST;
	public static final ModConfigSpec.DoubleValue PURE_A1_RANGE;
	public static final ModConfigSpec.DoubleValue PURE_A1_DAMAGE;
	public static final ModConfigSpec.DoubleValue PURE_A2_COST;
	public static final ModConfigSpec.DoubleValue PURE_A2_RADIUS;
	public static final ModConfigSpec.DoubleValue PURE_A2_BREAK_CHANCE;
	public static final ModConfigSpec.IntValue PURE_LEVEL_HEAVY;
	public static final ModConfigSpec.IntValue PURE_LEVEL_REINFORCE;
	public static final ModConfigSpec.IntValue PURE_LEVEL_POWERBLAST;
	public static final ModConfigSpec.IntValue PURE_LEVEL_ABILITIES;
	public static final ModConfigSpec.IntValue PURE_ATTACK_KNOCKBACK;
	public static final ModConfigSpec.IntValue PURE_ATTACK_NAUSEA_TICKS;
	public static final ModConfigSpec.IntValue PURE_ATTACK_COOLDOWN;
	public static final ModConfigSpec.IntValue PURE_HEAVY_FIRE_RES_TICKS;
	public static final ModConfigSpec.IntValue PURE_HEAVY_HIT_WINDOW;
	public static final ModConfigSpec.IntValue PURE_HEAVY_COOLDOWN;
	public static final ModConfigSpec.IntValue PURE_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue PURE_REINFORCE_REGEN;
	public static final ModConfigSpec.IntValue PURE_REINFORCE_EFFECT_TICKS;
	public static final ModConfigSpec.IntValue PURE_REINFORCE_STRENGTH;
	public static final ModConfigSpec.IntValue PURE_REINFORCE_COOLDOWN;
	public static final ModConfigSpec.IntValue PURE_PB1_DURATION;
	public static final ModConfigSpec.IntValue PURE_PB1_COOLDOWN;
	public static final ModConfigSpec.IntValue PURE_PB2_BASE_SECONDS;
	public static final ModConfigSpec.IntValue PURE_PB2_PER_LEVEL_SECONDS;
	public static final ModConfigSpec.IntValue PURE_PB2_MIN_SECONDS;
	public static final ModConfigSpec.IntValue PURE_PB2_MAX_SECONDS;
	public static final ModConfigSpec.IntValue PURE_PB2_COOLDOWN;
	public static final ModConfigSpec.IntValue PURE_A1_KNOCKBACK;
	public static final ModConfigSpec.IntValue PURE_A1_COOLDOWN;
	public static final ModConfigSpec.IntValue PURE_A2_MAX_RISKY_CORE_LEVEL;
	public static final ModConfigSpec.IntValue PURE_A2_COOLDOWN;
	public static final ModConfigSpec.BooleanValue PURE_L5_KEEPS_REDUCTION;

	// ---- Protective ----
	public static final ModConfigSpec.DoubleValue PROT_PASSIVE_QI_PER_SECOND;
	public static final ModConfigSpec.DoubleValue PROT_PASSIVE_MAX_HEALTH;
	public static final ModConfigSpec.DoubleValue PROT_PASSIVE_QI_PER_DAMAGE;
	public static final ModConfigSpec.DoubleValue PROT_ATTACK1_COST;
	public static final ModConfigSpec.DoubleValue PROT_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue PROT_HEAVY_HEAL_FRACTION;
	public static final ModConfigSpec.DoubleValue PROT_REINFORCE_COST;
	public static final ModConfigSpec.DoubleValue PROT_REINFORCE_ALLY_RADIUS;
	public static final ModConfigSpec.DoubleValue PROT_PB1_COST;
	public static final ModConfigSpec.DoubleValue PROT_PB1_SPEED;
	public static final ModConfigSpec.DoubleValue PROT_PB1_CONTACT_RANGE;
	public static final ModConfigSpec.DoubleValue PROT_PB1_DAMAGE;
	public static final ModConfigSpec.DoubleValue PROT_PB1_USER_QI_LOSS;
	public static final ModConfigSpec.DoubleValue PROT_PB2_COST;
	public static final ModConfigSpec.DoubleValue PROT_PB2_RADIUS;
	public static final ModConfigSpec.DoubleValue PROT_A1_COST;
	public static final ModConfigSpec.DoubleValue PROT_A1_COST_PER_ALLY;
	public static final ModConfigSpec.DoubleValue PROT_A1_RADIUS;
	public static final ModConfigSpec.DoubleValue PROT_A1_CUT;
	public static final ModConfigSpec.DoubleValue PROT_A2_COST;
	public static final ModConfigSpec.DoubleValue PROT_A2_RADIUS;
	public static final ModConfigSpec.DoubleValue PROT_A2_DISTANCE;
	public static final ModConfigSpec.DoubleValue PROT_A2_SPEED;
	public static final ModConfigSpec.DoubleValue PROT_A2_DAMAGE;
	public static final ModConfigSpec.IntValue PROT_PASSIVE_RESISTANCE;
	public static final ModConfigSpec.IntValue PROT_PASSIVE_REGEN;
	public static final ModConfigSpec.IntValue PROT_ATTACK1_KNOCKBACK;
	public static final ModConfigSpec.IntValue PROT_ATTACK1_COOLDOWN;
	public static final ModConfigSpec.IntValue PROT_HEAVY_WINDOW_TICKS;
	public static final ModConfigSpec.IntValue PROT_HEAVY_THORNS;
	public static final ModConfigSpec.IntValue PROT_HEAVY_THORNS_TICKS;
	public static final ModConfigSpec.IntValue PROT_HEAVY_COOLDOWN;
	public static final ModConfigSpec.IntValue PROT_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue PROT_REINFORCE_ALLY_REGEN;
	public static final ModConfigSpec.IntValue PROT_REINFORCE_SELF_REGEN;
	public static final ModConfigSpec.IntValue PROT_REINFORCE_COOLDOWN;
	public static final ModConfigSpec.IntValue PROT_PB1_MAX_FLIGHT_TICKS;
	public static final ModConfigSpec.IntValue PROT_PB1_NULLIFY_TICKS;
	public static final ModConfigSpec.IntValue PROT_PB1_COOLDOWN;
	public static final ModConfigSpec.IntValue PROT_PB2_TICKS;
	public static final ModConfigSpec.IntValue PROT_PB2_COOLDOWN;
	public static final ModConfigSpec.IntValue PROT_A1_DURATION;
	public static final ModConfigSpec.IntValue PROT_A1_COOLDOWN;
	public static final ModConfigSpec.IntValue PROT_A2_SLOWNESS;
	public static final ModConfigSpec.IntValue PROT_A2_SLOWNESS_TICKS;
	public static final ModConfigSpec.IntValue PROT_A2_COOLDOWN;

	// ---- Water ----
	public static final ModConfigSpec.DoubleValue WATER_PASSIVE_QI_PER_MIN;
	public static final ModConfigSpec.DoubleValue WATER_PASSIVE_QI_PER_MIN_WET;
	public static final ModConfigSpec.DoubleValue WATER_ATTACK1_COST;
	public static final ModConfigSpec.DoubleValue WATER_ATTACK1_OUTPUT;
	public static final ModConfigSpec.DoubleValue WATER_HEAVY_COST;
	public static final ModConfigSpec.DoubleValue WATER_REINFORCE_COST;
	public static final ModConfigSpec.DoubleValue WATER_PB1_COST;
	public static final ModConfigSpec.DoubleValue WATER_PB1_OUTPUT;
	public static final ModConfigSpec.DoubleValue WATER_PB1_FIRE_RESIST_PER_LEVEL;
	public static final ModConfigSpec.DoubleValue WATER_PB2_COST;
	public static final ModConfigSpec.DoubleValue WATER_PB2_RADIUS;
	public static final ModConfigSpec.DoubleValue WATER_A1_COST;
	public static final ModConfigSpec.DoubleValue WATER_A2_COST;
	public static final ModConfigSpec.DoubleValue WATER_A2_OUTPUT;
	public static final ModConfigSpec.DoubleValue WATER_A2_EXTINGUISH_RADIUS;
	public static final ModConfigSpec.DoubleValue WATER_A2_NULLIFY_RADIUS;
	public static final ModConfigSpec.DoubleValue WATER_A2_SPEED;
	public static final ModConfigSpec.DoubleValue WATER_A2_CONTACT_RANGE;
	public static final ModConfigSpec.IntValue WATER_PASSIVE_SPEED;
	public static final ModConfigSpec.IntValue WATER_PASSIVE_FEATHER_FALLING;
	public static final ModConfigSpec.IntValue WATER_PASSIVE_WET_REGEN;
	public static final ModConfigSpec.IntValue WATER_PASSIVE_WET_RESISTANCE;
	public static final ModConfigSpec.IntValue WATER_ATTACK1_TICKS;
	public static final ModConfigSpec.IntValue WATER_ATTACK1_COOLDOWN;
	public static final ModConfigSpec.IntValue WATER_HEAVY_KNOCKBACK;
	public static final ModConfigSpec.IntValue WATER_HEAVY_COOLDOWN;
	public static final ModConfigSpec.IntValue WATER_REINFORCE_DURATION;
	public static final ModConfigSpec.IntValue WATER_REINFORCE_COOLDOWN;
	public static final ModConfigSpec.IntValue WATER_PB1_DURATION;
	public static final ModConfigSpec.IntValue WATER_PB1_SPEED;
	public static final ModConfigSpec.IntValue WATER_PB1_HASTE;
	public static final ModConfigSpec.IntValue WATER_PB1_FIRE_RESIST_LEVEL;
	public static final ModConfigSpec.IntValue WATER_PB1_COOLDOWN;
	public static final ModConfigSpec.IntValue WATER_PB2_FLOOD_TICKS;
	public static final ModConfigSpec.IntValue WATER_PB2_BLIND_TICKS;
	public static final ModConfigSpec.IntValue WATER_PB2_COOLDOWN;
	public static final ModConfigSpec.IntValue WATER_A1_SEARCH_RADIUS;
	public static final ModConfigSpec.IntValue WATER_A1_COOLDOWN;
	public static final ModConfigSpec.IntValue WATER_A2_OUTPUT_TICKS;
	public static final ModConfigSpec.IntValue WATER_A2_NULLIFY_TICKS;
	public static final ModConfigSpec.IntValue WATER_A2_WEAKNESS;
	public static final ModConfigSpec.IntValue WATER_A2_WEAKNESS_TICKS;
	public static final ModConfigSpec.IntValue WATER_A2_MAX_FLIGHT_TICKS;
	public static final ModConfigSpec.IntValue WATER_A2_BLOCKS_PER_TICK;
	public static final ModConfigSpec.IntValue WATER_A2_COOLDOWN;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.comment("Rules shared by all abilities").push("framework");
		EMPOWER_WINDOW_TICKS = b
				.comment("How long an empowered Attack 1 / Heavy waits for your next hit, in ticks (20 = 1 s).",
						"NOT in the document. 0 = it waits until your next hit, however long that takes.")
				.defineInRange("empower_window_ticks", 0, 0, Integer.MAX_VALUE);
		LOOKED_AT_RANGE = b
				.comment("Max distance (blocks) for the 'Looked-At Player' targeting rule. NOT in the document; placeholder.")
				.defineInRange("looked_at_range", 64.0, 1.0, 512.0);
		REINFORCE_DAMAGE_REDUCTION = b
				.comment("Standard Reinforce Body incoming damage reduction (0-1). Document: 50%.")
				.defineInRange("reinforce_damage_reduction", 0.5, 0.0, 1.0);
		b.pop();

		b.comment("WARRIOR. -1 = not finalized (ability disabled until set).").push("warrior");
		b.push("passive");
		WARRIOR_PASSIVE_QI_PER_SECOND = b.comment("Continuous Qi per second while holding a sword. Decided: 1.")
				.defineInRange("qi_per_second", 1.0, 0.0, Double.MAX_VALUE);
		WARRIOR_PASSIVE_QI_PER_SWORD_USE = b.comment("Qi per sword hit while the passive is active. Document: 2.")
				.defineInRange("qi_per_sword_use", 2.0, 0.0, Double.MAX_VALUE);
		WARRIOR_PASSIVE_SPEED_LEVEL = b.comment("Speed level while holding a sword. Document: IV.")
				.defineInRange("speed_level", 4, 0, 255);
		WARRIOR_PASSIVE_STRENGTH_LEVEL = b.comment("Strength level while holding a sword. Document: III.")
				.defineInRange("strength_level", 3, 0, 255);
		b.pop();

		b.push("attack1");
		WARRIOR_ATTACK1_COST = b.comment("Document: 5 Qi.").defineInRange("cost", 5.0, -1, Double.MAX_VALUE);
		WARRIOR_ATTACK1_MULTIPLIER = b.comment("Damage multiplier on the empowered hit. Document: 2x.").defineInRange("damage_multiplier", 2.0, 0, Double.MAX_VALUE);
		WARRIOR_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		WARRIOR_HEAVY_COST = b.comment("Document: 10 Qi.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		WARRIOR_HEAVY_MULTIPLIER = b.comment("Damage multiplier on the empowered hit. Document: 3x.").defineInRange("damage_multiplier", 3.0, 0, Double.MAX_VALUE);
		WARRIOR_HEAVY_FULL_HEAL = b.comment("Heal the user to full health on the empowered hit. Decided: full heal.").define("full_heal", true);
		WARRIOR_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		WARRIOR_REINFORCE_COST = b.comment("Document: 5 Qi.").defineInRange("cost", 5.0, -1, Double.MAX_VALUE);
		WARRIOR_REINFORCE_DURATION = b.comment("Balance pass 2026-10-07 (user): Reinforce Body lasts 60 s (all aspects except Protective, Lightning, Physical).").defineInRange("duration_ticks", 1200, -1, Integer.MAX_VALUE);
		WARRIOR_REINFORCE_COOLDOWN = b.comment("Cooldown ticks. Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		WARRIOR_PB1_COST = b.comment("Document: 20 Qi.").defineInRange("cost", 20.0, -1, Double.MAX_VALUE);
		WARRIOR_PB1_MULTIPLIER = b.comment("Qi blast damage = this x your attack damage. Document: 3x (base damage interpreted as your attack damage; confirm).")
				.defineInRange("damage_multiplier", 3.0, 0, Double.MAX_VALUE);
		WARRIOR_PB1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		WARRIOR_PB2_COST = b.comment("Yeet. Decided: 15 Qi.").defineInRange("cost", 15.0, -1, Double.MAX_VALUE);
		WARRIOR_PB2_KNOCKBACK_LEVEL = b.comment("Knockback level. Document: XV.").defineInRange("knockback_level", 15, 0, 255);
		WARRIOR_PB2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability1");
		WARRIOR_ABILITY1_COST = b.comment("Speedy Blade. Qi paid when cast. Decided: none (only the 1 Qi/s drain).").defineInRange("cost", 0.0, -1, Double.MAX_VALUE);
		WARRIOR_ABILITY1_DURATION = b.comment("Duration ticks. Document: 3 min.").defineInRange("duration_ticks", 3600, 0, Integer.MAX_VALUE);
		WARRIOR_ABILITY1_QI_PER_SECOND = b.comment("Constant Qi drain per second while active. Decided: 1 Qi/s.").defineInRange("qi_per_second", 1.0, 0.0, Double.MAX_VALUE);
		WARRIOR_ABILITY1_ATTACK_SPEED_BONUS = b.comment("Attack speed bonus (2.0 = +200%). Decided: +200%.").defineInRange("attack_speed_bonus", 2.0, 0.0, 100.0);
		WARRIOR_ABILITY1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability2");
		WARRIOR_ABILITY2_COST = b.comment("Manual: cast to arm; saves you from the next lethal hit. Document: 30 Qi.").defineInRange("cost", 30.0, -1, Double.MAX_VALUE);
		WARRIOR_ABILITY2_DISTANCE = b.comment("Blocks teleported backwards. Document: 3.").defineInRange("distance", 3.0, 0.0, 64.0);
		WARRIOR_ABILITY2_COOLDOWN = b.comment("Document: 30 s.").defineInRange("cooldown_ticks", 600, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("PHYSICAL. -1 = not finalized.").push("physical");
		b.push("passive");
		PHYSICAL_PASSIVE_QI_PER_MINUTE = b.comment("Base continuous upkeep. Document: 5 Qi/minute.").defineInRange("qi_per_minute", 5.0, 0.0, Double.MAX_VALUE);
		PHYSICAL_PASSIVE_SPEED_LEVEL = b.comment("Document: Speed X.").defineInRange("speed_level", 10, 0, 255);
		PHYSICAL_PASSIVE_STRENGTH_LEVEL = b.comment("Document: Strength III.").defineInRange("strength_level", 3, 0, 255);
		PHYSICAL_PASSIVE_UNARMED_MULTIPLIER = b.comment("Non-weapon melee damage multiplier. Document: 3x.").defineInRange("non_weapon_melee_multiplier", 3.0, 0.0, Double.MAX_VALUE);
		PHYSICAL_ENV_MULT_1 = b.comment("Upkeep multiplier with 1 environmental condition (lava / lightning / underwater). Document: 2x.").defineInRange("env_multiplier_1", 2.0, 0.0, Double.MAX_VALUE);
		PHYSICAL_ENV_MULT_2 = b.comment("With 2 conditions. Document: 4x.").defineInRange("env_multiplier_2", 4.0, 0.0, Double.MAX_VALUE);
		PHYSICAL_ENV_MULT_3 = b.comment("With all 3 conditions. Document: 9x (intentional).").defineInRange("env_multiplier_3", 9.0, 0.0, Double.MAX_VALUE);
		PHYSICAL_LIGHTNING_WINDOW_TICKS = b.comment("How long after being struck 'lightning strike' still counts as a condition (cost multiplier + fire from the strike nullified).",
				"Decided 2026-10-08: 160 ticks = 8 s, the length of vanilla lightning's burn; the passive checks once per second, so values under 20 are unreliable.")
				.defineInRange("lightning_window_ticks", 160, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("attack1");
		PHYSICAL_ATTACK1_COST = b.comment("Fists of Fury. Document: 10 Qi initial.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		PHYSICAL_ATTACK1_DURATION = b.comment("Document: 3 min.").defineInRange("duration_ticks", 3600, 0, Integer.MAX_VALUE);
		PHYSICAL_ATTACK1_QI_PER_SECOND = b.comment("Document: then 1 Qi every 2 s (= 0.5/s).").defineInRange("qi_per_second", 0.5, 0.0, Double.MAX_VALUE);
		PHYSICAL_ATTACK1_ATTACK_SPEED = b.comment("Attack Speed attribute value while active. Document: 34.").defineInRange("attack_speed", 34.0, 0.0, 1024.0);
		PHYSICAL_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		PHYSICAL_HEAVY_COST = b.comment("Bomb. Document: 20 Qi.").defineInRange("cost", 20.0, -1, Double.MAX_VALUE);
		PHYSICAL_HEAVY_DURATION = b.comment("Document: 1 min.").defineInRange("duration_ticks", 1200, 0, Integer.MAX_VALUE);
		PHYSICAL_HEAVY_EXPLOSION_POWER = b.comment("Explosion on every melee hit. Decided: creeper size (3).").defineInRange("explosion_power", 3.0, 0.0, 20.0);
		PHYSICAL_HEAVY_BREAKS_BLOCKS = b.comment("Decided: explosions break blocks.").define("breaks_blocks", true);
		PHYSICAL_HEAVY_BLOCKS_PER_CRATER_RADIUS = b.comment("Crater radius = blocks fallen / this (rounded down). Decided: scales with fall; 5 blocks per radius.")
				.defineInRange("blocks_fallen_per_crater_radius", 5.0, 0.5, 1000.0);
		PHYSICAL_HEAVY_MAX_CRATER_RADIUS = b.comment("Largest crater radius. 6.").defineInRange("max_crater_radius", 6, 1, 20);
		PHYSICAL_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		PHYSICAL_REINFORCE_COST = b.comment("Document: 50 Qi.").defineInRange("cost", 50.0, -1, Double.MAX_VALUE);
		PHYSICAL_REINFORCE_DURATION = b.comment("Decided: 10 s.").defineInRange("duration_ticks", 200, -1, Integer.MAX_VALUE);
		PHYSICAL_REINFORCE_REGEN_LEVEL = b.comment("Document: Regeneration 255.").defineInRange("regeneration_level", 255, 0, 255);
		PHYSICAL_REINFORCE_RESISTANCE_LEVEL = b.comment("Document: Resistance 255.").defineInRange("resistance_level", 255, 0, 255);
		PHYSICAL_REINFORCE_COOLDOWN = b.comment("Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		PHYSICAL_PB1_COST = b.comment("Balance pass 2026-10-07 (user): 50 Qi (was 20).").defineInRange("cost", 50.0, -1, Double.MAX_VALUE);
		PHYSICAL_PB1_DAMAGE = b.comment("Damage points on confirmed contact. Document: 50.").defineInRange("damage", 50.0, 0.0, Double.MAX_VALUE);
		PHYSICAL_PB1_SPEED = b.comment("Throw speed, blocks/tick. NOT in document; placeholder.").defineInRange("speed", 2.5, 0.1, 10.0);
		PHYSICAL_PB1_MAX_FLIGHT_TICKS = b.comment("Max flight time before it counts as a miss. NOT in document; placeholder 3 s.").defineInRange("max_flight_ticks", 60, 1, 1200);
		PHYSICAL_PB1_CONTACT_RANGE = b.comment("Distance that counts as contact. NOT in document; placeholder.").defineInRange("contact_range", 1.5, 0.5, 5.0);
		PHYSICAL_PB1_RANGE = b.comment("Balance pass 2026-10-07 (user): the target must be within this many blocks.").defineInRange("target_range", 20.0, 1.0, 512.0);
		PHYSICAL_PB1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 190 s.").defineInRange("cooldown_ticks", 3800, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		PHYSICAL_PB2_COST = b.comment("Sky High. Document: 40 Qi.").defineInRange("cost", 40.0, -1, Double.MAX_VALUE);
		PHYSICAL_PB2_ASCEND_SPEED = b.comment("Climb speed to the sky limit, blocks/tick. NOT in document; placeholder.").defineInRange("ascend_speed", 3.0, 0.5, 10.0);
		PHYSICAL_PB2_DAMAGE_PER_BLOCK = b.comment("Damage points per block fallen. Decided: 2.").defineInRange("damage_per_block", 2.0, 0.0, Double.MAX_VALUE);
		PHYSICAL_PB2_HIT_RADIUS = b.comment("Decided: only who you land on (1.5 blocks).").defineInRange("hit_radius", 1.5, 0.5, 16.0);
		PHYSICAL_PB2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		PHYSICAL_PB2_PASSIVE_NULLIFY_TICKS = b.comment("On a hit, the USER's own Physical passive is nullified for this long (balancing). Decided: 30 s.").defineInRange("own_passive_nullify_ticks", 600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability1");
		PHYSICAL_ABILITY1_QI_FRACTION = b.comment("Survivor. Fraction of remaining Qi paid on cast. Document: 50%.").defineInRange("remaining_qi_fraction", 0.5, 0.0, 1.0);
		PHYSICAL_ABILITY1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability2");
		PHYSICAL_ABILITY2_COST = b.comment("Get Out of My Domain. Document: 200 Qi. Under this: fires anyway and breaks YOUR Core (decided).").defineInRange("cost", 200.0, -1, Double.MAX_VALUE);
		PHYSICAL_ABILITY2_KNOCKBACK_LEVEL = b.comment("Knockback level on the user's melee hits while active. Document: 255 (speed is capped for safety).").defineInRange("knockback_level", 255, 0, 255);
		PHYSICAL_ABILITY2_STRENGTH_LEVEL = b.comment("Strength on the user. Document: 255.").defineInRange("strength_level", 255, 0, 255);
		PHYSICAL_ABILITY2_STRENGTH_DURATION = b.comment("Strength 255 duration. Decided: 3 min.").defineInRange("strength_duration_ticks", 3600, -1, Integer.MAX_VALUE);
		PHYSICAL_ABILITY2_KNOCKBACK_DURATION = b.comment("Knockback 255 on melee hits, duration. Decided: 3 min.").defineInRange("knockback_duration_ticks", 3600, 0, Integer.MAX_VALUE);
		PHYSICAL_ABILITY2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("DARK. -1 = not finalized.").push("dark");
		b.push("passive");
		DARK_PASSIVE_QI_PER_MINUTE = b.comment("Document: 1 Qi/minute.").defineInRange("qi_per_minute", 1.0, 0.0, Double.MAX_VALUE);
		DARK_NIGHT_START = b.comment("Night window start (time of day, ticks). Decided: 13800.").defineInRange("night_start", 13800, 0, 23999);
		DARK_NIGHT_END = b.comment("Night window end. Decided: 22200.").defineInRange("night_end", 22200, 0, 23999);
		DARK_NIGHT_OUTPUT_MULTIPLIER = b.comment("Total damage output at night. Document: 2x.").defineInRange("night_output_multiplier", 2.0, 0.0, Double.MAX_VALUE);
		DARK_NIGHT_INCOMING_DIVISOR = b.comment("Incoming damage is divided by this at night. Decided: 1.5 (~33% less).").defineInRange("night_incoming_divisor", 1.5, 1.0, 1000.0);
		b.pop();
		DARK_EFFECT_TICKS = b.comment("Duration of Dark status effects that have no listed duration. Decided: 30 s.").defineInRange("effect_duration_ticks", 600, 0, Integer.MAX_VALUE);
		DARK_DARKNESS_LIGHT = b.comment("'In darkness' = light level at or below this. Decided: 5.").defineInRange("darkness_light_level", 5, 0, 15);

		b.push("attack1");
		DARK_ATTACK1_COST = b.comment("Document: 10 Qi.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		DARK_ATTACK1_BLADE_MULTIPLIER = b.comment("Blade (sword) hit damage. Document: 2x.").defineInRange("blade_multiplier", 2.0, 0.0, Double.MAX_VALUE);
		DARK_ATTACK1_KNOCKBACK = b.comment("Non-blade hit knockback. Document: II.").defineInRange("non_blade_knockback", 2, 0, 255);
		DARK_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		DARK_HEAVY_COST = b.comment("Document: 20 Qi.").defineInRange("cost", 20.0, -1, Double.MAX_VALUE);
		DARK_HEAVY_DARK_MULTIPLIER = b.comment("Damage in darkness. Document: 3x.").defineInRange("darkness_multiplier", 3.0, 0.0, Double.MAX_VALUE);
		DARK_HEAVY_KNOCKBACK = b.comment("Knockback in darkness. Document: V.").defineInRange("darkness_knockback", 5, 0, 255);
		DARK_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		DARK_REINFORCE_COST = b.comment("Document: 30 Qi; 50% less when light level < 5.").defineInRange("cost", 30.0, -1, Double.MAX_VALUE);
		DARK_REINFORCE_DURATION = b.comment("Balance pass 2026-10-07 (user): Reinforce Body lasts 60 s (all aspects except Protective, Lightning, Physical).").defineInRange("duration_ticks", 1200, -1, Integer.MAX_VALUE);
		DARK_REINFORCE_THORNS = b.comment("Thorns level when light level < 5. Document: II.").defineInRange("thorns_level", 2, 0, 255);
		DARK_REINFORCE_COOLDOWN = b.comment("Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		DARK_PB1_COST = b.comment("Shadow Essence. Document: 5 Qi.").defineInRange("cost", 5.0, -1, Double.MAX_VALUE);
		DARK_PB1_COOLDOWN = b.comment("Document: 180 s.").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		DARK_PB1_DURATION = b.comment("Document: 3 min (Strength II); decided same for max health and Thorns.").defineInRange("duration_ticks", 3600, 0, Integer.MAX_VALUE);
		DARK_PB1_MAX_HEALTH = b.comment("Document: +100 max health.").defineInRange("max_health_bonus", 100.0, 0.0, 10000.0);
		DARK_PB1_STRENGTH = b.comment("Document: Strength II.").defineInRange("strength_level", 2, 0, 255);
		DARK_PB1_THORNS = b.comment("Document: Thorns II.").defineInRange("thorns_level", 2, 0, 255);
		b.pop();

		b.push("powerblast2");
		DARK_PB2_COST = b.comment("Yeet. Document: 10 Qi.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		DARK_PB2_COOLDOWN = b.comment("Document: 30 s.").defineInRange("cooldown_ticks", 600, 0, Integer.MAX_VALUE);
		DARK_PB2_HEIGHT = b.comment("Blocks the nearest player is thrown upward. Document: 40.").defineInRange("height", 40.0, 0.0, 400.0);
		b.pop();

		b.push("ability1");
		DARK_ABILITY1_COST = b.comment("Shadow-Walk. Document: 10 Qi per use, no cooldown.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		DARK_ABILITY1_HEALTH_THRESHOLD = b.comment("'Under 50 hearts' in health points.").defineInRange("health_threshold", 100.0, 0.0, 10000.0);
		DARK_ABILITY1_HEAL = b.comment("'Heal 30 hearts' in health points.").defineInRange("heal", 60.0, 0.0, 10000.0);
		DARK_ABILITY1_ESCAPE_RANGE = b.comment("Escape teleport range. Document: 50.").defineInRange("escape_range", 50, 1, 200);
		DARK_ABILITY1_PLAYER_RANGE = b.comment("Teleport-to-player range. Document: 40.").defineInRange("player_range", 40, 1, 200);
		b.pop();

		b.push("ultimate");
		DARK_ULT_COST = b.comment("Document: 60 Qi.").defineInRange("cost", 60.0, -1, Double.MAX_VALUE);
		DARK_ULT_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		DARK_ULT_RADIUS = b.comment("Document: 50 blocks.").defineInRange("radius", 50.0, 0.0, 512.0);
		DARK_ULT_UNLUCK = b.comment("Document: Unluck XIV.").defineInRange("unluck_level", 14, 0, 255);
		DARK_ULT_LIGHT_WEAKNESS = b.comment("Light-aspect targets: Weakness III.").defineInRange("light_weakness_level", 3, 0, 255);
		DARK_ULT_LIGHT_HEALTH_REDUCTION = b.comment("Light-aspect targets: 20% max-health reduction.").defineInRange("light_max_health_reduction", 0.2, 0.0, 0.99);
		DARK_ULT_LIGHT_DURATION = b.comment("Document: 3 min.").defineInRange("light_duration_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("EARTH. -1 = not finalized.").push("earth");
		EARTH_EFFECT_TICKS = b.comment("Duration of Earth effects with no listed duration. Decided: 30 s.").defineInRange("effect_duration_ticks", 600, 0, Integer.MAX_VALUE);
		b.push("passive");
		EARTH_PASSIVE_QI_PER_15S = b.comment("Document: 2 Qi every 15 s.").defineInRange("qi_per_15_seconds", 2.0, 0.0, Double.MAX_VALUE);
		EARTH_PASSIVE_HASTE = b.comment("'Faster mining'. Decided: Haste V.").defineInRange("haste_level", 5, 0, 255);
		EARTH_PASSIVE_FORTUNE = b.comment("Constant Fortune level on mined blocks. Document: III.").defineInRange("fortune_level", 3, 0, 255);
		b.pop();

		b.push("attack1");
		EARTH_ATTACK1_COST = b.comment("Document: 5 Qi.").defineInRange("cost", 5.0, -1, Double.MAX_VALUE);
		EARTH_ATTACK1_MULTIPLIER = b.comment("Document: 2x melee damage.").defineInRange("damage_multiplier", 2.0, 0.0, Double.MAX_VALUE);
		EARTH_ATTACK1_KNOCKBACK = b.comment("Document: Knockback V.").defineInRange("knockback_level", 5, 0, 255);
		EARTH_ATTACK1_SLOWNESS = b.comment("Document: Slowness II.").defineInRange("slowness_level", 2, 0, 255);
		EARTH_ATTACK1_SLOWNESS_TICKS = b.comment("Document: 1 min.").defineInRange("slowness_ticks", 1200, 0, Integer.MAX_VALUE);
		EARTH_ATTACK1_STUN_TICKS = b.comment("Wall impact: hit damage again + stun. Decided: both; stun 2 s.").defineInRange("impact_stun_ticks", 40, 0, Integer.MAX_VALUE);
		EARTH_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		EARTH_HEAVY_COST = b.comment("Balance pass 2026-10-07 (user): 35 Qi (was 20).").defineInRange("cost", 35.0, -1, Double.MAX_VALUE);
		EARTH_HEAVY_RADIUS = b.comment("Radius for 'all non-team players'. Decided: 50.").defineInRange("radius", 50.0, 0.0, 512.0);
		EARTH_HEAVY_HEIGHT = b.comment("Document: launched 20 blocks up.").defineInRange("launch_height", 20.0, 0.0, 400.0);
		EARTH_HEAVY_DAMAGE = b.comment("Document: 40 damage points.").defineInRange("damage", 40.0, 0.0, Double.MAX_VALUE);
		EARTH_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 30 s.").defineInRange("cooldown_ticks", 600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		EARTH_REINFORCE_COST = b.comment("Document: 10 Qi.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		EARTH_REINFORCE_DURATION = b.comment("Balance pass 2026-10-07 (user): Reinforce Body lasts 60 s (all aspects except Protective, Lightning, Physical).").defineInRange("duration_ticks", 1200, -1, Integer.MAX_VALUE);
		EARTH_REINFORCE_KB_RESIST = b.comment("Document: 50% knockback resistance.").defineInRange("knockback_resistance", 0.5, 0.0, 1.0);
		EARTH_REINFORCE_LOCK_TICKS = b.comment("Document: lock user input 3 s.").defineInRange("input_lock_ticks", 60, 0, Integer.MAX_VALUE);
		EARTH_REINFORCE_STONE_RESISTANCE = b.comment("While standing on stone-type blocks. Document: Resistance XV.").defineInRange("stone_resistance_level", 15, 0, 255);
		EARTH_REINFORCE_COOLDOWN = b.comment("Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		EARTH_PB1_COST = b.comment("Document: 15 Qi.").defineInRange("cost", 15.0, -1, Double.MAX_VALUE);
		EARTH_PB1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 30 s (was 15 s).").defineInRange("cooldown_ticks", 600, 0, Integer.MAX_VALUE);
		EARTH_PB1_DAMAGE = b.comment("Document: 30 damage points.").defineInRange("damage", 30.0, 0.0, Double.MAX_VALUE);
		EARTH_PB1_KNOCKBACK = b.comment("Document: Knockback IV.").defineInRange("knockback_level", 4, 0, 255);
		EARTH_PB1_STUN_TICKS = b.comment("Stun if the target touches stone. Document: 2 s.").defineInRange("stone_stun_ticks", 40, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		EARTH_PB2_COST = b.comment("Document: 40 Qi.").defineInRange("cost", 40.0, -1, Double.MAX_VALUE);
		EARTH_PB2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 60 s.").defineInRange("cooldown_ticks", 1200, 0, Integer.MAX_VALUE);
		EARTH_PB2_RADIUS = b.comment("Targets (non-allied) and grass-to-stone area. Decided: 20.").defineInRange("radius", 20.0, 0.0, 128.0);
		EARTH_PB2_SLOWNESS = b.comment("Document: Slowness III.").defineInRange("slowness_level", 3, 0, 255);
		EARTH_PB2_LAUNCH_HEIGHT = b.comment("Entities on stone are launched this high. Document: 50.").defineInRange("launch_height", 50.0, 0.0, 400.0);
		b.pop();

		b.push("ability1");
		EARTH_ABILITY1_COST = b.comment("Balance pass 2026-10-07 (user): 65 Qi (was 25).").defineInRange("cost", 65.0, -1, Double.MAX_VALUE);
		EARTH_ABILITY1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 15 s (was 2 min).").defineInRange("cooldown_ticks", 300, 0, Integer.MAX_VALUE);
		EARTH_ABILITY1_RADIUS = b.comment("'10-block area around user' read as 10 blocks across (radius 5). Confirm.").defineInRange("radius", 5, 1, 32);
		EARTH_ABILITY1_RISE = b.comment("Document: rises 5 blocks.").defineInRange("rise", 5, 1, 64);
		EARTH_ABILITY1_RESISTANCE = b.comment("Document: user Resistance XV.").defineInRange("resistance_level", 15, 0, 255);
		EARTH_ABILITY1_ALLY_RANGE = b.comment("Document: allies within 30 blocks.").defineInRange("ally_range", 30.0, 0.0, 256.0);
		EARTH_ABILITY1_ALLY_REGEN = b.comment("Document: Regeneration III.").defineInRange("ally_regeneration_level", 3, 0, 255);
		b.pop();

		b.push("ultimate");
		EARTH_ULT_COST = b.comment("Balance pass 2026-10-07 (user): 100 Qi (was 60).").defineInRange("cost", 100.0, -1, Double.MAX_VALUE);
		EARTH_ULT_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s.").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		EARTH_ULT_RADIUS = b.comment("Radius for 'all non-allied players'. Decided: 50.").defineInRange("radius", 50.0, 0.0, 512.0);
		EARTH_ULT_RAISE_MIN = b.comment("Ground raised under each target, min blocks. Document: 15.").defineInRange("raise_min", 15, 1, 128);
		EARTH_ULT_RAISE_MAX = b.comment("Max blocks. Document: 40.").defineInRange("raise_max", 40, 1, 128);
		EARTH_ULT_REGEN_TICKS = b.comment("User Regeneration 255. Document: 3 s.").defineInRange("regeneration_ticks", 60, 0, Integer.MAX_VALUE);
		EARTH_ULT_RESISTANCE = b.comment("Document: Resistance XXV.").defineInRange("resistance_level", 25, 0, 255);
		EARTH_ULT_RESISTANCE_TICKS = b.comment("Document: 10 s.").defineInRange("resistance_ticks", 200, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("LIGHTNING (internal id ELECTRIC). -1 = not finalized.").push("lightning");
		ELEC_LIGHTNING_DAMAGE = b.comment("'Normal lightning damage' (vanilla bolt) in damage points.").defineInRange("normal_lightning_damage", 5.0, 0.0, 1000.0);
		b.push("passive");
		ELEC_PASSIVE_QI_PER_3S = b.comment("Storm Conduit upkeep. Document: 3 Qi every 3 s.").defineInRange("qi_per_3_seconds", 3.0, 0.0, Double.MAX_VALUE);
		ELEC_PASSIVE_SPEED = b.comment("Document: Speed X.").defineInRange("speed_level", 10, 0, 255);
		ELEC_PASSIVE_REGEN = b.comment("Document: Regeneration I.").defineInRange("regeneration_level", 1, 0, 255);
		ELEC_PASSIVE_OUTPUT = b.comment("Document: 2x total damage output.").defineInRange("output_multiplier", 2.0, 0.0, Double.MAX_VALUE);
		ELEC_PASSIVE_CREEPER_RANGE = b.comment("'Near a charged creeper'. Decided: 16 blocks.").defineInRange("charged_creeper_range", 16.0, 0.0, 128.0);
		ELEC_SAVE_COST = b.comment("Lethal save cost per instance. Document: 25 Qi. Only while the passive is active (decided), not during Nuclear Winter.").defineInRange("lethal_save_cost", 25.0, 0.0, Double.MAX_VALUE);
		ELEC_SAVE_TICKS = b.comment("Lethal save Regeneration 255 + Thorns II duration. Document: 3 s.").defineInRange("lethal_save_ticks", 60, 0, Integer.MAX_VALUE);
		ELEC_SAVE_THORNS = b.comment("Document: Thorns II.").defineInRange("lethal_save_thorns", 2, 0, 255);
		b.pop();

		b.push("attack1");
		ELEC_ATTACK1_COST = b.comment("Document: 5 Qi.").defineInRange("cost", 5.0, -1, Double.MAX_VALUE);
		ELEC_ATTACK1_PASSIVE_DISCOUNT = b.comment("Qi off while the passive is active. Document: 2.").defineInRange("passive_discount", 2.0, 0.0, Double.MAX_VALUE);
		ELEC_ATTACK1_DURATION = b.comment("Melee hits gain fire + stun for this long. Document: 30 s.").defineInRange("duration_ticks", 600, 0, Integer.MAX_VALUE);
		ELEC_ATTACK1_FIRE_TICKS = b.comment("Fire per hit. Decided: 3 s.").defineInRange("fire_ticks", 60, 0, Integer.MAX_VALUE);
		ELEC_ATTACK1_STUN_TICKS = b.comment("Stun per hit. Decided: 0.5 s.").defineInRange("stun_ticks", 10, 0, Integer.MAX_VALUE);
		ELEC_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		ELEC_HEAVY_COST = b.comment("Balance pass 2026-10-07 (user): 30 Qi normal (was 20); 50% off while the passive is active.").defineInRange("cost", 30.0, -1, Double.MAX_VALUE);
		ELEC_HEAVY_MULTIPLIER = b.comment("Triple total damage output on the next hit (decided: next hit).").defineInRange("next_hit_multiplier", 3.0, 0.0, Double.MAX_VALUE);
		ELEC_HEAVY_STRIKE_MULTIPLIER = b.comment("Lightning on nearest target: x normal lightning damage. Document: 3.").defineInRange("strike_multiplier", 3.0, 0.0, Double.MAX_VALUE);
		ELEC_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 30 s.").defineInRange("cooldown_ticks", 600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		ELEC_REINFORCE_COST = b.comment("Document: 15 Qi; 50% off while the passive is active.").defineInRange("cost", 15.0, -1, Double.MAX_VALUE);
		ELEC_REINFORCE_DURATION = b.comment("Decided: 10 s.").defineInRange("duration_ticks", 200, -1, Integer.MAX_VALUE);
		ELEC_REINFORCE_THORNS = b.comment("Document: Thorns X (not if wearing Thorns armor).").defineInRange("thorns_level", 10, 0, 255);
		ELEC_REINFORCE_THORNS_TICKS = b.comment("Document: 2 min.").defineInRange("thorns_ticks", 2400, 0, Integer.MAX_VALUE);
		ELEC_REINFORCE_COOLDOWN = b.comment("Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		ELEC_PB1_COST = b.comment("Document: 10 Qi; 50% off while the passive is active.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		ELEC_PB1_DURATION = b.comment("Document: 5 min.").defineInRange("duration_ticks", 6000, 0, Integer.MAX_VALUE);
		ELEC_PB1_SPEED = b.comment("Document: Speed V.").defineInRange("speed_level", 5, 0, 255);
		ELEC_PB1_HASTE = b.comment("'Attack Speed V' implemented as Haste V.").defineInRange("haste_level", 5, 0, 255);
		ELEC_PB1_RESISTANCE = b.comment("Document: Resistance IV.").defineInRange("resistance_level", 4, 0, 255);
		ELEC_PB1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		ELEC_PB2_COST = b.comment("Balance pass 2026-10-07 (user): 40 Qi normal (was 30); 20% off while the passive is active.").defineInRange("cost", 40.0, -1, Double.MAX_VALUE);
		ELEC_PB2_PASSIVE_DISCOUNT = b.comment("Fraction off while the passive is active. Document: 20%.").defineInRange("passive_discount", 0.2, 0.0, 1.0);
		ELEC_PB2_STRIKE_MULTIPLIER = b.comment("Lightning on nearest target: x normal lightning damage. Document: double.").defineInRange("strike_multiplier", 2.0, 0.0, Double.MAX_VALUE);
		ELEC_PB2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 30 s.").defineInRange("cooldown_ticks", 600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability1");
		ELEC_ABILITY1_COST = b.comment("Balance pass 2026-10-07 (user): 50 Qi (was 20).").defineInRange("cost", 50.0, -1, Double.MAX_VALUE);
		ELEC_ABILITY1_DURATION = b.comment("Document TBD -> decided: 2 min.").defineInRange("duration_ticks", 2400, -1, Integer.MAX_VALUE);
		ELEC_ABILITY1_INTERVAL = b.comment("Document: every 3 s.").defineInRange("strike_interval_ticks", 60, 1, Integer.MAX_VALUE);
		ELEC_ABILITY1_RADIUS = b.comment("Radius for 'all non-allied players'. Placeholder 50 (same as other aspects); confirm.").defineInRange("radius", 50.0, 0.0, 512.0);
		ELEC_ABILITY1_REGEN = b.comment("Document: Regeneration V.").defineInRange("regeneration_level", 5, 0, 255);
		ELEC_ABILITY1_SPEED = b.comment("Document: Speed III.").defineInRange("speed_level", 3, 0, 255);
		ELEC_ABILITY1_OUTPUT = b.comment("Final total output while active, not stacking with passive / Nuclear Winter. Document: +50%.").defineInRange("output_multiplier", 1.5, 0.0, Double.MAX_VALUE);
		ELEC_ABILITY1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s.").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ultimate");
		ELEC_ULT_COST = b.comment("Nuclear Winter. Document: 70 Qi. With less, it drains all Qi and breaks your Core (decided).").defineInRange("cost", 70.0, -1, Double.MAX_VALUE);
		ELEC_ULT_DURATION = b.comment("Storm length. Decided: 5 min.").defineInRange("duration_ticks", 6000, 1, Integer.MAX_VALUE);
		ELEC_ULT_OUTPUT = b.comment("Total output, stacks with passive 2x. Document: 1.5x.").defineInRange("output_multiplier", 1.5, 0.0, Double.MAX_VALUE);
		ELEC_ULT_BROKEN_MELEE = b.comment("Broken Core: total melee output. Document: 5x.").defineInRange("broken_melee_multiplier", 5.0, 0.0, Double.MAX_VALUE);
		ELEC_ULT_BROKEN_QI = b.comment("Broken Core: Qi locked at exactly this until the storm ends. Document: 5000.").defineInRange("broken_qi", 5000.0, 0.0, Double.MAX_VALUE);
		ELEC_ULT_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("FIRE. -1 = not finalized.").push("fire");
		b.push("passive");
		FIRE_PASSIVE_QI_PER_SECOND = b.comment("Immunity to heat/fire/soul fire/lava is always on; costs this per second only while in fire or lava. Document: 1 Qi/s.").defineInRange("qi_per_second_in_fire", 1.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.push("attack1");
		FIRE_ATTACK1_COST = b.comment("Decided: 10 Qi for both fist and sword.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		FIRE_ATTACK1_FIST_DAMAGE = b.comment("Next hit with anything but a sword. Document: 30 hearts = 60 damage points.").defineInRange("fist_damage", 60.0, 0.0, Double.MAX_VALUE);
		FIRE_ATTACK1_SWORD_REDUCTION = b.comment("Sword hit = fist damage reduced by this fraction. Document: 60% (60 -> 24).").defineInRange("sword_reduction", 0.6, 0.0, 1.0);
		FIRE_ATTACK1_KNOCKBACK = b.comment("Document: Knockback III.").defineInRange("knockback_level", 3, 0, 255);
		FIRE_ATTACK1_FIRE_TICKS = b.comment("'Set aflame' length. Not in document; placeholder 5 s (same as Flame Burst).").defineInRange("fire_ticks", 100, 0, Integer.MAX_VALUE);
		FIRE_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		FIRE_HEAVY_COST = b.comment("Yeet. Document: 30 Qi.").defineInRange("cost", 30.0, -1, Double.MAX_VALUE);
		FIRE_HEAVY_DAMAGE = b.comment("On a confirmed hit. Document: 60 hearts = 120 damage points.").defineInRange("damage", 120.0, 0.0, Double.MAX_VALUE);
		FIRE_HEAVY_KNOCKBACK = b.comment("Document: Knockback III.").defineInRange("knockback_level", 3, 0, 255);
		FIRE_HEAVY_FIRE_TICKS = b.comment("'Fire damage' = set aflame for this long. Placeholder 5 s.").defineInRange("fire_ticks", 100, 0, Integer.MAX_VALUE);
		FIRE_HEAVY_SPEED = b.comment("Flight speed toward the target, blocks/tick. Placeholder (same as Physical Yeet).").defineInRange("speed", 2.5, 0.1, 10.0);
		FIRE_HEAVY_MAX_CHASE_TICKS = b.comment("Flight time before it counts as a miss. Placeholder 3 s (same as Physical Yeet).").defineInRange("max_chase_ticks", 60, 1, Integer.MAX_VALUE);
		FIRE_HEAVY_CONTACT_RANGE = b.comment("Distance that counts as a confirmed hit. Placeholder (same as Physical Yeet).").defineInRange("contact_range", 1.5, 0.1, 10.0);
		FIRE_HEAVY_SEARCH_HEIGHT = b.comment("No player in sight: you're thrown up this many blocks to look for one. Placeholder.").defineInRange("search_height", 20.0, 0.0, 400.0);
		FIRE_HEAVY_MAX_SEARCH_TICKS = b.comment("Safety cap on the search (it normally ends when you land).").defineInRange("max_search_ticks", 200, 1, Integer.MAX_VALUE);
		FIRE_HEAVY_MISS_REFUND = b.comment("Nobody found while airborne: this fraction of the Qi paid is refunded (decided: 50%). Fall damage is negated.").defineInRange("no_target_refund", 0.5, 0.0, 1.0);
		FIRE_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		FIRE_REINFORCE_COST = b.comment("Document: 10 Qi.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		FIRE_REINFORCE_DURATION = b.comment("Balance pass 2026-10-07 (user): Reinforce Body lasts 60 s (all aspects except Protective, Lightning, Physical).").defineInRange("duration_ticks", 1200, -1, Integer.MAX_VALUE);
		FIRE_REINFORCE_COOLDOWN = b.comment("Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		FIRE_PB1_COST = b.comment("Flame Burst. Document: 20 Qi.").defineInRange("cost", 20.0, -1, Double.MAX_VALUE);
		FIRE_PB1_RADIUS = b.comment("Document: 8 blocks.").defineInRange("radius", 8.0, 0.0, 128.0);
		FIRE_PB1_DAMAGE = b.comment("Document: 10 hearts = 20 damage points.").defineInRange("damage", 20.0, 0.0, Double.MAX_VALUE);
		FIRE_PB1_FIRE_TICKS = b.comment("Document: set aflame 5 s.").defineInRange("fire_ticks", 100, 0, Integer.MAX_VALUE);
		FIRE_PB1_COOLDOWN = b.comment("Document: 20 s.").defineInRange("cooldown_ticks", 400, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		FIRE_PB2_COST = b.comment("Document: 50 Qi.").defineInRange("cost", 50.0, -1, Double.MAX_VALUE);
		FIRE_PB2_RADIUS = b.comment("Document: 50 blocks. Terrain changes are permanent; allies are hit too (decided).").defineInRange("radius", 50.0, 0.0, 128.0);
		FIRE_PB2_DAMAGE = b.comment("Document: 50 hearts = 100 damage points.").defineInRange("damage", 100.0, 0.0, Double.MAX_VALUE);
		FIRE_PB2_FIRE_TICKS = b.comment("'Set aflame' length. Not in document; placeholder 5 s.").defineInRange("fire_ticks", 100, 0, Integer.MAX_VALUE);
		FIRE_PB2_BLOCKS_PER_TICK = b.comment("Block positions checked per tick while converting the area (lower = less lag, slower spread).").defineInRange("blocks_per_tick", 32768, 1024, 1048576);
		FIRE_PB2_COOLDOWN = b.comment("Document: 30 s.").defineInRange("cooldown_ticks", 600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability1");
		FIRE_ABILITY1_QI_PER_SECOND = b.comment("Document: 5 Qi/s.").defineInRange("qi_per_second", 5.0, -1, Double.MAX_VALUE);
		FIRE_ABILITY1_QI_PER_SECOND_BURNING = b.comment("While you're on fire or in flames. Document: 3.5 Qi/s.").defineInRange("qi_per_second_burning", 3.5, 0.0, Double.MAX_VALUE);
		FIRE_ABILITY1_HEAL_PER_SECOND = b.comment("Healing rate. Not in document; placeholder 4 health (2 hearts) per second.").defineInRange("heal_per_second", 4.0, 0.0, Double.MAX_VALUE);
		FIRE_ABILITY1_MAX_TICKS = b.comment("Safety cap; it normally stops when you're at full health with no bad effects (decided).").defineInRange("max_duration_ticks", 6000, 1, Integer.MAX_VALUE);
		FIRE_ABILITY1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability2");
		FIRE_ABILITY2_COST = b.comment("Emergency Leave. Document: 20 Qi.").defineInRange("cost", 20.0, -1, Double.MAX_VALUE);
		FIRE_ABILITY2_RADIUS = b.comment("Search radius for the nearest campfire, lava or fire. Not in document; placeholder 48.").defineInRange("search_radius", 48, 1, 128);
		FIRE_ABILITY2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("LIGHT. -1 = not finalized.").push("light");
		b.push("passive");
		LIGHT_DAY_START = b.comment("Daytime window start ('sunrise, midday, sunset' = all of daytime, decided). Wraps past midnight.").defineInRange("day_start", 22200, 0, 23999);
		LIGHT_DAY_END = b.comment("Daytime window end (exactly when Dark's night isn't).").defineInRange("day_end", 13800, 0, 23999);
		LIGHT_PASSIVE_QI_PER_30MIN = b.comment("Document: 15 Qi per 30 min, only during the window.").defineInRange("qi_per_30_minutes", 15.0, 0.0, Double.MAX_VALUE);
		LIGHT_PASSIVE_RESISTANCE = b.comment("Document: Resistance I.").defineInRange("resistance_level", 1, 0, 255);
		LIGHT_PASSIVE_SPEED = b.comment("Document: Speed II.").defineInRange("speed_level", 2, 0, 255);
		b.pop();

		b.push("attack1");
		LIGHT_ATTACK1_COST = b.comment("Document: 10 Qi.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		LIGHT_ATTACK1_STRENGTH = b.comment("Document: Strength V.").defineInRange("strength_level", 5, 0, 255);
		LIGHT_ATTACK1_TICKS = b.comment("Not in document; placeholder 30 s (same default as other aspects).").defineInRange("duration_ticks", 600, 0, Integer.MAX_VALUE);
		LIGHT_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		LIGHT_HEAVY_COST = b.comment("Document: 20 Qi.").defineInRange("cost", 20.0, -1, Double.MAX_VALUE);
		LIGHT_HEAVY_TICKS = b.comment("Aspect labels over players in line of sight (their aspect at that moment). Decided: 30 s.").defineInRange("reveal_ticks", 600, 0, Integer.MAX_VALUE);
		LIGHT_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		LIGHT_REINFORCE_COST = b.comment("Document: 15 Qi.").defineInRange("cost", 15.0, -1, Double.MAX_VALUE);
		LIGHT_REINFORCE_DURATION = b.comment("Balance pass 2026-10-07 (user): Reinforce Body lasts 60 s (all aspects except Protective, Lightning, Physical).").defineInRange("duration_ticks", 1200, -1, Integer.MAX_VALUE);
		LIGHT_REINFORCE_REGEN_TICKS = b.comment("Document: Regeneration 255 for 30 s.").defineInRange("regeneration_ticks", 600, 0, Integer.MAX_VALUE);
		LIGHT_REINFORCE_FLIGHT_TICKS = b.comment("Document: Creative Flight for 2 min (Feather Falling lasts as long).").defineInRange("flight_ticks", 2400, 0, Integer.MAX_VALUE);
		LIGHT_REINFORCE_FEATHER_FALLING = b.comment("Feather Falling level (enchantment-style, 12% less fall damage per level). Not in document; placeholder I.").defineInRange("feather_falling_level", 1, 0, 8);
		LIGHT_REINFORCE_ASCEND_SPEED = b.comment("Climb speed to the sky limit, blocks/tick. Placeholder (same as Physical Sky High).").defineInRange("ascend_speed", 3.0, 0.1, 10.0);
		LIGHT_REINFORCE_COOLDOWN = b.comment("Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		LIGHT_PB1_COST = b.comment("Sorry But Please Go Away. Document: 25 Qi.").defineInRange("cost", 25.0, -1, Double.MAX_VALUE);
		LIGHT_PB1_RADIUS = b.comment("Decided: unallied players within 50.").defineInRange("radius", 50.0, 0.0, 256.0);
		LIGHT_PB1_DISTANCE = b.comment("Decided: pushed about 100 blocks away.").defineInRange("push_distance", 100.0, 0.0, 1000.0);
		LIGHT_PB1_SPEED = b.comment("Push speed, blocks/tick (the client caps motion near 3.9).").defineInRange("push_speed", 3.5, 0.1, 3.9);
		LIGHT_PB1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		LIGHT_PB2_COST = b.comment("May the Light Bless Your Soul. Document: 35 Qi.").defineInRange("cost", 35.0, -1, Double.MAX_VALUE);
		LIGHT_PB2_RADIUS = b.comment("Non-allied Dark-active players in range are nullified unless Reinforced. Placeholder 50.").defineInRange("radius", 50.0, 0.0, 256.0);
		LIGHT_PB2_NULLIFY_TICKS = b.comment("Nullify duration. Not in document; placeholder 30 s.").defineInRange("nullify_ticks", 600, 0, Integer.MAX_VALUE);
		LIGHT_PB2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability1");
		LIGHT_ABILITY1_COST = b.comment("I Am Good. Document: 40 Qi.").defineInRange("cost", 40.0, -1, Double.MAX_VALUE);
		LIGHT_ABILITY1_MIN_DISTANCE = b.comment("Document: 30-50 blocks away.").defineInRange("min_distance", 30.0, 0.0, 256.0);
		LIGHT_ABILITY1_MAX_DISTANCE = b.comment("Document: 30-50 blocks away.").defineInRange("max_distance", 50.0, 0.0, 256.0);
		LIGHT_ABILITY1_MIN_LIGHT = b.comment("'10/10 light level'. Decided: light 10 or more.").defineInRange("min_light", 10, 0, 15);
		LIGHT_ABILITY1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ultimate");
		LIGHT_ULT_COST = b.comment("Wakey Wakey. Document: 80 Qi. Forces daytime in every dimension with a day cycle.").defineInRange("cost", 80.0, -1, Double.MAX_VALUE);
		LIGHT_ULT_RADIUS = b.comment("Radius for 'all Light users' / 'Dark users'. Placeholder 50 (same as Dark's ultimate).").defineInRange("radius", 50.0, 0.0, 100000.0);
		LIGHT_ULT_EFFECT_TICKS = b.comment("Effect duration. Not in document; placeholder 30 s.").defineInRange("effect_ticks", 600, 0, Integer.MAX_VALUE);
		LIGHT_ULT_RESISTANCE = b.comment("Light users, enemies included. Document: Resistance V.").defineInRange("resistance_level", 5, 0, 255);
		LIGHT_ULT_HASTE = b.comment("'Attack Speed V' implemented as Haste V (as Lightning).").defineInRange("haste_level", 5, 0, 255);
		LIGHT_ULT_SPEED = b.comment("Document: Speed V.").defineInRange("speed_level", 5, 0, 255);
		LIGHT_ULT_WEAKNESS = b.comment("Dark users. Document: Weakness II.").defineInRange("weakness_level", 2, 0, 255);
		LIGHT_ULT_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("PURE (user's Pure list, 2026-10-06). Abilities unlock by Pure level and stay hidden until then. -1 = not finalized.").push("pure");
		b.push("unlock_levels");
		PURE_LEVEL_HEAVY = b.comment("BURNN. User: level 1.").defineInRange("heavy", 1, 0, 100);
		PURE_LEVEL_REINFORCE = b.comment("Protect Ye. User: level 2.").defineInRange("reinforce", 2, 0, 100);
		PURE_LEVEL_POWERBLAST = b.comment("Thunder and I Taketh. User: level 3.").defineInRange("powerblasts", 3, 0, 100);
		PURE_LEVEL_ABILITIES = b.comment("My Anime and My Turf. User: level 4.").defineInRange("abilities", 4, 0, 100);
		b.pop();

		b.push("passive");
		PURE_PASSIVE_QI_PER_MIN = b.comment("Constant cost at every level. User: 0.5 Qi/min.").defineInRange("qi_per_minute", 0.5, 0.0, Double.MAX_VALUE);
		PURE_COST_MULTIPLIER = b.comment("Level 2+: Pure ability costs multiplied by this. User: 0.5.").defineInRange("level2_ability_cost_multiplier", 0.5, 0.0, 1.0);
		PURE_L3_GAIN_PER_MIN = b.comment("Level 3: Qi added. User: 10 Qi every 2 min.").defineInRange("level3_qi_gain_per_minute", 5.0, 0.0, Double.MAX_VALUE);
		PURE_L4_GAIN_PER_MIN = b.comment("Level 4: Qi added (replaces level 3's). User: 30 Qi/min.").defineInRange("level4_qi_gain_per_minute", 30.0, 0.0, Double.MAX_VALUE);
		PURE_L4_REDUCTION = b.comment("Level 4: incoming damage reduction. User: 50%.").defineInRange("level4_damage_reduction", 0.5, 0.0, 1.0);
		PURE_L5_GAIN_PER_MIN = b.comment("Level 5: Qi added (replaces level 4's). User: 10 Qi/min.").defineInRange("level5_qi_gain_per_minute", 10.0, 0.0, Double.MAX_VALUE);
		PURE_L5_KEEPS_REDUCTION = b.comment("Does level 5 keep level 4's damage reduction? Not stated; placeholder: yes.").define("level5_keeps_damage_reduction", true);
		PURE_L5_ABSORB_FRACTION = b.comment("Level 5: when hit by a Qi-enhanced attack, this share of the Qi the attacker spent is stored. User: 50%.").defineInRange("level5_absorb_fraction", 0.5, 0.0, 1.0);
		PURE_L5_DAMAGE_PER_QI = b.comment("Stored Qi -> bonus damage on your next Qi-enhanced attack. Not stated; placeholder 1 damage point per Qi.").defineInRange("level5_damage_per_stored_qi", 1.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.push("attack1");
		PURE_ATTACK_COST = b.comment("EEEEEEEE. User: 10 Qi, level 0.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		PURE_ATTACK_DAMAGE = b.comment("Next hit deals this. User: 50.").defineInRange("damage", 50.0, 0.0, Double.MAX_VALUE);
		PURE_ATTACK_KNOCKBACK = b.comment("User: Knockback 34 on that hit.").defineInRange("knockback_level", 34, 0, 255);
		PURE_ATTACK_NAUSEA_TICKS = b.comment("User: Nausea 30 s.").defineInRange("nausea_ticks", 600, 0, Integer.MAX_VALUE);
		PURE_ATTACK_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		PURE_HEAVY_COST = b.comment("BURNN. User: 20 Qi.").defineInRange("cost", 20.0, -1, Double.MAX_VALUE);
		PURE_HEAVY_RADIUS = b.comment("Horizontal reach. User: '4 chunks wide' -> read as 64 across = 32 radius.").defineInRange("horizontal_radius", 32.0, 1.0, 512.0);
		PURE_HEAVY_VERTICAL = b.comment("User: within 30 blocks up or down.").defineInRange("vertical_range", 30.0, 0.0, 512.0);
		PURE_HEAVY_DAMAGE = b.comment("Your next hit on that player deals this. User: 60.").defineInRange("hit_damage", 60.0, 0.0, Double.MAX_VALUE);
		PURE_HEAVY_FIRE_RES_TICKS = b.comment("User: Fire Resistance 30 s.").defineInRange("fire_resistance_ticks", 600, 0, Integer.MAX_VALUE);
		PURE_HEAVY_HIT_WINDOW = b.comment("How long after teleporting the 60-damage hit is available. Not stated; placeholder 10 s.").defineInRange("hit_window_ticks", 200, 1, Integer.MAX_VALUE);
		PURE_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		PURE_REINFORCE_COST = b.comment("Protect Ye. User: 30 Qi.").defineInRange("cost", 30.0, -1, Double.MAX_VALUE);
		PURE_REINFORCE_DURATION = b.comment("Balance pass 2026-10-07 (user): Reinforce Body lasts 60 s (all aspects except Protective, Lightning, Physical).").defineInRange("duration_ticks", 1200, -1, Integer.MAX_VALUE);
		PURE_REINFORCE_RADIUS = b.comment("'Nearby' allies. Not stated; placeholder 50.").defineInRange("ally_radius", 50.0, 0.0, 512.0);
		PURE_REINFORCE_REGEN = b.comment("Nullified allies: nullify lifted + Regeneration XV. User: 15.").defineInRange("regeneration_level", 15, 0, 255);
		PURE_REINFORCE_STRENGTH = b.comment("Allies except Physical-active ones. Level not stated; placeholder I.").defineInRange("strength_level", 1, 0, 255);
		PURE_REINFORCE_EFFECT_TICKS = b.comment("Regeneration / Strength duration. Not stated; placeholder 30 s.").defineInRange("effect_ticks", 600, 0, Integer.MAX_VALUE);
		PURE_REINFORCE_COOLDOWN = b.comment("Not stated; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		PURE_PB1_COST = b.comment("Thunder. User: 30 Qi.").defineInRange("cost", 30.0, -1, Double.MAX_VALUE);
		PURE_PB1_OUTPUT = b.comment("Your total damage output. User: tripled.").defineInRange("output_multiplier", 3.0, 0.0, Double.MAX_VALUE);
		PURE_PB1_DURATION = b.comment("How long output stays tripled. Not stated; placeholder 30 s.").defineInRange("duration_ticks", 600, 0, Integer.MAX_VALUE);
		PURE_PB1_STRIKE_MULTIPLIER = b.comment("Nearest player struck for this x normal lightning damage (lightning.normal_lightning_damage). User: 3.").defineInRange("strike_multiplier", 3.0, 0.0, Double.MAX_VALUE);
		PURE_PB1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s.").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		PURE_PB2_COST = b.comment("I Taketh. User: 40 Qi. Next hit.").defineInRange("cost", 40.0, -1, Double.MAX_VALUE);
		PURE_PB2_DAMAGE = b.comment("User: 40.").defineInRange("damage", 40.0, 0.0, Double.MAX_VALUE);
		PURE_PB2_BASE_SECONDS = b.comment("Qi lock = clamp(base + highest level x per_level, min, max) seconds. User: 30 + level x 10, 40..100.").defineInRange("lock_base_seconds", 30, 0, 100000);
		PURE_PB2_PER_LEVEL_SECONDS = b.defineInRange("lock_seconds_per_level", 10, 0, 100000);
		PURE_PB2_MIN_SECONDS = b.defineInRange("lock_min_seconds", 40, 0, 100000);
		PURE_PB2_MAX_SECONDS = b.defineInRange("lock_max_seconds", 100, 0, 100000);
		PURE_PB2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s.").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability1");
		PURE_A1_COST = b.comment("Balance pass 2026-10-07 (user): 100 Qi (was 50).").defineInRange("cost", 100.0, -1, Double.MAX_VALUE);
		PURE_A1_RANGE = b.comment("User: 300 blocks. Pierces players, stops at blocks (default chosen).").defineInRange("range", 300.0, 1.0, 1000.0);
		PURE_A1_DAMAGE = b.comment("User: 200 (Reinforce halves it as usual).").defineInRange("damage", 200.0, 0.0, Double.MAX_VALUE);
		PURE_A1_KNOCKBACK = b.comment("User: Knockback 15.").defineInRange("knockback_level", 15, 0, 255);
		PURE_A1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 200 s.").defineInRange("cooldown_ticks", 4000, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability2");
		PURE_A2_COST = b.comment("Balance pass 2026-10-07 (user): 380 Qi (was 100).").defineInRange("cost", 380.0, -1, Double.MAX_VALUE);
		PURE_A2_RADIUS = b.comment("Everything non-allied in range dies. Not stated; default chosen 50.").defineInRange("radius", 50.0, 0.0, 1024.0);
		PURE_A2_BREAK_CHANCE = b.comment("Chance your own Core breaks. User: 50/300.").defineInRange("self_break_chance", 50.0 / 300.0, 0.0, 1.0);
		PURE_A2_MAX_RISKY_CORE_LEVEL = b.comment("The self-break risk applies while your core level is at most this ('not past level 2').").defineInRange("risky_up_to_core_level", 2, 0, 1000);
		PURE_A2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 380 s.").defineInRange("cooldown_ticks", 7600, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("PROTECTIVE. -1 = not finalized.").push("protective");
		b.push("passive");
		PROT_PASSIVE_QI_PER_SECOND = b.comment("Document: 5 Qi/s (3 protection + 2 damage-to-Qi).").defineInRange("qi_per_second", 5.0, 0.0, Double.MAX_VALUE);
		PROT_PASSIVE_RESISTANCE = b.comment("Document: Resistance X.").defineInRange("resistance_level", 10, 0, 255);
		PROT_PASSIVE_REGEN = b.comment("Document: Regeneration I.").defineInRange("regeneration_level", 1, 0, 255);
		PROT_PASSIVE_MAX_HEALTH = b.comment("Balance pass 2026-10-07 (user): max health 200 (was 400).").defineInRange("max_health", 200.0, 1.0, 1024.0);
		PROT_PASSIVE_QI_PER_DAMAGE = b.comment("Incoming damage -> Qi, counted before reductions. Decided: 1 damage = 1 Qi.").defineInRange("qi_per_damage", 1.0, 0.0, Double.MAX_VALUE);
		b.pop();

		b.push("attack1");
		PROT_ATTACK1_COST = b.comment("Document: 3 Qi. Next hit.").defineInRange("cost", 3.0, -1, Double.MAX_VALUE);
		PROT_ATTACK1_KNOCKBACK = b.comment("Document: Knockback X.").defineInRange("knockback_level", 10, 0, 255);
		PROT_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		PROT_HEAVY_COST = b.comment("Document: 5 Qi.").defineInRange("cost", 5.0, -1, Double.MAX_VALUE);
		PROT_HEAVY_WINDOW_TICKS = b.comment("Heals the damage taken in this window. Decided: last 10 s.").defineInRange("window_ticks", 200, 1, Integer.MAX_VALUE);
		PROT_HEAVY_HEAL_FRACTION = b.comment("Share of that damage healed. Decided: all of it.").defineInRange("heal_fraction", 1.0, 0.0, 10.0);
		PROT_HEAVY_THORNS = b.comment("Document: Thorns III.").defineInRange("thorns_level", 3, 0, 255);
		PROT_HEAVY_THORNS_TICKS = b.comment("Decided: 30 s.").defineInRange("thorns_ticks", 600, 0, Integer.MAX_VALUE);
		PROT_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		PROT_REINFORCE_COST = b.comment("Document: 50 Qi.").defineInRange("cost", 50.0, -1, Double.MAX_VALUE);
		PROT_REINFORCE_DURATION = b.comment("Decided: 2 min (Reinforce and its Regeneration).").defineInRange("duration_ticks", 2400, -1, Integer.MAX_VALUE);
		PROT_REINFORCE_ALLY_RADIUS = b.comment("Document: allies within 50 (when you're at full health).").defineInRange("ally_radius", 50.0, 0.0, 512.0);
		PROT_REINFORCE_ALLY_REGEN = b.comment("Document: Regeneration X for allies not already Reinforced.").defineInRange("ally_regeneration_level", 10, 0, 255);
		PROT_REINFORCE_SELF_REGEN = b.comment("Not at full health: you alone get Regeneration 255.").defineInRange("self_regeneration_level", 255, 0, 255);
		PROT_REINFORCE_COOLDOWN = b.comment("Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		PROT_PB1_COST = b.comment("U SAID WHAT!!??. Document: 25 Qi.").defineInRange("cost", 25.0, -1, Double.MAX_VALUE);
		PROT_PB1_SPEED = b.comment("'Blistering speed', blocks/tick (client caps near 3.9). Placeholder.").defineInRange("speed", 3.5, 0.1, 3.9);
		PROT_PB1_MAX_FLIGHT_TICKS = b.comment("Flight time before it counts as a miss. Placeholder 3 s.").defineInRange("max_flight_ticks", 60, 1, Integer.MAX_VALUE);
		PROT_PB1_CONTACT_RANGE = b.comment("Placeholder (same as the Yeets).").defineInRange("contact_range", 1.5, 0.1, 10.0);
		PROT_PB1_DAMAGE = b.comment("Document: 255 damage points on contact.").defineInRange("damage", 255.0, 0.0, Double.MAX_VALUE);
		PROT_PB1_NULLIFY_TICKS = b.comment("Target survives -> their Physical aspect is nullified this long. Placeholder 30 s.").defineInRange("nullify_ticks", 600, 0, Integer.MAX_VALUE);
		PROT_PB1_USER_QI_LOSS = b.comment("Document: your Qi reduced by 50% (applied at launch).").defineInRange("user_qi_loss", 0.5, 0.0, 1.0);
		PROT_PB1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		PROT_PB2_COST = b.comment("Document: 60 Qi.").defineInRange("cost", 60.0, -1, Double.MAX_VALUE);
		PROT_PB2_TICKS = b.comment("Balance pass 2026-10-07 (user): 2.5 min (was 5 min) - enemies' and your own Qi.").defineInRange("nullify_ticks", 3000, 0, Integer.MAX_VALUE);
		PROT_PB2_RADIUS = b.comment("Enemy range. Not in document; placeholder 50.").defineInRange("radius", 50.0, 0.0, 512.0);
		PROT_PB2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability1");
		PROT_A1_COST = b.comment("HOOO RAAA. Document: 50 Qi.").defineInRange("cost", 50.0, -1, Double.MAX_VALUE);
		PROT_A1_COST_PER_ALLY = b.comment("Document: +5 Qi per allied player in radius (added to the cast cost).").defineInRange("cost_per_ally", 5.0, 0.0, Double.MAX_VALUE);
		PROT_A1_RADIUS = b.comment("Document: 40 blocks.").defineInRange("radius", 40.0, 0.0, 512.0);
		PROT_A1_CUT = b.comment("Allies' passive Qi-cost reduction (not you). Decided: 50%.").defineInRange("passive_cost_cut", 0.5, 0.0, 1.0);
		PROT_A1_DURATION = b.comment("Document: 3 min.").defineInRange("duration_ticks", 3600, 1, Integer.MAX_VALUE);
		PROT_A1_COOLDOWN = b.comment("Document: 20 min.").defineInRange("cooldown_ticks", 24000, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability2");
		PROT_A2_COST = b.comment("Echo of Protection. Document: 100 Qi.").defineInRange("cost", 100.0, -1, Double.MAX_VALUE);
		PROT_A2_RADIUS = b.comment("Unallied players in range. Decided: 50.").defineInRange("radius", 50.0, 0.0, 512.0);
		PROT_A2_DISTANCE = b.comment("Document: knocked back 1000 blocks (decided: a real launch).").defineInRange("push_distance", 1000.0, 0.0, 10000.0);
		PROT_A2_SPEED = b.comment("Launch speed, blocks/tick (1000 blocks at 3.5 = about 14 s).").defineInRange("push_speed", 3.5, 0.1, 3.9);
		PROT_A2_DAMAGE = b.comment("Document: 50 damage points, bypasses Reinforce.").defineInRange("damage", 50.0, 0.0, Double.MAX_VALUE);
		PROT_A2_SLOWNESS = b.comment("Document: Slowness III (bypasses Reinforce).").defineInRange("slowness_level", 3, 0, 255);
		PROT_A2_SLOWNESS_TICKS = b.comment("Not in document; placeholder 30 s.").defineInRange("slowness_ticks", 600, 0, Integer.MAX_VALUE);
		PROT_A2_COOLDOWN = b.comment("Document: 30 min.").defineInRange("cooldown_ticks", 36000, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		b.comment("WATER. -1 = not finalized.").push("water");
		b.push("passive");
		WATER_PASSIVE_QI_PER_MIN = b.comment("Document: 2 Qi/min normally.").defineInRange("qi_per_minute", 2.0, 0.0, Double.MAX_VALUE);
		WATER_PASSIVE_QI_PER_MIN_WET = b.comment("Document: 1 Qi/min in water or rain.").defineInRange("qi_per_minute_wet", 1.0, 0.0, Double.MAX_VALUE);
		WATER_PASSIVE_SPEED = b.comment("Document: Speed I. (Also Night Vision + Water Breathing.) Off within 1 block of lava/fire and in the Nether/End.").defineInRange("speed_level", 1, 0, 255);
		WATER_PASSIVE_FEATHER_FALLING = b.comment("Document: Feather Falling I (12% less fall damage per level).").defineInRange("feather_falling_level", 1, 0, 8);
		WATER_PASSIVE_WET_REGEN = b.comment("In water/rain. Document: Regeneration I.").defineInRange("wet_regeneration_level", 1, 0, 255);
		WATER_PASSIVE_WET_RESISTANCE = b.comment("In water/rain. Document: Resistance I.").defineInRange("wet_resistance_level", 1, 0, 255);
		b.pop();

		b.push("attack1");
		WATER_ATTACK1_COST = b.comment("Document: 10 Qi.").defineInRange("cost", 10.0, -1, Double.MAX_VALUE);
		WATER_ATTACK1_OUTPUT = b.comment("Document: 2x total output.").defineInRange("output_multiplier", 2.0, 0.0, Double.MAX_VALUE);
		WATER_ATTACK1_TICKS = b.comment("Document: 30 s.").defineInRange("duration_ticks", 600, 0, Integer.MAX_VALUE);
		WATER_ATTACK1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("heavy");
		WATER_HEAVY_COST = b.comment("Document: 30 Qi.").defineInRange("cost", 30.0, -1, Double.MAX_VALUE);
		WATER_HEAVY_KNOCKBACK = b.comment("Decided: your next hit gets Knockback XXV, enchantment-style.").defineInRange("knockback_level", 25, 0, 255);
		WATER_HEAVY_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("reinforce");
		WATER_REINFORCE_COST = b.comment("Document: 20 Qi. Standard + immune to fall damage.").defineInRange("cost", 20.0, -1, Double.MAX_VALUE);
		WATER_REINFORCE_DURATION = b.comment("Balance pass 2026-10-07 (user): Reinforce Body lasts 60 s (all aspects except Protective, Lightning, Physical).").defineInRange("duration_ticks", 1200, -1, Integer.MAX_VALUE);
		WATER_REINFORCE_COOLDOWN = b.comment("Not in document; 0 = none.").defineInRange("cooldown_ticks", 0, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast1");
		WATER_PB1_COST = b.comment("Water Surge. Document: 30 Qi.").defineInRange("cost", 30.0, -1, Double.MAX_VALUE);
		WATER_PB1_DURATION = b.comment("Decided: 2 min (document said 1 min).").defineInRange("duration_ticks", 2400, 0, Integer.MAX_VALUE);
		WATER_PB1_SPEED = b.comment("Document: Speed III.").defineInRange("speed_level", 3, 0, 255);
		WATER_PB1_HASTE = b.comment("'Attack Speed II' implemented as Haste II (as elsewhere).").defineInRange("haste_level", 2, 0, 255);
		WATER_PB1_OUTPUT = b.comment("Document: 2x total output.").defineInRange("output_multiplier", 2.0, 0.0, Double.MAX_VALUE);
		WATER_PB1_FIRE_RESIST_LEVEL = b.comment("Fire-aspect Qi resistance level. Document: V.").defineInRange("fire_aspect_resistance_level", 5, 0, 255);
		WATER_PB1_FIRE_RESIST_PER_LEVEL = b.comment("Decided: 20% per level (V = immune to Fire-aspect powers).").defineInRange("fire_aspect_resistance_per_level", 0.2, 0.0, 1.0);
		WATER_PB1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("powerblast2");
		WATER_PB2_COST = b.comment("Domain Expansion. Document: 40 Qi.").defineInRange("cost", 40.0, -1, Double.MAX_VALUE);
		WATER_PB2_RADIUS = b.comment("Flood + Blindness radius. Default chosen: 20.").defineInRange("radius", 20.0, 1.0, 64.0);
		WATER_PB2_FLOOD_TICKS = b.comment("The flood drains after this. Default chosen: 30 s.").defineInRange("flood_ticks", 600, 1, Integer.MAX_VALUE);
		WATER_PB2_BLIND_TICKS = b.comment("Blindness on non-Water, non-allied Qi users. Placeholder 30 s.").defineInRange("blindness_ticks", 600, 0, Integer.MAX_VALUE);
		WATER_PB2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability1");
		WATER_A1_COST = b.comment("Say Hi to the Fishies. Document: 50 Qi.").defineInRange("cost", 50.0, -1, Double.MAX_VALUE);
		WATER_A1_SEARCH_RADIUS = b.comment("Nearest ocean search radius. Default chosen: 2000.").defineInRange("ocean_search_radius", 2000, 64, 10000);
		WATER_A1_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();

		b.push("ability2");
		WATER_A2_COST = b.comment("Did I Say You Can Be Here?. Document: 100 Qi. Unusable in the Nether/End.").defineInRange("cost", 100.0, -1, Double.MAX_VALUE);
		WATER_A2_OUTPUT = b.comment("Document: 5x total output.").defineInRange("output_multiplier", 5.0, 0.0, Double.MAX_VALUE);
		WATER_A2_OUTPUT_TICKS = b.comment("Not in document; placeholder 30 s.").defineInRange("output_ticks", 600, 0, Integer.MAX_VALUE);
		WATER_A2_EXTINGUISH_RADIUS = b.comment("Document: all fires in a 100-block area.").defineInRange("extinguish_radius", 100.0, 0.0, 256.0);
		WATER_A2_BLOCKS_PER_TICK = b.comment("Block positions checked per tick while extinguishing.").defineInRange("blocks_per_tick", 65536, 1024, 1048576);
		WATER_A2_NULLIFY_RADIUS = b.comment("Document: Fire-aspect users within 50 are nullified.").defineInRange("nullify_radius", 50.0, 0.0, 512.0);
		WATER_A2_NULLIFY_TICKS = b.comment("Not in document; placeholder 30 s.").defineInRange("nullify_ticks", 600, 0, Integer.MAX_VALUE);
		WATER_A2_WEAKNESS = b.comment("Document: Weakness II unless Reinforced.").defineInRange("weakness_level", 2, 0, 255);
		WATER_A2_WEAKNESS_TICKS = b.comment("Not in document; placeholder 30 s.").defineInRange("weakness_ticks", 600, 0, Integer.MAX_VALUE);
		WATER_A2_SPEED = b.comment("Launch speed toward the nearest player, blocks/tick. Placeholder.").defineInRange("launch_speed", 2.5, 0.1, 3.9);
		WATER_A2_MAX_FLIGHT_TICKS = b.comment("Placeholder 3 s.").defineInRange("max_flight_ticks", 60, 1, Integer.MAX_VALUE);
		WATER_A2_CONTACT_RANGE = b.comment("Flight stops this close to the target.").defineInRange("contact_range", 2.0, 0.1, 10.0);
		WATER_A2_COOLDOWN = b.comment("Balance pass 2026-10-07 (user): 180 s (every ability without its own cooldown).").defineInRange("cooldown_ticks", 3600, 0, Integer.MAX_VALUE);
		b.pop();
		b.pop();

		SPEC = b.build();
	}

	private AbilityConfig() {
	}
}
