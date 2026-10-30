package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Air DEFAULT kit + Sky / Storm / Gale sub-aspects (ASPECT GUIDE + user "use rec" 2026-10-29):
 * zyrenmoddedcontent_v2-air-server.toml.
 *
 * Values from the guide are the defaults. PLACEHOLDER = not given by the guide; chosen so the ability
 * works, change freely. "Damage points" are health points (2 = 1 heart). Times in seconds unless the
 * name says ticks. Effect levels as the guide writes them (Speed II = 2). Air has no Reinforce Body in
 * the guide yet: Light's is used (framework fallback).
 */
public final class AirConfig {
	public static final ModConfigSpec SPEC;

	// general
	public static final ModConfigSpec.DoubleValue THROW_SPEED, STONE_IMPACT_DAMAGE;
	public static final ModConfigSpec.IntValue SUFFOCATE_AIR_PER_TICK;
	public static final ModConfigSpec.DoubleValue SUFFOCATE_DAMAGE;
	// passive
	public static final ModConfigSpec.DoubleValue PASSIVE_QI_PER_MINUTE, PASSIVE_FALL_REDUCTION, PASSIVE_STEP_HEIGHT;
	public static final ModConfigSpec.IntValue PASSIVE_SPEED;
	// attack 1 / 2
	public static final ModConfigSpec.DoubleValue A1_COST, A2_COST, A2_MULTIPLIER;
	public static final ModConfigSpec.IntValue A1_COOLDOWN, A1_HITS, A1_KNOCKBACK, A1_BLIND_LEVEL, A1_BLIND_SECONDS, A2_COOLDOWN, A2_STUN_TICKS;
	// powerblasts
	public static final ModConfigSpec.DoubleValue PB1_COST, PB1_THROW, PB2_COST, PB2_HEIGHT, PB2_DAMAGE;
	public static final ModConfigSpec.IntValue PB1_COOLDOWN, PB1_RANGE, PB2_COOLDOWN, PB2_RANGE;
	// abilities
	public static final ModConfigSpec.DoubleValue AB1_COST, AB1_ELEMENTAL_DAMAGE, AB1_OTHER_DAMAGE, AB2_COST, AB2_DAMAGE;
	public static final ModConfigSpec.IntValue AB1_COOLDOWN, AB1_SECONDS, AB1_RADIUS, AB1_SLOWNESS, AB2_COOLDOWN, AB2_SECONDS, AB2_RADIUS;

	// ---- SKY ----
	public static final ModConfigSpec.DoubleValue SKY_A1_COST, SKY_A1_EXTRA, SKY_A1_THROW, SKY_A2_COST, SKY_A2_UP, SKY_A2_THROW, SKY_A2_DAMAGE,
			SKY_PB1_COST, SKY_PB1_DAMAGE, SKY_PB2_COST, SKY_AB1_COST, SKY_AB2_COST;
	public static final ModConfigSpec.IntValue SKY_A1_COOLDOWN, SKY_A1_STUN_SECONDS, SKY_A2_COOLDOWN, SKY_A2_RADIUS, SKY_PB1_COOLDOWN, SKY_PB1_NULLIFY_SECONDS,
			SKY_PB2_COOLDOWN, SKY_PB2_WINDOW_SECONDS, SKY_PB2_SUFFOCATE_SECONDS, SKY_PB2_NAUSEA_SECONDS, SKY_AB1_COOLDOWN, SKY_AB1_SECONDS, SKY_AB1_RADIUS,
			SKY_AB1_OPTIONS, SKY_AB1_PICK_SECONDS, SKY_AB2_COOLDOWN, SKY_AB2_SECONDS, SKY_AB2_RADIUS;
	// ---- STORM ----
	public static final ModConfigSpec.DoubleValue STORM_A1_COST, STORM_A1_MULTIPLIER, STORM_PB1_COST, STORM_PB1_QI, STORM_PB1_FREE_MAX_COST,
			STORM_PB2_COST, STORM_PB2_EXTRA, STORM_PB2_LIGHTNING_DAMAGE, STORM_AB1_COST, STORM_AB1_DISCOUNT, STORM_AB2_COST, STORM_AB2_HEAL;
	public static final ModConfigSpec.IntValue STORM_A1_COOLDOWN, STORM_A1_HITS, STORM_PB1_COOLDOWN, STORM_PB1_RADIUS, STORM_PB2_COOLDOWN,
			STORM_AB1_COOLDOWN, STORM_AB1_SECONDS, STORM_AB1_RADIUS, STORM_AB1_SELF_SECONDS, STORM_AB1_REGEN, STORM_AB1_SPEED,
			STORM_AB2_COOLDOWN, STORM_AB2_STUN_SECONDS;
	// ---- GALE ----
	public static final ModConfigSpec.DoubleValue GALE_PASSIVE_QI_PER_MINUTE, GALE_PB1_COST, GALE_PB1_HEAL, GALE_PB2_COST, GALE_AB1_COST,
			GALE_AB1_DAMAGE, GALE_AB1_STEAL, GALE_AB1_EXPLOSION, GALE_AB2_COST, GALE_AB2_SLAM_SPEED;
	public static final ModConfigSpec.IntValue GALE_ROCKETS, GALE_ROCKET_FLIGHT, GALE_PB1_COOLDOWN, GALE_PB1_RADIUS, GALE_PB1_BLOCK_SECONDS,
			GALE_PB2_COOLDOWN, GALE_PB2_RADIUS, GALE_PB2_SECONDS, GALE_AB1_COOLDOWN, GALE_AB1_SECONDS, GALE_AB2_COOLDOWN, GALE_AB2_RADIUS,
			GALE_AB2_HEIGHT, GALE_AB2_SELF_SECONDS;

