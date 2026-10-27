package net.zyren.cultivation.system;

import net.minecraft.server.level.ServerPlayer;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.network.CultivationNetwork;
import net.zyren.cultivation.network.SwitchStatusMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Changing the active aspect. Only one aspect is active at a time; activating Pure replaces
 * whatever was active.
 *
 * A switch is a timed channel (aspects.switch_ticks) so players can't swap mid-fight. It is
 * refused while the current aspect is in use, and cancelled by taking damage, attacking,
 * spending Qi or closing the cultivation screen (see AspectSwitchEvents / network handlers).
 *
 * Channel state is server memory only; it doesn't need saving (logging out cancels it).
 */
public final class AspectSwitching {
	private record Pending(AspectType target, long startTick, int durationTicks) {
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private AspectSwitching() {
	}

	public static ActionResult requestSwitch(ServerPlayer player, AspectType target) {
		ActionResult check = validate(player, target);
		if (!check.success()) {
			CultivationNetwork.sendStatus(player, SwitchStatusMessage.refused(target, check.message()));
			return check;
		}

		int duration = CultivationConfig.ASPECT_SWITCH_TICKS.get();
		if (duration <= 0) {
			complete(player, target);
			return ActionResult.ok("Now channeling " + target.displayName() + ".");
		}

		// A new request replaces any switch already in progress.
		PENDING.put(player.getUUID(), new Pending(target, player.serverLevel().getGameTime(), duration));
		CultivationNetwork.sendStatus(player, SwitchStatusMessage.started(target, duration));
		return ActionResult.ok("Switching to " + target.displayName() + "...");
	}

	private static ActionResult validate(ServerPlayer player, AspectType target) {
		if (target == null)
			return ActionResult.fail("Unknown aspect.");
		if (!player.isAlive() || player.isSpectator())
			return ActionResult.fail("You can't do that right now.");
		if (QiData.of(player).getCoreLevel() < 1 && !EmergencyCore.inGrace(player))
			return ActionResult.fail("You have no Qi Core.");
		AspectData a = AspectData.of(player);
		if (!a.owns(target))
			return ActionResult.fail("You don't have the " + target.displayName() + " aspect.");
		if (target == AspectType.PURE && QiData.of(player).getCoreType() != CoreType.PURE)
			return ActionResult.fail("A Corrupted or Poisoned Core cannot use the Pure aspect.");
		if (a.getActive() == target)
			return ActionResult.fail(target.displayName() + " is already active.");
		if (AspectUsage.isInUse(player))
			return ActionResult.fail("Your current aspect is in use.");
		return ActionResult.ok("");
	}

	/** Cancels a switch in progress, if any, and tells the client why. */
	public static void cancel(ServerPlayer player, String reason) {
		Pending p = PENDING.remove(player.getUUID());
		if (p != null)
			CultivationNetwork.sendStatus(player, SwitchStatusMessage.cancelled(p.target(), reason));
	}

	/** Drops state without messaging (logout). */
	public static void discard(ServerPlayer player) {
		PENDING.remove(player.getUUID());
	}

	public static boolean isSwitching(ServerPlayer player) {
		return PENDING.containsKey(player.getUUID());
	}

	/** Called every server tick for each player. */
	public static void tick(ServerPlayer player) {
		Pending p = PENDING.get(player.getUUID());
		if (p == null)
			return;
		if (player.serverLevel().getGameTime() - p.startTick() < p.durationTicks())
			return;
		PENDING.remove(player.getUUID());
		// Re-check ownership: the aspect could have been removed during the channel.
		if (!AspectData.of(player).owns(p.target())) {
			CultivationNetwork.sendStatus(player, SwitchStatusMessage.cancelled(p.target(), "Aspect no longer owned."));
			return;
		}
		// ...and the core: it could have broken (or turned Corrupted/Poisoned for Pure) during the channel.
		if (QiData.of(player).getCoreLevel() < 1 && !EmergencyCore.inGrace(player)) {
			CultivationNetwork.sendStatus(player, SwitchStatusMessage.cancelled(p.target(), "You have no Qi Core."));
			return;
		}
		if (p.target() == AspectType.PURE && QiData.of(player).getCoreType() != CoreType.PURE) {
			CultivationNetwork.sendStatus(player, SwitchStatusMessage.cancelled(p.target(), "A Corrupted or Poisoned Core cannot use the Pure aspect."));
			return;
		}
		complete(player, p.target());
	}

	private static void complete(ServerPlayer player, AspectType target) {
		AspectData.of(player).setActive(target);
		// Switching ends the other aspects' buffs (output multipliers, Reinforce) and Reinforce extras (user decision 2026-10-08).
		net.zyren.cultivation.ability.core.AspectCleanup.onSwitched(player, target);
		CultivationNetwork.sendStatus(player, SwitchStatusMessage.completed(target));
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		PENDING.clear();
	}
}
