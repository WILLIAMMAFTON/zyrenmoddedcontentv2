package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Protection DEFAULT kit + Healing sub-aspect (ASPECT GUIDE + user "use rec" 2026-11-06):
 * zyrenmoddedcontent_v2-protection-server.toml. Resistance / Nullification are disabled in the guide
 * (subaspects-server.toml disabled_subaspects).
 *
 * Values from the guide are the defaults. PLACEHOLDER = not given by the guide. "Damage points" are
 * health points (2 = 1 heart). Times in seconds. Exclusions: damage from a Space -> Abyssal user, or
 * of a damage type tagged zyrenmoddedcontent_v2:divine, ignores every Protection reduction / save.
 * Protection keeps its old Reinforce Body (abilities.toml).
 */
public final class ProtectionConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.DoubleValue PASSIVE_QI_PER_MINUTE, PASSIVE_REDUCTION;
	public static final ModConfigSpec.DoubleValue A1_COST, A1_QI_PER_MINUTE, A1_REDUCTION, A1_QI_PER_DAMAGE, A2_COST, A2_QI_PER_5S, A2_OUTPUT,
			A2_REDUCTION, A2_QI_PER_DAMAGE, PB1_COST, PB2_COST, AB1_COST, AB1_REDUCTION, AB1_QI_PER_HEART, AB2_COST, AB2_QI_PER_HEART;
	public static final ModConfigSpec.IntValue A1_COOLDOWN, A1_SECONDS, A1_RADIUS, A2_COOLDOWN, A2_SECONDS, A2_RESISTANCE, PB1_COOLDOWN, PB1_SECONDS,
			PB1_THROTTLE_TICKS, PB2_COOLDOWN, AB1_COOLDOWN, AB1_SECONDS, AB1_RADIUS, AB1_REGEN, AB2_COOLDOWN, AB2_SECONDS, AB2_RADIUS, AB2_PB2_LOCK_AFTER;

	public static final ModConfigSpec.DoubleValue HL_QI_PER_HEART, HL_HEAL_PER_SECOND, HL_A1_COST, HL_A1_CLEANSE_CHANCE, HL_A2_COST, HL_PB1_COST,
			HL_PB2_COST, HL_PB2_FRACTION, HL_AB1_COST;
	public static final ModConfigSpec.IntValue HL_RADIUS, HL_A1_COOLDOWN, HL_A2_COOLDOWN, HL_A2_RADIUS, HL_A2_MIN, HL_A2_MAX, HL_PB1_COOLDOWN, HL_PB1_SECONDS,
			HL_PB1_RADIUS, HL_PB2_COOLDOWN, HL_PB2_SECONDS, HL_PB2_RANGE, HL_PB2_REGEN, HL_AB1_COOLDOWN, HL_AB1_SECONDS, HL_AB1_RADIUS, HL_AB2_COOLDOWN,
			HL_AB2_SECONDS, HL_AB2_RADIUS, HL_AB2_ABSORPTION, HL_AB2_RESISTANCE;

	private static ModConfigSpec.DoubleValue d(ModConfigSpec.Builder b, String n, double v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	private static ModConfigSpec.IntValue i(ModConfigSpec.Builder b, String n, int v) {
		return b.defineInRange(n, v, 0, 1_000_000);
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.comment("DEFAULT passive: incoming damage reduced.").push("passive");
		PASSIVE_QI_PER_MINUTE = d(b, "qi_per_minute", 5);
		PASSIVE_REDUCTION = b.defineInRange("damage_reduction", 0.10, 0, 1);
		b.pop();

		b.push("default_kit");
		A1_COST = b.comment("Attack 1 'defencive aura': passive off; allies in the radius (not you) take less damage; you pay Qi per damage point removed (can't pay = aura ends).")
				.defineInRange("attack1_cost", 0.0, 0, 100000);
		A1_QI_PER_MINUTE = d(b, "attack1_upkeep_qi_per_minute", 2);
		A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 120);
		A1_SECONDS = i(b, "attack1_seconds", 30);
		A1_RADIUS = i(b, "attack1_radius", 30);
		A1_REDUCTION = b.defineInRange("attack1_reduction", 0.5, 0, 1);
		A1_QI_PER_DAMAGE = d(b, "attack1_qi_per_damage_removed", 1);
		A2_COST = b.comment("Attack 2 'tank': passive off; damage output x, Resistance; incoming damage cut, removed amount paid as Qi (can't pay = ends).")
				.defineInRange("attack2_cost", 0.0, 0, 100000);
		A2_QI_PER_5S = d(b, "attack2_upkeep_qi_per_5_seconds", 2);
		A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 120);
		A2_SECONDS = i(b, "attack2_seconds", 30);
		A2_OUTPUT = b.defineInRange("attack2_damage_output", 1.5, 1, 100);
		A2_RESISTANCE = i(b, "attack2_resistance_level", 2);
		A2_REDUCTION = b.defineInRange("attack2_reduction", 0.5, 0, 1);
		A2_QI_PER_DAMAGE = d(b, "attack2_qi_per_damage_removed", 1);
		PB1_COST = b.comment("Powerblast 1 'intelegence': hits from players show their health / Qi / aspect in chat; hits with no owner show the error line.")
				.defineInRange("powerblast1_cost", 50.0, 0, 100000);
		PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 240);
		PB1_SECONDS = i(b, "powerblast1_seconds", 60);
		PB1_THROTTLE_TICKS = b.comment("PLACEHOLDER: at most one line per attacker this often.").defineInRange("powerblast1_throttle_ticks", 40, 0, 10000);
		PB2_COST = b.comment("Powerblast 2 'invaild kill': armed until a lethal hit, which is cancelled. Reinforce Body removed + locked while armed.")
				.defineInRange("powerblast2_cost", 100.0, 0, 100000);
		PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 380);
		AB1_COST = b.comment("Ability 1 'guardians oath': allies in the radius (not you) take less damage + Regeneration; you pay Qi per heart negated (can't pay = ends).")
				.defineInRange("ability1_cost", 0.0, 0, 100000);
		AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 480);
		AB1_SECONDS = i(b, "ability1_seconds", 30);
		AB1_RADIUS = i(b, "ability1_radius", 60);
		AB1_REDUCTION = b.defineInRange("ability1_reduction", 0.5, 0, 1);
		AB1_QI_PER_HEART = d(b, "ability1_qi_per_heart_negated", 5);
		AB1_REGEN = i(b, "ability1_regeneration_level", 1);
		AB2_COST = b.comment("Ability 2 'a worthy death': allies in the radius (not you) negate all damage, you pay Qi per heart; when it ends you take the total in one hit",
				"(ignores your reductions / Reinforce). 'invaild kill' can't be used or trigger during it + some seconds.").defineInRange("ability2_cost", 0.0, 0, 100000);
		AB2_COOLDOWN = i(b, "ability2_cooldown_seconds", 1500);
		AB2_SECONDS = i(b, "ability2_seconds", 60);
		AB2_RADIUS = i(b, "ability2_radius", 40);
		AB2_QI_PER_HEART = d(b, "ability2_qi_per_heart_negated", 10);
		AB2_PB2_LOCK_AFTER = i(b, "ability2_powerblast2_lock_seconds_after", 5);
		b.pop();

		b.comment("PROTECTION -> HEALING. Its passive REPLACES the Protection passive.").push("sub_healing");
		HL_RADIUS = b.comment("Passive: every second heal the lowest-health injured ally in the radius (else yourself), paying Qi per heart healed.")
				.defineInRange("passive_radius", 5, 0, 256);
		HL_QI_PER_HEART = d(b, "passive_qi_per_heart_healed", 4);
		HL_HEAL_PER_SECOND = d(b, "passive_heal_per_second", 1);
		HL_A1_COST = b.comment("Attack 1 'lady mercy': armed until your next melee hit on an ally: no damage, healed to full, chance to remove one harmful effect.")
				.defineInRange("attack1_cost", 15.0, 0, 100000);
		HL_A1_COOLDOWN = i(b, "attack1_cooldown_seconds", 30);
		HL_A1_CLEANSE_CHANCE = b.defineInRange("attack1_cleanse_chance", 1.0 / 6.0, 0, 1);
		HL_A2_COST = b.comment("Attack 2 'mending pulse': allies in the radius (you too) heal a random amount.").defineInRange("attack2_cost", 45.0, 0, 100000);
		HL_A2_COOLDOWN = i(b, "attack2_cooldown_seconds", 60);
		HL_A2_RADIUS = i(b, "attack2_radius", 10);
		HL_A2_MIN = i(b, "attack2_heal_min", 4);
		HL_A2_MAX = i(b, "attack2_heal_max", 7);
		HL_PB1_COST = b.comment("Powerblast 1 'resistance gift': allies in the radius (you too): harmful effects removed; no new harmful effects + no corruption while it lasts.")
				.defineInRange("powerblast1_cost", 80.0, 0, 100000);
		HL_PB1_COOLDOWN = i(b, "powerblast1_cooldown_seconds", 120);
		HL_PB1_SECONDS = i(b, "powerblast1_seconds", 30);
		HL_PB1_RADIUS = i(b, "powerblast1_radius", 15);
		HL_PB2_COST = b.comment("Powerblast 2 'lifeline': link to an ally (looked at, else nearest): part of their incoming damage goes to you; they get Regeneration.")
				.defineInRange("powerblast2_cost", 100.0, 0, 100000);
		HL_PB2_COOLDOWN = i(b, "powerblast2_cooldown_seconds", 250);
		HL_PB2_SECONDS = i(b, "powerblast2_seconds", 15);
		HL_PB2_RANGE = b.comment("PLACEHOLDER").defineInRange("powerblast2_range", 20, 1, 256);
		HL_PB2_FRACTION = b.defineInRange("powerblast2_redirect_fraction", 0.4, 0, 1);
		HL_PB2_REGEN = i(b, "powerblast2_regeneration_level", 1);
		HL_AB1_COST = b.comment("Ability 1 'deaths door': the first lethal hit on an ally (or you) in the radius while it lasts is negated; then it ends.")
				.defineInRange("ability1_cost", 300.0, 0, 100000);
		HL_AB1_COOLDOWN = i(b, "ability1_cooldown_seconds", 480);
		HL_AB1_SECONDS = i(b, "ability1_seconds", 60);
		HL_AB1_RADIUS = i(b, "ability1_radius", 40);
		HL_AB2_COOLDOWN = b.comment("Ability 2 'i give you my all.': costs all your Qi (at least 1); allies in the radius (you too): Absorption + Resistance.")
				.defineInRange("ability2_cooldown_seconds", 1000, 0, 1_000_000);
		HL_AB2_SECONDS = i(b, "ability2_seconds", 90);
		HL_AB2_RADIUS = i(b, "ability2_radius", 50);
		HL_AB2_ABSORPTION = i(b, "ability2_absorption_level", 10);
		HL_AB2_RESISTANCE = i(b, "ability2_resistance_level", 2);
		b.pop();

		SPEC = b.build();
	}

	private ProtectionConfig() {
	}
}
