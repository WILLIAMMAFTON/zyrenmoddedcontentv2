package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.resources.ResourceLocation;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiCosts;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.CoreBreak;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.List;

/**
 * PHYSICAL (source-of-truth document + decisions 2026-10-06).
 *
 *   Passive        Speed X, Strength III, non-weapon melee x3, 5 Qi/minute. Upkeep x1/x2/x4/x9 with
 *                  0/1/2/3 environmental conditions (lava, lightning strike, underwater); damage
 *                  from those conditions is nullified.
 *   Attack 1       Fists of Fury: 10 Qi, Attack Speed attribute 34 for 3 min, then 1 Qi / 2 s.
 *   Heavy          Bomb: 20 Qi, 1 min. Melee hits explode (creeper size, breaks blocks). Falls
 *                  make a crater sized by fall distance instead of fall damage. User immune.
 *   Reinforce      50 Qi, 10 s. Standard + Regeneration 255 + Resistance 255 + Thorns I.
 *                  Passive off meanwhile.
 *   Powerblast 1   Yeet: 20 Qi. Thrown at the nearest player, breaking blocks; contact = 50 dmg.
 *   Powerblast 2   Sky High: 40 Qi. Rise to the sky limit; landing on a player deals 2 dmg per
 *                  block fallen. User takes no fall damage from it. On a hit, the user's own
 *                  Physical passive is nullified for 30 s (balancing).
 *   Ability 1      Survivor: manual, 50% of remaining Qi. Armed until the next lethal hit, which
 *                  is cancelled and the user is healed to full (Totem-like).
 *   Ability 2      Get Out of My Domain: 200 Qi. User gets Strength 255 + Knockback 255 on melee
 *                  hits for 3 min; the looked-at player's Core breaks. Under 200 Qi it still
 *                  fires but breaks the user's own Core.
 */
public final class PhysicalKit {
	static final AspectType P = AspectType.PHYSICAL;

	/** Bomb instance, so the event handlers can check whether it's running. */
	static final Bomb BOMB = new Bomb();

	private PhysicalKit() {
	}

	public static AspectKit create() {
		return new AspectKit(P, new Passive(),
				new FistsOfFury(), BOMB, new Reinforce(), new Yeet(), new SkyHigh(), new Survivor(), new Domain());
	}

	static boolean isBombActive(ServerPlayer player) {
		return AbilityState.of(player).isSustained(BOMB);
	}

