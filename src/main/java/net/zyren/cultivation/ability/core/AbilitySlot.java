package net.zyren.cultivation.ability.core;

/**
 * The activatable ability slots an aspect kit can fill (Passive is separate: always on while
 * the aspect is active). Not every aspect uses every slot (e.g. some have Ability 2, others an
 * Ultimate).
 */
public enum AbilitySlot {
	ATTACK_1("Attack 1"),
	HEAVY("Heavy"),
	REINFORCE("Reinforce Body"),
	POWERBLAST_1("Powerblast 1"),
	POWERBLAST_2("Powerblast 2"),
	ABILITY_1("Ability 1"),
	ABILITY_2("Ability 2"),
	ULTIMATE("Ultimate");

	private final String displayName;

	AbilitySlot(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}

	public static AbilitySlot byOrdinal(int i) {
		AbilitySlot[] v = values();
		return i >= 0 && i < v.length ? v[i] : null;
	}
}
