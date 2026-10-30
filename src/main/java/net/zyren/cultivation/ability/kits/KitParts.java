package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Building blocks for config-driven abilities (used by the Electric kits): cost and cooldown come
 * straight from config values, timed abilities get once-a-second / twice-a-second callbacks while
 * they run and are still the CURRENT ability (a form toggle / aspect switch / nullify stops them).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class KitParts {
	private KitParts() {
	}

	/** Ability with config cost (Qi) and config cooldown (seconds). */
	abstract static class Cfg extends Ability {
		private final Supplier<? extends Number> cost, cooldownSeconds;
		private final boolean breaksCore;

		Cfg(AspectType a, AbilitySlot slot, String name, SubAspectType sub, Supplier<? extends Number> cost, Supplier<? extends Number> cooldownSeconds,
				boolean breaksCore) {
			super(a, slot, name, sub);
			this.cost = cost;
			this.cooldownSeconds = cooldownSeconds;
			this.breaksCore = breaksCore;
		}

		@Override
		public double baseCost(Player p) {
			return cost.get().doubleValue();
		}

		@Override
		public int cooldownTicks() {
			return cooldownSeconds.get().intValue() * 20;
		}

		@Override
		public boolean canBreakCore() {
			return breaksCore;
		}
	}

	/** Empowers the next melee hit. */
	abstract static class Empower extends Cfg {
		Empower(AspectType a, AbilitySlot slot, String name, SubAspectType sub, Supplier<? extends Number> cost, Supplier<? extends Number> cd, boolean breaksCore) {
			super(a, slot, name, sub, cost, cd, breaksCore);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok(name() + " ready.");
		}

		@Override
		public abstract float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage);
	}

	/** Runs for a config duration (seconds). */
	abstract static class Timed extends Cfg {
		static final List<Timed> ALL = new ArrayList<>();
		private final Supplier<? extends Number> seconds;

		Timed(AspectType a, AbilitySlot slot, String name, SubAspectType sub, Supplier<? extends Number> cost, Supplier<? extends Number> cd,
				Supplier<? extends Number> seconds, boolean breaksCore) {
			super(a, slot, name, sub, cost, cd, breaksCore);
			this.seconds = seconds;
			ALL.add(this);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds.get().intValue() * 20;
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			start(p);
			second(p);
			return ActionResult.ok(name() + "!");
		}

		@Override
		public void onStop(ServerPlayer p) {
			end(p);
		}

		/** Once when cast. */
		void start(ServerPlayer p) {
		}

		/** Once a second while running (effects use short durations so they fade when it stops). */
		void second(ServerPlayer p) {
		}

		/** Every 10 ticks while running. */
		void half(ServerPlayer p) {
		}

		/** Once when it ends for any reason. */
		void end(ServerPlayer p) {
		}

		/** True if this ability turns the aspect's passive off while it runs. */
		boolean disablesPassive() {
			return false;
		}
	}

	static boolean running(Player p, Ability a) {
		return AbilityState.of(p).isSustained(a) && KitResolver.isCurrent(p, a);
	}

	/** Any running timed ability of this player that switches the passive off. */
	static boolean passiveDisabled(Player p) {
		for (Timed t : Timed.ALL)
			if (t.disablesPassive() && running(p, t))
				return true;
		return false;
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		long phase = p.serverLevel().getGameTime() + p.getId();
		if (phase % 10 != 0 || AbilityState.of(p).activeSlotsMask() == 0)
			return;
		for (Timed t : Timed.ALL) {
			if (!running(p, t))
				continue;
			t.half(p);
			if (phase % 20 == 0)
				t.second(p);
		}
	}
}
