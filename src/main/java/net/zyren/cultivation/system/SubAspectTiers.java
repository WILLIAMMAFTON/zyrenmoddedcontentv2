package net.zyren.cultivation.system;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.zyren.cultivation.config.SubAspectConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Which sub-aspect sits in which tier (server-configurable) and what level each tier needs.
 *
 * Validation per aspect: every non-empty tier must name a sub-aspect of THAT aspect, and no
 * sub-aspect may appear twice. If an aspect's entries are invalid, a warning is logged once and
 * that aspect uses its default order (the enum order). Other aspects are unaffected.
 *
 * Changing the tiers never removes anything from players: a learned sub-aspect that moved to a
 * higher tier (or out of every tier) simply shows as locked until the player qualifies again.
 *
 * Works on both sides: server config values are synced to clients on join. Before that (or if
 * the config isn't loaded) the defaults are used.
 */
public final class SubAspectTiers {
	private static final Logger LOG = LogUtils.getLogger();

	/** aspect -> tiers (size 3, entries may be null for an empty tier). */
	private static final Map<AspectType, List<SubAspectType>> CACHE = new EnumMap<>(AspectType.class);
	/** aspect -> the raw strings the cache entry was built from (rebuilt when they change). */
	private static final Map<AspectType, List<String>> CACHE_RAW = new EnumMap<>(AspectType.class);

	private SubAspectTiers() {
	}

	/** Sub-aspects of an aspect by tier (index 0 = tier 1). Entries may be null (empty tier). */
	public static synchronized List<SubAspectType> tiers(AspectType aspect) {
		List<String> raw = rawIds(aspect);
		if (raw == null)
			return defaults(aspect);
		if (!raw.equals(CACHE_RAW.get(aspect))) {
			CACHE_RAW.put(aspect, raw);
			CACHE.put(aspect, resolve(aspect, raw));
		}
		return CACHE.get(aspect);
	}

	/** Tier 1..3 of a sub-aspect under the current config, or 0 if it isn't in any tier. */
	public static int tierOf(SubAspectType sub) {
		if (sub == null)
			return 0;
		List<SubAspectType> t = tiers(sub.parent());
		int i = t.indexOf(sub);
		return i < 0 ? 0 : i + 1;
	}

	/** Aspect level needed for this sub-aspect, or -1 if locked (not in a tier, or tier level -1). */
	public static int requiredLevel(SubAspectType sub) {
		int tier = tierOf(sub);
		if (tier == 0)
			return -1;
		try {
			return SubAspectConfig.tierLevel(sub.parent(), tier - 1);
		} catch (IllegalStateException notLoaded) {
			return -1;
		}
	}

	/** True if a player with this aspect level may use / equip / learn the sub-aspect. */
	public static boolean levelAllows(SubAspectType sub, int aspectLevel) {
		int req = requiredLevel(sub);
		return req >= 0 && aspectLevel >= req;
	}

	// ---------------------------------------------------------------- internals

	private static List<String> rawIds(AspectType aspect) {
		try {
			List<String> raw = new ArrayList<>();
			for (int t = 0; t < SubAspectConfig.TIERS; t++)
				raw.add(SubAspectConfig.rawTierId(aspect, t));
			return raw;
		} catch (IllegalStateException notLoaded) {
			return null;
		}
	}

	private static List<SubAspectType> defaults(AspectType aspect) {
		List<SubAspectType> list = new ArrayList<>(SubAspectType.of(aspect));
		while (list.size() < SubAspectConfig.TIERS)
			list.add(null);
		return list.subList(0, SubAspectConfig.TIERS);
	}

	private static List<SubAspectType> resolve(AspectType aspect, List<String> raw) {
		List<SubAspectType> out = new ArrayList<>();
		for (int t = 0; t < raw.size(); t++) {
			String id = raw.get(t) == null ? "" : raw.get(t).replace("\"", "").trim();
			if (id.isEmpty()) {
				out.add(null);
				continue;
			}
			SubAspectType sub = SubAspectType.fromId(id);
			String problem = null;
			if (sub == null)
				problem = "'" + id + "' is not a sub-aspect";
			else if (sub.parent() != aspect)
				problem = sub.id() + " belongs to " + sub.parent().displayName() + ", not " + aspect.displayName();
			else if (out.stream().anyMatch(s -> Objects.equals(s, sub)))
				problem = sub.id() + " is listed in more than one tier";
			if (problem != null) {
				LOG.warn("[Cultivation] subaspects config, [{}] tier_{}: {}. Using the default order for {}.",
						aspect.id().toLowerCase(java.util.Locale.ROOT), t + 1, problem, aspect.displayName());
				return defaults(aspect);
			}
			out.add(sub);
		}
		return out;
	}
}
