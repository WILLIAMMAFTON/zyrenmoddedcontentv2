package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.network.DeathChoiceMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Normal death consequences for PURE / POISONED cores (source: User Core Death System Source of
 * Truth + decisions in claude/death-penalty.md). CORRUPTED cores are skipped (their own rules apply).
 *
 * The highest-level aspect (Pure included) is hit:
 *   - one non-Pure aspect highest -> it is fully reset and removed from its slot;
 *   - Pure alone highest          -> Pure level -2 (never below pure_level_on_core_formation);
 *   - a tie (2+ aspects, Pure may be one of them; decided) -> after respawn the player picks
 *     which one takes the penalty in a GUI that can't be dismissed.
 * Only players with a formed core (level 1+) are affected (decided). Dying again while a choice
 * is pending adds no new penalty (decided).
 *
 * The pending choice lives in the player's persistent NBT (survives respawn and logout).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class DeathPenalty {
	private static final String KEY = "zyren_death_choice";

	private DeathPenalty() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player))
			return;
		QiData q = QiData.of(player);
		if (q.getCoreLevel() < 1 || q.getCoreType() == CoreType.CORRUPTED)
			return;
		if (!pending(player).isEmpty())
			return; // an earlier death's choice is still open: no new penalty (decided)

		AspectData a = AspectData.of(player);
		List<AspectType> tied = highest(a);
		if (tied.isEmpty())
			return;
		if (tied.size() == 1) {
			apply(player, tied.get(0));
			return;
		}
		setPending(player, tied);
		player.displayClientMessage(Component.literal("Your aspects are tied: choose which one to lose after you respawn.")
				.withStyle(ChatFormatting.DARK_RED), false);
	}

	/** All owned aspects (Pure + slots) sharing the highest level (level 1+). */
	static List<AspectType> highest(AspectData a) {
		List<AspectType> list = new ArrayList<>();
		int best = 0;
		List<AspectType> owned = new ArrayList<>();
		owned.add(AspectType.PURE);
		for (int s = 1; s <= AspectData.SLOT_COUNT; s++)
			if (a.getSlotType(s) != null)
				owned.add(a.getSlotType(s));
		for (AspectType t : owned) {
			int lvl = a.getLevel(t);
			if (lvl > best) {
				best = lvl;
				list.clear();
			}
			if (lvl == best && lvl > 0)
				list.add(t);
		}
		return list;
	}

	/** Applies the death penalty to one aspect. */
	static void apply(ServerPlayer player, AspectType type) {
		AspectData a = AspectData.of(player);
		if (type == AspectType.PURE) {
			int min = CultivationConfig.PURE_LEVEL_ON_CORE_FORMATION.get();
			int before = a.getPureLevel();
			int after = Math.max(min, before - 2);
			a.setPureLevel(after);
			player.displayClientMessage(Component.literal("Death weakens your Pure aspect: level " + before + " -> " + after + ".")
					.withStyle(ChatFormatting.DARK_RED), false);
			return;
		}
		int slot = a.findSlot(type);
		if (slot == 0)
			return;
		a.setSlot(slot, null, 0); // level -> 0, aspect leaves the slot
		a.setSlotXp(slot, 0);     // mastery XP -> 0
		if (a.getActive() == type) {
			a.setActive(null);
			AspectSwitching.discard(player);
		}
		player.displayClientMessage(Component.literal("Death tears away your " + type.displayName() + " aspect. Its slot is free again.")
				.withStyle(ChatFormatting.DARK_RED), false);
	}

	// ---------------------------------------------------------------- pending choice

	public static List<AspectType> pending(Player player) {
		List<AspectType> list = new ArrayList<>();
		String raw = player.getPersistentData().getString(KEY);
		if (raw.isEmpty())
			return list;
		for (String id : raw.split(",")) {
			AspectType t = AspectType.fromId(id);
			if (t != null)
				list.add(t);
		}
		return list;
	}

	private static void setPending(Player player, List<AspectType> list) {
		if (list.isEmpty()) {
			player.getPersistentData().remove(KEY);
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (AspectType t : list)
			sb.append(sb.length() == 0 ? "" : ",").append(t.id());
		player.getPersistentData().putString(KEY, sb.toString());
	}

	/** The client picked an aspect in the GUI. Validated against the pending choice. */
	public static void choose(ServerPlayer player, String aspectId) {
		List<AspectType> options = pending(player);
		AspectType pick = AspectType.fromId(aspectId);
		if (options.isEmpty())
			return; // nothing pending (already chosen, or never died with a tie)
		if (pick == null || !options.contains(pick) || !player.isAlive()) {
			DeathChoiceMessage.send(player, options); // invalid: show the choices again
			return;
		}
		setPending(player, List.of()); // clear first: the choice can't be made twice
		DEADLINES.remove(player.getUUID());
		// Only still-owned aspects can be lost; if it vanished meanwhile, nothing else is touched.
		if (pick == AspectType.PURE || AspectData.of(player).findSlot(pick) != 0)
			apply(player, pick);
		DeathChoiceMessage.send(player, List.of()); // closes the GUI
	}

	private static void showIfPending(ServerPlayer player) {
		List<AspectType> options = pending(player);
		if (options.isEmpty())
			return;
		DeathChoiceMessage.send(player, options);
		int timeout = CultivationConfig.DEATH_CHOICE_TIMEOUT_TICKS.get();
		if (timeout > 0)
			DEADLINES.put(player.getUUID(), player.serverLevel().getGameTime() + timeout);
	}

	// ---------------------------------------------------------------- idle timeout

	/** When the shown choice must be made by (game time). In memory: re-armed on every login/respawn. */
	private static final java.util.Map<java.util.UUID, Long> DEADLINES = new java.util.HashMap<>();

	/**
	 * Choice not made in time (user decision 2026-10-08): one tied option is picked at random,
	 * applied, and the player is kicked for idling. (Never kicks the singleplayer host — that would
	 * just close their own world; the pick still happens.)
	 */
	@SubscribeEvent
	public static void onTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || (p.serverLevel().getGameTime() + p.getId()) % 20 != 0)
			return;
		Long deadline = DEADLINES.get(p.getUUID());
		if (deadline == null || p.serverLevel().getGameTime() < deadline || !p.isAlive())
			return;
		DEADLINES.remove(p.getUUID());
		List<AspectType> options = pending(p);
		if (options.isEmpty())
			return;
		AspectType pick = options.get(p.getRandom().nextInt(options.size()));
		setPending(p, List.of());
		if (pick == AspectType.PURE || AspectData.of(p).findSlot(pick) != 0)
			apply(p, pick);
		DeathChoiceMessage.send(p, List.of());
		Component reason = Component.literal("Kicked for idling: you didn't choose an aspect to lose, so "
				+ (pick == AspectType.PURE ? "Pure lost 2 levels." : pick.displayName() + " was removed."));
		if (p.server.isSingleplayerOwner(p.getGameProfile()))
			p.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), false);
		else
			p.connection.disconnect(reason);
	}

	public static void resetAll() {
		DEADLINES.clear();
	}

	// ---------------------------------------------------------------- lifecycle

	/** Persistent NBT is not copied to the respawned player by default. */
	@SubscribeEvent
	public static void onClone(PlayerEvent.Clone event) {
		CompoundTag old = event.getOriginal().getPersistentData();
		if (old.contains(KEY))
			event.getEntity().getPersistentData().putString(KEY, old.getString(KEY));
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && !event.isEndConquered())
			showIfPending(p);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		DEADLINES.remove(event.getEntity().getUUID());
	}

	@SubscribeEvent
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && p.isAlive())
			showIfPending(p);
	}
}
