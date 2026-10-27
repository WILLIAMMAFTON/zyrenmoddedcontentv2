package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.config.AbilityConfig;

import java.util.Comparator;
import java.util.UUID;

/**
 * The document's targeting rules:
 *  - Nearest Player       = nearest valid player other than the user
 *  - Last Damaged Player  = player most recently damaged by the user
 *  - Looked-At Player     = valid player currently targeted by line of sight
 *  - "target" (no method) = Last Damaged Player when valid, otherwise Looked-At Player
 *
 * Valid = another living, non-spectator player in the same dimension.
 */
public final class Targeting {
	private Targeting() {
	}

	public static boolean isValid(ServerPlayer user, ServerPlayer other) {
		return other != null && other != user && other.isAlive() && !other.isSpectator() && other.level() == user.level();
	}

	/** Nearest valid player in the same dimension (no range limit in the document), or null. */
	public static ServerPlayer nearestPlayer(ServerPlayer user) {
		return user.serverLevel().players().stream()
				.filter(p -> isValid(user, p))
				.min(Comparator.comparingDouble(p -> p.distanceToSqr(user)))
				.orElse(null);
	}

	public static ServerPlayer lastDamagedPlayer(ServerPlayer user) {
		UUID id = AbilityState.of(user).lastDamagedPlayer();
		if (id == null)
			return null;
		ServerPlayer p = user.server.getPlayerList().getPlayer(id);
		return isValid(user, p) ? p : null;
	}

	/** Player under the crosshair within framework.looked_at_range, with line of sight, or null. */
	public static ServerPlayer lookedAtPlayer(ServerPlayer user) {
		double range = AbilityConfig.LOOKED_AT_RANGE.get();
		Vec3 eye = user.getEyePosition();
		Vec3 end = eye.add(user.getLookAngle().scale(range));
		ServerPlayer best = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayer p : user.serverLevel().players()) {
			if (!isValid(user, p) || p.distanceToSqr(user) > range * range)
				continue;
			AABB box = p.getBoundingBox().inflate(0.3);
			var hit = box.clip(eye, end);
			if (hit.isPresent() && user.hasLineOfSight(p)) {
				double d = eye.distanceToSqr(hit.get());
				if (d < bestDist) {
					bestDist = d;
					best = p;
				}
			}
		}
		return best;
	}

	/** Default rule for abilities that just say "target". */
	public static ServerPlayer defaultTarget(ServerPlayer user) {
		ServerPlayer p = lastDamagedPlayer(user);
		return p != null ? p : lookedAtPlayer(user);
	}

	/** Allied/team members count as allied (vanilla scoreboard teams). The user is never "unallied". */
	public static boolean isAllied(ServerPlayer user, ServerPlayer other) {
		return other == user || user.isAlliedTo(other);
	}
}
