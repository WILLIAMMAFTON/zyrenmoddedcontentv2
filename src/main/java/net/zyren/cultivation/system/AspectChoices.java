package net.zyren.cultivation.system;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;

/**
 * Breakthrough choices: the only way aspects are gained or leveled.
 * Each pending choice is spent on exactly one of:
 *  - gain a new aspect into an empty slot (max 4; Pure can't be gained, it's always owned)
 *  - upgrade an owned aspect (+1 level), including Pure
 */
public final class AspectChoices {
	private AspectChoices() {
	}

	public static void grantChoices(Player player, int count) {
		if (player.level().isClientSide() || count <= 0)
			return;
		AspectData a = AspectData.of(player);
		a.setPendingChoices(a.getPendingChoices() + count);
		player.displayClientMessage(Component.literal("Breakthrough choice available. Open the cultivation menu to choose."), false);
	}

	/**
	 * Offered as a breakthrough choice: a slot aspect (not Pure, not legacy Warrior) that has a kit.
	 * Air / Space stay hidden until their kits exist (user 2026-10-27), then appear automatically.
	 */
	public static boolean offered(AspectType type) {
		return type != null && !type.isSpecial() && !type.isLegacy() && net.zyren.cultivation.ability.core.AspectKits.get(type) != null;
	}

	public static ActionResult chooseNewAspect(Player player, AspectType type) {
		return chooseNewAspect(player, type, false);
	}

	/** @param admin true for /qi aspect gain: also allows aspects without a kit yet (still never Warrior). */
	public static ActionResult chooseNewAspect(Player player, AspectType type, boolean admin) {
		if (player.level().isClientSide())
			return ActionResult.fail("Server only.");
		AspectData a = AspectData.of(player);
		if (a.getPendingChoices() <= 0)
			return ActionResult.fail("No breakthrough choice available.");
		if (type == null)
			return ActionResult.fail("Unknown aspect.");
		if (type.isSpecial())
			return ActionResult.fail(type.displayName() + " is always available; upgrade it instead.");
		if (type.isLegacy())
			return ActionResult.fail("Warrior is now a Physical sub-aspect (Physical \u2192 Warrior).");
		if (!admin && !offered(type))
			return ActionResult.fail(type.displayName() + " isn't available yet.");
		if (a.owns(type))
			return ActionResult.fail("You already have " + type.displayName() + ".");
		int slot = a.firstEmptySlot();
		if (slot == 0)
			return ActionResult.fail("All " + AspectData.SLOT_COUNT + " aspect slots are full.");

		a.setSlot(slot, type, CultivationConfig.NEW_ASPECT_LEVEL.get());
		a.setPendingChoices(a.getPendingChoices() - 1);
		return ActionResult.ok("Gained the " + type.displayName() + " aspect.");
	}

	public static ActionResult chooseUpgrade(Player player, AspectType type) {
		if (player.level().isClientSide())
			return ActionResult.fail("Server only.");
		AspectData a = AspectData.of(player);
		if (a.getPendingChoices() <= 0)
			return ActionResult.fail("No breakthrough choice available.");
		if (type == null || !a.owns(type))
			return ActionResult.fail("You don't have that aspect.");

		int newLevel;
		if (type == AspectType.PURE) {
			newLevel = a.getPureLevel() + 1;
			a.setPureLevel(newLevel);
		} else {
			int slot = a.findSlot(type);
			newLevel = a.getSlotLevel(slot) + 1;
			a.setSlotLevel(slot, newLevel);
		}
		a.setPendingChoices(a.getPendingChoices() - 1);
		return ActionResult.ok(type.displayName() + " is now level " + newLevel + ".");
	}
}
