package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Fire DEFAULT kit (ASPECT GUIDE + user decisions 2026-10-27): zyrenmoddedcontent_v2-fire-server.toml.
 *
 * Values from the guide are the defaults. PLACEHOLDER = not given by the guide; chosen so the
 * ability works, change freely. "Damage points" are health points (2 = 1 heart).
 * Reinforce Body is not in the guide: Fire keeps its old one (abilities.toml, fire section).
 */
public final class FireConfig {
	public static final ModConfigSpec SPEC;

	// general
	public static final ModConfigSpec.IntValue FIRE_TICKS;
	// passive
	public static final ModConfigSpec.DoubleValue PASSIVE_QI_PER_MINUTE, HEAL_HEARTS_PER_SECOND, HEAL_QI_PER_HEART, NETHER_COST_REDUCTION;
	public static final ModConfigSpec.IntValue SENSE_RADIUS, SENSE_NAME_RADIUS;
	public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> SENSED_SUB_ASPECTS;
	// attack 1
	public static final ModConfigSpec.DoubleValue A1_COST, A1_DAMAGE;
	public static final ModConfigSpec.IntValue A1_COOLDOWN, A1_HITS, A1_TIME_LIMIT;
	// attack 2
	public static final ModConfigSpec.DoubleValue A2_COST, A2_EXTRA_UNARMORED, A2_EXTRA_ARMORED, A2_CRIT_MULTIPLIER;
	public static final ModConfigSpec.IntValue A2_COOLDOWN;
	// powerblast 1
	public static final ModConfigSpec.DoubleValue PB1_COST, PB1_HEAL, PB1_NETHER_EXTRA_COST, PB1_FALLBACK_HEAL, PB1_FALLBACK_COST;
	public static final ModConfigSpec.IntValue PB1_COOLDOWN, PB1_RADIUS, PB1_ENEMY_CLEAR_RADIUS, PB1_PORTAL_SEARCH_RADIUS;
	// powerblast 2
	public static final ModConfigSpec.DoubleValue PB2_COST, PB2_DAMAGE, PB2_KNOCKBACK_BLOCKS, PB2_COST_MULTIPLIER;
	public static final ModConfigSpec.IntValue PB2_COOLDOWN, PB2_RANGE, PB2_DEBUFF_SECONDS;
	// ability 1
	public static final ModConfigSpec.DoubleValue AB1_COST, AB1_WOOD_BURN_CHANCE, AB1_ATTACK_MULTIPLIER, AB1_FIRE_DISCOUNT;
	public static final ModConfigSpec.IntValue AB1_COOLDOWN, AB1_RADIUS, AB1_SOURCE_RADIUS, AB1_DEPTH, AB1_HEIGHT, AB1_BUFF_SECONDS, AB1_REGEN_LEVEL;
	// ability 2
	public static final ModConfigSpec.DoubleValue AB2_QI_FRACTION, AB2_COST_PER_PLAYER, AB2_LAVA_CHANCE;
	public static final ModConfigSpec.IntValue AB2_COOLDOWN, AB2_RADIUS, AB2_BEDROCK_LAVA_HEIGHT;
	// world edits
	public static final ModConfigSpec.IntValue BLOCKS_PER_TICK;

