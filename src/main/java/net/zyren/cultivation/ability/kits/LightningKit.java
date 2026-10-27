package net.zyren.cultivation.ability.kits;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.ability.core.Thorns;
import net.zyren.cultivation.client.ClientAbilityState;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.List;

/**
 * LIGHTNING (internal id ELECTRIC; source-of-truth document + decisions 2026-10-06).
 *
 *   Passive        Storm Conduit, while a thunderstorm is active or a charged creeper is within
 *                  16 blocks: Speed X, Regeneration I, 2x total output, immune to lightning damage,
 *                  3 Qi / 3 s. Lethal save (25 Qi, only while the passive is active and not during
 *                  Nuclear Winter): the killing hit is cancelled, you're struck by lightning and get
 *                  Regeneration 255 + Thorns II for 3 s.
 *   Attack 1       5 Qi (3 with passive). Lightning strikes you; for 30 s melee hits set the target
 *                  on fire (3 s) and stun it (0.5 s).
 *   Heavy          20 Qi (10 with passive). Lightning strikes you; your next hit deals 3x; lightning
 *                  strikes the nearest player for 3x normal lightning damage.
 *   Reinforce      15 Qi (7.5 with passive), 10 s, standard + Thorns X for 2 min (not with Thorns armor).
 *   Powerblast 1   10 Qi (5 with passive). Lightning strikes you; Speed V, Haste V ("Attack Speed V"),
 *                  Resistance IV and creative flight for 5 min.
 *   Powerblast 2   30 Qi (24 with passive). Lightning strikes you, then the nearest player for 2x.
 *   Ability 1      20 Qi, 2 min: lightning strikes every non-allied player within 50 every 3 s;
 *                  Regeneration V + Speed III; +50% final output (not with passive / Nuclear Winter).
 *   Ultimate       Nuclear Winter: 70 Qi. Global thunderstorm for 5 min; passive upkeep off;
 *                  1.5x total output (stacks with passive). If you can't pay 70 Qi, all your Qi is
 *                  used and your Core breaks: 5x melee output, Qi locked at 5000 until the storm
 *                  ends, then all Qi and cultivation stats are removed.
 * Lightning that "strikes you" is visual and never hurts you.
 */
public final class LightningKit {
	static final AspectType L = AspectType.ELECTRIC;
	static final Ability1 ABILITY1 = new Ability1();

	private LightningKit() {
	}

	public static AspectKit create() {
		return new AspectKit(L, new Passive(),
				new Attack1(), new Heavy(), new Reinforce(), new Powerblast1(), new Powerblast2(), ABILITY1, new NuclearWinter());
	}

	/** Is Storm Conduit active for this player? Works on both sides (client uses the synced flag). */
	static boolean passiveOn(Player p) {
		if (p instanceof ServerPlayer sp)
			return AbilityState.of(sp).passiveActive() && AspectData.of(sp).getActive() == L;
		return ClientAbilityState.passiveActive();
	}

