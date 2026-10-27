package net.zyren.cultivation.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every sub-aspect, tied to its parent major aspect.
 *
 * Ids are qualified with the parent ("FIRE_BODY", "WATER_BODY", "DARK_RESISTANCE", ...) because
 * several names repeat across aspects. The id is what gets stored in player variables, so never
 * rename one once players have it.
 *
 * Declaration order inside each parent = the DEFAULT tier order (tier 1, 2, 3). Server owners can
 * change which sub-aspect sits in which tier in zyrenmoddedcontent_v2-subaspects-server.toml
 * (see {@link net.zyren.cultivation.system.SubAspectTiers}).
 *
 * List follows the user's ASPECT GUIDE (latest version, 2026-10-26). New entries go at the END of
 * their aspect group (ids are saved). Use {@code disclosed = false} for any sub-aspect that must
 * show as "???".
 */
public enum SubAspectType {
	FIRE_BODY(AspectType.FIRE, "Body"),
	FIRE_VOLCANIC(AspectType.FIRE, "Volcanic"),
	FIRE_INFERNO(AspectType.FIRE, "Inferno"),

	WATER_FREEZING(AspectType.WATER, "Freezing"),
	WATER_BODY(AspectType.WATER, "Body"),
	WATER_MOON(AspectType.WATER, "Moon"),

	ELECTRIC_PLASMA(AspectType.ELECTRIC, "Plasma"),
	ELECTRIC_ENHANCED_RECEPTORS(AspectType.ELECTRIC, "Enhanced Receptors"),
	ELECTRIC_CHAOS(AspectType.ELECTRIC, "Chaos"),

	EARTH_MAGMA(AspectType.EARTH, "Magma"),
	EARTH_SAND(AspectType.EARTH, "Sand"),
	EARTH_NATURE(AspectType.EARTH, "Nature"),

	PROTECTION_HEALING(AspectType.PROTECTIVE, "Healing"),
	PROTECTION_RESISTANCE(AspectType.PROTECTIVE, "Resistance"),
	PROTECTION_NULLIFICATION(AspectType.PROTECTIVE, "Nullification"),

	LIGHT_SHIELDING(AspectType.LIGHT, "Shielding"),
	LIGHT_SUN(AspectType.LIGHT, "Sun"),
	LIGHT_VITALITY(AspectType.LIGHT, "Vitality"),

	DARK_VOID(AspectType.DARK, "Void"),
	DARK_SHADOW(AspectType.DARK, "Shadow"),
	DARK_RESISTANCE(AspectType.DARK, "Resistance"),

	PHYSICAL_WARRIOR(AspectType.PHYSICAL, "Warrior"),
	PHYSICAL_BODY(AspectType.PHYSICAL, "Body"),
	PHYSICAL_MIND(AspectType.PHYSICAL, "Mind"),

	PURE_RESISTANCE(AspectType.PURE, "Resistance"),
	PURE_ENHANCEMENTS(AspectType.PURE, "Enhancements"),
	PURE_DEVASTATION(AspectType.PURE, "Devastation"),

	AIR_SKY(AspectType.AIR, "Sky"),
	AIR_STORM(AspectType.AIR, "Storm"),
	AIR_GALE(AspectType.AIR, "Gale"),

	SPACE_DIMENSIONAL(AspectType.SPACE, "Dimensional"),
	SPACE_MATTER(AspectType.SPACE, "Matter"),
	SPACE_ABYSSAL(AspectType.SPACE, "Abyssal");

	private final AspectType parent;
	private final String displayName;
	private final boolean disclosed;

	SubAspectType(AspectType parent, String displayName) {
		this(parent, displayName, true);
	}

	SubAspectType(AspectType parent, String displayName, boolean disclosed) {
		this.parent = parent;
		this.displayName = displayName;
		this.disclosed = disclosed;
	}

	/** Stored value, exactly as written into player variables. */
	public String id() {
		return name();
	}

	public AspectType parent() {
		return parent;
	}

	/** "Body" (short name). */
	public String displayName() {
		return disclosed ? displayName : "???";
	}

	/** "Fire -> Body" (unambiguous name for chat and commands). */
	public String fullName() {
		return parent.displayName() + " → " + displayName();
	}

	/** False for sub-aspects whose mechanics are intentionally undisclosed. */
	public boolean disclosed() {
		return disclosed;
	}

	/** Sub-aspects of one major aspect, in default tier order. */
	public static List<SubAspectType> of(AspectType parent) {
		List<SubAspectType> list = new ArrayList<>();
		for (SubAspectType s : values())
			if (s.parent == parent)
				list.add(s);
		return list;
	}

	/** Aspects that have at least one sub-aspect defined. */
	public static List<AspectType> parents() {
		List<AspectType> list = new ArrayList<>();
		for (SubAspectType s : values())
			if (!list.contains(s.parent))
				list.add(s.parent);
		return list;
	}

	/**
	 * Parses a stored or typed value, ignoring case and stray quote marks (MCreator stores
	 * empty String defaults as {@code ""} with the quotes).
	 *
	 * @return the sub-aspect, or null for empty / unknown values
	 */
	public static SubAspectType fromId(String id) {
		if (id == null)
			return null;
		String cleaned = id.replace("\"", "").trim().toUpperCase(Locale.ROOT);
		if (cleaned.isEmpty())
			return null;
		try {
			return valueOf(cleaned);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
