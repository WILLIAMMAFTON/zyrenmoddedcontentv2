package net.zyren.cultivation.data;

import java.util.Locale;

/**
 * Qi aspects, grouped in families (ASPECT GUIDE): NATURE (= "elemental", user 2026-10-27),
 * BODY, SOUL.
 *
 * PURE is special: every cultivator has it, it is leveled, and it never occupies one of the
 * 4 aspect slots. It is NOT the same thing as a PURE core type (see {@link CoreType}).
 *
 * Stored ids never change (they're in player saves):
 *  - ELECTRIC: "LIGHTNING" accepted as an alias.
 *  - PROTECTIVE: shown as "Protection" (user 2026-10-27, display name only); "PROTECTION" accepted.
 *  - WARRIOR: LEGACY. Warrior is now Physical -> Warrior (a sub-aspect). Kept only so old saves can
 *    be read and migrated on login (AspectMigration); never offered, never gainable.
 * New entries go at the END (enum order is used in client sync arrays).
 */
public enum AspectType {
	PURE("Pure", true, Family.SOUL),
	FIRE("Fire", false, Family.NATURE),
	WATER("Water", false, Family.NATURE),
	EARTH("Earth", false, Family.NATURE),
	WARRIOR("Warrior (legacy)", false, Family.BODY),
	PROTECTIVE("Protection", false, Family.BODY),
	ELECTRIC("Electric", false, Family.NATURE),
	PHYSICAL("Physical", false, Family.BODY),
	LIGHT("Light", false, Family.SOUL),
	DARK("Dark", false, Family.SOUL),
	AIR("Air", false, Family.NATURE),
	SPACE("Space", false, Family.BODY);

	public enum Family {
		NATURE("Nature"),
		BODY("Body"),
		SOUL("Soul");

		private final String displayName;

		Family(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}
	}

	private final String displayName;
	private final boolean special;
	private final Family family;

	AspectType(String displayName, boolean special, Family family) {
		this.displayName = displayName;
		this.special = special;
		this.family = family;
	}

	/** Stored value, exactly as written into player variables. */
	public String id() {
		return name();
	}

	public String displayName() {
		return displayName;
	}

	public Family family() {
		return family;
	}

	/** "Elemental" Qi = the Nature family (Fire, Water, Air, Electric, Earth). */
	public boolean isElemental() {
		return family == Family.NATURE;
	}

	/** True for PURE: always owned, never stored in a slot. */
	public boolean isSpecial() {
		return special;
	}

	/** True for WARRIOR: only exists to migrate old saves. */
	public boolean isLegacy() {
		return this == WARRIOR;
	}

	/** Aspects that can sit in one of the 4 slots (not Pure, not legacy). */
	public static AspectType[] slottable() {
		return java.util.Arrays.stream(values()).filter(a -> !a.special && !a.isLegacy()).toArray(AspectType[]::new);
	}

	/**
	 * Parses a stored or typed value, ignoring case and stray quote marks.
	 *
	 * @return the aspect, or null for empty / unknown values (an empty slot)
	 */
	public static AspectType fromId(String id) {
		if (id == null)
			return null;
		String cleaned = id.replace("\"", "").trim().toUpperCase(Locale.ROOT);
		if (cleaned.isEmpty())
			return null;
		if (cleaned.equals("LIGHTNING"))
			return ELECTRIC;
		if (cleaned.equals("PROTECTION"))
			return PROTECTIVE;
		try {
			return valueOf(cleaned);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
