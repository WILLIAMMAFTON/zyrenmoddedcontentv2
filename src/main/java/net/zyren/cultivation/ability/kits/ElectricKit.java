package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.ElectricConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ELECTRIC — DEFAULT kit (ASPECT GUIDE, user "use rec" 2026-10-29). Replaces the old Lightning kit;
 * only its Reinforce Body is kept (the guide has none yet). Values: electric-server.toml.
 * Lightning that "strikes the user" is visual only.
 *
 *   Passive      5 Qi/min. Thunderstorm: Speed I, Strength I, Regen II. Immune to lightning damage.
 *   Attack 1     lightning enhanced fists: 40 Qi, 60 s. Self-strike; next 5 hits x2.
 *   Heavy        blade of thunder: 60 Qi, 120 s. Next 5 sword hits call lightning on the target.
 *   Powerblast 1 electric blast: 80 Qi, 240 s. Looked-at (else nearest) player within 20 under open sky: lightning + Blindness 10 s.
 *   Powerblast 2 enhanced powers: 100 Qi, 380 s, 120 s. Self-strike; passive off; Strength II, Speed IV, Resistance III, Haste IV.
 *   Ability 1    nuclear blast: 180 Qi, 500 s. 4 nearest unallied within 60: lightning, 10 damage, 5x5x5 crater.
 *   Ability 2    weaponized local monson: 250 Qi, 1800 s, 120 s. Thunderstorm; every sword hit calls lightning.
 */
public final class ElectricKit {
	static final AspectType E = AspectType.ELECTRIC;

	private ElectricKit() {
	}

	static final Passive PASSIVE = new Passive();
	static final Fists FISTS = new Fists();
	static final Blade BLADE = new Blade();
	static final ElectricBlast BLAST = new ElectricBlast();
	static final EnhancedPowers POWERS = new EnhancedPowers();
	static final Monsoon MONSOON = new Monsoon();

	public static AspectKit create() {
		return new AspectKit(E, PASSIVE, FISTS, BLADE, new LightningKit.Reinforce(), BLAST, POWERS, new NuclearBlast(), MONSOON);
	}

	// ------------------------------------------------------------------ helpers (shared with the sub-aspects)

	static int seconds(int s) {
		return s * 20;
	}

	static boolean inStorm(Player p) {
		return p.level().isThundering();
	}

	static void selfStrike(ServerPlayer p) {
		LightningEvents.strikeVisual(p.serverLevel(), p.position());
	}

	static double lightning() {
		return ElectricConfig.LIGHTNING_DAMAGE.get();
	}

	/** Thunderstorm for this long, where weather exists. */
	static void thunder(ServerLevel level, int ticks) {
		if (level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling())
			level.setWeatherParameters(0, ticks, true, true);
	}

