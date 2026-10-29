package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Water DEFAULT kit + Freezing / Body / Moon sub-aspects (ASPECT GUIDE + user decisions 2026-10-29):
 * zyrenmoddedcontent_v2-water-server.toml.
 *
 * Values from the guide are the defaults. PLACEHOLDER = not given by the guide; chosen so the ability
 * works (user: "run with your recommendations"), change freely. "Damage points" are health points
 * (2 = 1 heart). Times are in seconds unless the name says ticks. Effect levels are written like the
 * guide (Strength II = 2). Reinforce Body is not in the guide: Water keeps its old one (abilities.toml).
 */
public final class WaterConfig {
	public static final ModConfigSpec SPEC;

	// general
	public static final ModConfigSpec.DoubleValue PUSH_SPEED, PULL_SPEED;
	public static final ModConfigSpec.IntValue SNOW_COLUMNS_PER_TICK;
	// passive
	public static final ModConfigSpec.DoubleValue PASSIVE_QI_PER_SECOND;
	public static final ModConfigSpec.IntValue PASSIVE_COOLDOWN, PASSIVE_REGEN, PASSIVE_STRENGTH;
	// attack 1
	public static final ModConfigSpec.DoubleValue A1_COST, A1_UNARMED_EXTRA, A1_WEAPON_EXTRA;
	public static final ModConfigSpec.IntValue A1_COOLDOWN, A1_HITS;
	// attack 2
	public static final ModConfigSpec.DoubleValue A2_COST, A2_BLOCKS;
	public static final ModConfigSpec.IntValue A2_COOLDOWN;
	// powerblast 1
	public static final ModConfigSpec.DoubleValue PB1_COST, PB1_DAMAGE, PB1_PUSH_BLOCKS;
	public static final ModConfigSpec.IntValue PB1_COOLDOWN, PB1_RADIUS, PB1_WATER_RADIUS, PB1_WATER_SECONDS;
	// powerblast 2
	public static final ModConfigSpec.DoubleValue PB2_COST;
	public static final ModConfigSpec.IntValue PB2_COOLDOWN, PB2_WATER_RADIUS, PB2_ENEMY_CLEAR_RADIUS, PB2_ALLY_RADIUS, PB2_OCEAN_RADIUS, PB2_VERTICAL;
	// ability 1
	public static final ModConfigSpec.DoubleValue AB1_COST, AB1_WATER_DISCOUNT;
	public static final ModConfigSpec.IntValue AB1_COOLDOWN, AB1_SECONDS, AB1_RADIUS, AB1_STRENGTH, AB1_REGEN, AB1_SPEED;
	// ability 2
	public static final ModConfigSpec.DoubleValue AB2_COST;
	public static final ModConfigSpec.IntValue AB2_COOLDOWN, AB2_SECONDS, AB2_FLOOD_RADIUS, AB2_NULLIFY_RADIUS, AB2_STRENGTH;

