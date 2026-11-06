package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;

import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Extra cast rules kits can plug into {@link AbilityService} (first used by Physical):
 * a rule may refuse a cast (locks), or make it free (no Qi, no cooldown). Also holds
 * "can't be picked as a target" windows used by {@link Targeting#isValid}.
 */
public final class CastRules {
	private CastRules() {
	}

	public interface Rule {
		/** Non-null = this cast is refused with that message. Checked before the cooldown. */
		default ActionResult refuse(ServerPlayer p, Ability a, long now) {
			return null;
		}

		/** True = no Qi cost and no cooldown for this cast (cooldown already running is ignored). */
		default boolean free(ServerPlayer p, Ability a, long now) {
			return false;
		}

		/** After a successful cast. */
		default void used(ServerPlayer p, Ability a, long now) {
		}
	}

	private static final List<Rule> RULES = new ArrayList<>();

	/** Idempotent per rule instance. */
	public static void register(Rule r) {
		if (!RULES.contains(r))
			RULES.add(r);
	}

	static ActionResult refuse(ServerPlayer p, Ability a, long now) {
		for (Rule r : RULES) {
			ActionResult res = r.refuse(p, a, now);
			if (res != null)
				return res;
		}
		return null;
	}

	static boolean free(ServerPlayer p, Ability a, long now) {
		for (Rule r : RULES)
			if (r.free(p, a, now))
				return true;
		return false;
	}

	static void used(ServerPlayer p, Ability a, long now) {
		for (Rule r : RULES)
			r.used(p, a, now);
	}

	// ------------------------------------------------------------------ untargetable windows

	private static final Map<UUID, Long> HIDDEN_UNTIL = new HashMap<>();

	/** The player can't be picked by player targeting (nearest / looked-at / "valid player") for this long. */
	public static void hideFromTargeting(ServerPlayer p, int ticks) {
		HIDDEN_UNTIL.put(p.getUUID(), p.serverLevel().getGameTime() + ticks);
	}

	public static boolean hiddenFromTargeting(ServerPlayer p) {
		if (HIDDEN_UNTIL.isEmpty())
			return false;
		Long until = HIDDEN_UNTIL.get(p.getUUID());
		if (until == null)
			return false;
		if (p.serverLevel().getGameTime() >= until) {
			HIDDEN_UNTIL.remove(p.getUUID());
			return false;
		}
		return true;
	}

	public static void clear(UUID id) {
		HIDDEN_UNTIL.remove(id);
	}

	public static void resetAll() {
		HIDDEN_UNTIL.clear();
	}
}