	// ---- sub-aspect FIRE_BODY ----
	public static final ModConfigSpec.DoubleValue BODY_PASSIVE_QI_PER_SECOND, BODY_A1_COST, BODY_A1_EXTRA, BODY_A2_COST, BODY_A2_SHARPNESS_BONUS,
			BODY_AB1_COST, BODY_AB1_BASE_DAMAGE, BODY_AB1_DAMAGE_PER_PLAYER, BODY_AB1_SPEED, BODY_AB2_COST;
	public static final ModConfigSpec.IntValue BODY_A1_COOLDOWN, BODY_A2_COOLDOWN, BODY_A2_SECONDS, BODY_A2_FIRE_TICKS, BODY_AB1_COOLDOWN,
			BODY_AB1_COUNT_RADIUS, BODY_AB1_BASE_RANGE, BODY_AB1_RANGE_PER_PLAYER, BODY_AB1_MAX_RANGE, BODY_AB2_COOLDOWN, BODY_AB2_SECONDS, BODY_AB2_RADIUS;
	// ---- sub-aspect FIRE_VOLCANIC ----
	public static final ModConfigSpec.DoubleValue VOLC_A1_COST, VOLC_EXPLOSION_POWER, VOLC_A2_COST, VOLC_A2_EXTRA, VOLC_AB1_COST, VOLC_AB1_QI_PER_MINUTE,
			VOLC_AB2_COST, VOLC_AB2_NETHERITE_MULTIPLIER;
	public static final ModConfigSpec.IntValue VOLC_A1_COOLDOWN, VOLC_A1_SECONDS, VOLC_A2_COOLDOWN, VOLC_A2_SECONDS, VOLC_A2_NAUSEA_SECONDS,
			VOLC_AB1_COOLDOWN, VOLC_AB1_SECONDS, VOLC_AB2_COOLDOWN, VOLC_AB2_SECONDS, VOLC_AB2_RADIUS;
	public static final ModConfigSpec.BooleanValue VOLC_EXPLOSION_BREAKS_BLOCKS;
	// ---- sub-aspect FIRE_INFERNO ----
	public static final ModConfigSpec.DoubleValue INF_PASSIVE_QI_PER_5MIN, INF_COST_REDUCTION, INF_COVER_FRACTION, INF_PASSIVE_REDUCTION,
			INF_A1_COST, INF_A1_DAMAGE, INF_A1_DAMAGE_ARMORED, INF_A1_REFUND, INF_A2_COST, INF_A2_DAMAGE,
			INF_AB1_COST, INF_AB1_REDUCTION, INF_AB2_COST, INF_AB2_REDUCTION, INF_AB2_ARMORED_BONUS;
	public static final ModConfigSpec.IntValue INF_COVER_RADIUS, INF_HEARTS, INF_HEARTS_ARMORED, INF_A1_COOLDOWN, INF_A1_HITS,
			INF_A2_COOLDOWN, INF_A2_SECONDS, INF_A2_RADIUS, INF_AB1_COOLDOWN, INF_AB1_SECONDS, INF_AB2_COOLDOWN, INF_AB2_SECONDS;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.push("general");
		FIRE_TICKS = b.comment("PLACEHOLDER (user OK 2026-10-27): how long 'apply fire' sets a target burning, in ticks.").defineInRange("fire_ticks", 100, 0, 72000);
		BLOCKS_PER_TICK = b.comment("World-edit speed for Hell on Earth / Meteor (blocks examined per tick). Lower = less lag, slower.")
				.defineInRange("blocks_per_tick", 8000, 100, 1_000_000);
		b.pop();

		b.comment("Passive: Fire Resistance, clear vision in lava, heals in fire, cheaper abilities in the Nether, senses Earth->Magma / Electric->Plasma users.").push("passive");
		PASSIVE_QI_PER_MINUTE = b.defineInRange("qi_per_minute", 2.5, 0, 10000);
		HEAL_HEARTS_PER_SECOND = b.comment("PLACEHOLDER (user OK): hearts healed per second while standing in fire.").defineInRange("heal_hearts_per_second", 1.0, 0, 1000);
		HEAL_QI_PER_HEART = b.defineInRange("heal_qi_per_heart", 1.0, 0, 10000);
		NETHER_COST_REDUCTION = b.comment("In the Nether every Fire ability costs this much less (not below 0).").defineInRange("nether_cost_reduction", 1.0, 0, 10000);
		SENSE_RADIUS = b.defineInRange("sense_radius", 15, 0, 256);
		SENSE_NAME_RADIUS = b.comment("Within this range the sensed player's name is shown too.").defineInRange("sense_name_radius", 7, 0, 256);
		SENSED_SUB_ASPECTS = b.comment("Sub-aspect forms the passive senses.")
				.defineListAllowEmpty("sensed_sub_aspects", java.util.List.of("EARTH_MAGMA", "ELECTRIC_PLASMA"), () -> "EARTH_MAGMA", o -> o instanceof String);
		b.pop();

		b.comment("Attack 1 'Enhanced fists of flames': the next N hits without a weapon deal X damage + fire; cooldown starts after the last hit.").push("attack_1");
		A1_COST = b.defineInRange("cost", 10.0, 0, 100000);
		A1_COOLDOWN = b.comment("Seconds, starts after the last charged hit.").defineInRange("cooldown_seconds", 5, 0, 100000);
		A1_HITS = b.defineInRange("hits", 3, 1, 100);
		A1_DAMAGE = b.comment("Damage of each charged hit (replaces the punch's damage).").defineInRange("damage", 8.0, 0, 100000);
		A1_TIME_LIMIT = b.comment("PLACEHOLDER (user OK): seconds to use the charges; 0 = no limit.").defineInRange("time_limit_seconds", 0, 0, 100000);
		b.pop();

