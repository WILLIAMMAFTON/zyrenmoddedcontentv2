package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.config.CorruptionConfig;
import net.zyren.cultivation.data.QiData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Meditation: started from the cultivation menu or /qi meditate; lasts 3 min; the player must
 * sit still (moving stops it, closing the menu doesn't). While meditating and for 3 min after it
 * ends, corruption from corrupted areas is multiplied (x3). Outside corrupted areas it does
 * nothing yet (decided).
 */
public final class Meditation {
	private record Session(Vec3 anchor, long endTick) {
	}

	private static final Map<UUID, Session> ACTIVE = new HashMap<>();
	private static final Map<UUID, Long> AFTER_UNTIL = new HashMap<>();

	private Meditation() {
	}

	public static ActionResult start(ServerPlayer p) {
		if (!p.isAlive() || p.isSpectator())
			return ActionResult.fail("You can't meditate right now.");
		if (QiData.of(p).getCoreLevel() < 1)
			return ActionResult.fail("You need a formed Qi Core to meditate.");
		if (ACTIVE.containsKey(p.getUUID()))
			return ActionResult.fail("You are already meditating.");
		long now = p.serverLevel().getGameTime();
		ACTIVE.put(p.getUUID(), new Session(p.position(), now + CorruptionConfig.MEDITATION_TICKS.get()));
		return ActionResult.ok("You begin to meditate. Stay still.");
	}

	public static boolean isMeditating(ServerPlayer p) {
		return ACTIVE.containsKey(p.getUUID());
	}

	/** Meditation multiplier on area corruption right now (1 = none). */
	public static double multiplier(ServerPlayer p) {
		long now = p.serverLevel().getGameTime();
		Long after = AFTER_UNTIL.get(p.getUUID());
		return isMeditating(p) || after != null && now < after ? CorruptionConfig.MEDITATION_MULTIPLIER.get() : 1.0;
	}

	public static int ticksLeft(ServerPlayer p) {
		Session s = ACTIVE.get(p.getUUID());
		return s == null ? 0 : (int) Math.max(0, s.endTick() - p.serverLevel().getGameTime());
	}

	public static int afterTicksLeft(ServerPlayer p) {
		Long after = AFTER_UNTIL.get(p.getUUID());
		return after == null ? 0 : (int) Math.max(0, after - p.serverLevel().getGameTime());
	}

	/** Called every tick: stops when the player moves or the time is up. */
	public static void tick(ServerPlayer p) {
		Session s = ACTIVE.get(p.getUUID());
		if (s == null)
			return;
		long now = p.serverLevel().getGameTime();
		if (p.position().distanceTo(s.anchor()) > CorruptionConfig.MEDITATION_MOVE_TOLERANCE.get()) {
			stop(p, "You moved: meditation interrupted.");
		} else if (now >= s.endTick()) {
			stop(p, "Meditation complete.");
		}
	}

	public static void stop(ServerPlayer p, String message) {
		if (ACTIVE.remove(p.getUUID()) == null)
			return;
		AFTER_UNTIL.put(p.getUUID(), p.serverLevel().getGameTime() + CorruptionConfig.MEDITATION_AFTER_TICKS.get());
		p.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE), true);
	}

	public static void forget(UUID id) {
		ACTIVE.remove(id);
		AFTER_UNTIL.remove(id);
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		ACTIVE.clear();
		AFTER_UNTIL.clear();
	}
}
