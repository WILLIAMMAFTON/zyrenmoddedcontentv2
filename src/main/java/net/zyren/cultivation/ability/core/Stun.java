package net.zyren.cultivation.ability.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stun / freeze / "lock input": the entity is held in place (horizontal movement cancelled,
 * snapped back if it moves) for the duration. Falling still works, so a stunned entity in the
 * air drops straight down.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class Stun {
	private record Hold(Vec3 pos, long endTick) {
	}

	private static final Map<UUID, Hold> HELD = new HashMap<>();

	private Stun() {
	}

	public static void apply(LivingEntity entity, int ticks) {
		if (ticks <= 0 || !(entity.level() instanceof ServerLevel level))
			return;
		long end = level.getGameTime() + ticks;
		HELD.merge(entity.getUUID(), new Hold(entity.position(), end), (a, b) -> a.endTick() >= b.endTick() ? a : new Hold(a.pos(), b.endTick()));
		EffectHelper.apply(entity, MobEffects.MOVEMENT_SLOWDOWN, 7, ticks, false); // visual + blocks sprint
	}

	public static boolean isStunned(LivingEntity entity) {
		Hold h = HELD.get(entity.getUUID());
		return h != null && entity.level().getGameTime() < h.endTick();
	}

	@SubscribeEvent
	public static void onTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof LivingEntity e) || e.level().isClientSide())
			return;
		Hold h = HELD.get(e.getUUID());
		if (h == null)
			return;
		if (!e.isAlive() || e.level().getGameTime() >= h.endTick()) {
			HELD.remove(e.getUUID());
			return;
		}
		Vec3 v = e.getDeltaMovement();
		e.setDeltaMovement(0, Math.min(v.y, 0), 0);
		double dx = e.getX() - h.pos().x, dz = e.getZ() - h.pos().z;
		if (dx * dx + dz * dz > 0.01) {
			if (e instanceof ServerPlayer p)
				p.teleportTo(h.pos().x, p.getY(), h.pos().z);
			else
				e.setPos(h.pos().x, e.getY(), h.pos().z);
		}
		e.hurtMarked = true;
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		HELD.clear();
	}
}
