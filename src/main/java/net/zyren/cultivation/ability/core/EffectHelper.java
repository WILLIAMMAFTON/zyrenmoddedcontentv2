package net.zyren.cultivation.ability.core;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Safe application of extreme status effects and knockback.
 *
 * Levels are written the way the document writes them (Roman numerals as numbers):
 * "Strength III" = level 3, "Regeneration 255" = level 255. Levels are clamped to 1..255
 * (Minecraft's maximum), so values like 255 or XV can't overflow.
 *
 * Notes on vanilla behaviour at extreme levels:
 *  - Resistance V or higher already blocks all damage that Resistance can block.
 *  - Regeneration 255 heals every tick.
 */
public final class EffectHelper {
	public static final int MAX_LEVEL = 255;
	/** Hard cap on knockback speed (blocks/tick) to stay within what the server accepts. */
	private static final double MAX_KNOCKBACK_SPEED = 10.0;

	private EffectHelper() {
	}

	/** @param level document level (1 = I). 0 or less does nothing. */
	public static void apply(LivingEntity target, Holder<MobEffect> effect, int level, int durationTicks, boolean showParticles) {
		if (level <= 0 || durationTicks <= 0)
			return;
		int amplifier = Math.min(level, MAX_LEVEL) - 1;
		target.addEffect(new MobEffectInstance(effect, durationTicks, amplifier, !showParticles, showParticles, true));
	}

	/**
	 * For effects a passive keeps up by re-applying every second. Regeneration heals on a fixed
	 * rhythm of its REMAINING duration (every 50 ticks at level I), so resetting it to 60 each
	 * second made it heal every second (~2.5x too fast). Here it's only topped up when nearly out,
	 * by +100 ticks — a multiple of the level I/II heal interval (50/25) — so the rhythm is kept.
	 */
	public static void applyPassive(LivingEntity target, Holder<MobEffect> effect, int level) {
		if (level <= 0)
			return;
		int amplifier = Math.min(level, MAX_LEVEL) - 1;
		MobEffectInstance cur = target.getEffect(effect);
		if (cur != null && cur.getAmplifier() == amplifier) {
			if (cur.getDuration() > 30)
				return;
			target.addEffect(new MobEffectInstance(effect, cur.getDuration() + 100, amplifier, true, false, true));
			return;
		}
		target.addEffect(new MobEffectInstance(effect, 100, amplifier, true, false, true));
	}

	/** Light level (0-15) at the entity's position, combining sky light (time of day) and block light. */
	public static int lightAt(Entity entity) {
		return entity.level().getMaxLocalRawBrightness(entity.blockPosition());
	}

	/** Upward speed (blocks/tick) needed to rise about {@code blocks} high with vanilla gravity and drag. */
	public static double launchSpeedForHeight(double blocks) {
		double lo = 0, hi = 20;
		for (int i = 0; i < 40; i++) {
			double v = (lo + hi) / 2, y = 0, vy = v;
			while (vy > 0) {
				y += vy;
				vy = (vy - 0.08) * 0.98;
			}
			if (y < blocks)
				lo = v;
			else
				hi = v;
		}
		return hi;
	}

	/**
	 * Knockback "level N" like the Knockback enchantment: each level adds 0.5 knockback
	 * strength, pushed away from {@code from}.
	 */
	public static void knockback(LivingEntity target, Entity from, int level) {
		if (level <= 0)
			return;
		double strength = level * 0.5;
		Vec3 dir = target.position().subtract(from.position());
		double dx = -dir.x, dz = -dir.z; // vanilla knockback takes the vector pointing TOWARDS the source
		if (dx * dx + dz * dz < 1.0E-4) {
			Vec3 look = from.getLookAngle();
			dx = -look.x;
			dz = -look.z;
		}
		// Applied at the end of the server tick, not now: most callers run inside a damage event,
		// and vanilla's own hit knockback (applied right after the event) halves any velocity set
		// here — Knockback X ended up around half strength.
		PENDING_KNOCKBACK.add(new PendingKnockback(target, Math.min(strength, MAX_KNOCKBACK_SPEED), dx, dz));
	}

	private record PendingKnockback(LivingEntity target, double strength, double dx, double dz) {
	}

	private static final java.util.List<PendingKnockback> PENDING_KNOCKBACK = new java.util.ArrayList<>();

	/** Called once per server tick (ServerStateReset). */
	public static void flushKnockback() {
		if (PENDING_KNOCKBACK.isEmpty())
			return;
		java.util.List<PendingKnockback> batch = new java.util.ArrayList<>(PENDING_KNOCKBACK);
		PENDING_KNOCKBACK.clear();
		for (PendingKnockback k : batch) {
			if (!k.target().isAlive() || k.target().isRemoved())
				continue;
			k.target().knockback(k.strength(), k.dx(), k.dz());
			k.target().hurtMarked = true; // make sure players receive the velocity update
		}
	}

	public static void resetAll() {
		PENDING_KNOCKBACK.clear();
	}
}
