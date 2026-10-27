package net.zyren.cultivation.ability.kits;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.Thorns;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PROTECTIVE (source-of-truth document + decisions 2026-10-06).
 *
 *   Passive        5 Qi/s: Resistance X, Regeneration I, max health 400; incoming damage -> Qi (1:1, before reductions).
 *   Attack 1       3 Qi, next hit: Knockback X.
 *   Heavy          5 Qi: heal the damage taken in the last 10 s; Thorns III 30 s.
 *   Reinforce      50 Qi, 2 min, standard. Full health: allies within 50 (not Reinforced) Regeneration X.
 *                  Otherwise: you alone Regeneration 255.
 *   Powerblast 1   U SAID WHAT!!??: 25 Qi. Removes the nearest Physical player's Reinforce, halves your Qi,
 *                  launches you at them; contact = 255 damage; if they survive, their Physical is nullified.
 *   Powerblast 2   60 Qi: enemies within 50 (not Reinforced) lose their Qi for 2.5 min (balance pass), and so do you. If an
 *                  enemy has Protective active, only you and the Protective enemies are nullified.
 *   Ability 1      HOOO RAAA: 50 Qi + 5 per ally in range, 3 min, 20 min cd: allies within 40 (not you)
 *                  pay 50% less passive upkeep.
 *   Ability 2      Echo of Protection: 100 Qi, 30 min cd: unallied players within 50 take 50 damage and
 *                  Slowness III (bypassing Reinforce) and are launched ~1000 blocks away.
 */
public final class ProtectiveKit {
	static final AspectType PR = AspectType.PROTECTIVE;
	static final HoooRaaa HOOO_RAAA = new HoooRaaa();

	private ProtectiveKit() {
	}

	public static AspectKit create() {
		return new AspectKit(PR, new Passive(),
				new Attack1(), new Heavy(), new Reinforce(), new USaidWhat(), new Powerblast2(), HOOO_RAAA, new Echo());
	}

