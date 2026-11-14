package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Space DEFAULT kit + Dimensional / Abyssal / Energy sub-aspects (ASPECT GUIDE + user "rec" 2026-11-14):
 * zyrenmoddedcontent_v2-space-server.toml. Energy replaced Matter in the guide.
 *
 * Values from the guide are the defaults. PLACEHOLDER = not given by the guide. "Damage points" are
 * health points (2 = 1 heart). Times in seconds unless named _ticks. Effect levels: 1 = I.
 * Space has no Reinforce Body of its own (Light's is used).
 */
public final class SpaceConfig {
	public static final ModConfigSpec SPEC;

	// DEFAULT
	public static final ModConfigSpec.DoubleValue PASSIVE_QI_PER_MINUTE, A1_COST, A1_FRACTION, A1_CAP, A2_COST, PB1_COST, PB1_RETURN_HEALTH, PB2_COST,
			AB1_COST, AB2_COST;
	public static final ModConfigSpec.IntValue PASSIVE_RESISTANCE, PASSIVE_CHORUS_SECONDS, A1_COOLDOWN, A2_COOLDOWN, A2_SECONDS, A2_THROTTLE_TICKS,
			PB1_COOLDOWN, PB1_SECONDS, PB2_COOLDOWN, PB2_RANGE, SAFE_CLEAR_RADIUS, AB1_COOLDOWN, AB1_SECONDS, AB1_SPEED, AB1_WEAKNESS, AB1_FATIGUE,
			AB2_COOLDOWN, AB2_SECONDS, AB2_INTERVAL, AB2_RADIUS;
	// DIMENSIONAL
	public static final ModConfigSpec.DoubleValue DM_PASSIVE_COST, DM_A1_COST, DM_A1_OUTPUT, DM_A2_COST, DM_PB1_COST, DM_PB1_UP, DM_PB1_BACK, DM_PB2_COST,
			DM_AB1_COST, DM_AB2_COST, DM_AB2_DAMAGE, DM_AB2_DRAIN;
	public static final ModConfigSpec.IntValue DM_PASSIVE_GAP_TICKS, DM_A1_COOLDOWN, DM_A1_SECONDS, DM_A2_COOLDOWN, DM_A2_SECONDS, DM_PB1_COOLDOWN,
			DM_PB1_RADIUS, DM_PB2_COOLDOWN, DM_PB2_MIN, DM_PB2_MAX, DM_AB1_COOLDOWN, DM_AB1_SECONDS, DM_AB1_USES, DM_AB2_COOLDOWN, DM_AB2_SECONDS,
			DM_AB2_RANGE, DM_AB2_INNER, DM_AB2_OUTER, DM_AB2_DRAIN_RADIUS;
	// ABYSSAL
	public static final ModConfigSpec.DoubleValue AB_A1_COST, AB_A1_FIST, AB_A1_WEAPON, AB_A2_COST, AB_A2_DRAIN, AB_A2_QI_PER_HEALTH, AB_A2_FINAL, AB_AB1_COST,
			AB_AB1_DRAIN, AB_AB1_GAIN, AB_AB2_COST, AB_AB2_DAMAGE;
	public static final ModConfigSpec.IntValue AB_A1_COOLDOWN, AB_A1_SLOW_LEVEL, AB_A1_SLOW_SECONDS, AB_A2_COOLDOWN, AB_A2_RANGE, AB_A2_MAX_SECONDS,
			AB_AB1_COOLDOWN, AB_AB1_SECONDS, AB_AB1_GLOW_RADIUS, AB_AB1_SLOW_RADIUS, AB_AB1_SLOW_LEVEL, AB_AB1_DRAIN_RADIUS, AB_AB2_COOLDOWN,
			AB_AB2_RESET_RADIUS, AB_AB2_KILL_RADIUS, AB_AB2_CLEANSE_RADIUS, AB_AB2_CLEANSE_SECONDS;
	// ENERGY
	public static final ModConfigSpec.DoubleValue EN_PASSIVE_QI_PER_MINUTE, EN_A1_COST, EN_A1_DISTANCE, EN_A1_WALL_DAMAGE, EN_A2_COST, EN_A2_AIR_DAMAGE,
			EN_A2_GROUND_DAMAGE, EN_PB1_COST, EN_PB1_DISTANCE, EN_PB1_LAND_DAMAGE, EN_PB2_COST, EN_PB2_JUMP_CUT, EN_AB1_COST, EN_AB1_DAMAGE, EN_AB2_COST,
			EN_AB2_DAMAGE, EN_AB2_FINAL;
	public static final ModConfigSpec.IntValue EN_SENSE_RADIUS, EN_STILL_TICKS, EN_PHASE_TICKS, EN_A1_COOLDOWN, EN_A1_HITS, EN_A1_SECONDS, EN_A1_ROOT_TICKS,
			EN_A2_COOLDOWN, EN_A2_SECONDS, EN_A2_ROOT_TICKS, EN_PB1_COOLDOWN, EN_PB1_BLIND_TICKS, EN_PB2_COOLDOWN, EN_PB2_SECONDS, EN_PB2_RADIUS,
			EN_PB2_RANGE, EN_AB1_COOLDOWN, EN_AB1_LENGTH, EN_AB1_WIDTH, EN_AB1_LOCK_SECONDS, EN_AB2_COOLDOWN, EN_AB2_SECONDS, EN_AB2_RADIUS,
			EN_AB2_FREE_GAP_SECONDS;

	private static ModConfigSpec.DoubleValue d(ModConfigSpec.Builder b, String n, double v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue i(ModConfigSpec.Builder b, String n, int v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue ph(ModConfigSpec.Builder b, String n, int v) {
		return b.comment("PLACEHOLDER").defineInRange(n, v, 0, 1_000_000);
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("general");
		SAFE_CLEAR_RADIUS = b.comment("'Safe' teleport spots: room to stand, no liquid / fire / magma, and no hostile mob or other player within this radius.")
				.defineInRange("safe_spot_clear_radius", 5, 0, 64);
		b.pop();

		b.comment("DEFAULT passive: a chorus fruit kept topped up (temporary, can't be dropped) + Resistance.").push("passive");
		PASSIVE_QI_PER_MINUTE = d(b, "qi_per_minute", 5);
		PASSIVE_RESISTANCE = i(b, "resistance_level", 1);
		PASSIVE_CHORUS_SECONDS = ph(b, "chorus_refill_seconds", 5);
		b.pop();

		b.push("default_kit");
		A1_COST = b.comment("Attack 1 'enhanced compisiotion of the blade': next hit deals extra = fraction of the target's current health (capped).").defineInRange("attack1_cost", 15.0, 0, 100000);
		A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 120);
		A1_FRACTION = b.defineInRange("attack1_health_fraction", 0.5, 0, 1);
		A1_CAP = d(b, "attack1_max_damage", 10);
		A2_COST = b.comment("Attack 2 'mind scanning': your hits show the target's health / usable Qi / aspect in chat.").defineInRange("attack2_cost", 45.0, 0, 100000);
		A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		A2_SECONDS = i(b, "attack2_seconds", 45);
		A2_THROTTLE_TICKS = ph(b, "attack2_throttle_ticks", 40);
		PB1_COST = b.comment("Powerblast 1 'spatial anchor': marks your spot; key 'Space Anchor Recall' (B) returns you; also automatic at low health or on a lethal hit. Removed after use.")
				.defineInRange("powerblast1_cost", 65.0, 0, 100000);
		PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 60);
		PB1_SECONDS = i(b, "powerblast1_seconds", 480);
		PB1_RETURN_HEALTH = b.comment("Auto-return at or below this many health points (4 = 2 hearts).").defineInRange("powerblast1_return_health", 4.0, 0, 1000);
		PB2_COST = b.comment("Powerblast 2 'relocation': nearest safe spot within range, else your bed; no bed = fails (no Qi spent).").defineInRange("powerblast2_cost", 85.0, 0, 100000);
		PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 120);
		PB2_RANGE = i(b, "powerblast2_range", 250);
		AB1_COST = b.comment("Ability 1 'dimentional shift': hidden Invisibility / Speed / Weakness / Mining Fatigue; untargetable, hidden from Qi sensing, can't die.")
				.defineInRange("ability1_cost", 100.0, 0, 100000);
		AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 360);
		AB1_SECONDS = i(b, "ability1_seconds", 120);
		AB1_SPEED = i(b, "ability1_speed_level", 2);
		AB1_WEAKNESS = i(b, "ability1_weakness_level", 255);
		AB1_FATIGUE = i(b, "ability1_mining_fatigue_level", 1);
		AB2_COST = b.comment("Ability 2 'Hi there': every N seconds teleport behind a random unallied player in the radius.").defineInRange("ability2_cost", 200.0, 0, 100000);
		AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 680);
		AB2_SECONDS = i(b, "ability2_seconds", 120);
		AB2_INTERVAL = i(b, "ability2_interval_seconds", 8);
		AB2_RADIUS = i(b, "ability2_radius", 40);
		b.pop();

		b.comment("SPACE -> DIMENSIONAL. Its passive REPLACES the Space passive.").push("sub_dimensional");
		DM_PASSIVE_COST = b.comment("Passive: sneak + right-click air (empty hand): travel to the mode chosen with the mode key (V). Overworld: 1 Nether / 2 End; Nether: 1 Overworld / 2 End; End: 1 Nether / 2 Overworld.")
				.defineInRange("passive_cost_per_use", 45.0, 0, 100000);
		DM_PASSIVE_GAP_TICKS = ph(b, "passive_gap_ticks", 60);
		DM_A1_COST = b.comment("Attack 1 'blades of the walker': damage output x.").defineInRange("attack1_cost", 30.0, 0, 100000);
		DM_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 120);
		DM_A1_SECONDS = i(b, "attack1_seconds", 30);
		DM_A1_OUTPUT = b.defineInRange("attack1_damage_output", 1.7, 1, 100);
		DM_A2_COST = b.comment("Attack 2 'pushed into a alt reality': armed until your next hit; target gets hidden Weakness 255 / Mining Fatigue / Resistance 255 / Invisibility / Blindness.")
				.defineInRange("attack2_cost", 50.0, 0, 100000);
		DM_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		DM_A2_SECONDS = i(b, "attack2_effect_seconds", 60);
		DM_PB1_COST = b.comment("Powerblast 1 'harmonic shift': unallied players in the radius thrown up and back.").defineInRange("powerblast1_cost", 65.0, 0, 100000);
		DM_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 120);
		DM_PB1_RADIUS = i(b, "powerblast1_radius", 8);
		DM_PB1_UP = d(b, "powerblast1_up_blocks", 15);
		DM_PB1_BACK = d(b, "powerblast1_back_blocks", 30);
		DM_PB2_COST = b.comment("Powerblast 2 'blink': random safe spot between min and max blocks away.").defineInRange("powerblast2_cost", 85.0, 0, 100000);
		DM_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 60);
		DM_PB2_MIN = ph(b, "powerblast2_min_blocks", 20);
		DM_PB2_MAX = i(b, "powerblast2_max_blocks", 100);
		DM_AB1_COST = b.comment("Ability 1 'nexus point': allied Dimensional users' passive teleports go to your marker (replacing one option, see guide) for the duration or N uses.")
				.defineInRange("ability1_cost", 100.0, 0, 100000);
		DM_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 680);
		DM_AB1_SECONDS = i(b, "ability1_seconds", 60);
		DM_AB1_USES = i(b, "ability1_uses", 10);
		DM_AB2_COST = b.comment("Ability 2 'a abyssal rift in reality': rift where you look; inner: pulled in + damage every 2 s; outer: drawn in slowly; drain radius: unallied lose Qi/s + HUD info.")
				.defineInRange("ability2_cost", 200.0, 0, 100000);
		DM_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 680);
		DM_AB2_SECONDS = i(b, "ability2_seconds", 120);
		DM_AB2_RANGE = ph(b, "ability2_range", 30);
		DM_AB2_INNER = i(b, "ability2_inner_radius", 3);
		DM_AB2_OUTER = i(b, "ability2_outer_radius", 15);
		DM_AB2_DAMAGE = d(b, "ability2_damage_every_2s", 1);
		DM_AB2_DRAIN_RADIUS = i(b, "ability2_drain_radius", 50);
		DM_AB2_DRAIN = d(b, "ability2_qi_drain_per_second", 10);
		b.pop();

		b.comment("SPACE -> ABYSSAL. No passive (the Space passive does not apply). Powerblast 1 / 2: none (Space's are used).").push("sub_abyssal");
		AB_A1_COST = b.comment("Attack 1 'void attack': armed until your next hit: extra damage (more bare-fisted) + Slowness.").defineInRange("attack1_cost", 70.0, 0, 100000);
		AB_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 30);
		AB_A1_FIST = d(b, "attack1_fist_damage", 14);
		AB_A1_WEAPON = d(b, "attack1_weapon_damage", 7);
		AB_A1_SLOW_LEVEL = ph(b, "attack1_slowness_level", 2);
		AB_A1_SLOW_SECONDS = ph(b, "attack1_slowness_seconds", 5);
		AB_A2_COST = b.comment("Attack 2 'a slow death': drain the nearest unallied player in range and heal (Qi per health point); when full, deal final damage. Guide '10 per minute' read as per second.")
				.defineInRange("attack2_cost", 200.0, 0, 100000);
		AB_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 380);
		AB_A2_RANGE = i(b, "attack2_range", 8);
		AB_A2_DRAIN = d(b, "attack2_qi_drain_per_second", 10);
		AB_A2_QI_PER_HEALTH = d(b, "attack2_qi_per_health_point", 5);
		AB_A2_FINAL = d(b, "attack2_final_damage", 7);
		AB_A2_MAX_SECONDS = ph(b, "attack2_max_seconds", 120);
		AB_AB1_COST = b.comment("Ability 1 'qi suction': no upfront cost, you get the gain fraction of all Qi drained. Glow + HUD aspects; Slowness near; Qi drain wider.")
				.defineInRange("ability1_cost", 0.0, 0, 100000);
		AB_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 880);
		AB_AB1_SECONDS = i(b, "ability1_seconds", 60);
		AB_AB1_GLOW_RADIUS = i(b, "ability1_glow_radius", 50);
		AB_AB1_SLOW_RADIUS = i(b, "ability1_slowness_radius", 15);
		AB_AB1_SLOW_LEVEL = i(b, "ability1_slowness_level", 2);
		AB_AB1_DRAIN_RADIUS = i(b, "ability1_drain_radius", 20);
		AB_AB1_DRAIN = d(b, "ability1_qi_drain_per_second", 2);
		AB_AB1_GAIN = b.defineInRange("ability1_gain_fraction", 0.5, 0, 1);
		AB_AB2_COST = b.comment("Ability 2 'ERR' (players, not you; bypasses Reinforce): within reset radius damage + core destroyed; within kill radius killed + items deleted;",
				"Corrupted / Poisoned within cleanse radius nullified, then cleansed to Pure.").defineInRange("ability2_cost", 500.0, 0, 100000);
		AB_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 1480);
		AB_AB2_RESET_RADIUS = i(b, "ability2_reset_radius", 5);
		AB_AB2_DAMAGE = d(b, "ability2_damage", 20);
		AB_AB2_KILL_RADIUS = i(b, "ability2_kill_radius", 2);
		AB_AB2_CLEANSE_RADIUS = i(b, "ability2_cleanse_radius", 15);
		AB_AB2_CLEANSE_SECONDS = i(b, "ability2_cleanse_seconds", 15);
		b.pop();

		b.comment("SPACE -> ENERGY (replaced Matter). Its passive REPLACES the Space passive.").push("sub_energy");
		EN_PASSIVE_QI_PER_MINUTE = b.comment("Passive 'Spatial Awareness': players nearby on the HUD; airborne / in the End: Speed I; no fall damage; stand still a while -> pass through 1-block walls briefly.")
				.defineInRange("passive_qi_per_minute", 4.0, 0, 100000);
		EN_SENSE_RADIUS = i(b, "passive_sense_radius", 25);
		EN_STILL_TICKS = i(b, "passive_still_ticks", 60);
		EN_PHASE_TICKS = ph(b, "passive_phase_ticks", 60);
		EN_A1_COST = b.comment("Attack 1 'Fold Punch': next N hits (within the window) teleport the target along your look (up if looking steeply); a wall stops it: + damage + root.")
				.defineInRange("attack1_cost", 20.0, 0, 100000);
		EN_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 25);
		EN_A1_HITS = i(b, "attack1_hits", 3);
		EN_A1_SECONDS = i(b, "attack1_window_seconds", 12);
		EN_A1_DISTANCE = d(b, "attack1_fold_blocks", 4);
		EN_A1_WALL_DAMAGE = d(b, "attack1_wall_damage", 8);
		EN_A1_ROOT_TICKS = ph(b, "attack1_root_ticks", 20);
		EN_A2_COST = b.comment("Attack 2 'Gravity Spike': next hit (within the window): airborne -> slammed down + damage; grounded -> rooted + damage. Reinforce blocks the root only.")
				.defineInRange("attack2_cost", 45.0, 0, 100000);
		EN_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 40);
		EN_A2_SECONDS = i(b, "attack2_window_seconds", 8);
		EN_A2_AIR_DAMAGE = d(b, "attack2_airborne_damage", 12);
		EN_A2_GROUND_DAMAGE = d(b, "attack2_grounded_damage", 6);
		EN_A2_ROOT_TICKS = i(b, "attack2_root_ticks", 30);
		EN_PB1_COST = b.comment("Powerblast 1 'Rift Step': blink along your look, stopping before walls; landing next to a player: damage + Blindness.").defineInRange("powerblast1_cost", 70.0, 0, 100000);
		EN_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 18);
		EN_PB1_DISTANCE = d(b, "powerblast1_blocks", 12);
		EN_PB1_LAND_DAMAGE = d(b, "powerblast1_land_damage", 8);
		EN_PB1_BLIND_TICKS = i(b, "powerblast1_blind_ticks", 20);
		EN_PB2_COST = b.comment("Powerblast 2 'Anchor Well': well where you look; unallied inside pulled in slowly, jump height cut. You are never pulled.").defineInRange("powerblast2_cost", 110.0, 0, 100000);
		EN_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 90);
		EN_PB2_SECONDS = i(b, "powerblast2_seconds", 8);
		EN_PB2_RADIUS = i(b, "powerblast2_radius", 6);
		EN_PB2_RANGE = i(b, "powerblast2_range", 25);
		EN_PB2_JUMP_CUT = b.defineInRange("powerblast2_jump_cut", 0.5, 0, 1);
		EN_AB1_COST = b.comment("Ability 1 'Dimensional Shear': slash ahead: unallied damage, rooted + movement abilities locked, dashes / elytra cancelled. Visual only.").defineInRange("ability1_cost", 180.0, 0, 100000);
		EN_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 280);
		EN_AB1_LENGTH = i(b, "ability1_length", 20);
		EN_AB1_WIDTH = i(b, "ability1_width", 4);
		EN_AB1_DAMAGE = d(b, "ability1_damage", 14);
		EN_AB1_LOCK_SECONDS = i(b, "ability1_lock_seconds", 3);
		EN_AB2_COST = b.comment("Ability 2 'Event Horizon': sphere around you: unallied inside damage every 2 s, no movement abilities, Reinforce suppressed; you + allied Energy users: Rift Step free;",
				"at the end: pulled to the centre + damage.").defineInRange("ability2_cost", 280.0, 0, 100000);
		EN_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 720);
		EN_AB2_SECONDS = i(b, "ability2_seconds", 12);
		EN_AB2_RADIUS = i(b, "ability2_radius", 12);
		EN_AB2_DAMAGE = d(b, "ability2_damage_every_2s", 2);
		EN_AB2_FINAL = d(b, "ability2_final_damage", 10);
		EN_AB2_FREE_GAP_SECONDS = i(b, "ability2_free_rift_step_gap_seconds", 6);
		b.pop();

		SPEC = b.build();
	}

	private SpaceConfig() {
	}
}
