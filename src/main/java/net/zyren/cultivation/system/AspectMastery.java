package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aspect mastery: slot aspects gain XP through practice and level up between core
 * breakthroughs (breakthrough choices still work too). Pure never gains XP.
 *
 * XP sources (all amounts in config, aspect_mastery):
 *   1. Qi spent on the aspect's abilities and passive
 *   2. the aspect's ability / empowered hit damaging a living target
 *   3. kills made with the aspect's ability / empowered hit
 *   4. aspect-flavour actions ({@link net.zyren.cultivation.ability.kits.MasteryEvents})
 * XP only goes to the aspect that did the thing, never to every owned aspect.
 */
public final class AspectMastery {
	private record LastHit(UUID attacker, AspectType aspect, long tick) {
	}

	/** Victim -> the last aspect-ability hit on it (for kill credit). */
	private static final Map<UUID, LastHit> LAST_HIT = new HashMap<>();

	private AspectMastery() {
	}

	/** XP needed to go from {@code level} to {@code level + 1}. */
	public static double xpNeeded(int level) {
		return CultivationConfig.MASTERY_XP_BASE.get() * Math.pow(CultivationConfig.MASTERY_XP_GROWTH.get(), Math.max(0, level - 1));
	}

	/** XP needed for this player's aspect's next level, or -1 (Pure, not owned, capped, disabled). For the HUD. */
	public static double xpNeededFor(Player player, AspectType type) {
		if (!CultivationConfig.MASTERY_ENABLED.get() || type == null || type.isSpecial())
			return -1;
		AspectData data = AspectData.of(player);
		int slot = data.findSlot(type);
		if (slot == 0)
			return -1;
		int max = CultivationConfig.MASTERY_MAX_LEVEL.get();
		int level = data.getSlotLevel(slot);
		return max > 0 && level >= max ? -1 : xpNeeded(level);
	}

	/** Adds XP to an owned slot aspect and levels it up as far as the XP allows. */
	public static void addXp(ServerPlayer player, AspectType type, double amount) {
		if (amount <= 0 || type == null || type.isSpecial() || !CultivationConfig.MASTERY_ENABLED.get())
			return;
		AspectData data = AspectData.of(player);
		int slot = data.findSlot(type);
		if (slot == 0)
			return;
		int max = CultivationConfig.MASTERY_MAX_LEVEL.get();
		int level = data.getSlotLevel(slot);
		if (max > 0 && level >= max)
			return;
		double xp = data.getSlotXp(slot) + amount;
		int gained = 0;
		while ((max <= 0 || level < max) && xp >= xpNeeded(level)) {
			xp -= xpNeeded(level);
			level++;
			gained++;
		}
		if (max > 0 && level >= max)
			xp = 0;
		data.setSlotXp(slot, xp);
		if (gained > 0) {
			data.setSlotLevel(slot, level);
			player.displayClientMessage(Component.literal(type.displayName() + " mastery rises to level " + level + "!")
					.withStyle(ChatFormatting.AQUA), false);
			player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
			AbilityState.of(player).markDirty(); // HUD gets the new "XP needed"
		}
	}

	// ---- sources 1-3 ----

	/** Source 1: Qi spent on an aspect (abilities, passive upkeep, sustained upkeep). */
	public static void onQiSpent(ServerPlayer player, AspectType aspect, double qi) {
		addXp(player, aspect, qi * CultivationConfig.MASTERY_XP_PER_QI.get());
	}

	/** Source 2: an aspect's ability or empowered hit damaged a living target. Remembered for kill credit. */
	public static void onAbilityHit(ServerPlayer attacker, AspectType aspect, LivingEntity target) {
		if (aspect == null || target == attacker || target instanceof ArmorStand)
			return;
		addXp(attacker, aspect, CultivationConfig.MASTERY_XP_PER_HIT.get());
		LAST_HIT.put(target.getUUID(), new LastHit(attacker.getUUID(), aspect, attacker.serverLevel().getGameTime()));
	}

	/** Source 3: a kill shortly after the killer's aspect ability hit the victim. */
	public static void onDeath(LivingEntity victim, ServerPlayer killer) {
		LastHit hit = LAST_HIT.remove(victim.getUUID());
		if (killer == null || hit == null || !hit.attacker().equals(killer.getUUID()))
			return;
		if (killer.serverLevel().getGameTime() - hit.tick() <= CultivationConfig.MASTERY_KILL_WINDOW_TICKS.get())
			addXp(killer, hit.aspect(), CultivationConfig.MASTERY_XP_PER_KILL.get());
	}

	/** True if this killer hit the victim with an aspect ability / empowered hit within the kill window. */
	/** True if {@code attacker} landed a Qi ability / empowered hit on {@code victim} this very tick. */
	public static boolean qiHitThisTick(LivingEntity victim, ServerPlayer attacker) {
		LastHit hit = LAST_HIT.get(victim.getUUID());
		return hit != null && hit.attacker().equals(attacker.getUUID()) && hit.tick() == attacker.serverLevel().getGameTime();
	}

	/** Records Qi used on a target (for kill credit / "killed without Qi") without granting hit XP. */
	public static void recordQiHit(ServerPlayer attacker, AspectType aspect, LivingEntity target) {
		if (aspect != null && target != attacker)
			LAST_HIT.put(target.getUUID(), new LastHit(attacker.getUUID(), aspect, attacker.serverLevel().getGameTime()));
	}

	public static boolean recentlyHitWithQi(LivingEntity victim, ServerPlayer killer) {
		LastHit hit = LAST_HIT.get(victim.getUUID());
		return hit != null && hit.attacker().equals(killer.getUUID())
				&& killer.serverLevel().getGameTime() - hit.tick() <= CultivationConfig.MASTERY_KILL_WINDOW_TICKS.get();
	}

	/** Drops kill-credit entries older than the window (called now and then). */
	public static void prune(long now) {
		int window = CultivationConfig.MASTERY_KILL_WINDOW_TICKS.get();
		LAST_HIT.values().removeIf(h -> now - h.tick() > window);
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		LAST_HIT.clear();
	}
}
