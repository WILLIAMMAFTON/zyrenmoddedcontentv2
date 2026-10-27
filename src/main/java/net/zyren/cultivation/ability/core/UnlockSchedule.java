package net.zyren.cultivation.ability.core;

import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.common.ModConfigSpec;

import net.zyren.cultivation.config.UnlockConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;

/**
 * Reads the unlock schedule (UnlockConfig). Works on both sides; before the server config has
 * arrived it uses the default values, so client and server never disagree for long.
 */
public final class UnlockSchedule {
	private UnlockSchedule() {
	}

	/** Level needed for a slot of a normal / sub-aspect kit. Pure: 0 (Pure abilities set their own). */
	public static int level(AspectType aspect, AbilitySlot slot) {
		if (aspect == AspectType.PURE || slot == null)
			return 0;
		return read(switch (slot) {
			case ATTACK_1 -> UnlockConfig.ATTACK_1;
			case HEAVY -> UnlockConfig.HEAVY;
			case REINFORCE -> UnlockConfig.REINFORCE;
			case POWERBLAST_1 -> UnlockConfig.POWERBLAST_1;
			case POWERBLAST_2 -> UnlockConfig.POWERBLAST_2;
			case ABILITY_1 -> UnlockConfig.ABILITY_1;
			case ABILITY_2 -> UnlockConfig.ABILITY_2;
			case ULTIMATE -> UnlockConfig.ULTIMATE;
		});
	}

	/** Level needed for an aspect's passive. Pure: 0 (unchanged). */
	public static int passiveLevel(AspectType aspect) {
		return aspect == AspectType.PURE ? 0 : read(UnlockConfig.PASSIVE);
	}

	/** True if the player's level in this aspect unlocks its passive. */
	public static boolean passiveUnlocked(Player player, AspectType aspect) {
		int req = passiveLevel(aspect);
		return req >= 0 && AspectData.of(player).getLevel(aspect) >= req;
	}

	private static int read(ModConfigSpec.IntValue v) {
		try {
			return v.get();
		} catch (IllegalStateException notLoaded) {
			return v.getDefault();
		}
	}
}