	static List<Player> alliesNear(Player user, double r) {
		List<Player> out = new ArrayList<>();
		for (Player p : user.level().players())
			if (p != user && p.isAlive() && !p.isSpectator() && user.isAlliedTo(p) && p.distanceTo(user) <= r)
				out.add(p);
		return out;
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return perSeconds(AbilityConfig.PROT_PASSIVE_QI_PER_SECOND.get(), 1);
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.PROT_PASSIVE_RESISTANCE.get(), 50, false);
			EffectHelper.applyPassive(player, MobEffects.REGENERATION, AbilityConfig.PROT_PASSIVE_REGEN.get());
			AttributeInstance hp = player.getAttribute(Attributes.MAX_HEALTH);
			if (hp != null) {
				double extra = AbilityConfig.PROT_PASSIVE_MAX_HEALTH.get() - hp.getBaseValue();
				if (extra > 0)
					TimedAttributes.apply(player, Attributes.MAX_HEALTH, "protective_max_health", extra, AttributeModifier.Operation.ADD_VALUE, 50);
			}
		}
	}

	// ------------------------------------------------------------------ abilities

	static final class Attack1 extends Ability {
		Attack1() {
			super(PR, AbilitySlot.ATTACK_1, "Attack 1");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PROT_ATTACK1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PROT_ATTACK1_COOLDOWN.get();
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			QiEffects.knockback(target, attacker, AbilityConfig.PROT_ATTACK1_KNOCKBACK.get(), false);
			return damage;
		}
	}

	static final class Heavy extends Ability {
		Heavy() {
			super(PR, AbilitySlot.HEAVY, "Heavy");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PROT_HEAVY_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PROT_HEAVY_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			float heal = (float) (ProtectiveEvents.recentDamage(player, AbilityConfig.PROT_HEAVY_WINDOW_TICKS.get())
					* AbilityConfig.PROT_HEAVY_HEAL_FRACTION.get());
			if (heal > 0)
				player.heal(heal);
			Thorns.grant(player, "protective_heavy", AbilityConfig.PROT_HEAVY_THORNS.get(), AbilityConfig.PROT_HEAVY_THORNS_TICKS.get());
			return ActionResult.ok("Healed " + Math.round(heal) + " from damage taken. Thorns III.");
		}
	}

	static final class Reinforce extends Ability {
		Reinforce() {
			super(PR, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PROT_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PROT_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.PROT_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = AbilityConfig.PROT_REINFORCE_DURATION.get();
			boolean full = player.getHealth() >= player.getMaxHealth();
			QiEffects.applyReinforce(player, PR, ticks);
			if (!full) {
				EffectHelper.apply(player, MobEffects.REGENERATION, AbilityConfig.PROT_REINFORCE_SELF_REGEN.get(), ticks, true);
				return ActionResult.ok("Body reinforced. Regeneration 255.");
			}
			int n = 0;
			for (Player ally : alliesNear(player, AbilityConfig.PROT_REINFORCE_ALLY_RADIUS.get())) {
				if (QiEffects.isReinforced(ally))
					continue;
				EffectHelper.apply(ally, MobEffects.REGENERATION, AbilityConfig.PROT_REINFORCE_ALLY_REGEN.get(), ticks, true);
				n++;
			}
			return ActionResult.ok("Body reinforced. " + n + " all" + (n == 1 ? "y" : "ies") + " regenerating.");
		}
	}

	static final class USaidWhat extends Ability {
		USaidWhat() {
			super(PR, AbilitySlot.POWERBLAST_1, "U SAID WHAT!!??");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PROT_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PROT_PB1_COOLDOWN.get();
		}

		static ServerPlayer nearestPhysical(ServerPlayer user) {
			return user.serverLevel().players().stream()
					.filter(p -> Targeting.isValid(user, p) && AspectData.of(p).getActive() == AspectType.PHYSICAL)
					.min(Comparator.comparingDouble(p -> p.distanceToSqr(user)))
					.orElse(null);
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			if (ProtectiveEvents.isFlying(player))
				return ActionResult.fail("Already flying.");
			return nearestPhysical(player) == null ? ActionResult.fail("No Physical-aspect player to go after.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer target = nearestPhysical(player);
			if (target == null)
				return ActionResult.fail("Target lost.");
			// Nullify the target's Physical Reinforce Body (buff + its extra effects).
			AbilityState ts = AbilityState.of(target);
			if (ts.hasBuff(QiEffects.reinforceId(AspectType.PHYSICAL), target.serverLevel().getGameTime())) {
				ts.removeBuff(QiEffects.reinforceId(AspectType.PHYSICAL));
				target.removeEffect(MobEffects.REGENERATION);
				target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
				Thorns.revoke(target, "physical_reinforce");
			}
			// "Reduce user's Qi by 50%."
			QiData q = QiData.of(player);
			q.setCoreAmount(q.getCoreAmount() * (1 - AbilityConfig.PROT_PB1_USER_QI_LOSS.get()));
			ProtectiveEvents.startFlight(player, target);
			return ActionResult.ok("U SAID WHAT!!?? Flying at " + target.getName().getString() + ".");
		}
	}

	static final class Powerblast2 extends Ability {
		Powerblast2() {
			super(PR, AbilitySlot.POWERBLAST_2, "Powerblast 2");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PROT_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PROT_PB2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			double r = AbilityConfig.PROT_PB2_RADIUS.get();
			int ticks = AbilityConfig.PROT_PB2_TICKS.get();
			List<ServerPlayer> enemies = new ArrayList<>();
			List<ServerPlayer> protectiveEnemies = new ArrayList<>();
			for (ServerPlayer t : player.serverLevel().players()) {
				if (!Targeting.isValid(player, t) || Targeting.isAllied(player, t) || t.distanceTo(player) > r)
					continue;
				enemies.add(t);
				if (AspectData.of(t).getActive() == PR)
					protectiveEnemies.add(t);
			}
			List<ServerPlayer> targets = protectiveEnemies.isEmpty() ? enemies : protectiveEnemies;
			int n = 0;
			for (ServerPlayer t : targets) {
				if (QiEffects.isReinforced(t))
					continue;
				QiEffects.nullifyAll(t, ticks);
				n++;
			}
			QiEffects.nullifyAll(player, ticks);
			player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 200, r / 4, 1, r / 4, 0.5);
			return ActionResult.ok((protectiveEnemies.isEmpty() ? "" : "Protective standoff: ") + n + " enemy Qi sealed for " + fmtTicks(AbilityConfig.PROT_PB2_TICKS.get()) + ", and yours too.");
		}
	}

	static final class HoooRaaa extends Ability {
		HoooRaaa() {
			super(PR, AbilitySlot.ABILITY_1, "HOOO RAAA");
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		/** 50 Qi + 5 per allied player in range at cast time. */
		@Override
		public double baseCost(Player p) {
			double c = AbilityConfig.PROT_A1_COST.get();
			if (c < 0)
				return c;
			return c + AbilityConfig.PROT_A1_COST_PER_ALLY.get() * alliesNear(p, AbilityConfig.PROT_A1_RADIUS.get()).size();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PROT_A1_COOLDOWN.get();
		}

		@Override
		public int sustainTicks() {
			return AbilityConfig.PROT_A1_DURATION.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("HOOO RAAA! Allies within 40 pay half passive upkeep for 3 min.");
		}
	}

	static final class Echo extends Ability {
		Echo() {
			super(PR, AbilitySlot.ABILITY_2, "Echo of Protection");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PROT_A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PROT_A2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			double r = AbilityConfig.PROT_A2_RADIUS.get();
			int n = 0;
			for (ServerPlayer t : player.serverLevel().players()) {
				if (!Targeting.isValid(player, t) || Targeting.isAllied(player, t) || t.distanceTo(player) > r)
					continue;
				QiEffects.dealDamageBypassingReinforce(player, t, AbilityConfig.PROT_A2_DAMAGE.get().floatValue());
				EffectHelper.apply(t, MobEffects.MOVEMENT_SLOWDOWN, AbilityConfig.PROT_A2_SLOWNESS.get(), AbilityConfig.PROT_A2_SLOWNESS_TICKS.get(), true);
				if (t.isAlive())
					LightEvents.pushAway(player, t, AbilityConfig.PROT_A2_DISTANCE.get(), AbilityConfig.PROT_A2_SPEED.get());
				n++;
			}
			player.serverLevel().sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
			return ActionResult.ok(n == 0 ? "The echo finds no one." : "Echo of Protection sends " + n + " away.");
		}
	}

	/** "2.5 min", "30 s" — for chat messages that show a config duration. */
	static String fmtTicks(int ticks) {
		double s = ticks / 20.0;
		return s >= 60 ? (s % 60 == 0 ? (long) (s / 60) + " min" : String.format("%.1f min", s / 60)) : (long) s + " s";
	}
}
