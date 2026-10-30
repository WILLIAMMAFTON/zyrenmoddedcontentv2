package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.common.NeoForgeMod;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.SubAspectKit;
import net.zyren.cultivation.ability.core.Suffocation;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.config.AirConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.zyren.cultivation.ability.kits.AirKit.seconds;

/**
 * Air's sub-aspects (ASPECT GUIDE + user "use rec" 2026-10-29). Slots a sub-aspect doesn't list fall
 * back to the DEFAULT Air kit (Sky / Storm: the passive; Storm: Attack 2 (unfinished in the guide);
 * Gale: Attack 1 + 2). Every ability uses its parent AIR + the same slot, so cooldowns are shared.
 * "Can break core" follows the sheet (Sky PB2 and Storm PB1 yes; Gale Ability 1 no). Event side: AirSubEvents.
 */
public final class AirSubKits {
	static final AspectType A = AspectType.AIR;

	private AirSubKits() {
	}

	/** Re-arms an empowered ability that hit something that isn't a valid target ("until next valid hit"). */
	static void rearm(ServerPlayer p, Ability a) {
		AbilityState.of(p).arm(a, p.serverLevel().getGameTime(), AbilityConfig.EMPOWER_WINDOW_TICKS.get());
	}

	// ================================================================== SKY

	static final SubAspectType SKY = SubAspectType.AIR_SKY;
	static final DidUNeedOxygen OXYGEN = new DidUNeedOxygen();

	public static SubAspectKit sky() {
		return new SubAspectKit(SKY, null, new OnePunch(), new PressureWave(), new ImmaJustDoThis(), new UWillDie(), new ForgorRent(), OXYGEN)
				.onLeave(AirSubEvents::leaveSky);
	}

	/** Stays armed until an UNARMED hit. */
	static final class OnePunch extends Ability {
		OnePunch() {
			super(A, AbilitySlot.ATTACK_1, "ONE PUNCH", SKY);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.SKY_A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.SKY_A1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("ONE PUNCH ready (bare fist).");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (!attacker.getMainHandItem().isEmpty()) {
				rearm(attacker, this);
				return damage;
			}
			if (QiEffects.canAffect(target, false))
				AirKit.throwAway(attacker, target, AirConfig.SKY_A1_THROW.get(), 0, (hit, block) -> {
					if (FireWorld.isStoneType(block))
						Stun.apply(hit, seconds(AirConfig.SKY_A1_STUN_SECONDS.get()));
				});
			return (float) (damage + AirConfig.SKY_A1_EXTRA.get());
		}
	}

	static final class PressureWave extends Ability {
		PressureWave() {
			super(A, AbilitySlot.HEAVY, "pressure wave", SKY);
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.SKY_A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.SKY_A2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			double lift = EffectHelper.launchSpeedForHeight(AirConfig.SKY_A2_UP.get());
			int n = 0;
			for (ServerPlayer t : AirKit.unalliedWithin(p, AirConfig.SKY_A2_RADIUS.get())) {
				QiEffects.dealDamage(p, t, AirConfig.SKY_A2_DAMAGE.get().floatValue());
				if (QiEffects.canAffect(t, false))
					AirKit.throwAway(p, t, AirConfig.SKY_A2_THROW.get(), lift, null);
				n++;
			}
			return ActionResult.ok("Pressure wave" + (n > 0 ? ": " + n + " sent flying." : "."));
		}
	}

	/** Stays armed until it hits an Air-active player. */
	static final class ImmaJustDoThis extends Ability {
		ImmaJustDoThis() {
			super(A, AbilitySlot.POWERBLAST_1, "imma just do this", SKY);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.SKY_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.SKY_PB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Imma just do this: hit an Air user.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (!(target instanceof ServerPlayer t) || !AirKit.isAirActive(t)) {
				rearm(attacker, this);
				return damage;
			}
			if (QiEffects.canAffect(t, false))
				QiEffects.nullify(t, A, seconds(AirConfig.SKY_PB1_NULLIFY_SECONDS.get()));
			return (float) (damage + AirConfig.SKY_PB1_DAMAGE.get());
		}
	}

