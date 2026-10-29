package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.config.WaterConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * WATER — DEFAULT kit (ASPECT GUIDE, user decisions 2026-10-29). Replaces the old Water kit; only the
 * old Reinforce Body is kept (the guide doesn't define one yet). Values: water-server.toml.
 *
 *   Passive      1 Qi/s while in water (not rain): Regeneration I, Conduit Power, Water Breathing,
 *                Strength I. After leaving water it can't turn on again for 10 s.
 *   Attack 1     water fists: 15 Qi, 108 s. Next 10 valid hits: +4 unarmed / +2.5 with a weapon.
 *   Heavy        yeet: 30 Qi, 140 s. Next hit throws the target 10 blocks back.
 *   Reinforce    (old Water Reinforce Body: standard + fall damage immunity)
 *   Powerblast 1 tidal blast: 25 Qi, 380 s. Unallied players within 10: 5 damage, launched back 5;
 *                water sources around you (radius 5, drain after 30 s).
 *   Powerblast 2 water seeker: 35 Qi, 30 s. Teleport to water / a Water ally / the ocean.
 *   Ability 1    my area now: 100 Qi, 180 s, 480 s cooldown, can break core. Aura (radius 30).
 *   Ability 2    OCEANIC DOOM: 200 Qi, 180 s, 480 s cooldown, can break core.
 */
public final class WaterKit {
	static final AspectType W = AspectType.WATER;

	private WaterKit() {
	}

	static final Passive PASSIVE = new Passive();
	static final WaterFists FISTS = new WaterFists();
	static final MyAreaNow MY_AREA = new MyAreaNow();
	static final OceanicDoom OCEANIC_DOOM = new OceanicDoom();
	static final WaterSeeker SEEKER = new WaterSeeker();

	public static AspectKit create() {
		return new AspectKit(W, PASSIVE,
				FISTS, new Yeet(), new Reinforce(), new TidalBlast(), SEEKER, MY_AREA, OCEANIC_DOOM);
	}

	// ------------------------------------------------------------------ helpers (shared with the sub-aspects)

	static int seconds(int s) {
		return s * 20;
	}

	/** The world's night (false in the Nether / End: no day cycle). */
	static boolean isNight(Level level) {
		return level.isNight();
	}

	/** Standing in a water SOURCE block (feet or head). */
	static boolean inWaterSource(LivingEntity e) {
		var lvl = e.level();
		var feet = lvl.getFluidState(e.blockPosition());
		var head = lvl.getFluidState(net.minecraft.core.BlockPos.containing(e.getEyePosition()));
		return feet.is(FluidTags.WATER) && feet.isSource() || head.is(FluidTags.WATER) && head.isSource();
	}

	/** Valid unallied players within {@code radius} of the user. */
	static List<ServerPlayer> unalliedWithin(ServerPlayer user, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer o : user.serverLevel().players())
			if (Targeting.isValid(user, o) && !Targeting.isAllied(user, o) && o.distanceTo(user) <= radius)
				out.add(o);
		return out;
	}

	/** Nearest valid unallied player within range that the user can see, or null. */
	static ServerPlayer nearestVisibleUnallied(ServerPlayer user, double range) {
		ServerPlayer best = null;
		for (ServerPlayer o : unalliedWithin(user, range))
			if (user.hasLineOfSight(o) && (best == null || o.distanceToSqr(user) < best.distanceToSqr(user)))
				best = o;
		return best;
	}

	/** Rain for this long (only where weather exists: not the Nether / End). */
	static void rain(ServerLevel level, int ticks) {
		if (level.dimensionType().hasSkyLight() && !level.dimensionType().ultraWarm() && !level.dimensionType().hasCeiling())
			level.setWeatherParameters(0, ticks, true, false);
	}

	public static boolean isDefaultPassive(AspectPassive p) {
		return p == PASSIVE;
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return WaterEvents.passiveCondition(player);
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return perSeconds(WaterConfig.PASSIVE_QI_PER_SECOND.get(), 1);
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.applyPassive(player, MobEffects.REGENERATION, WaterConfig.PASSIVE_REGEN.get());
			EffectHelper.apply(player, MobEffects.CONDUIT_POWER, 1, 50, false);
			EffectHelper.apply(player, MobEffects.WATER_BREATHING, 1, 50, false);
			EffectHelper.apply(player, MobEffects.DAMAGE_BOOST, WaterConfig.PASSIVE_STRENGTH.get(), 50, false);
		}
	}

	// ------------------------------------------------------------------ Attack 1

	/** Charges live in WaterEvents, tied to the buff "water_fists" (a form change / nullify ends them). */
	static final class WaterFists extends Ability {
		WaterFists() {
			super(W, AbilitySlot.ATTACK_1, "water fists");
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.A1_COOLDOWN.get());
		}

		@Override
		public boolean canBreakCore() {
			return false;
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return WaterEvents.fistCharges(player) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int hits = WaterConfig.A1_HITS.get();
			WaterEvents.startFists(player, hits);
			return ActionResult.ok("Water fists: your next " + hits + " hits strike harder.");
		}
	}

	// ------------------------------------------------------------------ Heavy

	static final class Yeet extends Ability {
		Yeet() {
			super(W, AbilitySlot.HEAVY, "yeet");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.A2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("Yeet ready: your next hit throws them.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (QiEffects.canAffect(target, false))
				WaterEvents.push(target, attacker.position(), WaterConfig.A2_BLOCKS.get());
			return damage;
		}
	}

	// ------------------------------------------------------------------ Reinforce (old one, kept)

	static final class Reinforce extends Ability {
		Reinforce() {
			super(W, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WATER_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WATER_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.WATER_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			QiEffects.applyReinforce(player, W, AbilityConfig.WATER_REINFORCE_DURATION.get());
			return ActionResult.ok("Body reinforced. No fall damage.");
		}
	}

	// ------------------------------------------------------------------ Powerblast 1

	static final class TidalBlast extends Ability {
		TidalBlast() {
			super(W, AbilitySlot.POWERBLAST_1, "tidal blast");
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.PB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int hit = 0;
			for (ServerPlayer t : unalliedWithin(player, WaterConfig.PB1_RADIUS.get())) {
				QiEffects.dealDamage(player, t, WaterConfig.PB1_DAMAGE.get().floatValue());
				if (QiEffects.canAffect(t, false))
					WaterEvents.push(t, player.position(), WaterConfig.PB1_PUSH_BLOCKS.get());
				hit++;
			}
			int placed = player.serverLevel().dimensionType().ultraWarm() ? 0
					: WaterEvents.flood(player, WaterConfig.PB1_WATER_RADIUS.get(), seconds(WaterConfig.PB1_WATER_SECONDS.get()));
			player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.PLAYERS, 2f, 0.8f);
			return ActionResult.ok("Tidal blast: " + hit + " hit, " + placed + " water placed.");
		}
	}

	// ------------------------------------------------------------------ Powerblast 2

	static final class WaterSeeker extends Ability {
		WaterSeeker() {
			super(W, AbilitySlot.POWERBLAST_2, "water seeker");
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.PB2_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return WaterEvents.planSeek(player);
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return WaterEvents.executeSeek(player);
		}
	}

	// ------------------------------------------------------------------ Ability 1

	/** Aura ticked by WaterEvents while running. */
	static final class MyAreaNow extends Ability {
		MyAreaNow() {
			super(W, AbilitySlot.ABILITY_1, "my area now");
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(WaterConfig.AB1_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.AB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			rain(player.serverLevel(), sustainTicks());
			return ActionResult.ok("My area now. The rain answers you.");
		}
	}

	// ------------------------------------------------------------------ Ability 2

	static final class OceanicDoom extends Ability {
		static final String FLIGHT = "water_oceanic_flight";

		OceanicDoom() {
			super(W, AbilitySlot.ABILITY_2, "OCEANIC DOOM domain expansion");
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(WaterConfig.AB2_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return WaterConfig.AB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(WaterConfig.AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = sustainTicks();
			ServerLevel level = player.serverLevel();
			int placed = level.dimensionType().ultraWarm() ? 0 : WaterEvents.flood(player, WaterConfig.AB2_FLOOD_RADIUS.get(), ticks);
			rain(level, ticks);
			net.zyren.cultivation.ability.core.TimedAttributes.apply(player, net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT, FLIGHT, 1,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE, ticks);
			EffectHelper.apply(player, MobEffects.DAMAGE_BOOST, WaterConfig.AB2_STRENGTH.get(), ticks, true);
			AbilityState.of(player).addBuff(new Buff("water_oceanic_doom", W, 1.0, 0, false, level.getGameTime() + ticks));
			level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE, net.minecraft.sounds.SoundSource.PLAYERS, 3f, 0.6f);
			return ActionResult.ok("OCEANIC DOOM. " + placed + " blocks flooded.");
		}

		@Override
		public void onStop(ServerPlayer player) {
			WaterEvents.endOceanicDoom(player);
		}
	}
}
