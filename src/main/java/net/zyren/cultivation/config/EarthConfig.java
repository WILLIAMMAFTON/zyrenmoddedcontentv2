package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Earth DEFAULT kit + Magma / Sand / Nature sub-aspects (ASPECT GUIDE + user "rec all" 2026-11-05):
 * zyrenmoddedcontent_v2-earth-server.toml.
 *
 * Values from the guide are the defaults. PLACEHOLDER = not given by the guide; chosen so the ability
 * works, change freely. "Damage points" are health points (2 = 1 heart). Times in seconds. Terrain
 * changes are permanent unless said otherwise. Earth keeps its old Reinforce Body (abilities.toml).
 */
public final class EarthConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;

	public static final ModConfigSpec.DoubleValue PASSIVE_QI_PER_MINUTE;
	public static final ModConfigSpec.IntValue PASSIVE_HASTE, PASSIVE_FORTUNE, PASSIVE_EFFICIENCY;

	public static final ModConfigSpec.DoubleValue A1_COST, A1_EXTRA, A1_PUSH, A2_COST, A2_MULTIPLIER, PB1_COST, PB2_COST, PB2_THROW, AB1_COST, AB1_UP,
			AB2_COST, AB2_DAMAGE, AB2_UP;
	public static final ModConfigSpec.IntValue A1_COOLDOWN, A1_SECONDS, A2_COOLDOWN, A2_HITS, PB1_COOLDOWN, PB1_RANGE, PB2_COOLDOWN, PB2_RANGE,
			AB1_COOLDOWN, AB1_RADIUS, AB2_COOLDOWN, AB2_RADIUS, AB2_SLOW_SECONDS, AB2_CRATER_RADIUS, AB2_CRATER_DEPTH;

	// Magma
	public static final ModConfigSpec.DoubleValue MG_PASSIVE_QI_PER_MINUTE, MG_A1_COST, MG_A1_EXTRA, MG_A1_PUSH, MG_A2_COST, MG_A2_UP, MG_A2_DAMAGE,
			MG_PB1_COST, MG_PB1_DAMAGE, MG_PB2_COST, MG_PB2_DAMAGE, MG_PB2_UP, MG_AB1_COST, MG_AB1_DAMAGE, MG_AB1_UP, MG_AB2_COST, MG_AB2_DAMAGE;
	public static final ModConfigSpec.IntValue MG_A1_COOLDOWN, MG_A2_COOLDOWN, MG_A2_RANGE, MG_PB1_COOLDOWN, MG_PB1_LENGTH, MG_PB1_WIDTH, MG_PB1_BURN_SECONDS,
			MG_PB2_COOLDOWN, MG_PB2_RADIUS, MG_PB2_SLOW_SECONDS, MG_PB2_PATCHES, MG_PB2_PATCH_SECONDS, MG_AB1_COOLDOWN, MG_AB1_SECONDS, MG_AB1_RADIUS,
			MG_AB1_HEIGHT, MG_AB2_COOLDOWN, MG_AB2_RANGE, MG_AB2_RADIUS, MG_AB2_DEPTH, MG_AB2_LAVA_SECONDS, MG_AB2_SLOW_SECONDS, MG_AB2_SELF_SECONDS, MG_AB2_LAVA_POOLS;
	// Sand
	public static final ModConfigSpec.DoubleValue SD_PASSIVE_QI_PER_MINUTE, SD_STEP, SD_A1_COST, SD_A2_COST, SD_PB1_COST, SD_PB2_COST, SD_AB1_COST,
			SD_AB2_COST, SD_AB2_FIRE_DISCOUNT, SD_AB2_COST_MULTIPLIER;
	public static final ModConfigSpec.IntValue SD_SPEED, SD_A1_COOLDOWN, SD_A1_RANGE, SD_A1_SECONDS, SD_A2_COOLDOWN, SD_A2_SECONDS, SD_PB1_COOLDOWN,
			SD_PB1_RADIUS, SD_PB1_CLEAR, SD_PB2_COOLDOWN, SD_PB2_SECONDS, SD_PB2_WALL_SECONDS, SD_AB1_COOLDOWN, SD_AB1_SIZE, SD_AB1_DEPTH, SD_AB1_PULL,
			SD_AB2_COOLDOWN, SD_AB2_SECONDS, SD_AB2_RADIUS;
	public static final ModConfigSpec.BooleanValue SD_AB2_SAND;
	// Nature
	public static final ModConfigSpec.DoubleValue NT_PASSIVE_QI_PER_5MIN, NT_A1_COST, NT_A2_COST, NT_A2_PULL, NT_PB1_COST, NT_PB2_COST, NT_AB1_COST,
			NT_AB1_FLOWER_CHANCE, NT_AB2_COST, NT_AB2_QI_PER_SECOND;
	public static final ModConfigSpec.IntValue NT_PASSIVE_REGEN, NT_BONEMEAL_COOLDOWN_TICKS, NT_A1_COOLDOWN, NT_A1_HITS, NT_A1_POISON_SECONDS,
			NT_A1_ROOT_TICKS, NT_A2_COOLDOWN, NT_A2_RANGE, NT_A2_SLOW_SECONDS, NT_PB1_COOLDOWN, NT_PB1_SECONDS, NT_PB1_RADIUS, NT_PB2_COOLDOWN,
			NT_PB2_SECONDS, NT_PB2_RADIUS, NT_AB1_COOLDOWN, NT_AB1_SECONDS, NT_AB1_RADIUS, NT_AB2_COOLDOWN, NT_AB2_SECONDS, NT_AB2_HEIGHT,
			NT_AB2_NEAR, NT_AB2_ROOT_RADIUS, NT_AB2_BURNS;

	private static ModConfigSpec.DoubleValue d(ModConfigSpec.Builder b, String n, double v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue i(ModConfigSpec.Builder b, String n, int v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("general");
		BLOCKS_PER_TICK = b.comment("Big terrain edits (Sandy grave, Sun's hatred): blocks / columns handled per tick (lower = less lag, slower).")
				.defineInRange("blocks_per_tick", 2000, 50, 1_000_000);
		b.pop();

		b.comment("Passive: standing on stone / dirt: Haste, and mined blocks drop as with Fortune and break as with Efficiency (no real enchantments added).").push("passive");
		PASSIVE_QI_PER_MINUTE = d(b, "qi_per_minute", 3);
		PASSIVE_HASTE = i(b, "haste_level", 2);
		PASSIVE_FORTUNE = i(b, "fortune_level", 2);
		PASSIVE_EFFICIENCY = i(b, "efficiency_level", 3);
		b.pop();

		b.push("default_kit");
		A1_COST = b.comment("Attack 1 'fist of stone': bare-fist hits get extra damage + knockback while it lasts.").defineInRange("attack1_cost", 15.0, 0, 100000);
		A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 15);
		A1_SECONDS = i(b, "attack1_seconds", 35);
		A1_EXTRA = d(b, "attack1_extra_damage", 6);
		A1_PUSH = d(b, "attack1_push_blocks", 3);
		A2_COST = b.comment("Attack 2 'sharpened blade': next N sword hits x damage.").defineInRange("attack2_cost", 35.0, 0, 100000);
		A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 75);
		A2_HITS = b.defineInRange("attack2_hits", 4, 1, 1000);
		A2_MULTIPLIER = b.defineInRange("attack2_multiplier", 2.0, 1, 100);
		PB1_COST = b.comment("Powerblast 1 'fortress of stone': nearest unallied player in range gets a 2-thick hollow stone shell (fills air only).").defineInRange("powerblast1_cost", 60.0, 0, 100000);
		PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 120);
		PB1_RANGE = b.comment("PLACEHOLDER (user OK).").defineInRange("powerblast1_range", 30, 1, 256);
		PB2_COST = b.comment("Powerblast 2 'stone toss': nearest player in range thrown away, then a hollow 5x5x5 gravel ball where they land.").defineInRange("powerblast2_cost", 80.0, 0, 100000);
		PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 450);
		PB2_RANGE = i(b, "powerblast2_range", 25);
		PB2_THROW = b.comment("PLACEHOLDER (user OK).").defineInRange("powerblast2_throw_blocks", 10.0, 0, 100000);
		AB1_COST = b.comment("Ability 1 'heavens staircases': unallied players in radius launched up; a stone pillar grows under each.").defineInRange("ability1_cost", 120.0, 0, 100000);
		AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 600);
		AB1_RADIUS = i(b, "ability1_radius", 35);
		AB1_UP = d(b, "ability1_up_blocks", 50);
		AB2_COST = b.comment("Ability 2 'earth quake': everything in radius (not you / allies): damage, launched, Slowness; crater around you.").defineInRange("ability2_cost", 200.0, 0, 100000);
		AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 850);
		AB2_RADIUS = i(b, "ability2_radius", 30);
		AB2_DAMAGE = d(b, "ability2_damage", 16);
		AB2_UP = b.comment("PLACEHOLDER (user OK).").defineInRange("ability2_up_blocks", 8.0, 0, 100000);
		AB2_SLOW_SECONDS = i(b, "ability2_slowness_seconds", 10);
		AB2_CRATER_RADIUS = b.comment("PLACEHOLDER (user OK).").defineInRange("ability2_crater_radius", 10, 0, 64);
		AB2_CRATER_DEPTH = b.comment("PLACEHOLDER (user OK).").defineInRange("ability2_crater_depth", 4, 0, 32);
		b.pop();

		b.comment("EARTH -> MAGMA (sub-aspect). Its passive REPLACES the Earth passive.").push("sub_magma");
		MG_PASSIVE_QI_PER_MINUTE = b.comment("Passive: on / in lava or magma, or in the Nether: Fire Resistance, Regeneration I, Resistance I.").defineInRange("passive_qi_per_minute", 3.0, 0, 100000);
		MG_A1_COST = b.comment("Attack 1 'flaming fist': stays armed until a bare-fist hit: fire + extra damage + knockback.").defineInRange("attack1_cost", 20.0, 0, 100000);
		MG_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 30);
		MG_A1_EXTRA = d(b, "attack1_extra_damage", 6);
		MG_A1_PUSH = d(b, "attack1_push_blocks", 2);
		MG_A2_COST = b.comment("Attack 2 'volcanic dispare': nearest unallied player in range: launched + damage + fire.").defineInRange("attack2_cost", 30.0, 0, 100000);
		MG_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		MG_A2_RANGE = i(b, "attack2_range", 20);
		MG_A2_UP = d(b, "attack2_up_blocks", 10);
		MG_A2_DAMAGE = d(b, "attack2_damage", 10);
		MG_PB1_COST = b.comment("Powerblast 1 'tectonic blast': a line ahead of you: damage, Knockback V, burning; stone / deepslate under it -> magma.").defineInRange("powerblast1_cost", 50.0, 0, 100000);
		MG_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 150);
		MG_PB1_LENGTH = i(b, "powerblast1_length", 15);
		MG_PB1_WIDTH = i(b, "powerblast1_width", 3);
		MG_PB1_DAMAGE = d(b, "powerblast1_damage", 15);
		MG_PB1_BURN_SECONDS = i(b, "powerblast1_burn_seconds", 5);
		MG_PB2_COST = b.comment("Powerblast 2 'tremor of heat': unallied in radius: damage, Slowness III, launched if on the ground; temporary magma patches.").defineInRange("powerblast2_cost", 80.0, 0, 100000);
		MG_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 280);
		MG_PB2_RADIUS = i(b, "powerblast2_radius", 15);
		MG_PB2_DAMAGE = d(b, "powerblast2_damage", 15);
		MG_PB2_SLOW_SECONDS = i(b, "powerblast2_slowness_seconds", 5);
		MG_PB2_UP = d(b, "powerblast2_up_blocks", 8);
		MG_PB2_PATCHES = b.comment("PLACEHOLDER (user OK).").defineInRange("powerblast2_magma_patches", 20, 0, 1000);
		MG_PB2_PATCH_SECONDS = i(b, "powerblast2_patch_seconds", 10);
		MG_AB1_COST = b.comment("Ability 1 'volcanic rasing': a volcanic-rock platform rises under you (stays); enemies on it thrown up + damage; you: Resistance IV + Fire Resistance.").defineInRange("ability1_cost", 100.0, 0, 100000);
		MG_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 480);
		MG_AB1_SECONDS = i(b, "ability1_seconds", 60);
		MG_AB1_RADIUS = i(b, "ability1_radius", 6);
		MG_AB1_HEIGHT = i(b, "ability1_height", 6);
		MG_AB1_DAMAGE = d(b, "ability1_damage", 10);
		MG_AB1_UP = b.comment("PLACEHOLDER: how high enemies on the platform are thrown.").defineInRange("ability1_enemy_up_blocks", 6.0, 0, 100000);
		MG_AB2_COST = b.comment("Ability 2 'cavous magma bowl': crater (stays) where you look; magma floor; lava pools go after the duration. Unallied inside pulled in + damage + Slowness IV.").defineInRange("ability2_cost", 280.0, 0, 100000);
		MG_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 1500);
		MG_AB2_RANGE = i(b, "ability2_range", 20);
		MG_AB2_RADIUS = i(b, "ability2_radius", 6);
		MG_AB2_DEPTH = i(b, "ability2_depth", 6);
		MG_AB2_LAVA_SECONDS = i(b, "ability2_lava_seconds", 90);
		MG_AB2_LAVA_POOLS = b.comment("PLACEHOLDER: lava source blocks on the crater floor (removed after lava_seconds).").defineInRange("ability2_lava_pools", 5, 0, 100);
		MG_AB2_DAMAGE = d(b, "ability2_damage", 10);
		MG_AB2_SLOW_SECONDS = i(b, "ability2_slowness_seconds", 8);
		MG_AB2_SELF_SECONDS = i(b, "ability2_self_buff_seconds", 15);
		b.pop();

		b.comment("EARTH -> SAND (sub-aspect). Its passive REPLACES the Earth passive. Sand-form users never suffocate in sand.").push("sub_sand");
		SD_PASSIVE_QI_PER_MINUTE = b.comment("Passive: on sand / sandstone: Speed + taller step.").defineInRange("passive_qi_per_minute", 5.0, 0, 100000);
		SD_SPEED = i(b, "passive_speed_level", 4);
		SD_STEP = b.defineInRange("passive_step_height", 2.0, 0.6, 10);
		SD_A1_COST = b.comment("Attack 1 'pocket sand': nearest unallied player in range: Blindness + Weakness.").defineInRange("attack1_cost", 10.0, 0, 100000);
		SD_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 30);
		SD_A1_RANGE = i(b, "attack1_range", 5);
		SD_A1_SECONDS = i(b, "attack1_seconds", 15);
		SD_A2_COST = b.comment("Attack 2 'enhanced body': passive off; on sand: Strength III, Speed III, Resistance III.").defineInRange("attack2_cost", 20.0, 0, 100000);
		SD_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 380);
		SD_A2_SECONDS = i(b, "attack2_seconds", 30);
		SD_PB1_COST = b.comment("Powerblast 1 'sandworm': teleport onto the nearest sand block with room above and no unallied player near it.").defineInRange("powerblast1_cost", 50.0, 0, 100000);
		SD_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 400);
		SD_PB1_RADIUS = i(b, "powerblast1_radius", 50);
		SD_PB1_CLEAR = i(b, "powerblast1_clear_radius", 5);
		SD_PB2_COST = b.comment("Powerblast 2 'sand block': melee / arrow hits on you are blocked by a 2-high sand wall (gone after a few s); attacker within 1 block pushed back. Not Qi damage.").defineInRange("powerblast2_cost", 75.0, 0, 100000);
		SD_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 480);
		SD_PB2_SECONDS = i(b, "powerblast2_seconds", 60);
		SD_PB2_WALL_SECONDS = b.comment("PLACEHOLDER (user OK).").defineInRange("powerblast2_wall_seconds", 5, 1, 100000);
		SD_AB1_COST = b.comment("Ability 1 'a sandy grave': square around you, from the surface down: everything -> sand (chests / unbreakable skipped); unallied players inside pulled down.").defineInRange("ability1_cost", 100.0, 0, 100000);
		SD_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 890);
		SD_AB1_SIZE = i(b, "ability1_size", 30);
		SD_AB1_DEPTH = i(b, "ability1_depth", 10);
		SD_AB1_PULL = i(b, "ability1_pull_blocks", 6);
		SD_AB2_COST = b.comment("Ability 2 'SUNS HATRED': noon (whole server); surface in radius -> sand; unallied: natural (food) healing off, Weakness I, Glowing; Fire users: -50% Qi instead; Water / Dark users: x2 Qi.").defineInRange("ability2_cost", 250.0, 0, 100000);
		SD_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 1090);
		SD_AB2_SECONDS = i(b, "ability2_seconds", 120);
		SD_AB2_RADIUS = i(b, "ability2_radius", 100);
		SD_AB2_SAND = b.comment("Turn the surface into sand (permanent).").define("ability2_place_sand", true);
		SD_AB2_FIRE_DISCOUNT = b.defineInRange("ability2_fire_discount", 0.5, 0, 1);
		SD_AB2_COST_MULTIPLIER = b.defineInRange("ability2_water_dark_cost_multiplier", 2.0, 1, 100);
		b.pop();

		b.comment("EARTH -> NATURE (sub-aspect). Its passive REPLACES the Earth passive.").push("sub_nature");
		NT_PASSIVE_QI_PER_5MIN = b.comment("Passive: Regeneration; right-click plants to bonemeal them (free).").defineInRange("passive_qi_per_5_minutes", 3.0, 0, 100000);
		NT_PASSIVE_REGEN = i(b, "passive_regeneration_level", 2);
		NT_BONEMEAL_COOLDOWN_TICKS = i(b, "passive_bonemeal_cooldown_ticks", 10);
		NT_A1_COST = b.comment("Attack 1 'throned fists': next N bare-fist hits: Poison + rooted a moment.").defineInRange("attack1_cost", 15.0, 0, 100000);
		NT_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 45);
		NT_A1_HITS = b.defineInRange("attack1_hits", 3, 1, 1000);
		NT_A1_POISON_SECONDS = i(b, "attack1_poison_seconds", 5);
		NT_A1_ROOT_TICKS = i(b, "attack1_root_ticks", 20);
		NT_A2_COST = b.comment("Attack 2 'vine lash': nearest visible unallied player in range pulled to you + Slowness.").defineInRange("attack2_cost", 31.0, 0, 100000);
		NT_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		NT_A2_RANGE = i(b, "attack2_range", 20);
		NT_A2_PULL = d(b, "attack2_pull_blocks", 20);
		NT_A2_SLOW_SECONDS = i(b, "attack2_slowness_seconds", 3);
		NT_PB1_COST = b.comment("Powerblast 1 'root snare': unallied players in radius on dirt / grass / moss get cobwebs at feet + head (removed after the duration).").defineInRange("powerblast1_cost", 55.0, 0, 100000);
		NT_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 360);
		NT_PB1_SECONDS = i(b, "powerblast1_seconds", 30);
		NT_PB1_RADIUS = i(b, "powerblast1_radius", 15);
		NT_PB2_COST = b.comment("Powerblast 2 'spore cloud': follows you: unallied inside Poison + Nausea, allies inside Regeneration.").defineInRange("powerblast2_cost", 80.0, 0, 100000);
		NT_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 450);
		NT_PB2_SECONDS = i(b, "powerblast2_seconds", 30);
		NT_PB2_RADIUS = i(b, "powerblast2_radius", 6);
		NT_AB1_COST = b.comment("Ability 1 'overgrowth': dirt -> grass + flowers around you (stays); on grass you get Strength I + Resistance I; allies in the area Regeneration I.").defineInRange("ability1_cost", 200.0, 0, 100000);
		NT_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 500);
		NT_AB1_SECONDS = i(b, "ability1_seconds", 120);
		NT_AB1_RADIUS = i(b, "ability1_radius", 15);
		NT_AB1_FLOWER_CHANCE = b.defineInRange("ability1_flower_chance", 0.2, 0, 1);
		NT_AB2_COST = b.comment("Ability 2 'world tree': a big tree (gone when it ends); near the trunk +Qi and Regeneration II; unallied near it held by roots; Fire users' hits nearby burn it down early.").defineInRange("ability2_cost", 300.0, 0, 100000);
		NT_AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 890);
		NT_AB2_SECONDS = i(b, "ability2_seconds", 120);
		NT_AB2_HEIGHT = i(b, "ability2_tree_height", 15);
		NT_AB2_NEAR = i(b, "ability2_near_trunk", 8);
		NT_AB2_QI_PER_SECOND = d(b, "ability2_qi_per_second", 5);
		NT_AB2_ROOT_RADIUS = i(b, "ability2_root_radius", 12);
		NT_AB2_BURNS = b.comment("Fire users' hits within root_radius of the trunk needed to burn it down.").defineInRange("ability2_burns", 10, 1, 1000);
		b.pop();

		SPEC = b.build();
	}

	private EarthConfig() {
	}
}
