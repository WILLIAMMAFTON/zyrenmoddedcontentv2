package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.List;

/**
 * The single entry point for using an ability. Order of checks:
 *  core formed -> an aspect is active -> it has this ability -> ability finalized ->
 *  aspect not nullified -> off cooldown -> ability's own checks -> enough Qi -> pay -> perform
 *
 * Qi is paid from the one shared pool (qi_core_amount); paying interrupts an aspect switch.
 */
public final class AbilityService {
	private AbilityService() {
	}

	/** The ability whose perform() is running right now (server thread), else null. Lets buffs remember their origin. */
	private static Ability performing;

	static Ability performing() {
		return performing;
	}

	/** The ability in this slot of the player's ACTIVE aspect (sub-aspect form included), or null. */
	public static Ability activeAbility(Player player, AbilitySlot slot) {
		return KitResolver.abilityFor(player, slot);
	}

	public static ActionResult use(ServerPlayer player, AbilitySlot slot) {
		if (slot == null)
			return ActionResult.fail("Unknown ability.");
		if (!player.isAlive() || player.isSpectator())
			return ActionResult.fail("You can't use abilities right now.");
		if (QiData.of(player).getCoreLevel() < 1 && !net.zyren.cultivation.system.EmergencyCore.inGrace(player))
			return ActionResult.fail("You have no Qi Core.");
		AspectType active = AspectData.of(player).getActive();
		if (active == null)
			return ActionResult.fail("No aspect is active.");
		Ability ability = activeAbility(player, slot);
		if (ability == null)
			return ActionResult.fail(active.displayName() + " has no " + slot.displayName() + ".");
		if (!ability.isUnlocked(player))
			return ActionResult.fail(ability.requiredLevel() < 0 ? ability.name() + " is disabled on this server."
					: "Locked: needs " + active.displayName() + " level " + ability.requiredLevel() + ".");
		List<String> missing = ability.missingValues();
		if (!missing.isEmpty())
			return ActionResult.fail(ability.name() + " is not finalized (" + String.join(", ", missing) + ").");

		AbilityState state = AbilityState.of(player);
		long now = player.serverLevel().getGameTime();
		if (state.isNullified(active, now))
			return ActionResult.fail(active.displayName() + " is nullified.");
		ActionResult recast = ability.onRecast(player);
		if (recast != null) {
			state.markDirty();
			return recast;
		}
		long cd = state.cooldownRemaining(ability, now);
		if (cd > 0)
			return ActionResult.fail(ability.name() + " is on cooldown (" + (cd + 19) / 20 + "s).");

		if (ability.kind() == Ability.Kind.SUSTAINED && state.isSustained(ability))
			return ActionResult.fail(ability.name() + " is already active.");
		if (ability.kind() == Ability.Kind.LETHAL_GUARD && state.lethalGuard() == ability)
			return ActionResult.fail(ability.name() + " is already armed.");

		ActionResult pre = ability.checkUse(player);
		if (!pre.success())
			return pre;

		double base = ability.baseCost(player);
		if (base < 0)
			return ActionResult.fail(ability.name() + " is not finalized (cost).");
		double cost = QiCosts.effective(player, base);
		if (ability.costCappedAtCurrentQi())
			cost = Math.min(cost, QiData.of(player).getCoreAmount());
		if (!QiCoreStorage.trySpendQi(player, cost)) {
			// Emergency Core Breakdown (Ability 1 / Ability 2 only, toggle ON): restores Qi, then retry.
			// Ability 1/2 slots incl. the Ultimate (user 2026-10-08: ultimates sit in the ability 1/2 positions).
			// Only abilities marked "can break core on use" (default: the Ability 1/2 slots).
			boolean rescued = ability.canBreakCore() && net.zyren.cultivation.system.EmergencyCore.tryEmergency(player, cost);
			if (rescued && !player.isAlive())
				return ActionResult.fail("Your Core was annihilated.");
			if (rescued) {
				cost = QiCosts.effective(player, base); // the core type may have changed
				if (ability.costCappedAtCurrentQi())
					cost = Math.min(cost, QiData.of(player).getCoreAmount());
			}
			if (!rescued || !QiCoreStorage.trySpendQi(player, cost)) {
				net.zyren.cultivation.system.CultivationAdvancements.onOutOfQi(player);
				return ActionResult.fail("Not enough Qi (" + fmt(cost) + " needed).");
			}
		}

		state.recordPaid(cost);
		net.zyren.cultivation.system.AspectMastery.onQiSpent(player, ability.aspect(), cost);
		state.startCooldown(ability, now, ability.cooldownTicks());
		switch (ability.kind()) {
			case EMPOWER -> state.arm(ability, now, AbilityConfig.EMPOWER_WINDOW_TICKS.get());
			case SUSTAINED -> state.startSustained(ability, now + ability.sustainTicks());
			case LETHAL_GUARD -> state.setLethalGuard(ability);
			default -> {
			}
		}
		ActionResult result;
		performing = ability;
		try {
			result = ability.perform(player);
		} finally {
			performing = null;
		}
		net.zyren.cultivation.system.CultivationAdvancements.onAbilityUsed(player, ability.aspect());
		state.markDirty();
		return result;
	}

	public static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
	}
}
