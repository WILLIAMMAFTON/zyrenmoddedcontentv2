package net.zyren.cultivation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sub-aspect settings: zyrenmoddedcontent_v2-subaspects-server.toml (one per world).
 *
 * Per aspect, server owners choose which sub-aspect fills tier 1, 2 and 3, and the aspect level
 * each tier unlocks at. Defaults follow the design table order and the level schedule
 * (tier 1 = Lv3, tier 2 = Lv5, tier 3 = Lv7). Invalid entries are reported in the server log
 * and that aspect falls back to its default order (see SubAspectTiers).
 *
 * PLACEHOLDERS: Pure's tier levels (Pure gets its own values, not decided yet: -1 = locked),
 * tier 3 = Lv7 (design says "levels 7-8", split not final), fallback cooldown 120 s.
 */
public final class SubAspectConfig {
	public static final ModConfigSpec SPEC;

	public static final ModConfigSpec.IntValue FALLBACK_COOLDOWN_SECONDS;
	public static final ModConfigSpec.IntValue FORM_TOGGLE_COOLDOWN_SECONDS;
	public static final ModConfigSpec.DoubleValue LOCKED_LEARN_CULTIVATED_QI;
	public static final ModConfigSpec.BooleanValue LOCKED_LEARN_FILL_QI;

	/** [aspect][tier 0..2] -> configured sub-aspect id ("" = no sub-aspect in that tier). */
	private static final Map<AspectType, ModConfigSpec.ConfigValue<String>[]> TIER_IDS = new EnumMap<>(AspectType.class);
	/** [aspect][tier 0..2] -> aspect level the tier unlocks at (-1 = locked / not set). */
	private static final Map<AspectType, ModConfigSpec.IntValue[]> TIER_LEVELS = new EnumMap<>(AspectType.class);

	public static final int TIERS = 3;

	private static final int[] DEFAULT_LEVELS = {3, 5, 7};

	@SuppressWarnings("unchecked")
	private static ModConfigSpec.ConfigValue<String>[] newIdArray() {
		return new ModConfigSpec.ConfigValue[TIERS];
	}

	static {
		ModConfigSpec.Builder b = new ModConfigSpec.Builder();

		b.comment("General sub-aspect values").push("general");
		FALLBACK_COOLDOWN_SECONDS = b
				.comment("PLACEHOLDER: cooldown (seconds) for a sub-aspect ability whose cooldown is not specified. Every ability using it is listed by /qi subaspect defaults.")
				.defineInRange("fallback_cooldown_seconds", 120, 0, Integer.MAX_VALUE);
		FORM_TOGGLE_COOLDOWN_SECONDS = b
				.comment("Cooldown (seconds) between switching normal form <-> sub-aspect form.")
				.defineInRange("form_toggle_cooldown_seconds", 15, 0, Integer.MAX_VALUE);
		LOCKED_LEARN_CULTIVATED_QI = b
				.comment("Learning a sub-aspect from a book while it is still locked (aspect not owned, or level too low) gives this much cultivated Qi (user: 50).")
				.defineInRange("locked_learn_cultivated_qi", 50.0, 0.0, Double.MAX_VALUE);
		LOCKED_LEARN_FILL_QI = b
				.comment("...and fills the player's usable Qi to their core's maximum (user: yes).")
				.define("locked_learn_fill_qi", true);
		b.pop();

		for (AspectType aspect : SubAspectType.parents()) {
			List<SubAspectType> defaults = SubAspectType.of(aspect);
			b.comment(aspect.displayName() + " sub-aspects. Valid ids: " + ids(defaults)
					+ ". Leave a tier empty (\"\") to have no sub-aspect in it. Each sub-aspect may appear in one tier only.")
					.push(aspect.id().toLowerCase(Locale.ROOT));
			ModConfigSpec.ConfigValue<String>[] idValues = newIdArray();
			ModConfigSpec.IntValue[] levelValues = new ModConfigSpec.IntValue[TIERS];
			for (int t = 0; t < TIERS; t++) {
				String def = t < defaults.size() ? defaults.get(t).id() : "";
				idValues[t] = b.define("tier_" + (t + 1), def, o -> o instanceof String);
				int defLevel = aspect == AspectType.PURE ? -1 : DEFAULT_LEVELS[t];
				levelValues[t] = b
						.comment(aspect == AspectType.PURE
								? "PLACEHOLDER: Pure level that unlocks tier " + (t + 1) + " (Pure gets its own values, not decided). -1 = locked."
								: "Aspect level that unlocks tier " + (t + 1) + (t == 2 ? " (PLACEHOLDER: design says levels 7-8)" : "") + ". -1 = locked.")
						.defineInRange("tier_" + (t + 1) + "_level", defLevel, -1, Integer.MAX_VALUE);
			}
			TIER_IDS.put(aspect, idValues);
			TIER_LEVELS.put(aspect, levelValues);
			b.pop();
		}

		SPEC = b.build();
	}

	private SubAspectConfig() {
	}

	private static String ids(List<SubAspectType> list) {
		StringBuilder sb = new StringBuilder();
		for (SubAspectType s : list)
			sb.append(sb.length() == 0 ? "" : ", ").append(s.id());
		return sb.toString();
	}

	/** Raw configured id for a tier (0-based), or "" if the aspect has no sub-aspect section. */
	public static String rawTierId(AspectType aspect, int tier) {
		ModConfigSpec.ConfigValue<String>[] v = TIER_IDS.get(aspect);
		return v == null ? "" : v[tier].get();
	}

	/** Unlock level for a tier (0-based); -1 = locked. */
	public static int tierLevel(AspectType aspect, int tier) {
		ModConfigSpec.IntValue[] v = TIER_LEVELS.get(aspect);
		return v == null ? -1 : v[tier].get();
	}
}
