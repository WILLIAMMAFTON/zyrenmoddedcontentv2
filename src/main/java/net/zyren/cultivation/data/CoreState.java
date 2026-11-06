package net.zyren.cultivation.data;

import java.util.Locale;

/**
 * The core STATE: the condition of the Qi Core (PURE / CORRUPTED / POISONED). Formerly called
 * "core type" (renamed 2026-10-30). Stored in the MCreator variable qi_core_type (name kept so
 * saves are untouched). This is NOT a Qi aspect (a PURE core does not mean the player has the Pure
 * aspect) and NOT the core TYPE ({@link CoreType}: Cursed / Elemental / Void / Core / Soul).
 */
public enum CoreState {
	PURE,
	CORRUPTED,
	POISONED;

	/** Stored value, exactly as written into the qi_core_type player variable. */
	public String id() {
		return name();
	}

	/**
	 * Parses a stored value, ignoring case and stray quote marks (MCreator stores the default
	 * as "pure" including the quotes). Unknown or empty values fall back to PURE (the initial state).
	 */
	public static CoreState fromId(String id) {
		if (id == null)
			return PURE;
		String cleaned = id.replace("\"", "").trim();
		if (cleaned.isEmpty())
			return PURE;
		try {
			return valueOf(cleaned.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return PURE;
		}
	}
}
