package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Physical DEFAULT kit + Warrior / Body / Instinct sub-aspects (ASPECT GUIDE + user "rec" 2026-11-06):
 * zyrenmoddedcontent_v2-physical-server.toml.
 *
 * Values from the guide are the defaults. PLACEHOLDER = not given by the guide; chosen so the ability
 * works, change freely. "Damage points" are health points (2 = 1 heart). Times in seconds unless
 * named _ticks. "Attack speed N" = Haste N (project convention), except Warrior's exact attributes.
 * Physical keeps its old Reinforce Body (abilities.toml).
 */
public final class PhysicalConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.ConfigValue<List<? extends String>> MOBILITY_ABILITIES;

	// DEFAULT
	public static final ModConfigSpec.DoubleValue PASSIVE_QI_PER_5MIN, A1_COST, A1_PER_HIT, A2_COST, A2_UP, A2_DAMAGE, PB1_COST, PB1_BLOCKS, PB2_COST,
			PB2_REFLECT_CHANCE, AB1_COST, AB2_COST, AB2_THROW, AB2_STONE_DAMAGE, AB2_DAMAGE;
	public static final ModConfigSpec.IntValue PASSIVE_STRENGTH, PASSIVE_HASTE, PASSIVE_SPEED, A1_COOLDOWN, A1_SECONDS, A2_COOLDOWN, A2_RANGE, PB1_COOLDOWN,
			PB2_COOLDOWN, PB2_SECONDS, PB2_DEFLECTS, AB1_COOLDOWN, AB1_SECONDS, AB1_STRENGTH, AB1_HASTE, AB1_SPEED, AB1_GAP_TICKS, AB2_COOLDOWN, AB2_RANGE,
			AB2_WINDOW_SECONDS, AB2_LOCK_SECONDS;
	// WARRIOR
	public static final ModConfigSpec.DoubleValue WR_PASSIVE_QI_PER_MINUTE, WR_KNOCKBACK, WR_ATTACK_SPEED, WR_DAMAGE, WR_A1_COST, WR_A1_BONUS, WR_A1_CRIT_BONUS,
			WR_A2_COST, WR_A2_CRIT_MULTIPLIER, WR_PB1_COST, WR_PB1_HOP, WR_PB1_PARRY_BONUS, WR_PB2_COST, WR_PB2_KB_RESIST, WR_PB2_DAMAGE, WR_AB1_COST,
			WR_AB1_REFUND, WR_AB1_STACK_DAMAGE, WR_AB2_COST, WR_AB2_RANGE, WR_AB2_ARC, WR_AB2_DAMAGE, WR_AB2_BLEED_DAMAGE;
	public static final ModConfigSpec.IntValue WR_A1_COOLDOWN, WR_A1_SECONDS, WR_A2_COOLDOWN, WR_A2_WHIFF_COOLDOWN, WR_A2_SECONDS, WR_A2_STAGGER_TICKS,
			WR_PB1_COOLDOWN, WR_PB1_DODGE_TICKS, WR_PB1_PARRY_WINDOW_SECONDS, WR_PB2_COOLDOWN, WR_PB2_SECONDS, WR_PB2_RADIUS, WR_AB1_COOLDOWN, WR_AB1_SECONDS,
			WR_AB1_MAX_STACKS, WR_AB2_COOLDOWN, WR_AB2_BLEED_SECONDS, WR_AB2_REINFORCE_BREAK_SECONDS;
	// BODY
	public static final ModConfigSpec.DoubleValue BD_PASSIVE_QI_PER_MINUTE, BD_STEP, BD_A1_COST, BD_A1_EXTRA, BD_A2_COST, BD_A2_HORIZONTAL, BD_A2_VERTICAL,
			BD_PB1_COST, BD_PB1_DAMAGE, BD_PB2_COST, BD_PB2_DAMAGE, BD_PB2_FOLLOW, BD_AB1_COST, BD_AB1_STEP, BD_AB2_COST, BD_AB2_UP, BD_AB2_FLING;
	public static final ModConfigSpec.IntValue BD_STRENGTH, BD_SPEED, BD_A1_COOLDOWN, BD_A1_HITS, BD_A2_COOLDOWN, BD_PB1_COOLDOWN, BD_PB1_RANGE, BD_PB1_THROUGH,
			BD_PB2_COOLDOWN, BD_PB2_RANGE, BD_PB2_PUNCHES, BD_PB2_INTERVAL_TICKS, BD_AB1_COOLDOWN, BD_AB1_SECONDS, BD_AB1_STRENGTH, BD_AB1_SPEED, BD_AB1_HASTE,
			BD_AB1_GLOW_RADIUS, BD_AB2_COOLDOWN, BD_AB2_SECONDS, BD_AB2_CRATER_RADIUS, BD_AB2_CRATER_DEPTH, BD_AB2_FLING_RADIUS;
	// INSTINCT
	public static final ModConfigSpec.DoubleValue IN_PASSIVE_QI_PER_MINUTE, IN_DODGE_CHANCE, IN_DODGE_BLOCKS, IN_DODGE_REDUCTION, IN_A1_COST, IN_A1_BOOST,
			IN_A2_COST, IN_PB1_COST, IN_PB1_REACH, IN_PB2_COST, IN_AB1_COST, IN_AB2_COST;
	public static final ModConfigSpec.IntValue IN_DODGE_MAX, IN_DODGE_REGEN_SECONDS, IN_DODGE_GAP_SECONDS, IN_DODGE_TICKS, IN_A1_COOLDOWN, IN_A1_SECONDS,
			IN_A1_HASTE, IN_A1_HITS, IN_A2_COOLDOWN, IN_A2_BLOCKS, IN_A2_SECONDS, IN_PB1_COOLDOWN, IN_PB1_SECONDS, IN_PB1_RADIUS, IN_PB2_COOLDOWN,
			IN_PB2_SECONDS, IN_PB2_DETECT, IN_PB2_ASPECT, IN_PB2_SUB, IN_PB2_FULL, IN_AB1_COOLDOWN, IN_AB1_HITS, IN_AB1_SECONDS, IN_AB2_COOLDOWN,
			IN_AB2_SECONDS, IN_AB2_STRENGTH, IN_AB2_SPEED, IN_AB2_HASTE, IN_AB2_MAX_DASHES, IN_AB2_REGEN_DASHES, IN_AB2_REGEN_SECONDS;

	private static ModConfigSpec.DoubleValue d(ModConfigSpec.Builder b, String n, double v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue i(ModConfigSpec.Builder b, String n, int v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	private static ModConfigSpec.DoubleValue ph(ModConfigSpec.Builder b, String n, double v) {
		return b.comment("PLACEHOLDER").defineInRange(n, v, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue phi(ModConfigSpec.Builder b, String n, int v) {
		return b.comment("PLACEHOLDER").defineInRange(n, v, 0, 1_000_000);
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("general");
		MOBILITY_ABILITIES = b.comment("Movement / teleport / escape abilities (names, any case). Blocked while a player is under a movement lock",
				"(Physical 'my fist, ur fase'). Ender pearls and chorus fruit are blocked too.")
				.defineListAllowEmpty("mobility_abilities", List.of("DASH", "JUMPIN BLOW", "SIDESTEP", "to the horizons", "i am the boulder",
						"dashing fists of fury", "my fist, ur fase", "sandworm", "riding the lightning", "burning gaze", "Burning retreat", "water seeker",
						"I Am Good", "BURNN"), o -> o instanceof String);
		b.pop();

		b.comment("DEFAULT passive: Strength, Haste ('attack speed'), Speed.").push("passive");
		PASSIVE_QI_PER_5MIN = d(b, "qi_per_5_minutes", 3);
		PASSIVE_STRENGTH = i(b, "strength_level", 1);
		PASSIVE_HASTE = i(b, "haste_level", 5);
		PASSIVE_SPEED = i(b, "speed_level", 1);
		b.pop();

		b.push("default_kit");
		A1_COST = b.comment("Attack 1 'EAT MY FIST': every landed melee hit adds bonus damage to the following ones; taking damage resets it.").defineInRange("attack1_cost", 15.0, 0, 100000);
		A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 120);
		A1_SECONDS = i(b, "attack1_seconds", 60);
		A1_PER_HIT = d(b, "attack1_damage_per_hit", 2);
		A2_COST = b.comment("Attack 2 'JUMPIN BLOW': up N blocks, then at an unallied player in sight; damage on contact.").defineInRange("attack2_cost", 31.0, 0, 100000);
		A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		A2_UP = d(b, "attack2_up_blocks", 8);
		A2_RANGE = phi(b, "attack2_range", 30);
		A2_DAMAGE = d(b, "attack2_damage", 12);
		PB1_COST = b.comment("Powerblast 1 'DASH': N blocks where you move (else where you look); stops 1 block before an unallied player / at walls.").defineInRange("powerblast1_cost", 20.0, 0, 100000);
		PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 120);
		PB1_BLOCKS = d(b, "powerblast1_blocks", 8);
		PB2_COST = b.comment("Powerblast 2 'DEFLECTING BLOW': blocks N hits (melee, arrows, Qi damage of attacks / powerblasts / ability 1); not explosions / ability 2 / effects; chance to send it back.").defineInRange("powerblast2_cost", 84.0, 0, 100000);
		PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 380);
		PB2_SECONDS = i(b, "powerblast2_seconds", 60);
		PB2_DEFLECTS = b.defineInRange("powerblast2_deflects", 2, 1, 1000);
		PB2_REFLECT_CHANCE = b.defineInRange("powerblast2_reflect_chance", 0.5, 0, 1);
		AB1_COST = b.comment("Ability 1 'EHANCED REFLEXES': passive off; Strength, Haste, Speed; DASH + Reinforce Body free with no cooldown.").defineInRange("ability1_cost", 150.0, 0, 100000);
		AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 680);
		AB1_SECONDS = i(b, "ability1_seconds", 60);
		AB1_STRENGTH = i(b, "ability1_strength_level", 2);
		AB1_HASTE = i(b, "ability1_haste_level", 10);
		AB1_SPEED = i(b, "ability1_speed_level", 2);
		AB1_GAP_TICKS = b.comment("PLACEHOLDER: minimum ticks between free casts (anti-macro).").defineInRange("ability1_free_cast_gap_ticks", 10, 0, 1000);
		AB2_COST = b.comment("Ability 2 'my fist, ur fase': teleport to an unallied player in sight; your next hit (within the window) throws them back + damage through Reinforce; Reinforce + movement abilities locked.").defineInRange("ability2_cost", 200.0, 0, 100000);
		AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 800);
		AB2_RANGE = phi(b, "ability2_range", 30);
		AB2_WINDOW_SECONDS = phi(b, "ability2_hit_window_seconds", 5);
		AB2_THROW = d(b, "ability2_throw_blocks", 14);
		AB2_STONE_DAMAGE = d(b, "ability2_stone_impact_damage", 3);
		AB2_DAMAGE = d(b, "ability2_damage", 12);
		AB2_LOCK_SECONDS = i(b, "ability2_lock_seconds", 10);
		b.pop();

		b.comment("PHYSICAL -> WARRIOR. Its passive REPLACES the Physical passive.").push("sub_warrior");
		WR_PASSIVE_QI_PER_MINUTE = b.comment("Passive: while holding a sword, these are added to your attributes.").defineInRange("passive_qi_per_minute", 1.0, 0, 100000);
		WR_KNOCKBACK = d(b, "passive_attack_knockback", 2);
		WR_ATTACK_SPEED = d(b, "passive_attack_speed", 0.2);
		WR_DAMAGE = d(b, "passive_attack_damage", 4.5);
		WR_A1_COST = b.comment("Attack 1 'Measured strike': fully charged sword hits deal bonus; fully charged crits more. Cooldown starts when it ends.").defineInRange("attack1_cost", 20.0, 0, 100000);
		WR_A1_COOLDOWN = i(b, "attack1_cooldown_seconds_after_end", 60);
		WR_A1_SECONDS = i(b, "attack1_seconds", 30);
		WR_A1_BONUS = d(b, "attack1_bonus_damage", 4);
		WR_A1_CRIT_BONUS = d(b, "attack1_crit_extra_damage", 2);
		WR_A2_COST = b.comment("Attack 2 'PARRY COUNTERPLAY': a melee hit / Attack-slot move landing on you in the window is cancelled, attacker staggered, next sword hit a crit. Whiff = longer cooldown.").defineInRange("attack2_cost", 45.0, 0, 100000);
		WR_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		WR_A2_WHIFF_COOLDOWN = i(b, "attack2_whiff_cooldown_seconds", 600);
		WR_A2_SECONDS = i(b, "attack2_window_seconds", 5);
		WR_A2_STAGGER_TICKS = phi(b, "attack2_stagger_ticks", 40);
		WR_A2_CRIT_MULTIPLIER = ph(b, "attack2_crit_multiplier", 1.5);
		WR_PB1_COST = b.comment("Powerblast 1 'SIDESTEP': hop left / right (last strafe direction, else right); briefly untargetable + dodges projectiles; mobs lose you.").defineInRange("powerblast1_cost", 25.0, 0, 100000);
		WR_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 120);
		WR_PB1_HOP = ph(b, "powerblast1_hop_blocks", 3);
		WR_PB1_DODGE_TICKS = phi(b, "powerblast1_dodge_ticks", 20);
		WR_PB1_PARRY_WINDOW_SECONDS = phi(b, "powerblast1_after_parry_seconds", 3);
		WR_PB1_PARRY_BONUS = d(b, "powerblast1_after_parry_bonus_damage", 5);
		WR_PB2_COST = b.comment("Powerblast 2 'WAR BANNER': banner at your feet; Physical allies in the radius: knockback resistance + sword damage; one unallied hit breaks it. Cooldown starts when it ends.").defineInRange("powerblast2_cost", 80.0, 0, 100000);
		WR_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds_after_end", 240);
		WR_PB2_SECONDS = i(b, "powerblast2_seconds", 120);
		WR_PB2_RADIUS = phi(b, "powerblast2_radius", 10);
		WR_PB2_KB_RESIST = ph(b, "powerblast2_knockback_resistance", 0.5);
		WR_PB2_DAMAGE = ph(b, "powerblast2_sword_damage", 2);
		WR_AB1_COST = b.comment("Ability 1 'FLOWING STANCE': parry / sidestep refund Qi and reset each other's cooldown; parries stack sword damage (cleared when hit).").defineInRange("ability1_cost", 100.0, 0, 100000);
		WR_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 650);
		WR_AB1_SECONDS = i(b, "ability1_seconds", 120);
		WR_AB1_REFUND = ph(b, "ability1_qi_refund", 10);
		WR_AB1_STACK_DAMAGE = ph(b, "ability1_damage_per_stack", 1);
		WR_AB1_MAX_STACKS = phi(b, "ability1_max_stacks", 5);
		WR_AB2_COST = b.comment("Ability 2 'THOUSAND CUTS': sword wave in an arc ahead, hits everything (allies too): damage, bleeding, Reinforce broken + locked.").defineInRange("ability2_cost", 200.0, 0, 100000);
		WR_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 800);
		WR_AB2_RANGE = ph(b, "ability2_range", 8);
		WR_AB2_ARC = ph(b, "ability2_arc_degrees", 120);
		WR_AB2_DAMAGE = ph(b, "ability2_damage", 12);
		WR_AB2_BLEED_DAMAGE = ph(b, "ability2_bleed_damage_per_second", 1);
		WR_AB2_BLEED_SECONDS = phi(b, "ability2_bleed_seconds", 5);
		WR_AB2_REINFORCE_BREAK_SECONDS = phi(b, "ability2_reinforce_break_seconds", 5);
		b.pop();

		b.comment("PHYSICAL -> BODY. Its passive REPLACES the Physical passive.").push("sub_body");
		BD_PASSIVE_QI_PER_MINUTE = b.comment("Passive: with no armor / leather / chainmail / armor tagged zyrenmoddedcontent_v2:qipowered or :cultivatedmade: Strength, step height, Speed.").defineInRange("passive_qi_per_minute", 5.0, 0, 100000);
		BD_STRENGTH = i(b, "passive_strength_level", 2);
		BD_STEP = b.defineInRange("passive_step_height", 4.0, 0.6, 10);
		BD_SPEED = i(b, "passive_speed_level", 3);
		BD_A1_COST = b.comment("Attack 1 'hardend fist': next N bare-fist hits deal extra damage.").defineInRange("attack1_cost", 15.0, 0, 100000);
		BD_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 30);
		BD_A1_HITS = b.defineInRange("attack1_hits", 2, 1, 1000);
		BD_A1_EXTRA = d(b, "attack1_extra_damage", 7);
		BD_A2_COST = b.comment("Attack 2 'to the horizons': huge leap where you move (else where you look); no fall damage.").defineInRange("attack2_cost", 30.0, 0, 100000);
		BD_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 60);
		BD_A2_HORIZONTAL = d(b, "attack2_horizontal_blocks", 50);
		BD_A2_VERTICAL = d(b, "attack2_vertical_blocks", 25);
		BD_PB1_COST = b.comment("Powerblast 1 'i am the boulder': you fly at a player (looked at, else nearest) breaking up to N blocks; damage on contact.").defineInRange("powerblast1_cost", 10.0, 0, 100000);
		BD_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 120);
		BD_PB1_RANGE = i(b, "powerblast1_range", 20);
		BD_PB1_THROUGH = i(b, "powerblast1_max_blocks_broken", 10);
		BD_PB1_DAMAGE = d(b, "powerblast1_damage", 15);
		BD_PB2_COST = b.comment("Powerblast 2 'dashing fists of fury': dash to the nearest unallied player, then N punches while they stay close and in sight.").defineInRange("powerblast2_cost", 150.0, 0, 100000);
		BD_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 180);
		BD_PB2_RANGE = i(b, "powerblast2_range", 12);
		BD_PB2_PUNCHES = i(b, "powerblast2_punches", 6);
		BD_PB2_DAMAGE = d(b, "powerblast2_damage_per_punch", 3);
		BD_PB2_FOLLOW = d(b, "powerblast2_follow_blocks", 4);
		BD_PB2_INTERVAL_TICKS = b.comment("PLACEHOLDER (6 punches in 3 s).").defineInRange("powerblast2_interval_ticks", 10, 1, 1000);
		BD_AB1_COST = b.comment("Ability 1 'expert body builder': Strength, Speed, step height, Haste; every other player within the radius glows.").defineInRange("ability1_cost", 200.0, 0, 100000);
		BD_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 380);
		BD_AB1_SECONDS = i(b, "ability1_seconds", 60);
		BD_AB1_STRENGTH = i(b, "ability1_strength_level", 2);
		BD_AB1_SPEED = i(b, "ability1_speed_level", 2);
		BD_AB1_STEP = b.defineInRange("ability1_step_height", 2.0, 0.6, 10);
		BD_AB1_HASTE = phi(b, "ability1_haste_level", 2);
		BD_AB1_GLOW_RADIUS = i(b, "ability1_glow_radius", 30);
		BD_AB2_COST = b.comment("Ability 2 'kratos crator': launched up; on landing a crater (permanent, spread over ticks); unallied nearby flung up + Nausea + Blindness. No fall damage meanwhile.").defineInRange("ability2_cost", 300.0, 0, 100000);
		BD_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 850);
		BD_AB2_SECONDS = i(b, "ability2_seconds", 10);
		BD_AB2_UP = d(b, "ability2_up_blocks", 30);
		BD_AB2_CRATER_RADIUS = b.comment("PLACEHOLDER (30 wide).").defineInRange("ability2_crater_radius", 15, 0, 64);
		BD_AB2_CRATER_DEPTH = phi(b, "ability2_crater_depth", 15);
		BD_AB2_FLING_RADIUS = phi(b, "ability2_fling_radius", 15);
		BD_AB2_FLING = d(b, "ability2_fling_up_blocks", 5);
		b.pop();

		b.comment("PHYSICAL -> INSTINCT (was Mind). Its passive REPLACES the Physical passive.").push("sub_instinct");
		IN_PASSIVE_QI_PER_MINUTE = b.comment("Passive: when hit, a chance to dash back; that hit and hits during the dash are reduced. Limited dashes that regenerate.").defineInRange("passive_qi_per_minute", 5.0, 0, 100000);
		IN_DODGE_CHANCE = b.defineInRange("passive_dodge_chance", 0.25, 0, 1);
		IN_DODGE_BLOCKS = d(b, "passive_dodge_blocks", 3);
		IN_DODGE_REDUCTION = b.defineInRange("passive_dodge_damage_reduction", 0.75, 0, 1);
		IN_DODGE_MAX = i(b, "passive_max_dashes", 5);
		IN_DODGE_REGEN_SECONDS = i(b, "passive_dash_regen_seconds", 5);
		IN_DODGE_GAP_SECONDS = i(b, "passive_seconds_between_triggers", 2);
		IN_DODGE_TICKS = phi(b, "passive_dash_ticks", 10);
		IN_A1_COST = b.comment("Attack 1 'enhanced combo': Haste; 3 hits in a row without being hurt by that target -> damage output boosted.").defineInRange("attack1_cost", 25.0, 0, 100000);
		IN_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 60);
		IN_A1_SECONDS = i(b, "attack1_seconds", 30);
		IN_A1_HASTE = phi(b, "attack1_haste_level", 2);
		IN_A1_HITS = i(b, "attack1_hits_needed", 3);
		IN_A1_BOOST = d(b, "attack1_damage_boost", 0.5);
		IN_A2_COST = b.comment("Attack 2 'survival instinct': holding a shield, the next N melee / arrow hits are blocked automatically.").defineInRange("attack2_cost", 50.0, 0, 100000);
		IN_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		IN_A2_BLOCKS = i(b, "attack2_blocks", 3);
		IN_A2_SECONDS = phi(b, "attack2_seconds", 60);
		IN_PB1_COST = b.comment("Powerblast 1 'qi guided attacks': nearest player in radius marked (glowing); your Qi damage landing within reach of them also hits them.").defineInRange("powerblast1_cost", 150.0, 0, 100000);
		IN_PB1_COOLDOWN = phi(b, "powerblast1_cooldown_seconds", 120);
		IN_PB1_SECONDS = phi(b, "powerblast1_seconds", 30);
		IN_PB1_RADIUS = i(b, "powerblast1_radius", 10);
		IN_PB1_REACH = d(b, "powerblast1_reach_blocks", 1);
		IN_PB2_COST = b.comment("Powerblast 2 'awareness of the hunter': HUD list of players around you, more info the closer they are.").defineInRange("powerblast2_cost", 200.0, 0, 100000);
		IN_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 180);
		IN_PB2_SECONDS = i(b, "powerblast2_seconds", 60);
		IN_PB2_DETECT = i(b, "powerblast2_detect_radius", 50);
		IN_PB2_ASPECT = i(b, "powerblast2_aspect_radius", 30);
		IN_PB2_SUB = i(b, "powerblast2_subaspect_radius", 20);
		IN_PB2_FULL = i(b, "powerblast2_full_radius", 10);
		IN_AB1_COST = b.comment("Ability 1 'unchained awareness of the parrying blade': passive off; the next N hits on you are blocked and sent back to the attacker.").defineInRange("ability1_cost", 250.0, 0, 100000);
		IN_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 340);
		IN_AB1_HITS = i(b, "ability1_hits", 3);
		IN_AB1_SECONDS = phi(b, "ability1_seconds", 60);
		IN_AB2_COST = b.comment("Ability 2 'qi infused body': Strength, Speed, Haste; passive max dashes + regen raised.").defineInRange("ability2_cost", 300.0, 0, 100000);
		IN_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 890);
		IN_AB2_SECONDS = i(b, "ability2_seconds", 120);
		IN_AB2_STRENGTH = i(b, "ability2_strength_level", 3);
		IN_AB2_SPEED = i(b, "ability2_speed_level", 3);
		IN_AB2_HASTE = i(b, "ability2_haste_level", 3);
		IN_AB2_MAX_DASHES = i(b, "ability2_max_dashes", 10);
		IN_AB2_REGEN_DASHES = b.comment("Guide says '2 per minute'; read as 2 every regen_seconds (user OK 2026-11-06).").defineInRange("ability2_regen_dashes", 2, 0, 1000);
		IN_AB2_REGEN_SECONDS = i(b, "ability2_regen_seconds", 5);
		b.pop();

		SPEC = b.build();
	}

	private PhysicalConfig() {
	}
}