	/** Next hit on a PLAYER within the window: suffocation + Nausea. */
	static final class UWillDie extends Ability {
		private final Map<UUID, Long> windowEnd = new HashMap<>();

		UWillDie() {
			super(A, AbilitySlot.POWERBLAST_2, "u will die", SKY);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public boolean canBreakCore() {
			return true; // sheet: true
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.SKY_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.SKY_PB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			long now = p.serverLevel().getGameTime();
			int window = seconds(AirConfig.SKY_PB2_WINDOW_SECONDS.get());
			windowEnd.put(p.getUUID(), now + window);
			AbilityState.of(p).arm(this, now, window);
			return ActionResult.ok("U will die: hit a player within " + AirConfig.SKY_PB2_WINDOW_SECONDS.get() + " s.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			long now = attacker.serverLevel().getGameTime();
			if (!(target instanceof ServerPlayer t)) {
				long left = windowEnd.getOrDefault(attacker.getUUID(), now) - now;
				if (left > 0)
					AbilityState.of(attacker).arm(this, now, (int) left);
				return damage;
			}
			windowEnd.remove(attacker.getUUID());
			if (QiEffects.canAffect(t, false)) {
				Suffocation.apply(t, seconds(AirConfig.SKY_PB2_SUFFOCATE_SECONDS.get()));
				EffectHelper.apply(t, MobEffects.CONFUSION, 1, seconds(AirConfig.SKY_PB2_NAUSEA_SECONDS.get()), true);
			}
			return damage;
		}
	}

	/** Pick screen (AirSubEvents); the chosen player suffocates. */
	static final class ForgorRent extends Ability {
		ForgorRent() {
			super(A, AbilitySlot.ABILITY_1, "sorry mate ur forgor rent", SKY);
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.SKY_AB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.SKY_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return AirKit.unalliedWithin(p, AirConfig.SKY_AB1_RADIUS.get()).isEmpty()
					? ActionResult.fail("No one within " + AirConfig.SKY_AB1_RADIUS.get() + " blocks.")
					: ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int n = AirSubEvents.startPick(p);
			return ActionResult.ok("Pick who forgot their rent (" + n + " option" + (n == 1 ? "" : "s") + ").");
		}
	}

	/** Aura (AirSubEvents). */
	static final class DidUNeedOxygen extends Ability {
		DidUNeedOxygen() {
			super(A, AbilitySlot.ABILITY_2, "did u need oxygen?", SKY);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(AirConfig.SKY_AB2_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.SKY_AB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.SKY_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Did u need oxygen?");
		}

		@Override
		public void onStop(ServerPlayer p) {
			AirSubEvents.stopOxygen(p);
		}
	}

	// ================================================================== STORM

	static final SubAspectType STORM = SubAspectType.AIR_STORM;
	static final StormyAnime STORMY = new StormyAnime();
	static final StormFront STORM_FRONT = new StormFront();

	public static SubAspectKit storm() {
		// Attack 2 'windy bs' is unfinished in the guide: the default Air Attack 2 is used until it's written.
		return new SubAspectKit(STORM, null, STORMY, new Cat5(), new ElectricShock(), STORM_FRONT, new MoreAnime())
				.onLeave(AirSubEvents::leaveStorm);
	}

	static final class StormyAnime extends Ability {
		static final String BUFF = "air_storm_charge";

		StormyAnime() {
			super(A, AbilitySlot.ATTACK_1, "just a stormy anime move", STORM);
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.STORM_A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.STORM_A1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return AirSubEvents.stormCharges(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			LightningEvents.strikeVisual(p.serverLevel(), p.position());
			AirSubEvents.startStormCharge(p, AirConfig.STORM_A1_HITS.get());
			return ActionResult.ok("The storm answers: your next " + AirConfig.STORM_A1_HITS.get() + " hits hit twice as hard.");
		}
	}

	static final class Cat5 extends Ability {
		Cat5() {
			super(A, AbilitySlot.POWERBLAST_1, "Cat 5 storm", STORM);
		}

		@Override
		public boolean canBreakCore() {
			return true; // sheet: true
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.STORM_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.STORM_PB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int r = AirConfig.STORM_PB1_RADIUS.get(), charged = 0, freeCasts = 0;
			for (ServerPlayer o : p.serverLevel().players()) {
				if (o == p || !Targeting.isValid(p, o) || !Targeting.isAllied(p, o) || o.distanceTo(p) > r || !o.level().canSeeSky(o.blockPosition().above()))
					continue;
				LightningEvents.strikeVisual(o.serverLevel(), o.position());
				QiData q = QiData.of(o);
				if (q.getCoreLevel() < 1)
					continue;
				if (q.getCoreAmount() >= q.getCoreMax()) {
					if (AirSubEvents.freeCastHighest(o, AirConfig.STORM_PB1_FREE_MAX_COST.get()))
						freeCasts++;
				} else {
					QiCoreStorage.addQi(o, AirConfig.STORM_PB1_QI.get());
					o.displayClientMessage(net.minecraft.network.chat.Component.literal(p.getGameProfile().getName() + "'s storm charges your Qi."), true);
					charged++;
				}
			}
			return ActionResult.ok("Cat 5 storm: " + charged + " charged, " + freeCasts + " free cast" + (freeCasts == 1 ? "" : "s") + ".");
		}
	}

	static final class ElectricShock extends Ability {
		ElectricShock() {
			super(A, AbilitySlot.POWERBLAST_2, "a electric shock", STORM);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.STORM_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.STORM_PB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("Electric shock ready.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			AirSubEvents.queueStrike(attacker, target); // after this hit resolves
			return (float) (damage + AirConfig.STORM_PB2_EXTRA.get());
		}
	}

	/** Aura (AirSubEvents): allied Electric users inside pay less Qi. */
	static final class StormFront extends Ability {
		StormFront() {
			super(A, AbilitySlot.ABILITY_1, "Storm front", STORM);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public int sustainTicks() {
			return seconds(AirConfig.STORM_AB1_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.STORM_AB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.STORM_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			var level = p.serverLevel();
			if (level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling())
				level.setWeatherParameters(0, sustainTicks(), true, true);
			int self = seconds(AirConfig.STORM_AB1_SELF_SECONDS.get());
			EffectHelper.apply(p, MobEffects.REGENERATION, AirConfig.STORM_AB1_REGEN.get(), self, true);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, AirConfig.STORM_AB1_SPEED.get(), self, true);
			return ActionResult.ok("Storm front.");
		}
	}

	/** Stays armed until it hits a player: their Qi halved + stun; you heal. */
	static final class MoreAnime extends Ability {
		MoreAnime() {
			super(A, AbilitySlot.ABILITY_2, "more anime bullshit", STORM);
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.STORM_AB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.STORM_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			return ActionResult.ok("More anime bullshit: hit a player.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (!(target instanceof ServerPlayer t)) {
				rearm(attacker, this);
				return damage;
			}
			if (QiEffects.canAffect(t, false)) {
				QiData q = QiData.of(t);
				q.setCoreAmount(q.getCoreAmount() / 2);
				Stun.apply(t, seconds(AirConfig.STORM_AB2_STUN_SECONDS.get()));
			}
			attacker.heal(AirConfig.STORM_AB2_HEAL.get().floatValue());
			return damage;
		}
	}

	// ================================================================== GALE

	static final SubAspectType GALE = SubAspectType.AIR_GALE;
	static final GalePassive GALE_PASSIVE = new GalePassive();
	static final BomberAura BOMBER = new BomberAura();

	public static SubAspectKit gale() {
		return new SubAspectKit(GALE, GALE_PASSIVE, new DidISayFly(), new TrafficController(), BOMBER, new ICanMoveU()) // Attack 1 / 2 -> default Air
				.onLeave(AirSubEvents::leaveGale);
	}

	static boolean galeOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == GALE_PASSIVE;
	}

	/** Elytra + endless rockets (AirSubEvents). Off while blocked (traffic controller / ripped elytra). */
	static final class GalePassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return !AirSubEvents.galeBlocked(p);
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(AirConfig.GALE_PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			AirSubEvents.ensureGaleGear(p);
		}
	}

	static final class DidISayFly extends Ability {
		DidISayFly() {
			super(A, AbilitySlot.POWERBLAST_1, "did i say u could fly?", GALE);
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.GALE_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.GALE_PB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int n = 0;
			for (ServerPlayer t : AirKit.unalliedWithin(p, AirConfig.GALE_PB1_RADIUS.get()))
				if (t.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.ELYTRA) && QiEffects.canAffect(t, false)) {
					AirSubEvents.ripElytra(t);
					n++;
				}
			p.heal(AirConfig.GALE_PB1_HEAL.get().floatValue());
			return ActionResult.ok("Did I say u could fly? " + n + " grounded.");
		}
	}

	static final class TrafficController extends Ability {
		TrafficController() {
			super(A, AbilitySlot.POWERBLAST_2, "traffic controlller is in house", GALE);
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.GALE_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.GALE_PB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int n = 0;
			int ticks = seconds(AirConfig.GALE_PB2_SECONDS.get());
			for (ServerPlayer t : AirKit.unalliedWithin(p, AirConfig.GALE_PB2_RADIUS.get()))
				if (KitResolver.formInUse(t) == GALE && QiEffects.canAffect(t, false)) {
					AirSubEvents.blockGale(t, ticks);
					t.displayClientMessage(net.minecraft.network.chat.Component.literal("Traffic control grounded your Gale passive."), false);
					n++;
				}
			return ActionResult.ok("Traffic controller is in the house: " + n + " Gale user" + (n == 1 ? "" : "s") + " grounded.");
		}
	}

	static final class BomberAura extends Ability {
		BomberAura() {
			super(A, AbilitySlot.ABILITY_1, "bomber aura", GALE);
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public boolean canBreakCore() {
			return false; // sheet: false
		}

		@Override
		public int sustainTicks() {
			return seconds(AirConfig.GALE_AB1_SECONDS.get());
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.GALE_AB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.GALE_AB1_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			AirSubEvents.giveBomberBow(p);
			return ActionResult.ok("Bomber aura: your arrows explode.");
		}

		@Override
		public void onStop(ServerPlayer p) {
			AirSubEvents.removeTemp(p, AirSubEvents.TEMP_BOMBER);
		}
	}

	static final class ICanMoveU extends Ability {
		static final String FLIGHT = "air_gale_flight";

		ICanMoveU() {
			super(A, AbilitySlot.ABILITY_2, "i can move u?", GALE);
		}

		@Override
		public double baseCost(Player p) {
			return AirConfig.GALE_AB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return seconds(AirConfig.GALE_AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int n = 0;
			double h = AirConfig.GALE_AB2_HEIGHT.get();
			for (ServerPlayer t : AirKit.unalliedWithin(p, AirConfig.GALE_AB2_RADIUS.get())) {
				boolean flying = t.isFallFlying() || t.getAbilities().flying || t.getY() >= h;
				if (!flying || !QiEffects.canAffect(t, false))
					continue;
				if (t.getAbilities().flying) {
					t.getAbilities().flying = false;
					t.onUpdateAbilities();
				}
				Motion.slam(t, AirConfig.GALE_AB2_SLAM_SPEED.get(), 200);
				n++;
			}
			int self = seconds(AirConfig.GALE_AB2_SELF_SECONDS.get());
			long now = p.serverLevel().getGameTime();
			AbilityState.of(p).setPassiveCostCut(1.0, now + self);
			TimedAttributes.apply(p, NeoForgeMod.CREATIVE_FLIGHT, FLIGHT, 1, AttributeModifier.Operation.ADD_VALUE, self);
			AbilityState.of(p).addBuff(new Buff("air_gale_move", A, 1.0, 0, false, now + self));
			return ActionResult.ok("I can move u? " + n + " brought down.");
		}
	}
}
