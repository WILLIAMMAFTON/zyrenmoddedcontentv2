package net.zyren.cultivation.data;

import java.util.Locale;

/**
 * The core TYPE (added 2026-10-30; BLOOD added 2026-11-06): what kind of core a player was born with. Assigned at random
 * the first time a player joins (system/CoreTypes) and kept for good; core breaks, annihilation,
 * death and /qi reset do NOT change it. Later items/actions will change it through
 * CoreTypes.set(...).
 *
 * Labels only for now: no type has any gameplay effect yet.
 *
 * Not to be confused with the core STATE ({@link CoreState}: PURE / CORRUPTED / POISONED).
 * Stored in the MCreator player_persistent String variable qi_core_kind.
 */
public enum CoreType {
	CURSED("Cursed"),
	ELEMENTAL("Elemental"),
	VOID("Void"),
	CORE("Core"),
	SOUL("Soul"),
	BLOOD("Blood");

	private final String displayName;

	CoreType(String displayName) {
		this.displayName = displayName;
	}

	/** Stored value, exactly as written into qi_core_kind. */
	public String id() {
		return name();
	}

	public String displayName() {
		return displayName;
	}

	/** Parses a stored value, ignoring case and stray quotes. Empty or unknown = null (not assigned). */
	public static CoreType fromId(String id) {
		if (id == null)
			return null;
		String cleaned = id.replace("\"", "").trim();
		if (cleaned.isEmpty())
			return null;
		try {
			return valueOf(cleaned.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