	static List<ServerPlayer> unalliedWithin(ServerPlayer user, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer o : user.serverLevel().players())
			if (Targeting.isValid(user, o) && !Targeting.isAllied(user, o) && o.distanceTo(user) <= radius)
				out.add(o);
		out.sort(Comparator.comparingDouble(o -> o.distanceToSqr(user)));
		return out;
	}

	static boolean openSky(ServerPlayer t) {
		return t.level().canSeeSky(t.blockPosition().above());
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return !KitParts.passiveDisabled(p);
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(ElectricConfig.PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			if (!inStorm(p))
				return;
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, ElectricConfig.PASSIVE_SPEED.get(), 50, false);
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, ElectricConfig.PASSIVE_STRENGTH.get(), 50, false);
			EffectHelper.applyPassive(p, MobEffects.REGENERATION, ElectricConfig.PASSIVE_REGEN.get());
		}

		@Override
		public double incomingReduction(ServerPlayer p, DamageSource source) {
			return source.is(DamageTypes.LIGHTNING_BOLT) ? 1.0 : 0.0;
		}
	}

	// ------------------------------------------------------------------ Attack 1 / Heavy (hit charges in ElectricEvents)

	static final class Fists extends KitParts.Cfg {
		Fists() {
			super(E, AbilitySlot.ATTACK_1, "lightning enhanced fists", null, ElectricConfig.A1_COST, ElectricConfig.A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return ElectricEvents.FISTS.left(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			selfStrike(p);
			ElectricEvents.FISTS.start(p, ElectricConfig.A1_HITS.get());
			return ActionResult.ok("Lightning fists: next " + ElectricConfig.A1_HITS.get() + " hits x2.");
		}
	}

	static final class Blade extends KitParts.Cfg {
		Blade() {
			super(E, AbilitySlot.HEAVY, "blade of thunder", null, ElectricConfig.A2_COST, ElectricConfig.A2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return ElectricEvents.BLADE.left(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ElectricEvents.BLADE.start(p, ElectricConfig.A2_HITS.get());
			return ActionResult.ok("Blade of thunder: next " + ElectricConfig.A2_HITS.get() + " sword hits call lightning.");
		}
	}

	// ------------------------------------------------------------------ Powerblast 1

	static final class ElectricBlast extends KitParts.Cfg {
		ElectricBlast() {
			super(E, AbilitySlot.POWERBLAST_1, "electric blast", null, ElectricConfig.PB1_COST, ElectricConfig.PB1_COOLDOWN, false);
		}

		static ServerPlayer target(ServerPlayer p) {
			int r = ElectricConfig.PB1_RANGE.get();
			ServerPlayer looked = Targeting.lookedAtPlayer(p);
			if (looked != null && !Targeting.isAllied(p, looked) && looked.distanceTo(p) <= r)
				return looked;
			List<ServerPlayer> near = unalliedWithin(p, r);
			return near.isEmpty() ? null : near.get(0);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			ServerPlayer t = target(p);
			if (t == null)
				return ActionResult.fail("No one within " + ElectricConfig.PB1_RANGE.get() + " blocks.");
			return openSky(t) ? ActionResult.ok("") : ActionResult.fail(t.getGameProfile().getName() + " is under cover.");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = target(p);
			if (t == null)
				return ActionResult.fail("Target lost.");
			LightningEvents.strike(p, t, lightning());
			QiEffects.applyEffect(t, MobEffects.BLINDNESS, 1, seconds(ElectricConfig.PB1_BLIND_SECONDS.get()), false);
			return ActionResult.ok("Electric blast hits " + t.getGameProfile().getName() + ".");
		}
	}

	// ------------------------------------------------------------------ Powerblast 2

	static final class EnhancedPowers extends KitParts.Timed {
		EnhancedPowers() {
			super(E, AbilitySlot.POWERBLAST_2, "enhanced powers", null, ElectricConfig.PB2_COST, ElectricConfig.PB2_COOLDOWN, ElectricConfig.PB2_SECONDS, false);
		}

		@Override
		boolean disablesPassive() {
			return true;
		}

		@Override
		void start(ServerPlayer p) {
			selfStrike(p);
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, ElectricConfig.PB2_STRENGTH.get(), 30, true);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, ElectricConfig.PB2_SPEED.get(), 30, true);
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, ElectricConfig.PB2_RESISTANCE.get(), 30, true);
			EffectHelper.apply(p, MobEffects.DIG_SPEED, ElectricConfig.PB2_HASTE.get(), 30, true); // "attack speed" = Haste (project convention)
		}
	}

	// ------------------------------------------------------------------ Ability 1

	static final class NuclearBlast extends KitParts.Cfg {
		NuclearBlast() {
			super(E, AbilitySlot.ABILITY_1, "nuclear blast", null, ElectricConfig.AB1_COST, ElectricConfig.AB1_COOLDOWN, true);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return unalliedWithin(p, ElectricConfig.AB1_RADIUS.get()).isEmpty()
					? ActionResult.fail("No one within " + ElectricConfig.AB1_RADIUS.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			List<ServerPlayer> targets = unalliedWithin(p, ElectricConfig.AB1_RADIUS.get());
			int n = Math.min(targets.size(), ElectricConfig.AB1_TARGETS.get());
			for (int i = 0; i < n; i++) {
				ServerPlayer t = targets.get(i);
				LightningEvents.strike(p, t, ElectricConfig.AB1_DAMAGE.get());
				ElectricEvents.crater(t.serverLevel(), t.blockPosition(), ElectricConfig.AB1_CRATER.get());
			}
			return ActionResult.ok("Nuclear blast: " + n + " struck.");
		}
	}

	// ------------------------------------------------------------------ Ability 2

	/** Sword hits call lightning while it runs (ElectricEvents). */
	static final class Monsoon extends KitParts.Timed {
		Monsoon() {
			super(E, AbilitySlot.ABILITY_2, "weaponized local monson", null, ElectricConfig.AB2_COST, ElectricConfig.AB2_COOLDOWN, ElectricConfig.AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			thunder(p.serverLevel(), sustainTicks());
		}
	}
}