		b.comment("Attack 2 'ENHANCED FLAMES': next hit gets EXTRA damage (2 unarmoured / 6 armoured), x2 on a sword/axe critical, + fire. Refused while Attack 1 charges remain.").push("attack_2");
		A2_COST = b.defineInRange("cost", 15.0, 0, 100000);
		A2_COOLDOWN = b.defineInRange("cooldown_seconds", 30, 0, 100000);
		A2_EXTRA_UNARMORED = b.defineInRange("extra_damage_unarmored", 2.0, 0, 100000);
		A2_EXTRA_ARMORED = b.defineInRange("extra_damage_armored", 6.0, 0, 100000);
		A2_CRIT_MULTIPLIER = b.comment("Multiplies the EXTRA damage on a sword/axe critical hit.").defineInRange("crit_multiplier", 2.0, 1, 1000);
		b.pop();

		b.comment("Powerblast 1 'Burning retreat'. (a) flame/lava/campfire within radius, no unallied player near it: teleport + heal."
				+ " (b) Overworld only: to the Nether, next to the nearest nether portal (+extra cost); no portal: (a) again in the Nether."
				+ " (c) otherwise: heal only, different cost.").push("powerblast_1");
		PB1_COST = b.defineInRange("cost", 10.0, 0, 100000);
		PB1_COOLDOWN = b.defineInRange("cooldown_seconds", 10, 0, 100000);
		PB1_RADIUS = b.defineInRange("search_radius", 50, 1, 128);
		PB1_ENEMY_CLEAR_RADIUS = b.defineInRange("no_unallied_within", 4, 0, 64);
		PB1_HEAL = b.defineInRange("heal", 10.0, 0, 100000);
		PB1_NETHER_EXTRA_COST = b.defineInRange("nether_extra_cost", 30.0, 0, 100000);
		PB1_PORTAL_SEARCH_RADIUS = b.comment("PLACEHOLDER: blocks around your Nether-scaled position searched for a portal. Larger = more chunks loaded.")
				.defineInRange("portal_search_radius", 64, 8, 256);
		PB1_FALLBACK_HEAL = b.defineInRange("fallback_heal", 20.0, 0, 100000);
		PB1_FALLBACK_COST = b.defineInRange("fallback_cost", 15.0, 0, 100000);
		b.pop();

		b.comment("Powerblast 2 'Blast of Flames': looked-at (else nearest) player within range: damage, knocked back, fire; Water/Air-active targets also Nausea + Blindness and x2 Qi costs.").push("powerblast_2");
		PB2_COST = b.defineInRange("cost", 20.0, 0, 100000);
		PB2_COOLDOWN = b.defineInRange("cooldown_seconds", 30, 0, 100000);
		PB2_RANGE = b.defineInRange("range", 15, 1, 256);
		PB2_DAMAGE = b.defineInRange("damage", 15.0, 0, 100000);
		PB2_KNOCKBACK_BLOCKS = b.defineInRange("knockback_blocks", 10.0, 0, 1000);
		PB2_DEBUFF_SECONDS = b.defineInRange("debuff_seconds", 15, 0, 100000);
		PB2_COST_MULTIPLIER = b.defineInRange("qi_cost_multiplier", 2.0, 1, 1000);
		b.pop();

		b.comment("Ability 1 'HELL ON EARTH' (Overworld, flame source nearby). Permanent terrain changes in the radius; Fire-active players buffed.").push("ability_1");
		AB1_COST = b.defineInRange("cost", 100.0, 0, 100000);
		AB1_COOLDOWN = b.defineInRange("cooldown_seconds", 180, 0, 100000);
		AB1_RADIUS = b.defineInRange("radius", 100, 1, 256);
		AB1_SOURCE_RADIUS = b.defineInRange("flame_source_radius", 5, 1, 32);
		AB1_DEPTH = b.comment("PLACEHOLDER: blocks below the surface that are changed.").defineInRange("depth_below_surface", 8, 0, 64);
		AB1_HEIGHT = b.comment("PLACEHOLDER: blocks above the surface that are changed (trees, houses).").defineInRange("height_above_surface", 24, 0, 128);
		AB1_WOOD_BURN_CHANCE = b.comment("Wooden blocks: this chance to be set aflame, otherwise deleted.").defineInRange("wood_burn_chance", 0.5, 0, 1);
		AB1_BUFF_SECONDS = b.defineInRange("buff_seconds", 180, 0, 100000);
		AB1_REGEN_LEVEL = b.defineInRange("regeneration_level", 2, 1, 255);
		AB1_ATTACK_MULTIPLIER = b.comment("PLACEHOLDER (Claude's pick, user OK): damage multiplier for Fire-active players.").defineInRange("attack_multiplier", 1.25, 1, 100);
		AB1_FIRE_DISCOUNT = b.comment("Fire abilities cost this much less for Fire-active players during the buff.").defineInRange("fire_ability_discount", 5.0, 0, 100000);
		b.pop();

