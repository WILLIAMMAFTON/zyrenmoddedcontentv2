package net.zyren.cultivation.ability.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectType;

/**
 * Everything abilities do TO OTHER entities goes through here, so the shared rules apply:
 *  - Reinforce Body: Qi blast/attack/ability effects delivered to a reinforced player are
 *    nullified (unless the ability says it bypasses Reinforce Body).
 *  - Qi damage is tagged so the damage pipeline knows it isn't a plain melee hit.
 *
 * Also holds the standard Reinforce Body and Nullify operations.
 */
public final class QiEffects {
	private static final ThreadLocal<Boolean> DEALING_QI_DAMAGE = ThreadLocal.withInitial(() -> false);

	private QiEffects() {
	}

	// ---- Reinforce Body ----

	public static String reinforceId(AspectType aspect) {
		return "reinforce:" + aspect.id();
	}

	/** Standard Reinforce Body: 50% incoming reduction + incoming Qi effects nullified. */
	public static void applyReinforce(ServerPlayer player, AspectType aspect, int durationTicks) {
		long now = player.serverLevel().getGameTime();
		AbilityState.of(player).addBuff(new Buff(reinforceId(aspect), aspect, 1.0,
				AbilityConfig.REINFORCE_DAMAGE_REDUCTION.get(), true, now + durationTicks));
	}

	public static boolean isReinforced(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer p))
			return false;
		long now = p.serverLevel().getGameTime();
		return AbilityState.of(p).buffs().stream().anyMatch(b -> b.nullifiesIncomingQiEffects() && b.endTick() > now);
	}

	/** Can Qi effects (status effects, knockback...) from an ability reach this target? */
	public static boolean canAffect(LivingEntity target, boolean bypassReinforce) {
		return bypassReinforce || !isReinforced(target);
	}

	// ---- Effects on targets ----

	public static boolean applyEffect(LivingEntity target, Holder<MobEffect> effect, int level, int ticks, boolean bypassReinforce) {
		if (!canAffect(target, bypassReinforce))
			return false;
		EffectHelper.apply(target, effect, level, ticks, true);
		return true;
	}

	public static boolean knockback(LivingEntity target, Entity from, int level, boolean bypassReinforce) {
		if (!canAffect(target, bypassReinforce))
			return false;
		EffectHelper.knockback(target, from, level);
		return true;
	}

	// ---- Qi damage ----

	/**
	 * Deals Qi ability damage from the user (attributed to them, so "total damage output"
	 * multipliers apply). Ignores the target's hit cooldown so the ability always lands.
	 */
	public static boolean dealDamage(ServerPlayer user, LivingEntity target, float amount) {
		if (amount <= 0)
			return false;
		DEALING_QI_DAMAGE.set(true);
		try {
			target.invulnerableTime = 0;
			boolean hurt = target.hurt(user.damageSources().playerAttack(user), amount);
			if (hurt)
				net.zyren.cultivation.system.AspectMastery.onAbilityHit(user, net.zyren.cultivation.data.AspectData.of(user).getActive(), target);
			return hurt;
		} finally {
			DEALING_QI_DAMAGE.set(false);
		}
	}

	private static final ThreadLocal<Boolean> BYPASS_REINFORCE = ThreadLocal.withInitial(() -> false);

	/** Qi damage that ignores the target's Reinforce Body reduction (e.g. Echo of Protection). */
	public static boolean dealDamageBypassingReinforce(ServerPlayer user, LivingEntity target, float amount) {
		BYPASS_REINFORCE.set(true);
		try {
			return dealDamage(user, target, amount);
		} finally {
			BYPASS_REINFORCE.set(false);
		}
	}

	public static boolean isBypassingReinforce() {
		return BYPASS_REINFORCE.get();
	}

	/** True while {@link #dealDamage} is running (used by the damage pipeline). */
	public static boolean isDealingQiDamage() {
		return DEALING_QI_DAMAGE.get();
	}

	// ---- Nullify ----

	/**
	 * Nullify: disables the aspect's passive and abilities on the target for the duration and
	 * removes its active states (buffs, armed hit).
	 */
	public static void nullify(ServerPlayer target, AspectType aspect, int durationTicks) {
		long now = target.serverLevel().getGameTime();
		AbilityState.of(target).nullify(aspect, now + Math.max(0, durationTicks));
		AspectCleanup.clearReinforceExtras(target, aspect); // what that aspect's Reinforce attached goes too
		notifyNullified(target, "Your " + aspect.displayName() + " aspect has been nullified", durationTicks);
	}

	/** Nullifies every aspect at once (Qi fully sealed), with a single notification. */
	public static void nullifyAll(ServerPlayer target, int durationTicks) {
		long now = target.serverLevel().getGameTime();
		AbilityState state = AbilityState.of(target);
		for (AspectType a : AspectType.values()) {
			state.nullify(a, now + Math.max(0, durationTicks));
			AspectCleanup.clearReinforceExtras(target, a);
		}
		notifyNullified(target, "Your Qi has been sealed: all aspects nullified", durationTicks);
	}

	/** Chat warning + sound so the player knows; the HUD shows the countdown. */
	private static void notifyNullified(ServerPlayer target, String what, int durationTicks) {
		net.zyren.cultivation.system.CultivationAdvancements.onNullified(target);
		int seconds = (durationTicks + 19) / 20;
		target.displayClientMessage(Component.literal("\u26A0 " + what + " for " + seconds + "s!").withStyle(ChatFormatting.RED), false);
		target.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}
}
