package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectData;
import net.zyren.cultivation.data.SubAspectType;

/**
 * One-time save migrations, run on login before the normal clean-up.
 *
 * WARRIOR -> PHYSICAL (user decision 2026-10-27): Warrior became the sub-aspect Physical -> Warrior.
 *   - The Warrior slot becomes Physical with the same level and mastery XP, and Physical -> Warrior
 *     is LEARNED (not equipped).
 *   - Already owning Physical in another slot: the Warrior slot is freed, Physical keeps the higher
 *     of the two levels (and that slot's mastery XP if it was the higher one).
 *   - An active Warrior aspect becomes active Physical (normal form).
 */
public final class AspectMigration {
	private AspectMigration() {
	}

	public static void migrate(ServerPlayer player) {
		AspectData a = AspectData.of(player);
		int warriorSlot = a.findSlot(AspectType.WARRIOR);
		if (warriorSlot == 0) {
			if (a.getActive() == AspectType.WARRIOR)
				a.setActive(null);
			return;
		}
		boolean wasActive = a.getActive() == AspectType.WARRIOR;
		int wLevel = a.getSlotLevel(warriorSlot);
		double wXp = a.getSlotXp(warriorSlot);
		int physSlot = a.findSlot(AspectType.PHYSICAL);
		int target;
		if (physSlot == 0) {
			a.setSlot(warriorSlot, AspectType.PHYSICAL, wLevel); // a different type resets XP ...
			a.setSlotXp(warriorSlot, wXp); // ... so put it back
			target = warriorSlot;
		} else {
			if (wLevel > a.getSlotLevel(physSlot)) {
				a.setSlotLevel(physSlot, wLevel);
				a.setSlotXp(physSlot, wXp);
			}
			a.setSlot(warriorSlot, null, 0);
			target = physSlot;
		}
		SubAspectData.of(player).learn(SubAspectType.PHYSICAL_WARRIOR);
		if (wasActive)
			a.setActive(AspectType.PHYSICAL);
		player.displayClientMessage(Component.literal("Warrior is now a Physical sub-aspect: your Warrior became Physical (level "
				+ a.getSlotLevel(target) + ") and you know Physical → Warrior. Equip it in the cultivation menu (K → Sub).")
				.withStyle(ChatFormatting.GOLD), false);
	}
}