		b.comment("Ability 2 'METEOR': crater around you, then Burning retreat for free. Surface of the crater: fire or lava. You take no fall damage.").push("ability_2");
		AB2_QI_FRACTION = b.comment("Cost = this share of your current Qi ...").defineInRange("qi_fraction", 0.5, 0, 1);
		AB2_COST_PER_PLAYER = b.comment("... + this per other player inside the crater radius.").defineInRange("cost_per_player", 10.0, 0, 100000);
		AB2_COOLDOWN = b.defineInRange("cooldown_seconds", 7200, 0, 1000000);
		AB2_RADIUS = b.comment("50 x 50 x 50 -> radius 25.").defineInRange("radius", 25, 1, 128);
		AB2_LAVA_CHANCE = b.comment("PLACEHOLDER (user OK): each crater surface block becomes lava with this chance, otherwise gets fire on top.").defineInRange("lava_chance", 0.5, 0, 1);
		AB2_BEDROCK_LAVA_HEIGHT = b.comment("If the crater reaches bedrock, lava sources are placed this many blocks above it.").defineInRange("bedrock_lava_height", 15, 0, 256);
		b.pop();

		// =====================================================================  sub-aspects
		b.comment("FIRE -> BODY (sub-aspect). Its passive REPLACES the default Fire passive while in Body form.").push("sub_body");
		BODY_PASSIVE_QI_PER_SECOND = b.comment("Passive: in lava, fire or the Nether -> Regeneration I, Strength II, Speed I. Qi only while a condition holds.")
				.defineInRange("passive_qi_per_second", 1.1, 0, 10000);
		BODY_A1_COST = b.comment("Attack 1 'did i say u could be here?': next hit +extra damage + fire; only while the Body passive is on. Fire-active target hit in the Overworld is sent to the Nether.")
				.defineInRange("attack1_cost", 15.0, 0, 100000);
		BODY_A1_COOLDOWN = b.defineInRange("attack1_cooldown_seconds", 15, 0, 100000);
		BODY_A1_EXTRA = b.defineInRange("attack1_extra_damage", 15.0, 0, 100000);
		BODY_A2_COST = b.comment("Attack 2 'Fission blade': Strength II, Haste III; sword/axe hits get Sharpness II + Fire Aspect (simulated, item untouched).")
				.defineInRange("attack2_cost", 30.0, 0, 100000);
		BODY_A2_COOLDOWN = b.defineInRange("attack2_cooldown_seconds", 100, 0, 100000);
		BODY_A2_SECONDS = b.defineInRange("attack2_seconds", 15, 1, 100000);
		BODY_A2_SHARPNESS_BONUS = b.comment("Sharpness II = +1.5 damage (vanilla formula).").defineInRange("attack2_sharpness_bonus", 1.5, 0, 1000);
		BODY_A2_FIRE_TICKS = b.comment("Fire Aspect I = 4 s burning.").defineInRange("attack2_fire_ticks", 80, 0, 72000);
		BODY_AB1_COST = b.comment("Ability 1 'a line straight from hell': charge forward; range 30 +5 per Fire-active player within 50 (max 80); damage 10 +2 per player.")
				.defineInRange("ability1_cost", 50.0, 0, 100000);
		BODY_AB1_COOLDOWN = b.defineInRange("ability1_cooldown_seconds", 380, 0, 100000);
		BODY_AB1_COUNT_RADIUS = b.defineInRange("ability1_count_radius", 50, 0, 256);
		BODY_AB1_BASE_RANGE = b.defineInRange("ability1_base_range", 30, 1, 1000);
		BODY_AB1_RANGE_PER_PLAYER = b.defineInRange("ability1_range_per_player", 5, 0, 1000);
		BODY_AB1_MAX_RANGE = b.defineInRange("ability1_max_range", 80, 1, 1000);
		BODY_AB1_BASE_DAMAGE = b.defineInRange("ability1_base_damage", 10.0, 0, 100000);
		BODY_AB1_DAMAGE_PER_PLAYER = b.defineInRange("ability1_damage_per_player", 2.0, 0, 100000);
		BODY_AB1_SPEED = b.comment("PLACEHOLDER: charge speed in blocks per tick (client limit ~3.9).").defineInRange("ability1_speed", 2.0, 0.1, 3.9);
		BODY_AB2_COST = b.comment("Ability 2 'Heating Touch': aura for the duration; Water/Air players inside it are nullified (bypasses Reinforce) while inside.")
				.defineInRange("ability2_cost", 100.0, 0, 100000);
		BODY_AB2_COOLDOWN = b.defineInRange("ability2_cooldown_seconds", 380, 0, 100000);
		BODY_AB2_SECONDS = b.defineInRange("ability2_seconds", 60, 1, 100000);
		BODY_AB2_RADIUS = b.defineInRange("ability2_radius", 5, 1, 64);
		b.pop();