	static double lightningDamage() {
		return AbilityConfig.ELEC_LIGHTNING_DAMAGE.get();
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			if (player.level().isThundering())
				return true;
			double r = AbilityConfig.ELEC_PASSIVE_CREEPER_RANGE.get();
			return !player.level().getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(r), Creeper::isPowered).isEmpty();
		}

		/** "3 Qi every 3 s"; Nuclear Winter disables the upkeep. */
		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return LightningEvents.nuclearWinterActive(player) ? 0 : perSeconds(AbilityConfig.ELEC_PASSIVE_QI_PER_3S.get(), 3);
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.ELEC_PASSIVE_SPEED.get(), 50, false);
			EffectHelper.applyPassive(player, MobEffects.REGENERATION, AbilityConfig.ELEC_PASSIVE_REGEN.get());
		}

		@Override
		public double outgoingMultiplier(ServerPlayer player, LivingEntity target, DamageSource source) {
			return AbilityConfig.ELEC_PASSIVE_OUTPUT.get();
		}

		/** "Immunity to Lightning-based attacks, including natural/weather lightning." */
		@Override
		public double incomingReduction(ServerPlayer player, DamageSource source) {
			return source.is(DamageTypes.LIGHTNING_BOLT) ? 1.0 : 0.0;
		}
	}

	// ------------------------------------------------------------------ abilities

	static final class Attack1 extends Ability {
		Attack1() {
			super(L, AbilitySlot.ATTACK_1, "Attack 1");
		}

		@Override
		public double baseCost(Player p) {
			double c = AbilityConfig.ELEC_ATTACK1_COST.get();
			return c > 0 && passiveOn(p) ? Math.max(0, c - AbilityConfig.ELEC_ATTACK1_PASSIVE_DISCOUNT.get()) : c;
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.ELEC_ATTACK1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			LightningEvents.strikeVisual(player.serverLevel(), player.position());
			LightningEvents.chargeFists(player, AbilityConfig.ELEC_ATTACK1_DURATION.get());
			return ActionResult.ok("Charged: your melee hits burn and stun for 30 s.");
		}
	}

	static final class Heavy extends Ability {
		Heavy() {
			super(L, AbilitySlot.HEAVY, "Heavy");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			double c = AbilityConfig.ELEC_HEAVY_COST.get();
			return c > 0 && passiveOn(p) ? c * 0.5 : c;
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.ELEC_HEAVY_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			LightningEvents.strikeVisual(player.serverLevel(), player.position());
			ServerPlayer target = Targeting.nearestPlayer(player);
			if (target != null)
				LightningEvents.strike(player, target, lightningDamage() * AbilityConfig.ELEC_HEAVY_STRIKE_MULTIPLIER.get());
			return ActionResult.ok("Heavy armed: your next hit deals triple damage.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			return (float) (damage * AbilityConfig.ELEC_HEAVY_MULTIPLIER.get());
		}
	}

	static final class Reinforce extends Ability {
		Reinforce() {
			super(L, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			double c = AbilityConfig.ELEC_REINFORCE_COST.get();
			return c > 0 && passiveOn(p) ? c * 0.5 : c;
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.ELEC_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.ELEC_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			QiEffects.applyReinforce(player, L, AbilityConfig.ELEC_REINFORCE_DURATION.get());
			if (wearsThornsArmor(player))
				return ActionResult.ok("Body reinforced (Thorns X doesn't stack with your Thorns armor).");
			Thorns.grant(player, "lightning_reinforce", AbilityConfig.ELEC_REINFORCE_THORNS.get(), AbilityConfig.ELEC_REINFORCE_THORNS_TICKS.get());
			return ActionResult.ok("Body reinforced. Thorns X for 2 minutes.");
		}

		private static boolean wearsThornsArmor(ServerPlayer player) {
			Holder<Enchantment> thorns = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.THORNS);
			for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET })
				if (player.getItemBySlot(slot).getEnchantmentLevel(thorns) > 0)
					return true;
			return false;
		}
	}

	static final class Powerblast1 extends Ability {
		Powerblast1() {
			super(L, AbilitySlot.POWERBLAST_1, "Powerblast 1");
		}

		@Override
		public double baseCost(Player p) {
			double c = AbilityConfig.ELEC_PB1_COST.get();
			return c > 0 && passiveOn(p) ? c * 0.5 : c;
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.ELEC_PB1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = AbilityConfig.ELEC_PB1_DURATION.get();
			LightningEvents.strikeVisual(player.serverLevel(), player.position());
			EffectHelper.apply(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.ELEC_PB1_SPEED.get(), ticks, true);
			EffectHelper.apply(player, MobEffects.DIG_SPEED, AbilityConfig.ELEC_PB1_HASTE.get(), ticks, true);
			EffectHelper.apply(player, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.ELEC_PB1_RESISTANCE.get(), ticks, true);
			LightningEvents.grantFlight(player, ticks);
			return ActionResult.ok("Supercharged: speed, haste, resistance and flight for 5 minutes.");
		}
	}

	static final class Powerblast2 extends Ability {
		Powerblast2() {
			super(L, AbilitySlot.POWERBLAST_2, "Powerblast 2");
		}

		@Override
		public double baseCost(Player p) {
			double c = AbilityConfig.ELEC_PB2_COST.get();
			return c > 0 && passiveOn(p) ? c * (1 - AbilityConfig.ELEC_PB2_PASSIVE_DISCOUNT.get()) : c;
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.ELEC_PB2_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return Targeting.nearestPlayer(player) == null ? ActionResult.fail("No player to strike.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			LightningEvents.strikeVisual(player.serverLevel(), player.position());
			ServerPlayer target = Targeting.nearestPlayer(player);
			if (target == null)
				return ActionResult.fail("Target lost.");
			LightningEvents.strike(player, target, lightningDamage() * AbilityConfig.ELEC_PB2_STRIKE_MULTIPLIER.get());
			return ActionResult.ok("Lightning strikes " + target.getName().getString() + ".");
		}
	}

	/** Ability 1: the storm. Strikes are done by LightningEvents while this is running. */
	static final class Ability1 extends Ability {
		Ability1() {
			super(L, AbilitySlot.ABILITY_1, "Ability 1");
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.ELEC_ABILITY1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.ELEC_ABILITY1_COOLDOWN.get();
		}

		@Override
		public int sustainTicks() {
			return AbilityConfig.ELEC_ABILITY1_DURATION.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.ELEC_ABILITY1_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = sustainTicks();
			EffectHelper.apply(player, MobEffects.REGENERATION, AbilityConfig.ELEC_ABILITY1_REGEN.get(), ticks, true);
			EffectHelper.apply(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.ELEC_ABILITY1_SPEED.get(), ticks, true);
			return ActionResult.ok("A storm follows you for 2 minutes.");
		}

		@Override
		public void onStop(ServerPlayer player) {
			removeIfOurs(player, MobEffects.REGENERATION, AbilityConfig.ELEC_ABILITY1_REGEN.get());
			removeIfOurs(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.ELEC_ABILITY1_SPEED.get());
		}

		/** Removing an effect removes every instance of it: only do so if the active one is ours. */
		private static void removeIfOurs(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int level) {
			var inst = player.getEffect(effect);
			if (inst != null && inst.getAmplifier() == Math.max(0, level - 1))
				player.removeEffect(effect);
		}
	}

	static final class NuclearWinter extends Ability {
		NuclearWinter() {
			super(L, AbilitySlot.ULTIMATE, "Nuclear Winter");
		}

		/** Paid inside perform: being short is allowed (the Core breaks). */
		@Override
		public double baseCost(Player p) {
			return 0;
		}

		@Override
		public double displayCost(Player p) {
			return AbilityConfig.ELEC_ULT_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.ELEC_ULT_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("Qi cost", AbilityConfig.ELEC_ULT_COST.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			if (net.zyren.cultivation.data.QiData.of(player).getCoreType() == net.zyren.cultivation.data.CoreType.CORRUPTED)
				return ActionResult.fail("A Corrupted Core cannot call Nuclear Winter."); // user decision 2026-10-07
			if (LightningEvents.nuclearWinterActive(player))
				return ActionResult.fail("Nuclear Winter is already raging.");
			// Being short breaks the Core: only allowed with Emergency Core Breakdown ON.
			double required = net.zyren.cultivation.ability.core.QiCosts.effective(player, AbilityConfig.ELEC_ULT_COST.get());
			if (net.zyren.cultivation.data.QiData.of(player).getCoreAmount() < required
					&& (!net.zyren.cultivation.system.EmergencyCore.allowed(player) || net.zyren.cultivation.data.QiData.of(player).getCoreLevel() < 1)) // no Core to pay with (annihilation grace)
				return ActionResult.fail("Not enough Qi (" + net.zyren.cultivation.ability.core.AbilityService.fmt(required) + " needed).");
			return ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return LightningEvents.startNuclearWinter(player);
		}
	}
}
