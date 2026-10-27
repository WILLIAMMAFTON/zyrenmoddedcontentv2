package net.zyren.cultivation.data;

import java.util.Locale;

/**
 * The condition of the Qi Core. This is NOT a Qi aspect: a PURE core does not mean the
 * player has the Pure aspect.
 */
public enum CoreType {
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
	public static CoreType fromId(String id) {
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
