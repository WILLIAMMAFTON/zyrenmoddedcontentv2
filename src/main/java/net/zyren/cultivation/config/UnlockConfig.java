package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Aspect-level unlock schedule: zyrenmoddedcontent_v2-progression-server.toml (one per world).
 *
 * Design schedule (user, 2026-10-26): Lv1 Attack 1 + Attack 2 (Heavy), Lv3 Powerblast 1, Lv4 Powerblast 2,
 * Lv5 Ability 1, Lv6 Ability 2 / Ultimate. Passive: from Lv0 (user, 2026-10-27, so a new aspect can
 * still be levelled through its passive).
 * Applies to every aspect's normal kit and to sub-aspect abilities. Pure is NOT covered: it keeps
 * its own unlock levels (abilities.toml, pure section) until its new values are given.
 *
 * PLACEHOLDER: Reinforce Body (per aspect, not specified yet) = 0, i.e. usable as soon as the
 * aspect is active, exactly as before.
 */
public final class UnlockConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.IntValue ATTACK_1, HEAVY, PASSIVE, POWERBLAST_1, POWERBLAST_2, ABILITY_1, ABILITY_2, ULTIMATE, REINFORCE;

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();
		b.comment("Aspect level needed to use each part of a kit (not Pure: Pure keeps its own unlock levels). -1 = never.")
				.push("ability_unlock_levels");
		ATTACK_1 = b.defineInRange("attack_1", 1, -1, Integer.MAX_VALUE);
		HEAVY = b.comment("Attack 2 / Heavy").defineInRange("heavy", 1, -1, Integer.MAX_VALUE);
		PASSIVE = b.comment("User 2026-10-27: passives work from level 0 (the design's 'Lv2 passive' is not applied).")
				.defineInRange("passive", 0, -1, Integer.MAX_VALUE);
		POWERBLAST_1 = b.defineInRange("powerblast_1", 3, -1, Integer.MAX_VALUE);
		POWERBLAST_2 = b.defineInRange("powerblast_2", 4, -1, Integer.MAX_VALUE);
		ABILITY_1 = b.defineInRange("ability_1", 5, -1, Integer.MAX_VALUE);
		ABILITY_2 = b.defineInRange("ability_2", 6, -1, Integer.MAX_VALUE);
		ULTIMATE = b.comment("Kits that call their Ability 2 an Ultimate").defineInRange("ultimate", 6, -1, Integer.MAX_VALUE);
		REINFORCE = b.comment("PLACEHOLDER: Reinforce Body is per aspect and not specified yet; 0 = available immediately (as before).")
				.defineInRange("reinforce", 0, -1, Integer.MAX_VALUE);
		b.pop();
		SPEC = b.build();
	}

	private UnlockConfig() {
	}
}