	// ---- FREEZING ----
	public static final ModConfigSpec.DoubleValue FRZ_PASSIVE_QI_PER_MINUTE, FRZ_A1_COST, FRZ_A1_DAMAGE, FRZ_A2_COST, FRZ_PB1_COST, FRZ_PB1_DAMAGE,
			FRZ_AB1_COST, FRZ_AB1_FIRE_DAMAGE, FRZ_AB1_COST_MULTIPLIER, FRZ_AB2_COST;
	public static final ModConfigSpec.IntValue FRZ_ABSORPTION, FRZ_REGEN, FRZ_SPEED, FRZ_NETHER_WEAKNESS, FRZ_NETHER_NAUSEA_AFTER,
			FRZ_A1_COOLDOWN, FRZ_A1_FREEZE_TICKS, FRZ_A2_COOLDOWN, FRZ_A2_NULLIFY_SECONDS, FRZ_A2_BLIND_SECONDS,
			FRZ_PB1_COOLDOWN, FRZ_PB1_RANGE, FRZ_PB1_FREEZE_SECONDS, FRZ_PB1_FIRE_NULLIFY_SECONDS,
			FRZ_AB1_COOLDOWN, FRZ_AB1_SECONDS, FRZ_AB1_RADIUS, FRZ_AB2_COOLDOWN, FRZ_AB2_SECONDS, FRZ_AB2_RADIUS;
	public static final ModConfigSpec.ConfigValue<List<? extends String>> FRZ_BIOMES;
	public static final ModConfigSpec.BooleanValue FRZ_AB2_SNOW;
	// ---- BODY ----
	public static final ModConfigSpec.DoubleValue BODY_PASSIVE_QI_PER_SECOND, BODY_FLOWING_MULTIPLIER, BODY_A1_COST, BODY_A1_EXTRA, BODY_A1_HEAL,
			BODY_A2_COST, BODY_A2_DAMAGE_BONUS, BODY_AB1_COST, BODY_AB2_COST, BODY_AB2_EXTRA;
	public static final ModConfigSpec.IntValue BODY_STRENGTH, BODY_REGEN, BODY_ABSORPTION, BODY_A1_COOLDOWN, BODY_A1_HITS,
			BODY_A2_COOLDOWN, BODY_A2_SECONDS, BODY_A2_SPEED, BODY_A2_KNOCKBACK, BODY_AB1_COOLDOWN, BODY_AB1_SECONDS, BODY_AB1_REGEN,
			BODY_AB1_RESISTANCE, BODY_AB1_SPEED, BODY_AB2_COOLDOWN, BODY_AB2_SECONDS, BODY_AB2_RESISTANCE, BODY_AB2_SPEED;
	// ---- MOON ----
	public static final ModConfigSpec.DoubleValue MOON_PASSIVE_QI_PER_SECOND, MOON_A1_COST, MOON_A1_PULL, MOON_A1_PULL_NIGHT,
			MOON_A2_COST, MOON_A2_DAMAGE, MOON_A2_PULL, MOON_A2_PULL_NIGHT, MOON_PB1_COST, MOON_PB1_DAMAGE, MOON_PB1_PUSH, MOON_PB1_PUSH_NIGHT,
			MOON_PB2_COST, MOON_AB1_COST, MOON_AB1_PULL, MOON_AB1_PULL_NIGHT, MOON_AB1_PUSH, MOON_AB1_PUSH_NIGHT,
			MOON_AB2_COST, MOON_AB2_PUSH, MOON_AB2_DAMAGE, MOON_AB2_DAMAGE_NIGHT;
	public static final ModConfigSpec.IntValue MOON_RESISTANCE, MOON_SIGHT_RADIUS, MOON_WATER_REGEN, MOON_WATER_STRENGTH,
			MOON_A1_COOLDOWN, MOON_A1_SLOW_SECONDS, MOON_SLOWNESS, MOON_A2_COOLDOWN, MOON_A2_RANGE, MOON_A2_RANGE_NIGHT,
			MOON_PB1_COOLDOWN, MOON_PB1_RADIUS, MOON_PB1_RADIUS_NIGHT, MOON_PB2_COOLDOWN, MOON_PB2_RANGE, MOON_PB2_RANGE_NIGHT,
			MOON_PB2_SECONDS, MOON_PB2_SECONDS_NIGHT, MOON_AB1_COOLDOWN, MOON_AB1_SECONDS, MOON_AB1_RADIUS, MOON_AB1_RADIUS_NIGHT,
			MOON_AB1_PULSE_SECONDS, MOON_AB2_COOLDOWN, MOON_AB2_RADIUS, MOON_AB2_RADIUS_NIGHT, MOON_AB2_SLOW_SECONDS;