	static boolean isWeapon(ItemStack stack) {
		return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
				|| stack.getItem() instanceof TridentItem || stack.getItem() instanceof MaceItem;
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			// "Passive completely disabled during [Reinforce Body]", and after a Sky High hit
			return !AbilityState.of(player).hasBuff(QiEffects.reinforceId(P), player.serverLevel().getGameTime())
					&& !PhysicalEvents.passiveSuppressed(player);
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			double mult = switch (PhysicalEvents.environmentConditions(player)) {
				case 0 -> 1.0;
				case 1 -> AbilityConfig.PHYSICAL_ENV_MULT_1.get();
				case 2 -> AbilityConfig.PHYSICAL_ENV_MULT_2.get();
				default -> AbilityConfig.PHYSICAL_ENV_MULT_3.get();
			};
			return perSeconds(AbilityConfig.PHYSICAL_PASSIVE_QI_PER_MINUTE.get(), 60) * mult;
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.PHYSICAL_PASSIVE_SPEED_LEVEL.get(), 50, false);
			EffectHelper.apply(player, MobEffects.DAMAGE_BOOST, AbilityConfig.PHYSICAL_PASSIVE_STRENGTH_LEVEL.get(), 50, false);
		}

		/** "Non-weapon melee damage x3." */
		@Override
		public double outgoingMultiplier(ServerPlayer player, LivingEntity target, DamageSource source) {
			boolean melee = source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player && !QiEffects.isDealingQiDamage();
			return melee && !isWeapon(player.getMainHandItem()) ? AbilityConfig.PHYSICAL_PASSIVE_UNARMED_MULTIPLIER.get() : 1.0;
		}

		/** "All environmental damage from those conditions is nullified." */
		@Override
		public double incomingReduction(ServerPlayer player, DamageSource source) {
			if (source.is(DamageTypes.LAVA) || source.is(DamageTypes.LIGHTNING_BOLT) || source.is(DamageTypes.DROWN))
				return 1.0;
			boolean fire = source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE);
			if (fire && (player.isInLava() || PhysicalEvents.recentlyStruck(player)))
				return 1.0;
			return 0.0;
		}
	}

	// ------------------------------------------------------------------ abilities

	static final class FistsOfFury extends Ability {
		private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "physical_fists_of_fury");

		FistsOfFury() {
			super(P, AbilitySlot.ATTACK_1, "Fists of Fury");
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PHYSICAL_ATTACK1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PHYSICAL_ATTACK1_COOLDOWN.get();
		}

		@Override
		public int sustainTicks() {
			return AbilityConfig.PHYSICAL_ATTACK1_DURATION.get();
		}

		@Override
		public double sustainQiPerSecond() {
			return AbilityConfig.PHYSICAL_ATTACK1_QI_PER_SECOND.get();
		}

		/** Sets the Attack Speed attribute to 34 (base + modifier = 34). */
		@Override
		public ActionResult perform(ServerPlayer player) {
			AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
			if (speed != null) {
				speed.removeModifier(MODIFIER);
				double add = AbilityConfig.PHYSICAL_ATTACK1_ATTACK_SPEED.get() - speed.getBaseValue();
				speed.addTransientModifier(new AttributeModifier(MODIFIER, add, AttributeModifier.Operation.ADD_VALUE));
			}
			return ActionResult.ok("Fists of Fury!");
		}

		@Override
		public void onStop(ServerPlayer player) {
			AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
			if (speed != null)
				speed.removeModifier(MODIFIER);
		}
	}

	/** Bomb: the explosions and craters themselves live in {@link PhysicalEvents}. */
	static final class Bomb extends Ability {
		Bomb() {
			super(P, AbilitySlot.HEAVY, "Bomb");
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PHYSICAL_HEAVY_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PHYSICAL_HEAVY_COOLDOWN.get();
		}

		@Override
		public int sustainTicks() {
			return AbilityConfig.PHYSICAL_HEAVY_DURATION.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("Bomb: every hit explodes for 1 minute.");
		}
	}

	static final class Reinforce extends Ability {
		Reinforce() {
			super(P, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PHYSICAL_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PHYSICAL_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.PHYSICAL_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = AbilityConfig.PHYSICAL_REINFORCE_DURATION.get();
			QiEffects.applyReinforce(player, P, ticks);
			EffectHelper.apply(player, MobEffects.REGENERATION, AbilityConfig.PHYSICAL_REINFORCE_REGEN_LEVEL.get(), ticks, true);
			EffectHelper.apply(player, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.PHYSICAL_REINFORCE_RESISTANCE_LEVEL.get(), ticks, true);
			net.zyren.cultivation.ability.core.Thorns.grant(player, "physical_reinforce", 1, ticks); // Thorns I
			return ActionResult.ok("Body reinforced. Physical passive paused.");
		}
	}

	static final class Yeet extends Ability {
		Yeet() {
			super(P, AbilitySlot.POWERBLAST_1, "Yeet");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PHYSICAL_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PHYSICAL_PB1_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return target(player) == null ? ActionResult.fail("No player within " + (int) (double) AbilityConfig.PHYSICAL_PB1_RANGE.get() + " blocks to throw yourself at.") : ActionResult.ok("");
		}

		/** Nearest player, only if within the target range (balance pass 2026-10-07: 20 blocks). */
		private static ServerPlayer target(ServerPlayer player) {
			ServerPlayer t = Targeting.nearestPlayer(player);
			return t != null && t.distanceTo(player) <= AbilityConfig.PHYSICAL_PB1_RANGE.get() ? t : null;
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer target = target(player);
			if (target == null)
				return ActionResult.fail("Target lost.");
			PhysicalEvents.startYeet(player, target);
			return ActionResult.ok("Yeet!");
		}
	}

	static final class SkyHigh extends Ability {
		SkyHigh() {
			super(P, AbilitySlot.POWERBLAST_2, "Sky High");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.PHYSICAL_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PHYSICAL_PB2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			PhysicalEvents.startSkyHigh(player);
			return ActionResult.ok("Sky High!");
		}
	}

	/** Survivor: "Costs 50% of remaining Qi. Acts like a Totem of Undying and heals user to full. Not automatic." */
	static final class Survivor extends Ability {
		Survivor() {
			super(P, AbilitySlot.ABILITY_1, "Survivor");
		}

		@Override
		public Kind kind() {
			return Kind.LETHAL_GUARD;
		}

		@Override
		public double baseCost(Player p) {
			return QiData.of(p).getCoreAmount() * AbilityConfig.PHYSICAL_ABILITY1_QI_FRACTION.get();
		}

		@Override
		public boolean costCappedAtCurrentQi() {
			return true;
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PHYSICAL_ABILITY1_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return QiData.of(player).getCoreAmount() <= 0 ? ActionResult.fail("You have no Qi.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("Survivor armed: it will save you from the next lethal hit.");
		}

		@Override
		public boolean onLethal(ServerPlayer player) {
			player.setHealth(player.getMaxHealth());
			player.level().broadcastEntityEvent(player, (byte) 35); // Totem of Undying animation
			return true;
		}
	}

	/**
	 * Get Out of My Domain. Decided: targets the looked-at player; under 200 Qi it still fires,
	 * drains all Qi, and breaks the user's own Core. Reinforce Body nullifies it on the target
	 * (the document doesn't say it bypasses Reinforce).
	 */
	static final class Domain extends Ability {
		Domain() {
			super(P, AbilitySlot.ABILITY_2, "Get Out of My Domain");
		}

		/** Paid inside {@link #perform}, because being short on Qi is allowed (with a price). */
		@Override
		public double baseCost(Player p) {
			return 0;
		}

		@Override
		public double displayCost(Player p) {
			return AbilityConfig.PHYSICAL_ABILITY2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PHYSICAL_ABILITY2_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("Qi cost", AbilityConfig.PHYSICAL_ABILITY2_COST.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			if (Targeting.lookedAtPlayer(player) == null)
				return ActionResult.fail("Look at a player to use this.");
			// Being short breaks the user's own Core: only allowed with Emergency Core Breakdown ON.
			// (The TARGET's Core breaks regardless of the target's toggle — decided.)
			double required = QiCosts.effective(player, AbilityConfig.PHYSICAL_ABILITY2_COST.get());
			if (QiData.of(player).getCoreAmount() < required
					&& (!net.zyren.cultivation.system.EmergencyCore.allowed(player) || QiData.of(player).getCoreLevel() < 1)) // no Core to pay with (annihilation grace)
				return ActionResult.fail("Not enough Qi (" + net.zyren.cultivation.ability.core.AbilityService.fmt(required) + " needed).");
			return ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer target = Targeting.lookedAtPlayer(player);
			if (target == null)
				return ActionResult.fail("Target lost.");

			double required = QiCosts.effective(player, AbilityConfig.PHYSICAL_ABILITY2_COST.get());
			double have = QiData.of(player).getCoreAmount();
			boolean shortOfQi = have < required;
			double paid = Math.min(have, required);
			QiCoreStorage.trySpendQi(player, paid);
			// Paid here, not by the service (cost may exceed current Qi): record it like any cast.
			AbilityState.of(player).recordPaid(paid);
			net.zyren.cultivation.system.AspectMastery.onQiSpent(player, P, paid);

			int strengthTicks = AbilityConfig.PHYSICAL_ABILITY2_STRENGTH_DURATION.get();
			if (strengthTicks > 0) // duration not in document; skipped until set
				EffectHelper.apply(player, MobEffects.DAMAGE_BOOST, AbilityConfig.PHYSICAL_ABILITY2_STRENGTH_LEVEL.get(), strengthTicks, true);

			// Knockback 255 on the user's melee hits for 3 min (decided)
			PhysicalEvents.startDomainKnockback(player, AbilityConfig.PHYSICAL_ABILITY2_KNOCKBACK_DURATION.get());

			String result;
			if (QiEffects.canAffect(target, false)) {
				boolean broke = CoreBreak.breakCore(target, "Get Out of My Domain");
				result = broke ? target.getName().getString() + "'s Core breaks!" : target.getName().getString() + " has no Core to break.";
			} else {
				result = target.getName().getString() + "'s Reinforced Body nullified it.";
			}

			if (shortOfQi)
				CoreBreak.breakCore(player, "not enough Qi for Get Out of My Domain");
			return ActionResult.ok(result);
		}
	}
}
