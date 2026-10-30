package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Electric DEFAULT kit + Plasma / Enhanced Receptors / Chaos sub-aspects (ASPECT GUIDE + user "use rec"
 * 2026-10-29): zyrenmoddedcontent_v2-electric-server.toml.
 *
 * Values from the guide are the defaults. PLACEHOLDER = not given by the guide; chosen so the ability
 * works, change freely. "Damage points" are health points (2 = 1 heart). Times in seconds. Effect levels
 * as the guide writes them. Lightning that "strikes the user" is visual only. Electric keeps its old
 * Reinforce Body (abilities.toml) until the guide defines one.
 */
public final class ElectricConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.DoubleValue LIGHTNING_DAMAGE;
	public static final ModConfigSpec.BooleanValue CRATERS;

	public static final ModConfigSpec.DoubleValue PASSIVE_QI_PER_MINUTE;
	public static final ModConfigSpec.IntValue PASSIVE_SPEED, PASSIVE_STRENGTH, PASSIVE_REGEN;

	public static final ModConfigSpec.DoubleValue A1_COST, A1_MULTIPLIER, A2_COST, PB1_COST, PB2_COST, AB1_COST, AB1_DAMAGE, AB2_COST;
	public static final ModConfigSpec.IntValue A1_COOLDOWN, A1_HITS, A2_COOLDOWN, A2_HITS, PB1_COOLDOWN, PB1_RANGE, PB1_BLIND_SECONDS,
			PB2_COOLDOWN, PB2_SECONDS, PB2_STRENGTH, PB2_SPEED, PB2_RESISTANCE, PB2_HASTE, AB1_COOLDOWN, AB1_RADIUS, AB1_TARGETS, AB1_CRATER,
			AB2_COOLDOWN, AB2_SECONDS;

	// Plasma
	public static final ModConfigSpec.DoubleValue PL_A1_COST, PL_A1_EXTRA, PL_A2_COST, PL_PB1_COST, PL_PB1_UP, PL_PB2_COST, PL_AB1_COST, PL_AB1_DAMAGE,
			PL_AB2_COST, PL_AB2_DAMAGE;
	public static final ModConfigSpec.IntValue PL_A1_COOLDOWN, PL_A1_HITS, PL_A2_COOLDOWN, PL_A2_RANGE, PL_PB1_COOLDOWN, PL_PB1_RANGE, PL_PB1_GLOW_SECONDS,
			PL_PB2_COOLDOWN, PL_PB2_SECONDS, PL_AB1_COOLDOWN, PL_AB1_SECONDS, PL_AB1_FALLBACK_RANGE, PL_AB2_COOLDOWN, PL_AB2_SECONDS, PL_AB2_LENGTH,
			PL_AB2_CRATER;
	// Enhanced Receptors
	public static final ModConfigSpec.DoubleValue ER_MODE1_QI_PER_MINUTE, ER_MODE2_QI_PER_SECOND, ER_DEFENSE_COST, ER_CHANCE_ATTACK, ER_CHANCE_POWERBLAST,
			ER_CHANCE_ABILITY, ER_A1_COST, ER_A1_DAMAGE, ER_A2_COST, ER_PB2_COST, ER_AB1_COST, ER_AB1_BLOCK_CHANCE, ER_AB1_WEAPON_MULTIPLIER,
			ER_AB2_COST, ER_AB2_DAMAGE, ER_AB2_UP;
	public static final ModConfigSpec.IntValue ER_M1_SPEED, ER_M1_STRENGTH, ER_M1_RESISTANCE, ER_M1_REGEN, ER_SLOTS, ER_SLOT_RECHARGE_SECONDS,
			ER_DEFENSE_TICKS, ER_MODE_SWITCH_TICKS, ER_A1_COOLDOWN, ER_A2_COOLDOWN, ER_A2_RANGE, ER_A2_LOW_HEALTH, ER_A2_ESCAPE_MIN, ER_A2_ESCAPE_MAX,
			ER_A2_ESCAPE_CLEAR, ER_PB2_COOLDOWN, ER_PB2_SECONDS, ER_AB1_COOLDOWN, ER_AB1_SECONDS, ER_AB2_COOLDOWN, ER_AB2_SECONDS, ER_AB2_RADIUS,
			ER_AB2_INTERVAL_SECONDS;
	// Chaos
	public static final ModConfigSpec.DoubleValue CH_A1_COST, CH_A1_DISTANCE, CH_A2_COST, CH_A2_POWER, CH_PB1_COST, CH_PB1_STEP, CH_PB2_COST, CH_AB1_COST,
			CH_AB1_STEP, CH_AB2_COST, CH_AB2_KNOCK;
	public static final ModConfigSpec.IntValue CH_A1_COOLDOWN, CH_A2_COOLDOWN, CH_A2_SECONDS, CH_PB1_COOLDOWN, CH_PB1_SECONDS, CH_PB1_SPEED,
			CH_PB2_COOLDOWN, CH_PB2_SECONDS, CH_PB2_SPEED, CH_AB1_COOLDOWN, CH_AB1_SECONDS, CH_AB1_SPEED, CH_AB1_RESISTANCE, CH_AB1_JUMP,
			CH_AB2_COOLDOWN, CH_AB2_SECONDS, CH_AB2_SPEED, CH_AB2_RESISTANCE, CH_AB2_STRIKE_SECONDS, CH_AB2_STRIKE_COUNT, CH_AB2_STRIKE_RADIUS,
			CH_AB2_KNOCK_SECONDS, CH_AB2_KNOCK_RADIUS;
	public static final ModConfigSpec.BooleanValue CH_A2_BREAKS_BLOCKS;

	private static ModConfigSpec.DoubleValue d(ModConfigSpec.Builder b, String n, double v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue i(ModConfigSpec.Builder b, String n, int v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("general");
		LIGHTNING_DAMAGE = b.comment("PLACEHOLDER: damage of a lightning strike on a target when the guide gives none (vanilla lightning = 5).").defineInRange("lightning_damage", 5.0, 0, 100000);
		CRATERS = b.comment("Nuclear blast / Nuke's heated breath craters (permanent, no drops, unbreakable blocks stay).").define("craters", true);
		b.pop();

		b.comment("Passive: during a thunderstorm Speed, Strength, Regeneration. Always immune to lightning damage while the passive runs.").push("passive");
		PASSIVE_QI_PER_MINUTE = d(b, "qi_per_minute", 5);
		PASSIVE_SPEED = i(b, "storm_speed_level", 1);
		PASSIVE_STRENGTH = i(b, "storm_strength_level", 1);
		PASSIVE_REGEN = i(b, "storm_regeneration_level", 2);
		b.pop();

		b.push("default_kit");
		A1_COST = b.comment("Attack 1 'lightning enhanced fists': next N hits x damage.").defineInRange("attack1_cost", 40.0, 0, 100000);
		A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 60);
		A1_HITS = b.defineInRange("attack1_hits", 5, 1, 1000);
		A1_MULTIPLIER = b.defineInRange("attack1_damage_multiplier", 2.0, 1, 100);
		A2_COST = b.comment("Attack 2 'blade of thunder': next N SWORD hits call lightning on the target (general.lightning_damage).").defineInRange("attack2_cost", 60.0, 0, 100000);
		A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		A2_HITS = b.defineInRange("attack2_hits", 5, 1, 1000);
		PB1_COST = b.comment("Powerblast 1 'electric blast': the player you look at (else the nearest) in range, under open sky: lightning + Blindness.").defineInRange("powerblast1_cost", 80.0, 0, 100000);
		PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 240);
		PB1_RANGE = i(b, "powerblast1_range", 20);
		PB1_BLIND_SECONDS = i(b, "powerblast1_blindness_seconds", 10);
		PB2_COST = b.comment("Powerblast 2 'enhanced powers': passive off; Strength, Speed, Resistance, Haste ('attack speed').").defineInRange("powerblast2_cost", 100.0, 0, 100000);
		PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 380);
		PB2_SECONDS = i(b, "powerblast2_seconds", 120);
		PB2_STRENGTH = i(b, "powerblast2_strength_level", 2);
		PB2_SPEED = i(b, "powerblast2_speed_level", 4);
		PB2_RESISTANCE = i(b, "powerblast2_resistance_level", 3);
		PB2_HASTE = i(b, "powerblast2_haste_level", 4);
		AB1_COST = b.comment("Ability 1 'nuclear blast': the N nearest unallied players in radius: lightning + damage + a crater under them.").defineInRange("ability1_cost", 180.0, 0, 100000);
		AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 500);
		AB1_RADIUS = i(b, "ability1_radius", 60);
		AB1_TARGETS = b.defineInRange("ability1_targets", 4, 1, 100);
		AB1_DAMAGE = d(b, "ability1_damage", 10);
		AB1_CRATER = b.comment("Crater cube side.").defineInRange("ability1_crater_size", 5, 1, 15);
		AB2_COST = b.comment("Ability 2 'weaponized local monson': thunderstorm; every sword hit calls lightning while it lasts.").defineInRange("ability2_cost", 250.0, 0, 100000);
		AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 1800);
		AB2_SECONDS = i(b, "ability2_seconds", 120);
		b.pop();

		b.comment("ELECTRIC -> PLASMA (sub-aspect). Passive = the Electric passive.").push("sub_plasma");
		PL_A1_COST = b.comment("Attack 1 'heated thunder': next N hits set the target on fire + extra damage.").defineInRange("attack1_cost", 15.0, 0, 100000);
		PL_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 120);
		PL_A1_HITS = b.defineInRange("attack1_hits", 3, 1, 1000);
		PL_A1_EXTRA = d(b, "attack1_extra_damage", 5);
		PL_A2_COST = b.comment("Attack 2 'burning gaze': closest unallied player in sight and range: lightning, you teleport to them.").defineInRange("attack2_cost", 30.0, 0, 100000);
		PL_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 280);
		PL_A2_RANGE = i(b, "attack2_range", 15);
		PL_PB1_COST = b.comment("Powerblast 1 'the song of plasma': PLACEHOLDER cost (user rec: the default Electric PB1 cost). Nearest unallied player: lightning, thrown up, Glowing.").defineInRange("powerblast1_cost", 80.0, 0, 100000);
		PL_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 480);
		PL_PB1_RANGE = i(b, "powerblast1_range", 20);
		PL_PB1_UP = d(b, "powerblast1_up_blocks", 10);
		PL_PB1_GLOW_SECONDS = b.comment("PLACEHOLDER: Glowing length.").defineInRange("powerblast1_glowing_seconds", 10, 0, 100000);
		PL_PB2_COST = b.comment("Powerblast 2 'fires bigger brother': PLACEHOLDER cost (user rec: the default Electric PB2 cost). Every melee / arrow hit: fire + lightning.").defineInRange("powerblast2_cost", 100.0, 0, 100000);
		PL_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 250);
		PL_PB2_SECONDS = i(b, "powerblast2_seconds", 60);
		PL_AB1_COST = b.comment("Ability 1 'did i say something wrong?': the first unallied player to hurt you gets lightning + fire + damage; self-inflicted damage -> the nearest player in range instead. Then it ends.").defineInRange("ability1_cost", 200.0, 0, 100000);
		PL_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 380);
		PL_AB1_SECONDS = i(b, "ability1_seconds", 30);
		PL_AB1_DAMAGE = d(b, "ability1_damage", 12);
		PL_AB1_FALLBACK_RANGE = b.comment("PLACEHOLDER (user OK).").defineInRange("ability1_fallback_range", 20, 1, 256);
		PL_AB2_COST = b.comment("Ability 2 'nukes heated breath': a beam where you look (stopped by blocks): damage + fire every 0.5 s; crater at its end when it finishes.").defineInRange("ability2_cost", 300.0, 0, 100000);
		PL_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 900);
		PL_AB2_SECONDS = i(b, "ability2_seconds", 5);
		PL_AB2_LENGTH = i(b, "ability2_length", 25);
		PL_AB2_DAMAGE = d(b, "ability2_damage", 4);
		PL_AB2_CRATER = b.defineInRange("ability2_crater_size", 7, 1, 15);
		b.pop();

		b.comment("ELECTRIC -> ENHANCED RECEPTORS (sub-aspect). Its passive REPLACES the Electric passive and has two modes (key 'Switch Receptor Mode', default V).").push("sub_enhanced_receptors");
		ER_MODE1_QI_PER_MINUTE = b.comment("Mode 1: during a thunderstorm Speed, Strength, Resistance, Regeneration.").defineInRange("mode1_qi_per_minute", 10.0, 0, 100000);
		ER_M1_SPEED = i(b, "mode1_speed_level", 5);
		ER_M1_STRENGTH = i(b, "mode1_strength_level", 2);
		ER_M1_RESISTANCE = i(b, "mode1_resistance_level", 2);
		ER_M1_REGEN = i(b, "mode1_regeneration_level", 1);
		ER_MODE2_QI_PER_SECOND = b.comment("Mode 2: automatic defense slots (incoming Qi abilities only; chance by the attacker's slot).").defineInRange("mode2_qi_per_second", 10.0, 0, 100000);
		ER_SLOTS = b.defineInRange("mode2_slots", 5, 1, 100);
		ER_SLOT_RECHARGE_SECONDS = b.defineInRange("mode2_slot_recharge_seconds", 5, 1, 100000);
		ER_DEFENSE_COST = d(b, "mode2_defense_cost", 20);
		ER_DEFENSE_TICKS = b.comment("PLACEHOLDER (user OK): length of the triggered Reinforce Body.").defineInRange("mode2_reinforce_ticks", 20, 1, 100000);
		ER_CHANCE_ATTACK = b.defineInRange("mode2_chance_attack", 5.0 / 7, 0, 1);
		ER_CHANCE_POWERBLAST = b.defineInRange("mode2_chance_powerblast", 3.5 / 7, 0, 1);
		ER_CHANCE_ABILITY = b.defineInRange("mode2_chance_ability", 2.0 / 7, 0, 1);
		ER_MODE_SWITCH_TICKS = b.defineInRange("mode_switch_cooldown_ticks", 10, 0, 100000);
		ER_A1_COST = b.comment("Attack 1 'enhanced fists of bs': stays armed until an unarmed hit, which deals at least this much.").defineInRange("attack1_cost", 15.0, 0, 100000);
		ER_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 30);
		ER_A1_DAMAGE = d(b, "attack1_damage", 12);
		ER_A2_COST = b.comment("Attack 2 'riding the lightning': next to the nearest unallied player in range; under the health limit at cast: escape to a spot with no unallied player near it.").defineInRange("attack2_cost", 45.0, 0, 100000);
		ER_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		ER_A2_RANGE = i(b, "attack2_range", 45);
		ER_A2_LOW_HEALTH = b.comment("Health points (10 = 5 hearts).").defineInRange("attack2_low_health", 10, 0, 100000);
		ER_A2_ESCAPE_MIN = i(b, "attack2_escape_min_distance", 20);
		ER_A2_ESCAPE_MAX = i(b, "attack2_escape_max_distance", 45);
		ER_A2_ESCAPE_CLEAR = i(b, "attack2_escape_clear_radius", 20);
		ER_PB2_COST = b.comment("Powerblast 2 'localized ehancements': passive off; Speed, Strength, Resistance, Regeneration, creative flight. Can break core.").defineInRange("powerblast2_cost", 120.0, 0, 100000);
		ER_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 480);
		ER_PB2_SECONDS = i(b, "powerblast2_seconds", 60);
		ER_AB1_COST = b.comment("Ability 1 'I AM HIM': attacks from players are blocked by chance; one that gets through calls lightning on the attacker. Weapon hits x damage.").defineInRange("ability1_cost", 200.0, 0, 100000);
		ER_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 480);
		ER_AB1_SECONDS = i(b, "ability1_seconds", 120);
		ER_AB1_BLOCK_CHANCE = b.defineInRange("ability1_block_chance", 0.8, 0, 1);
		ER_AB1_WEAPON_MULTIPLIER = b.defineInRange("ability1_weapon_multiplier", 2.0, 1, 100);
		ER_AB2_COST = b.comment("Ability 2 'the reason we dont mess with me': a storm where you cast it; inside: Speed V, Strength III, Resistance III, flight; every interval, unallied players inside under open sky are struck + thrown up.").defineInRange("ability2_cost", 300.0, 0, 100000);
		ER_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 900);
		ER_AB2_SECONDS = i(b, "ability2_seconds", 30);
		ER_AB2_RADIUS = i(b, "ability2_radius", 30);
		ER_AB2_INTERVAL_SECONDS = b.defineInRange("ability2_interval_seconds", 3, 1, 100000);
		ER_AB2_DAMAGE = d(b, "ability2_damage", 8);
		ER_AB2_UP = d(b, "ability2_up_blocks", 15);
		b.pop();

		b.comment("ELECTRIC -> CHAOS (sub-aspect). Passive = the Electric passive.").push("sub_chaos");
		CH_A1_COST = b.comment("Attack 1 'horizon yeeting': next hit: target teleported to build height, this far the way you face (then falls).").defineInRange("attack1_cost", 50.0, 0, 100000);
		CH_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 120);
		CH_A1_DISTANCE = d(b, "attack1_distance", 100);
		CH_A2_COST = b.comment("Attack 2 'smoking crater': every hit makes an explosion; you're immune to your own.").defineInRange("attack2_cost", 75.0, 0, 100000);
		CH_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		CH_A2_SECONDS = i(b, "attack2_seconds", 30);
		CH_A2_POWER = b.comment("PLACEHOLDER (user OK): explosion power ('sphere of 10').").defineInRange("attack2_explosion_power", 5.0, 0, 20);
		CH_A2_BREAKS_BLOCKS = b.define("attack2_breaks_blocks", true);
		CH_PB1_COST = b.comment("Powerblast 1 'movement of the storm': thunderstorm; Speed, step height, no fall damage.").defineInRange("powerblast1_cost", 100.0, 0, 100000);
		CH_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 340);
		CH_PB1_SECONDS = i(b, "powerblast1_seconds", 120);
		CH_PB1_SPEED = i(b, "powerblast1_speed_level", 4);
		CH_PB1_STEP = b.defineInRange("powerblast1_step_height", 3.0, 0.6, 10);
		CH_PB2_COST = b.comment("Powerblast 2 'hi i can fly now': creative flight + Speed.").defineInRange("powerblast2_cost", 125.0, 0, 100000);
		CH_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 480);
		CH_PB2_SECONDS = i(b, "powerblast2_seconds", 120);
		CH_PB2_SPEED = i(b, "powerblast2_speed_level", 4);
		CH_AB1_COST = b.comment("Ability 1 'i am chaos': thunderstorm + night (whole server); Speed, Resistance, Jump Boost, step height, Night Vision; your hits on players call lightning.").defineInRange("ability1_cost", 150.0, 0, 100000);
		CH_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 680);
		CH_AB1_SECONDS = i(b, "ability1_seconds", 120);
		CH_AB1_SPEED = i(b, "ability1_speed_level", 3);
		CH_AB1_RESISTANCE = i(b, "ability1_resistance_level", 10);
		CH_AB1_JUMP = i(b, "ability1_jump_boost_level", 3);
		CH_AB1_STEP = b.defineInRange("ability1_step_height", 3.0, 0.6, 10);
		CH_AB2_COST = b.comment("Ability 2 'welcome to the eye of the storm': Speed, flight, Resistance, no fall damage; strikes the nearest unallied players and knocks everything near you away on intervals. Can break core.").defineInRange("ability2_cost", 200.0, 0, 100000);
		CH_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 1200);
		CH_AB2_SECONDS = i(b, "ability2_seconds", 30);
		CH_AB2_SPEED = i(b, "ability2_speed_level", 5);
		CH_AB2_RESISTANCE = i(b, "ability2_resistance_level", 10);
		CH_AB2_STRIKE_SECONDS = b.defineInRange("ability2_strike_interval_seconds", 2, 1, 100000);
		CH_AB2_STRIKE_COUNT = b.defineInRange("ability2_strike_count", 3, 1, 100);
		CH_AB2_STRIKE_RADIUS = i(b, "ability2_strike_radius", 30);
		CH_AB2_KNOCK_SECONDS = b.defineInRange("ability2_knock_interval_seconds", 5, 1, 100000);
		CH_AB2_KNOCK_RADIUS = b.comment("PLACEHOLDER (user OK).").defineInRange("ability2_knock_radius", 10, 1, 256);
		CH_AB2_KNOCK = b.comment("PLACEHOLDER (user OK): blocks knocked away.").defineInRange("ability2_knock_blocks", 6.0, 0, 100000);
		b.pop();

		SPEC = b.build();
	}

	private ElectricConfig() {
	}
}