	private static ModConfigSpec.DoubleValue d(ModConfigSpec.Builder b, String name, double def) {
		return b.defineInRange(name, def, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue i(ModConfigSpec.Builder b, String name, int def) {
		return b.defineInRange(name, def, 0, 1_000_000);
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("general");
		PUSH_SPEED = b.comment("PLACEHOLDER: speed (blocks/tick) of every Water push / launch.").defineInRange("push_speed", 1.5, 0.1, 10);
		PULL_SPEED = b.comment("PLACEHOLDER: speed (blocks/tick) of every Water pull. Solid blocks stop a pull.").defineInRange("pull_speed", 1.0, 0.1, 10);
		SNOW_COLUMNS_PER_TICK = b.comment("Freezing Apocalypse snow: ground columns handled per tick (lower = less lag, slower).")
				.defineInRange("snow_columns_per_tick", 2000, 50, 1_000_000);
		b.pop();

		b.comment("Passive: while in water: Regeneration, Conduit Power, Water Breathing, Strength. Qi only while in water (rain doesn't count).").push("passive");
		PASSIVE_QI_PER_SECOND = d(b, "qi_per_second", 1.0);
		PASSIVE_COOLDOWN = b.comment("After leaving water the passive can't turn back on for this long.").defineInRange("cooldown_seconds", 10, 0, 100000);
		PASSIVE_REGEN = i(b, "regeneration_level", 1);
		PASSIVE_STRENGTH = i(b, "strength_level", 1);
		b.pop();

		b.comment("Attack 1 'water fists': the next N valid hits get extra damage (on top of the normal hit). Unarmed = empty hand; weapon = sword, axe, trident, mace (no spears in 1.21.1). Other items: no bonus, no hit used.").push("attack_1");
		A1_COST = d(b, "cost", 15);
		A1_COOLDOWN = i(b, "cooldown_seconds", 108);
		A1_HITS = b.defineInRange("hits", 10, 1, 1000);
		A1_UNARMED_EXTRA = d(b, "unarmed_extra_damage", 4.0);
		A1_WEAPON_EXTRA = d(b, "weapon_extra_damage", 2.5);
		b.pop();

		b.comment("Attack 2 'yeet': your next hit throws the target back.").push("attack_2");
		A2_COST = d(b, "cost", 30);
		A2_COOLDOWN = i(b, "cooldown_seconds", 140);
		A2_BLOCKS = d(b, "throw_blocks", 10);
		b.pop();

		b.comment("Powerblast 1 'tidal blast': unallied players in radius take damage and are launched back; water sources placed around you (drained later).").push("powerblast_1");
		PB1_COST = d(b, "cost", 25);
		PB1_COOLDOWN = i(b, "cooldown_seconds", 380);
		PB1_RADIUS = i(b, "radius", 10);
		PB1_PUSH_BLOCKS = d(b, "push_blocks", 5);
		PB1_DAMAGE = d(b, "damage", 5);
		PB1_WATER_RADIUS = i(b, "water_radius", 5);
		PB1_WATER_SECONDS = b.comment("User 2026-10-29 (rec): the placed water drains after this.").defineInRange("water_seconds", 30, 1, 100000);
		b.pop();

		b.comment("Powerblast 2 'water seeker': teleport to the nearest water source with no unallied player near it; else the nearest Water-active ALLY; else the nearest ocean.").push("powerblast_2");
		PB2_COST = d(b, "cost", 35);
		PB2_COOLDOWN = i(b, "cooldown_seconds", 30);
		PB2_WATER_RADIUS = i(b, "water_radius", 50);
		PB2_VERTICAL = b.comment("PLACEHOLDER: how far up/down the water search looks (a full 50-block sphere is too slow for one tick).").defineInRange("water_search_vertical", 16, 1, 64);
		PB2_ENEMY_CLEAR_RADIUS = i(b, "enemy_clear_radius", 5);
		PB2_ALLY_RADIUS = i(b, "water_ally_radius", 100);
		PB2_OCEAN_RADIUS = b.comment("PLACEHOLDER (same as the old Fishies): nearest ocean search radius.").defineInRange("ocean_radius", 2000, 64, 10000);
		b.pop();

		b.comment("Ability 1 'my area now': aura around you: you and allies get Strength, Regeneration, Speed; Water-active ones also pay less Qi. Rain while it lasts.").push("ability_1");
		AB1_COST = d(b, "cost", 100);
		AB1_COOLDOWN = i(b, "cooldown_seconds", 480);
		AB1_SECONDS = i(b, "seconds", 180);
		AB1_RADIUS = b.comment("PLACEHOLDER (user OK 2026-10-29): aura radius.").defineInRange("radius", 30, 1, 256);
		AB1_STRENGTH = i(b, "strength_level", 2);
		AB1_REGEN = i(b, "regeneration_level", 2);
		AB1_SPEED = i(b, "speed_level", 3);
		AB1_WATER_DISCOUNT = b.defineInRange("water_ally_cost_discount", 0.5, 0, 1);
		b.pop();

		b.comment("Ability 2 'OCEANIC DOOM domain expansion': water on the ground (drains when it ends), rain, creative flight + Strength for you; unallied Fire players (Reinforce or not) and unallied Earth players (unless Reinforced) within the nullify radius are nullified while inside.").push("ability_2");
		AB2_COST = d(b, "cost", 200);
		AB2_COOLDOWN = i(b, "cooldown_seconds", 480);
		AB2_SECONDS = i(b, "seconds", 180);
		AB2_FLOOD_RADIUS = b.defineInRange("flood_radius", 50, 1, 64);
		AB2_NULLIFY_RADIUS = i(b, "nullify_radius", 25);
		AB2_STRENGTH = i(b, "strength_level", 5);
		b.pop();

		// ================================================================ FREEZING
		b.comment("WATER -> FREEZING (sub-aspect). Its passive REPLACES the Water passive. PB2 = the default Water PB2.").push("sub_freezing");
		FRZ_PASSIVE_QI_PER_MINUTE = d(b, "passive_qi_per_minute", 2);
		FRZ_BIOMES = b.comment("Passive biomes. 'ice caves' and 'dappled forest' are not 1.21.1 biomes (left out; add ids here if a mod adds them).")
				.defineListAllowEmpty("passive_biomes", List.of("minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean",
						"minecraft:deep_frozen_ocean", "minecraft:frozen_peaks", "minecraft:snowy_slopes", "minecraft:frozen_river",
						"minecraft:snowy_beach", "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga"),
						() -> "minecraft:snowy_plains", o -> o instanceof String);
		FRZ_ABSORPTION = i(b, "passive_absorption_level", 4);
		FRZ_REGEN = i(b, "passive_regeneration_level", 2);
		FRZ_SPEED = i(b, "passive_speed_level", 3);
		FRZ_NETHER_WEAKNESS = b.comment("In the Nether (whole Freezing form, no Qi): Weakness at this level ...").defineInRange("nether_weakness_level", 2, 0, 255);
		FRZ_NETHER_NAUSEA_AFTER = b.comment("... and Nausea after this many seconds in the Nether.").defineInRange("nether_nausea_after_seconds", 300, 0, 100000);
		FRZ_A1_COST = d(b, "attack1_cost", 15);
		FRZ_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 120);
		FRZ_A1_DAMAGE = b.comment("Attack 1 'freezing attack': next hit +damage (on top) and freezes the target in place.").defineInRange("attack1_extra_damage", 15.0, 0, 100000);
		FRZ_A1_FREEZE_TICKS = i(b, "attack1_freeze_ticks", 20);
		FRZ_A2_COST = b.comment("Attack 2 'freezing core': stays armed until it hits a Water / Fire / Space->Matter player: core nullified + Blindness.").defineInRange("attack2_cost", 30.0, 0, 100000);
		FRZ_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 150);
		FRZ_A2_NULLIFY_SECONDS = i(b, "attack2_nullify_seconds", 60);
		FRZ_A2_BLIND_SECONDS = i(b, "attack2_blindness_seconds", 10);
		FRZ_PB1_COST = b.comment("Powerblast 1 'FREEZING BLAST': nearest player in sight (only stone blocks sight) within range: damage, frozen in place + Blindness; Fire users also nullified.").defineInRange("powerblast1_cost", 50.0, 0, 100000);
		FRZ_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 280);
		FRZ_PB1_RANGE = i(b, "powerblast1_range", 30);
		FRZ_PB1_DAMAGE = d(b, "powerblast1_damage", 18);
		FRZ_PB1_FREEZE_SECONDS = i(b, "powerblast1_freeze_seconds", 45);
		FRZ_PB1_FIRE_NULLIFY_SECONDS = b.comment("User 2026-10-29 (rec): the sheet's 'duration 90' = Fire nullify time.").defineInRange("powerblast1_fire_nullify_seconds", 90, 0, 100000);
		FRZ_AB1_COST = b.comment("Ability 1 'WINTERY DEATH': aura: unallied non-Freezing players inside get Blindness; Fire users also take damage once per cast and pay x Qi while inside.").defineInRange("ability1_cost", 200.0, 0, 100000);
		FRZ_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 480);
		FRZ_AB1_SECONDS = i(b, "ability1_seconds", 120);
		FRZ_AB1_RADIUS = i(b, "ability1_radius", 50);
		FRZ_AB1_FIRE_DAMAGE = d(b, "ability1_fire_damage", 15);
		FRZ_AB1_COST_MULTIPLIER = b.defineInRange("ability1_fire_cost_multiplier", 2.0, 1, 100);
		FRZ_AB2_COST = b.comment("Ability 2 'FREEZING APOCALYPSE': snow + snowfall around you; unallied non-Freezing players inside freeze (vanilla frostbite); Fire / Electric ones are also nullified while inside.").defineInRange("ability2_cost", 300.0, 0, 100000);
		FRZ_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 480);
		FRZ_AB2_SECONDS = i(b, "ability2_seconds", 280);
		FRZ_AB2_RADIUS = b.defineInRange("ability2_radius", 200, 1, 512);
		FRZ_AB2_SNOW = b.comment("User 2026-10-29 (rec): place snow layers on the ground (permanent; they melt naturally).").define("ability2_place_snow", true);
		b.pop();

		// ================================================================ BODY
		b.comment("WATER -> BODY (sub-aspect). Its passive REPLACES the Water passive. PB1 / PB2 = the default Water ones.").push("sub_body");
		BODY_PASSIVE_QI_PER_SECOND = b.comment("Passive: in water: Strength, Regeneration, Absorption.").defineInRange("passive_qi_per_second", 0.5, 0, 100000);
		BODY_FLOWING_MULTIPLIER = b.comment("Cost multiplier when not in a water SOURCE block (flowing water).").defineInRange("passive_flowing_multiplier", 2.0, 1, 100);
		BODY_STRENGTH = i(b, "passive_strength_level", 5);
		BODY_REGEN = i(b, "passive_regeneration_level", 2);
		BODY_ABSORPTION = i(b, "passive_absorption_level", 4);
		BODY_A1_COST = b.comment("Attack 1 'infused blood': next N unarmed hits +damage and heal you.").defineInRange("attack1_cost", 39.0, 0, 100000);
		BODY_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 25);
		BODY_A1_HITS = b.defineInRange("attack1_hits", 3, 1, 1000);
		BODY_A1_EXTRA = d(b, "attack1_extra_damage", 4);
		BODY_A1_HEAL = d(b, "attack1_heal_per_hit", 2);
		BODY_A2_COST = b.comment("Attack 2 'enhanced power': Strength (added as attack damage so it stacks with the passive's) + Speed; with the passive on, melee hits get Knockback.").defineInRange("attack2_cost", 50.0, 0, 100000);
		BODY_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 60);
		BODY_A2_SECONDS = i(b, "attack2_seconds", 12);
		BODY_A2_DAMAGE_BONUS = b.comment("Strength II = +6 attack damage (3 per level).").defineInRange("attack2_attack_damage", 6.0, 0, 100000);
		BODY_A2_SPEED = i(b, "attack2_speed_level", 2);
		BODY_A2_KNOCKBACK = i(b, "attack2_knockback_level", 4);
		BODY_AB1_COST = b.comment("Ability 1 'INFUSED WITH THE WATER': passive off; in water: Regeneration, Resistance, Speed (paused out of water, clock keeps running).").defineInRange("ability1_cost", 200.0, 0, 100000);
		BODY_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 240);
		BODY_AB1_SECONDS = i(b, "ability1_seconds", 90);
		BODY_AB1_REGEN = i(b, "ability1_regeneration_level", 4);
		BODY_AB1_RESISTANCE = i(b, "ability1_resistance_level", 2);
		BODY_AB1_SPEED = i(b, "ability1_speed_level", 4);
		BODY_AB2_COST = b.comment("Ability 2 'I AM HIM': while the passive is on: +damage per hit, Resistance, Speed (paused otherwise, clock keeps running).").defineInRange("ability2_cost", 300.0, 0, 100000);
		BODY_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 480);
		BODY_AB2_SECONDS = i(b, "ability2_seconds", 60);
		BODY_AB2_EXTRA = d(b, "ability2_extra_damage", 4);
		BODY_AB2_RESISTANCE = i(b, "ability2_resistance_level", 3);
		BODY_AB2_SPEED = i(b, "ability2_speed_level", 4);
		b.pop();

		// ================================================================ MOON
		b.comment("WATER -> MOON (sub-aspect). Its passive REPLACES the Water passive. 'Night' = the world's night (never in the Nether / End).").push("sub_moon");
		MOON_PASSIVE_QI_PER_SECOND = b.comment("Passive (night only, Qi only at night): Resistance, no knockback, see the aspect of players near you; in water also Regeneration + Strength.").defineInRange("passive_qi_per_second", 0.5, 0, 100000);
		MOON_RESISTANCE = i(b, "passive_resistance_level", 2);
		MOON_SIGHT_RADIUS = i(b, "passive_sight_radius", 15);
		MOON_WATER_REGEN = i(b, "passive_water_regeneration_level", 2);
		MOON_WATER_STRENGTH = i(b, "passive_water_strength_level", 2);
		MOON_SLOWNESS = b.comment("PLACEHOLDER (user OK 2026-10-29): Slowness level used by Tidal Grasp, Riptide and Tsunami.").defineInRange("slowness_level", 1, 1, 255);
		MOON_A1_COST = b.comment("Attack 1 'tidal grasp': next hit pulls the target toward you + Slowness.").defineInRange("attack1_cost", 25.0, 0, 100000);
		MOON_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 25);
		MOON_A1_PULL = d(b, "attack1_pull_blocks", 5);
		MOON_A1_PULL_NIGHT = d(b, "attack1_pull_blocks_night", 8);
		MOON_A1_SLOW_SECONDS = i(b, "attack1_slowness_seconds", 3);
		MOON_A2_COST = b.comment("Attack 2 'undertow': nearest visible unallied player in range: pulled toward you + damage.").defineInRange("attack2_cost", 45.0, 0, 100000);
		MOON_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 60);
		MOON_A2_RANGE = i(b, "attack2_range", 15);
		MOON_A2_RANGE_NIGHT = i(b, "attack2_range_night", 25);
		MOON_A2_PULL = d(b, "attack2_pull_blocks", 6);
		MOON_A2_PULL_NIGHT = d(b, "attack2_pull_blocks_night", 9);
		MOON_A2_DAMAGE = d(b, "attack2_damage", 4);
		MOON_PB1_COST = b.comment("Powerblast 1 'repulsed': unallied players in radius: damage + pushed away.").defineInRange("powerblast1_cost", 60.0, 0, 100000);
		MOON_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 120);
		MOON_PB1_RADIUS = i(b, "powerblast1_radius", 10);
		MOON_PB1_RADIUS_NIGHT = i(b, "powerblast1_radius_night", 15);
		MOON_PB1_DAMAGE = d(b, "powerblast1_damage", 6);
		MOON_PB1_PUSH = d(b, "powerblast1_push_blocks", 7);
		MOON_PB1_PUSH_NIGHT = b.comment("PLACEHOLDER (user OK): 'knockback increases' at night.").defineInRange("powerblast1_push_blocks_night", 10.0, 0, 100000);
		MOON_PB2_COST = b.comment("Powerblast 2 'riptide': nearest visible unallied player in range: no sprint speed + Slowness.").defineInRange("powerblast2_cost", 100.0, 0, 100000);
		MOON_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 160);
		MOON_PB2_RANGE = i(b, "powerblast2_range", 20);
		MOON_PB2_RANGE_NIGHT = i(b, "powerblast2_range_night", 30);
		MOON_PB2_SECONDS = i(b, "powerblast2_seconds", 5);
		MOON_PB2_SECONDS_NIGHT = i(b, "powerblast2_seconds_night", 7);
		MOON_AB1_COST = b.comment("Ability 1 'HIGH TIDE': a tidal field where you cast it; unallied players inside are pulled to its centre every pulse. Press again (free) to push them all out and end it.").defineInRange("ability1_cost", 150.0, 0, 100000);
		MOON_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 240);
		MOON_AB1_SECONDS = i(b, "ability1_seconds", 45);
		MOON_AB1_RADIUS = i(b, "ability1_radius", 20);
		MOON_AB1_RADIUS_NIGHT = i(b, "ability1_radius_night", 30);
		MOON_AB1_PULSE_SECONDS = b.comment("PLACEHOLDER (user OK): seconds between pulls.").defineInRange("ability1_pulse_seconds", 3, 1, 100000);
		MOON_AB1_PULL = d(b, "ability1_pull_blocks", 3);
		MOON_AB1_PULL_NIGHT = b.comment("PLACEHOLDER (user OK).").defineInRange("ability1_pull_blocks_night", 5.0, 0, 100000);
		MOON_AB1_PUSH = b.comment("PLACEHOLDER (user OK): second press push.").defineInRange("ability1_push_blocks", 7.0, 0, 100000);
		MOON_AB1_PUSH_NIGHT = b.comment("PLACEHOLDER (user OK).").defineInRange("ability1_push_blocks_night", 10.0, 0, 100000);
		MOON_AB2_COST = b.comment("Ability 2 'TSUNAMI': unallied, non-Reinforced players in radius and in sight: launched away + damage + Slowness.").defineInRange("ability2_cost", 300.0, 0, 100000);
		MOON_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 480);
		MOON_AB2_RADIUS = i(b, "ability2_radius", 40);
		MOON_AB2_RADIUS_NIGHT = i(b, "ability2_radius_night", 60);
		MOON_AB2_PUSH = d(b, "ability2_push_blocks", 15);
		MOON_AB2_DAMAGE = d(b, "ability2_damage", 15);
		MOON_AB2_DAMAGE_NIGHT = d(b, "ability2_damage_night", 20);
		MOON_AB2_SLOW_SECONDS = i(b, "ability2_slowness_seconds", 5);
		b.pop();

		SPEC = b.build();
	}

	private WaterConfig() {
	}
}