		b.comment("FIRE -> VOLCANIC (sub-aspect). No own passive: the default Fire passive stays.").push("sub_volcanic");
		VOLC_A1_COST = b.comment("Attack 1 'explosive magma': every damage you deal makes an explosion on the target + fire. You're immune to your own explosions.")
				.defineInRange("attack1_cost", 15.0, 0, 100000);
		VOLC_A1_COOLDOWN = b.defineInRange("attack1_cooldown_seconds", 60, 0, 100000);
		VOLC_A1_SECONDS = b.defineInRange("attack1_seconds", 180, 1, 100000);
		VOLC_EXPLOSION_POWER = b.comment("User 2026-10-27: power 1 (small).").defineInRange("explosion_power", 1.0, 0, 10);
		VOLC_EXPLOSION_BREAKS_BLOCKS = b.comment("PLACEHOLDER: do these explosions break blocks?").define("explosion_breaks_blocks", false);
		VOLC_A2_COST = b.comment("Attack 2 'explosive flaming fists for doom': while holding an item, every hit: explosion, fire, Nausea, +extra damage.")
				.defineInRange("attack2_cost", 25.0, 0, 100000);
		VOLC_A2_COOLDOWN = b.defineInRange("attack2_cooldown_seconds", 250, 0, 100000);
		VOLC_A2_SECONDS = b.defineInRange("attack2_seconds", 60, 1, 100000);
		VOLC_A2_NAUSEA_SECONDS = b.defineInRange("attack2_nausea_seconds", 3, 0, 100000);
		VOLC_A2_EXTRA = b.defineInRange("attack2_extra_damage", 15.0, 0, 100000);
		VOLC_AB1_COST = b.comment("Ability 1 'volcanic doom': while standing in lava: Regeneration III, Resistance III, Strength II, +Qi per minute.")
				.defineInRange("ability1_cost", 50.0, 0, 100000);
		VOLC_AB1_COOLDOWN = b.defineInRange("ability1_cooldown_seconds", 450, 0, 100000);
		VOLC_AB1_SECONDS = b.defineInRange("ability1_seconds", 190, 1, 100000);
		VOLC_AB1_QI_PER_MINUTE = b.defineInRange("ability1_qi_per_minute", 35.0, 0, 100000);
		VOLC_AB2_COST = b.comment("Ability 2 'the world will burn': radius: stone -> magma, water -> lava, sand -> soul sand, fire on the ground (permanent). You: Strength III, Speed II, netherite weapons x2.")
				.defineInRange("ability2_cost", 180.0, 0, 100000);
		VOLC_AB2_COOLDOWN = b.defineInRange("ability2_cooldown_seconds", 500, 0, 100000);
		VOLC_AB2_SECONDS = b.defineInRange("ability2_seconds", 250, 1, 100000);
		VOLC_AB2_RADIUS = b.defineInRange("ability2_radius", 50, 1, 128);
		VOLC_AB2_NETHERITE_MULTIPLIER = b.defineInRange("ability2_netherite_multiplier", 2.0, 1, 100);
		b.pop();

