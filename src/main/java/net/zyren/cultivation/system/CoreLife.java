package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CORE core type: Qi replaces health (claude/core-types.md, decided 2026-11-06).
 *
 * Active only while the player's core type is CORE and the core is formed (level 1+). Before the
 * core forms, and after it is lost (break / annihilation), the player uses normal health.
 *
 * How it works:
 *  - Damage: every vanilla and mod reduction (armor, Protection, Resistance, Reinforce Body,
 *    absorption) runs first. Whatever health the hit actually took is given back and
 *    qi_per_damage Qi (default 100) is taken per damage point instead.
 *  - Healing: every heal (Regeneration, Instant Health, golden apples, Healing / Vitality, ...)
 *    is cancelled and becomes qi_per_damage Qi per point healed, capped at max Qi (no overflow
 *    into cultivated Qi).
 *  - NO natural (food) regeneration (user, 2026-11-06): real health is pinned at max, so the
 *    hunger bar never heals and never drains for healing. Because vanilla Regeneration only heals
 *    below max health, its ticks are replayed here at the vanilla rate (1 point every
 *    50 >> amplifier ticks) and converted to Qi.
 *  - Reaching 0 usable Qi from ANY source (damage, ability costs, drains) kills the player; the
 *    normal death penalty applies. Respawn sets Qi to a flat respawn_qi (default 200).
 *  - /kill and the void (bypasses_invulnerability) still use normal health, like vanilla.
 *  - Totem of Undying: sets health to 1, so Qi becomes 1 point's worth.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class CoreLife {
	private CoreLife() {
	}

	/** Health right before the current hit (Pre runs before absorption; Post after health changed). */
	private static final Map<UUID, Float> HEALTH_BEFORE = new HashMap<>();

	// ---------------------------------------------------------------- queries (client-safe)

	/** True if Qi replaces health for this player right now. Works on the client (synced vars). */
	public static boolean active(Player player) {
		QiData q = QiData.of(player);
		return q.getCoreType() == CoreType.CORE && q.getCoreLevel() >= 1;
	}

	public static double qiPerDamage() {
		return CultivationConfig.CORE_QI_PER_DAMAGE.get();
	}

	/** Health as gameplay should see it (Qi / qi_per_damage for Core players). */
	public static float health(Player player) {
		if (!active(player) || qiPerDamage() <= 0)
			return player.getHealth();
		return (float) (QiData.of(player).getCoreAmount() / qiPerDamage());
	}

	/** Max health as gameplay should see it (max Qi / qi_per_damage for Core players). */
	public static float maxHealth(Player player) {
		if (!active(player) || qiPerDamage() <= 0)
			return player.getMaxHealth();
		return (float) (QiData.of(player).getCoreMax() / qiPerDamage());
	}

	/** Would this much final damage (before absorption) kill the player? For lethal guards. */
	public static boolean isLethal(Player player, float damage) {
		if (!active(player))
			return damage >= player.getHealth() + player.getAbsorptionAmount();
		double taken = Math.max(0, damage - player.getAbsorptionAmount());
		return taken * qiPerDamage() >= QiData.of(player).getCoreAmount();
	}

	// ---------------------------------------------------------------- hooks from other systems

	/** Forming the core (and becoming CORE with a formed core) fills the Qi pool. */
	public static void fillIfActive(ServerPlayer player) {
		if (!active(player))
			return;
		QiData q = QiData.of(player);
		q.setCoreAmount(q.getCoreMax());
		pinHealth(player);
	}

	// ---------------------------------------------------------------- damage

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDamagePre(LivingDamageEvent.Pre event) {
		if (event.getEntity() instanceof ServerPlayer p && active(p))
			HEALTH_BEFORE.put(p.getUUID(), p.getHealth());
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onDamagePost(LivingDamageEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		Float before = HEALTH_BEFORE.remove(p.getUUID());
		if (before == null || !active(p) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))
			return;
		float lost = before - p.getHealth();
		if (lost <= 0)
			return;
		QiData q = QiData.of(p);
		double left = q.getCoreAmount() - lost * qiPerDamage();
		if (left <= 0) {
			q.setCoreAmount(0);
			p.setHealth(0); // dies from THIS hit (real killer, death message; a totem can still save)
			return;
		}
		q.setCoreAmount(left);
		p.setHealth(before); // the hit cost Qi, not health
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onTotem(LivingUseTotemEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && active(p)) {
			QiData q = QiData.of(p);
			q.setCoreAmount(Math.min(q.getCoreMax(), qiPerDamage())); // totem leaves 1 health = 1 point of Qi
		}
	}

	// ---------------------------------------------------------------- healing

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onHeal(LivingHealEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || !active(p))
			return;
		event.setCanceled(true);
		if (event.getAmount() > 0)
			QiCoreStorage.addQi(p, event.getAmount() * qiPerDamage()); // capped at max Qi
	}

	// ---------------------------------------------------------------- tick: death at 0, pinned health

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || !p.isAlive() || !active(p))
			return;
		if (QiData.of(p).getCoreAmount() <= 0) {
			if (p.isCreative() || p.isSpectator())
				return;
			p.displayClientMessage(Component.literal("Your Qi runs dry. Your Core can no longer hold you together.").withStyle(ChatFormatting.DARK_RED), false);
			p.kill();
			return;
		}
		tickRegeneration(p);
		pinHealth(p);
	}

	/** Vanilla Regeneration rate (RegenerationMobEffect): 1 point every 50 >> amplifier ticks. */
	private static void tickRegeneration(ServerPlayer p) {
		net.minecraft.world.effect.MobEffectInstance regen = p.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
		if (regen == null)
			return;
		int interval = 50 >> regen.getAmplifier();
		if (interval > 0 && regen.getDuration() % interval != 0)
			return;
		p.heal(1.0f); // -> onHeal -> Qi (other heal rules, e.g. Sun's Hatred, still apply)
	}

	/** Health stays at max: natural regen never triggers (Core players don't regenerate from food). */
	private static void pinHealth(ServerPlayer p) {
		float max = p.getMaxHealth();
		if (p.getHealth() != max)
			p.setHealth(max);
	}

	// ---------------------------------------------------------------- respawn / login

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p) || !active(p))
			return;
		QiData q = QiData.of(p);
		q.setCoreAmount(Math.min(q.getCoreMax(), CultivationConfig.CORE_RESPAWN_QI.get())); // flat 200 (decided)
		pinHealth(p);
	}

	/** Saves from before this feature: a CORE player with a formed core but no Qi would die at once. */
	@SubscribeEvent(priority = EventPriority.LOW)
	public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && p.isAlive() && active(p) && QiData.of(p).getCoreAmount() <= 0)
			fillIfActive(p);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		HEALTH_BEFORE.remove(event.getEntity().getUUID());
	}
}
