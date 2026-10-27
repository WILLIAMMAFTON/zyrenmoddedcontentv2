package net.zyren.cultivation.system;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Anti-loop protection (user decisions 2026-10-07, claude/integration-audit-2026-10-07.md):
 * "Qi should reward new activity, not reversible conversions or sacrificial alts."
 *
 *  - Breakthrough choices are one-time per core level: a level regained after an Emergency
 *    Breakdown / Poisoned potion (or after a core break) grants no new choice.
 *  - Brewing pays only for potions actually produced by a brew.
 *  - Crafting pays for NET transformations: un-crafting (block -> ingots) pays nothing and
 *    banks a credit; re-crafting that item (ingots -> block) consumes the credit instead of paying.
 *  - Cultivator-kill bonus: the same killer only gets it again from the same victim once the
 *    victim's core level is higher than at the last rewarded kill.
 *
 * Per-player records live in the player's persistent NBT (survive death and logout).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class AntiFarm {
	private static final String ROOT = "zyren_antifarm";
	private static final String CHOICE_LEVEL = "choice_level";
	private static final String UNCRAFT = "uncraft";
	private static final String KILLS = "kills";
	private static final String BREWED = "zyren_brewed";

	/** "inputItem>resultItem" -> is there a reverse recipe (cached; cleared on server stop). */
	private static final Map<String, Boolean> REVERSIBLE = new HashMap<>();
	private static ItemStack[] brewBefore = null;

	private AntiFarm() {
	}

	private static CompoundTag root(Player p) {
		CompoundTag data = p.getPersistentData();
		if (!data.contains(ROOT))
			data.put(ROOT, new CompoundTag());
		return data.getCompound(ROOT);
	}

	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		CompoundTag old = event.getOriginal().getPersistentData();
		if (old.contains(ROOT))
			event.getEntity().getPersistentData().put(ROOT, old.getCompound(ROOT).copy());
	}

	public static void resetAll() {
		REVERSIBLE.clear();
		brewBefore = null;
	}

	// ---------------------------------------------------------------- breakthrough choices

	/** Records the current core level as "already reached" (call before lowering a core). */
	public static void markLevelReached(Player p) {
		CompoundTag t = root(p);
		int lvl = net.zyren.cultivation.data.QiData.of(p).getCoreLevel();
		if (!t.contains(CHOICE_LEVEL) || t.getInt(CHOICE_LEVEL) < lvl)
			t.putInt(CHOICE_LEVEL, lvl);
	}

	/**
	 * Called when the core reaches {@code newLevel}. @return true if this level has never been
	 * reached before (so it may grant its choice); records it either way.
	 */
	public static boolean firstTimeReaching(Player p, int newLevel) {
		CompoundTag t = root(p);
		// No record yet (older saves): assume everything below the new level was already reached.
		int best = t.contains(CHOICE_LEVEL) ? t.getInt(CHOICE_LEVEL) : newLevel - 1;
		if (newLevel <= best) {
			t.putInt(CHOICE_LEVEL, best);
			return false;
		}
		t.putInt(CHOICE_LEVEL, newLevel);
		return true;
	}

	// ---------------------------------------------------------------- brewing

	@SubscribeEvent
	public static void onBrewPre(PotionBrewEvent.Pre event) {
		brewBefore = new ItemStack[event.getLength()];
		for (int i = 0; i < event.getLength(); i++)
			brewBefore[i] = event.getItem(i).copy();
	}

	/** Mark the potions the brew actually changed. */
	@SubscribeEvent
	public static void onBrewPost(PotionBrewEvent.Post event) {
		ItemStack[] before = brewBefore;
		brewBefore = null;
		for (int i = 0; i < event.getLength(); i++) {
			ItemStack now = event.getItem(i);
			if (now.isEmpty() || now.get(DataComponents.POTION_CONTENTS) == null)
				continue;
			if (before != null && i < before.length && ItemStack.isSameItemSameComponents(before[i], now))
				continue; // unchanged by this brew
			CustomData.update(DataComponents.CUSTOM_DATA, now, tag -> tag.putBoolean(BREWED, true));
		}
	}

	/**
	 * Shift-clicking a potion out hands the event a COPY of the stack, so the real potion keeps
	 * its mark. A tick later, strip the mark from everything the player holds (a marked potion
	 * outside a brewing stand is never legitimate), so it can't be put back in and paid again.
	 */
	public static void stripBrewedMarksSoon(ServerPlayer p) {
		p.server.tell(new net.minecraft.server.TickTask(p.server.getTickCount() + 1, () -> {
			for (ItemStack s : p.getInventory().items)
				removeMark(s);
			for (ItemStack s : p.getInventory().offhand)
				removeMark(s);
			removeMark(p.containerMenu.getCarried());
		}));
	}

	private static void removeMark(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.copyTag().contains(BREWED))
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BREWED));
	}

	/** Recipes can change on /reload: drop the reversible-recipe cache. */
	@SubscribeEvent
	public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
		if (event.getPlayer() == null) // whole-server sync (/reload or server start), not a single join
			REVERSIBLE.clear();
	}

	/** Taking a potion out of a brewing stand: true (and the mark is removed) only if it was brewed. */
	public static boolean consumeBrewedMark(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.copyTag().getBoolean(BREWED))
			return false;
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(BREWED));
		return true;
	}

	// ---------------------------------------------------------------- crafting

	/** @return true if this craft should pay crafting Qi. */
	public static boolean craftPays(ServerPlayer p, ItemStack result, Container grid) {
		Item single = null;
		int slots = 0;
		for (int i = 0; i < grid.getContainerSize(); i++) {
			ItemStack s = grid.getItem(i);
			if (s.isEmpty())
				continue;
			slots++;
			if (single == null)
				single = s.getItem();
			else if (single != s.getItem())
				return true; // mixed ingredients: not a simple reversible conversion
		}
		if (single == null || single == result.getItem())
			return true;
		CompoundTag ledger = root(p).getCompound(UNCRAFT);
		if (slots == 1 && reversible(p, single, result.getItem(), true)) {
			// Un-crafting (block -> ingots): pays nothing, banks one credit for that block.
			String key = id(single);
			ledger.putInt(key, ledger.getInt(key) + 1);
			root(p).put(UNCRAFT, ledger);
			return false;
		}
		if (slots > 1 && reversible(p, single, result.getItem(), false)) {
			// Re-crafting (ingots -> block): uses up a credit if one was banked, else pays (net new).
			String key = id(result.getItem());
			int credit = ledger.getInt(key);
			if (credit > 0) {
				if (credit == 1)
					ledger.remove(key);
				else
					ledger.putInt(key, credit - 1);
				root(p).put(UNCRAFT, ledger);
				return false;
			}
		}
		return true;
	}

	/**
	 * Is there a crafting recipe going back from {@code result} to {@code input}?
	 * @param fromSingle true: the craft was 1 input -> many (look for a many-of-result -> input recipe);
	 *                   false: many -> 1 (look for a 1-result -> input recipe)
	 */
	private static boolean reversible(ServerPlayer p, Item input, Item result, boolean fromSingle) {
		String key = id(input) + ">" + id(result) + (fromSingle ? "/1" : "/n");
		Boolean cached = REVERSIBLE.get(key);
		if (cached != null)
			return cached;
		boolean found = false;
		ItemStack probe = new ItemStack(result);
		var registries = p.server.registryAccess();
		for (RecipeHolder<CraftingRecipe> h : p.server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
			CraftingRecipe r = h.value();
			if (r.getResultItem(registries).getItem() != input)
				continue;
			int count = 0;
			boolean allResult = true;
			for (Ingredient ing : r.getIngredients()) {
				if (ing.isEmpty())
					continue;
				count++;
				if (!ing.test(probe)) {
					allResult = false;
					break;
				}
			}
			if (allResult && count > 0 && (fromSingle ? count > 1 : count == 1)) {
				found = true;
				break;
			}
		}
		REVERSIBLE.put(key, found);
		return found;
	}

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	// ---------------------------------------------------------------- cultivator kills

	/** @return true if killing {@code victim} may pay the cultivator-kill bonus (and records it). */
	public static boolean cultivatorKillPays(ServerPlayer killer, ServerPlayer victim) {
		int level = net.zyren.cultivation.data.QiData.of(victim).getCoreLevel();
		CompoundTag kills = root(killer).getCompound(KILLS);
		String key = victim.getUUID().toString();
		if (kills.contains(key) && level <= kills.getInt(key))
			return false; // no progression since the last rewarded kill of this player
		kills.putInt(key, level);
		root(killer).put(KILLS, kills);
		return true;
	}
}