		b.comment("FIRE -> INFERNO (sub-aspect). Its passive REPLACES the default Fire passive while in Inferno form."
				+ " 'Fire covering the radius' = at least cover_fraction of the floor spots within cover_radius have fire.").push("sub_inferno");
		INF_COVER_RADIUS = b.defineInRange("cover_radius", 3, 1, 16);
		INF_COVER_FRACTION = b.comment("PLACEHOLDER (user OK): share of floor spots that must be on fire.").defineInRange("cover_fraction", 0.5, 0.01, 1);
		INF_PASSIVE_QI_PER_5MIN = b.defineInRange("passive_qi_per_5_minutes", 15.0, 0, 100000);
		INF_COST_REDUCTION = b.comment("PLACEHOLDER (user OK): standing in fire -> Fire abilities cost this share less.").defineInRange("passive_cost_reduction", 0.10, 0, 1);
		INF_HEARTS = b.comment("Covered by fire: Regeneration II + this many extra hearts ...").defineInRange("passive_extra_hearts", 10, 0, 1000);
		INF_HEARTS_ARMORED = b.comment("... wearing armour: Regeneration I + this many instead.").defineInRange("passive_extra_hearts_armored", 5, 0, 1000);
		INF_PASSIVE_REDUCTION = b.comment("Covered by fire: Water/Air Qi attacks deal this share less.").defineInRange("passive_water_air_reduction", 0.5, 0, 1);
		INF_A1_COST = b.comment("Attack 1 'the infernos heat': next N unarmed hits: damage (more vs armour), fire, Nausea vs Water/Air. Taking a weapon in hand ends it with a refund.")
				.defineInRange("attack1_cost", 25.0, 0, 100000);
		INF_A1_COOLDOWN = b.defineInRange("attack1_cooldown_seconds", 60, 0, 100000);
		INF_A1_HITS = b.defineInRange("attack1_hits", 3, 1, 100);
		INF_A1_DAMAGE = b.defineInRange("attack1_damage", 5.0, 0, 100000);
		INF_A1_DAMAGE_ARMORED = b.defineInRange("attack1_damage_armored", 10.0, 0, 100000);
		INF_A1_REFUND = b.defineInRange("attack1_weapon_refund", 5.0, 0, 100000);
		INF_A2_COST = b.comment("Attack 2 'raising the heat': damage to Water / Air / Space->Matter players in range; Strength III to you and Fire-active allies.")
				.defineInRange("attack2_cost", 40.0, 0, 100000);
		INF_A2_COOLDOWN = b.defineInRange("attack2_cooldown_seconds", 350, 0, 100000);
		INF_A2_SECONDS = b.defineInRange("attack2_seconds", 150, 1, 100000);
		INF_A2_RADIUS = b.defineInRange("attack2_radius", 20, 1, 256);
		INF_A2_DAMAGE = b.defineInRange("attack2_damage", 15.0, 0, 100000);
		INF_AB1_COST = b.comment("Ability 1 'heart': standing in fire: Strength II, Speed I; covered by fire: hits set fire, Water/Air Qi attacks -25%. Doesn't stack with the passive (stronger wins).")
				.defineInRange("ability1_cost", 60.0, 0, 100000);
		INF_AB1_COOLDOWN = b.defineInRange("ability1_cooldown_seconds", 380, 0, 100000);
		INF_AB1_SECONDS = b.defineInRange("ability1_seconds", 120, 1, 100000);
		INF_AB1_REDUCTION = b.defineInRange("ability1_water_air_reduction", 0.25, 0, 1);
		INF_AB2_COST = b.comment("Ability 2 'unbound': creates fire around you; while covered: Strength III, Speed II, Resistance II, fist hits set fire + bonus vs armour, Water/Air Qi attacks -50%. Paused while not covered.")
				.defineInRange("ability2_cost", 150.0, 0, 100000);
		INF_AB2_COOLDOWN = b.defineInRange("ability2_cooldown_seconds", 600, 0, 100000);
		INF_AB2_SECONDS = b.defineInRange("ability2_seconds", 180, 1, 100000);
		INF_AB2_REDUCTION = b.defineInRange("ability2_water_air_reduction", 0.5, 0, 1);
		INF_AB2_ARMORED_BONUS = b.comment("PLACEHOLDER (user OK): extra fist damage vs armoured targets.").defineInRange("ability2_armored_bonus", 5.0, 0, 100000);
		b.pop();

		SPEC = b.build();
	}

	private FireConfig() {
	}
}