	private static ModConfigSpec.DoubleValue d(ModConfigSpec.Builder b, String name, double def) {
		return b.defineInRange(name, def, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue i(ModConfigSpec.Builder b, String name, int def) {
		return b.defineInRange(name, def, 0, 1_000_000);
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("general");
		THROW_SPEED = b.comment("PLACEHOLDER: speed (blocks/tick) of every Air throw. Solid blocks stop a throw.").defineInRange("throw_speed", 1.6, 0.1, 10);
		STONE_IMPACT_DAMAGE = b.comment("Piss off mate: damage when the thrown target hits a stone-type block.").defineInRange("stone_impact_damage", 10.0, 0, 100000);
		SUFFOCATE_AIR_PER_TICK = b.comment("PLACEHOLDER (user OK): Qi suffocation drains this much air per tick (300 = full bar; 3 = empty in ~5 s).").defineInRange("suffocate_air_per_tick", 3, 1, 300);
		SUFFOCATE_DAMAGE = b.comment("PLACEHOLDER (user OK): damage per second once the air bar is empty (vanilla drowning = 2).").defineInRange("suffocate_damage_per_second", 2.0, 0, 100000);
		b.pop();

		b.comment("Passive: Speed, less fall damage, taller step, no knockback. Sky and Storm use this passive too.").push("passive");
		PASSIVE_QI_PER_MINUTE = d(b, "qi_per_minute", 5);
		PASSIVE_SPEED = i(b, "speed_level", 2);
		PASSIVE_FALL_REDUCTION = b.defineInRange("fall_damage_reduction", 0.85, 0, 1);
		PASSIVE_STEP_HEIGHT = b.comment("Step height in blocks (vanilla 0.6).").defineInRange("step_height", 2.0, 0.6, 10);
		b.pop();

		b.comment("Attack 1 'pressuried hit': the next N hits knock the TARGET back (user rec: the sheet said 'user') + Blindness.").push("attack_1");
		A1_COST = d(b, "cost", 15);
		A1_COOLDOWN = i(b, "cooldown_seconds", 60);
		A1_HITS = b.defineInRange("hits", 4, 1, 1000);
		A1_KNOCKBACK = i(b, "knockback_level", 5);
		A1_BLIND_LEVEL = i(b, "blindness_level", 2);
		A1_BLIND_SECONDS = b.comment("PLACEHOLDER: Blindness length.").defineInRange("blindness_seconds", 3, 0, 100000);
		b.pop();

		b.comment("Attack 2 'stabby wind': next hit x damage + stun.").push("attack_2");
		A2_COST = d(b, "cost", 30);
		A2_COOLDOWN = i(b, "cooldown_seconds", 60);
		A2_MULTIPLIER = b.defineInRange("damage_multiplier", 2.0, 1, 100);
		A2_STUN_TICKS = b.comment("PLACEHOLDER (user OK): stun length in ticks.").defineInRange("stun_ticks", 40, 0, 100000);
		b.pop();

		b.comment("Powerblast 1 'piss off mate': nearest unallied player in range thrown horizontally; hitting stone hurts (general.stone_impact_damage).").push("powerblast_1");
		PB1_COST = d(b, "cost", 40);
		PB1_COOLDOWN = i(b, "cooldown_seconds", 110);
		PB1_RANGE = b.comment("PLACEHOLDER (user OK).").defineInRange("range", 30, 1, 256);
		PB1_THROW = d(b, "throw_blocks", 15);
		b.pop();

		b.comment("Powerblast 2 'sky high': nearest unallied player in range thrown straight up + damage.").push("powerblast_2");
		PB2_COST = d(b, "cost", 90);
		PB2_COOLDOWN = i(b, "cooldown_seconds", 120);
		PB2_RANGE = i(b, "range", 15);
		PB2_HEIGHT = d(b, "height_blocks", 35);
		PB2_DAMAGE = d(b, "damage", 4);
		b.pop();

		b.comment("Ability 1 'my domain bro': unallied Qi users in radius at cast: elemental (Nature family) take damage + Slowness for the duration; anyone else takes more damage.").push("ability_1");
		AB1_COST = d(b, "cost", 100);
		AB1_COOLDOWN = i(b, "cooldown_seconds", 280);
		AB1_SECONDS = i(b, "seconds", 120);
		AB1_RADIUS = i(b, "radius", 30);
		AB1_ELEMENTAL_DAMAGE = d(b, "elemental_damage", 4);
		AB1_SLOWNESS = b.comment("PLACEHOLDER: Slowness level.").defineInRange("slowness_level", 1, 1, 255);
		AB1_OTHER_DAMAGE = d(b, "non_elemental_damage", 8);
		b.pop();

		b.comment("Ability 2 'violence was the option': unallied players in radius: damage + Nausea for the duration.").push("ability_2");
		AB2_COST = d(b, "cost", 200);
		AB2_COOLDOWN = i(b, "cooldown_seconds", 380);
		AB2_SECONDS = i(b, "seconds", 60);
		AB2_RADIUS = i(b, "radius", 50);
		AB2_DAMAGE = d(b, "damage", 18);
		b.pop();

		// ================================================================ SKY
		b.comment("AIR -> SKY (sub-aspect). Passive = the Air passive.").push("sub_sky");
		SKY_A1_COST = b.comment("Attack 1 'ONE PUNCH': stays armed until an UNARMED hit: +damage, thrown back; hitting stone stuns.").defineInRange("attack1_cost", 20.0, 0, 100000);
		SKY_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 60);
		SKY_A1_EXTRA = d(b, "attack1_extra_damage", 15);
		SKY_A1_THROW = d(b, "attack1_throw_blocks", 14);
		SKY_A1_STUN_SECONDS = i(b, "attack1_stone_stun_seconds", 5);
		SKY_A2_COST = b.comment("Attack 2 'pressure wave': unallied players in radius: thrown up + away + damage.").defineInRange("attack2_cost", 40.0, 0, 100000);
		SKY_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 280);
		SKY_A2_RADIUS = i(b, "attack2_radius", 15);
		SKY_A2_UP = d(b, "attack2_up_blocks", 15);
		SKY_A2_THROW = d(b, "attack2_throw_blocks", 20);
		SKY_A2_DAMAGE = d(b, "attack2_damage", 2);
		SKY_PB1_COST = b.comment("Powerblast 1 'imma just do this': stays armed until it hits an Air-active player: Air nullified + extra damage.").defineInRange("powerblast1_cost", 55.0, 0, 100000);
		SKY_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 360);
		SKY_PB1_NULLIFY_SECONDS = i(b, "powerblast1_nullify_seconds", 15);
		SKY_PB1_DAMAGE = d(b, "powerblast1_extra_damage", 2);
		SKY_PB2_COST = b.comment("Powerblast 2 'u will die': next hit on a player within the window: suffocation + Nausea. Can break core.").defineInRange("powerblast2_cost", 70.0, 0, 100000);
		SKY_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 280);
		SKY_PB2_WINDOW_SECONDS = i(b, "powerblast2_window_seconds", 20);
		SKY_PB2_SUFFOCATE_SECONDS = i(b, "powerblast2_suffocate_seconds", 10);
		SKY_PB2_NAUSEA_SECONDS = i(b, "powerblast2_nausea_seconds", 5);
		SKY_AB1_COST = b.comment("Ability 1 'sorry mate ur forgor rent': pick one of the nearest players (name + aspect) in a screen; they suffocate. No pick in time = the nearest.").defineInRange("ability1_cost", 120.0, 0, 100000);
		SKY_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 480);
		SKY_AB1_SECONDS = i(b, "ability1_seconds", 40);
		SKY_AB1_RADIUS = i(b, "ability1_radius", 20);
		SKY_AB1_OPTIONS = b.defineInRange("ability1_max_options", 5, 1, 20);
		SKY_AB1_PICK_SECONDS = b.comment("PLACEHOLDER (user OK): time to pick before the nearest is chosen.").defineInRange("ability1_pick_seconds", 10, 1, 120);
		SKY_AB2_COST = b.comment("Ability 2 'did u need oxygen?': aura: unallied players inside suffocate; Air users inside are nullified.").defineInRange("ability2_cost", 230.0, 0, 100000);
		SKY_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 690);
		SKY_AB2_SECONDS = i(b, "ability2_seconds", 60);
		SKY_AB2_RADIUS = i(b, "ability2_radius", 30);
		b.pop();

		// ================================================================ STORM
		b.comment("AIR -> STORM (sub-aspect). Passive = the Air passive. Attack 2 'windy bs' isn't finished in the guide: the default Air Attack 2 is used until it is.").push("sub_storm");
		STORM_A1_COST = b.comment("Attack 1 'just a stormy anime move': lightning strikes you (harmless), next N hits x damage.").defineInRange("attack1_cost", 20.0, 0, 100000);
		STORM_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 60);
		STORM_A1_HITS = b.defineInRange("attack1_hits", 3, 1, 1000);
		STORM_A1_MULTIPLIER = b.defineInRange("attack1_damage_multiplier", 2.0, 1, 100);
		STORM_PB1_COST = b.comment("Powerblast 1 'Cat 5 storm': allies (not you) in radius under open sky: harmless lightning + Qi; already full = their priciest usable ability up to the max cost is cast for free (still goes on cooldown). Can break core.").defineInRange("powerblast1_cost", 300.0, 0, 100000);
		STORM_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 480);
		STORM_PB1_RADIUS = i(b, "powerblast1_radius", 30);
		STORM_PB1_QI = d(b, "powerblast1_qi_given", 300);
		STORM_PB1_FREE_MAX_COST = d(b, "powerblast1_free_ability_max_cost", 300);
		STORM_PB2_COST = b.comment("Powerblast 2 'a electric shock': next hit +damage and a lightning strike on the target.").defineInRange("powerblast2_cost", 50.0, 0, 100000);
		STORM_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 120);
		STORM_PB2_EXTRA = d(b, "powerblast2_extra_damage", 4);
		STORM_PB2_LIGHTNING_DAMAGE = b.comment("PLACEHOLDER: the strike's damage (vanilla lightning = 5).").defineInRange("powerblast2_lightning_damage", 5.0, 0, 100000);
		STORM_AB1_COST = b.comment("Ability 1 'Storm front': thunderstorm; aura: allied Electric users inside pay less Qi; you get Regeneration + Speed for a while.").defineInRange("ability1_cost", 100.0, 0, 100000);
		STORM_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 600);
		STORM_AB1_SECONDS = i(b, "ability1_seconds", 120);
		STORM_AB1_RADIUS = i(b, "ability1_radius", 30);
		STORM_AB1_DISCOUNT = b.defineInRange("ability1_electric_discount", 0.5, 0, 1);
		STORM_AB1_SELF_SECONDS = i(b, "ability1_self_buff_seconds", 60);
		STORM_AB1_REGEN = i(b, "ability1_regeneration_level", 4);
		STORM_AB1_SPEED = i(b, "ability1_speed_level", 4);
		STORM_AB2_COST = b.comment("Ability 2 'more anime bullshit': stays armed until it hits a player: their Qi halved + stun; you heal.").defineInRange("ability2_cost", 200.0, 0, 100000);
		STORM_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 1200);
		STORM_AB2_STUN_SECONDS = b.comment("PLACEHOLDER (user OK).").defineInRange("ability2_stun_seconds", 3, 0, 100000);
		STORM_AB2_HEAL = d(b, "ability2_heal", 10);
		b.pop();

		// ================================================================ GALE
		b.comment("AIR -> GALE (sub-aspect). Its passive REPLACES the Air passive. Attack 1 / 2 = the default Air ones.").push("sub_gale");
		GALE_PASSIVE_QI_PER_MINUTE = b.comment("Passive: an elytra (Unbreaking III, Mending) + endless rockets; your chestplate is stored in your inventory meanwhile. No fall damage, maces deal no damage, the elytra can't be removed.").defineInRange("passive_qi_per_minute", 5.0, 0, 100000);
		GALE_ROCKETS = b.comment("Rockets kept topped up.").defineInRange("passive_rockets", 3, 1, 64);
		GALE_ROCKET_FLIGHT = b.comment("Rocket flight duration (1-3).").defineInRange("passive_rocket_flight", 3, 1, 3);
		GALE_PB1_COST = b.comment("Powerblast 1 'did i say u could fly?': unallied players in radius lose their elytra (dropped; a Gale elytra is deleted and that player's Gale passive is off for a while); you heal.").defineInRange("powerblast1_cost", 100.0, 0, 100000);
		GALE_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 120);
		GALE_PB1_RADIUS = i(b, "powerblast1_radius", 50);
		GALE_PB1_HEAL = d(b, "powerblast1_heal", 15);
		GALE_PB1_BLOCK_SECONDS = b.comment("PLACEHOLDER (user OK): Gale passive off after their elytra is ripped away.").defineInRange("powerblast1_gale_block_seconds", 10, 0, 100000);
		GALE_PB2_COST = b.comment("Powerblast 2 'traffic controller': unallied Gale-form players in radius: Gale passive off for the duration.").defineInRange("powerblast2_cost", 150.0, 0, 100000);
		GALE_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 500);
		GALE_PB2_RADIUS = i(b, "powerblast2_radius", 50);
		GALE_PB2_SECONDS = i(b, "powerblast2_seconds", 150);
		GALE_AB1_COST = b.comment("Ability 1 'bomber aura': a bow (Infinity) for the duration; its arrows explode on the ground; hitting a player: damage + steal Qi.").defineInRange("ability1_cost", 200.0, 0, 100000);
		GALE_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 360);
		GALE_AB1_SECONDS = i(b, "ability1_seconds", 60);
		GALE_AB1_DAMAGE = d(b, "ability1_arrow_damage", 10);
		GALE_AB1_STEAL = d(b, "ability1_qi_steal", 40);
		GALE_AB1_EXPLOSION = b.comment("PLACEHOLDER (user OK): explosion power (no block damage).").defineInRange("ability1_explosion_power", 2.0, 0, 10);
		GALE_AB2_COST = b.comment("Ability 2 'i can move u?': unallied players in radius who fly / glide or are at or above the height are slammed down; you: no passive cost + creative flight for a while.").defineInRange("ability2_cost", 300.0, 0, 100000);
		GALE_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 700);
		GALE_AB2_RADIUS = b.comment("PLACEHOLDER (user OK).").defineInRange("ability2_radius", 100, 1, 1024);
		GALE_AB2_HEIGHT = b.defineInRange("ability2_height", 200, -64, 512);
		GALE_AB2_SLAM_SPEED = b.comment("PLACEHOLDER: downward speed (blocks/tick).").defineInRange("ability2_slam_speed", 3.0, 0.5, 10);
		GALE_AB2_SELF_SECONDS = i(b, "ability2_self_seconds", 30);
		b.pop();

		SPEC = b.build();
	}

	private AirConfig() {
	}
}
