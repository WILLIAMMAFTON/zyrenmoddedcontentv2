package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiCosts;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.config.FireConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.List;

/**
 * FIRE — DEFAULT kit (ASPECT GUIDE, user decisions 2026-10-27). Replaces the old Fire kit; only the
 * old Reinforce Body is kept (the guide doesn't define one yet). Values: fire-server.toml.
 *
 *   Passive      2.5 Qi/min. Fire Resistance, clear vision in lava, heals 1 heart/s in fire for
 *                1 Qi per heart, in the Nether every Fire ability costs 1 less, senses
 *                Earth->Magma / Electric->Plasma users within 15 (name within 7) on the HUD.
 *   Attack 1     Enhanced fists of flames: 10 Qi. Next 3 hits without a weapon deal 8 + fire.
 *                5 s cooldown starts after the 3rd hit.
 *   Heavy        ENHANCED FLAMES: 15 Qi, 30 s. Next hit +2 damage (+6 vs armour), x2 on a
 *                sword/axe critical, + fire. Refused while Attack 1 charges remain.
 *   Reinforce    (old Fire Reinforce Body)
 *   Powerblast 1 Burning retreat: 10 Qi, 10 s. See FireEvents.planRetreat.
 *   Powerblast 2 Blast of Flames: 20 Qi, 30 s. Target within 15: 15 damage, knocked back 10,
 *                fire; Water/Air-active: Nausea + Blindness and x2 Qi costs for 15 s.
 *   Ability 1    HELL ON EARTH: 100 Qi, 180 s, can break core. Overworld, flame within 5.
 *   Ability 2    METEOR: 50% of Qi + 10 per player, 7200 s, can break core.
 */
public final class FireKit {
	static final AspectType F = AspectType.FIRE;

	private FireKit() {
	}

	public static AspectKit create() {
		return new AspectKit(F, new Passive(),
				FISTS, new EnhancedFlames(), new Reinforce(), RETREAT, new BlastOfFlames(), new HellOnEarth(), new Meteor());
	}

	static final Fists FISTS = new Fists();
	static final BurningRetreat RETREAT = new BurningRetreat();

	// ------------------------------------------------------------------ helpers

	/** Standing in fire, soul fire, a lit campfire or lava. (Also used by mastery XP.) */
	static boolean inFireOrLava(LivingEntity e) {
		if (e.isInLava())
			return true;
		return e.level().getBlockStatesIfLoaded(e.getBoundingBox().deflate(1.0E-3))
				.anyMatch(s -> s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES) || s.getFluidState().is(FluidTags.LAVA));
	}

	/** Weapons = swords, axes, trident, mace (same definition as Physical's passive). */
	static boolean isWeapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.TRIDENT) || s.is(Items.MACE);
	}

	static boolean hasArmor(LivingEntity e) {
		for (ItemStack s : e.getArmorSlots())
			if (!s.isEmpty())
				return true;
		return false;
	}

	static void ignite(LivingEntity t) {
		t.setRemainingFireTicks(Math.max(t.getRemainingFireTicks(), FireConfig.FIRE_TICKS.get()));
	}

	/**
	 * Fire ability costs (normal and sub-aspect): guide cost - Nether reduction (DEFAULT passive only)
	 * - Hell on Earth discount, then -10% while the Inferno passive is on and you stand in fire.
	 * Never below 0.
	 */
	static double fireCost(Player p, double base) {
		if (base < 0)
			return base;
		double c = base;
		boolean passiveOn = AbilityState.of(p).passiveActive();
		AspectPassive inUse = net.zyren.cultivation.ability.core.KitResolver.passiveFor(p);
		if (p.level().dimension() == Level.NETHER && passiveOn && isDefaultPassive(inUse))
			c -= FireConfig.NETHER_COST_REDUCTION.get();
		c -= FireEvents.hellDiscount(p);
		if (passiveOn && FireSubKits.isInfernoPassive(inUse) && FireSubEvents.standingInFire(p))
			c *= 1 - FireConfig.INF_COST_REDUCTION.get();
		return Math.max(0, c);
	}

	/** True for the DEFAULT Fire passive (its perks: Nether discount, lava vision, sensing). Body / Inferno replace it. */
	public static boolean isDefaultPassive(AspectPassive p) {
		return p instanceof Passive;
	}

	static int seconds(int s) {
		return s * 20;
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return perSeconds(FireConfig.PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		/** Once per second while active: Fire Resistance, and healing while standing in fire. */
		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.FIRE_RESISTANCE, 1, 50, false);
			if (!inFireOrLava(player) || player.getHealth() >= player.getMaxHealth())
				return;
			double hearts = Math.min(FireConfig.HEAL_HEARTS_PER_SECOND.get(), (player.getMaxHealth() - player.getHealth()) / 2.0);
			double cost = QiCosts.effective(player, hearts * FireConfig.HEAL_QI_PER_HEART.get());
			if (hearts > 0 && QiCoreStorage.trySpendQi(player, cost, false))
				player.heal((float) (hearts * 2));
		}
	}

	// ------------------------------------------------------------------ Attack 1

	/** Charges live in FireEvents, tied to the buff "fire_fists" (so a form change / nullify ends them). */
	static final class Fists extends Ability {
		Fists() {
			super(F, AbilitySlot.ATTACK_1, "Enhanced fists of flames");
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.A1_COST.get());
		}

		/** The cooldown starts after the last charged hit (FireEvents), not on cast. */
		@Override
		public int cooldownTicks() {
			return 0;
		}

		int realCooldownTicks() {
			return seconds(FireConfig.A1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return FireEvents.fistCharges(player) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int hits = FireConfig.A1_HITS.get();
			FireEvents.startFists(player, hits);
			return ActionResult.ok("Fists of flame: your next " + hits + " unarmed hits burn.");
		}
	}

	// ------------------------------------------------------------------ Heavy

	static final class EnhancedFlames extends Ability {
		EnhancedFlames() {
			super(F, AbilitySlot.HEAVY, "ENHANCED FLAMES");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.A2_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.A2_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return FireEvents.fistCharges(player) > 0 ? ActionResult.fail("Can't while Enhanced fists of flames is active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("ENHANCED FLAMES: your next hit burns.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			double extra = hasArmor(target) ? FireConfig.A2_EXTRA_ARMORED.get() : FireConfig.A2_EXTRA_UNARMORED.get();
			ItemStack held = attacker.getMainHandItem();
			if ((held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES)) && FireEvents.wasCritThisTick(attacker))
				extra *= FireConfig.A2_CRIT_MULTIPLIER.get();
			if (QiEffects.canAffect(target, false))
				ignite(target);
			return (float) (damage + extra);
		}
	}

	// ------------------------------------------------------------------ Reinforce (old one, kept)

	static final class Reinforce extends Ability {
		Reinforce() {
			super(F, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.FIRE_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.FIRE_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.FIRE_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			QiEffects.applyReinforce(player, F, AbilityConfig.FIRE_REINFORCE_DURATION.get());
			return ActionResult.ok("Body reinforced.");
		}
	}

	// ------------------------------------------------------------------ Powerblast 1

	static final class BurningRetreat extends Ability {
		BurningRetreat() {
			super(F, AbilitySlot.POWERBLAST_1, "Burning retreat");
		}

		/** The plan picked in checkUse decides the price (10 / 10+30 / 15). */
		@Override
		public double baseCost(Player p) {
			FireEvents.Plan plan = p instanceof ServerPlayer sp ? FireEvents.pendingPlan(sp) : null;
			double base = plan == null || plan.kind() != FireEvents.PlanKind.FALLBACK ? FireConfig.PB1_COST.get() : FireConfig.PB1_FALLBACK_COST.get();
			if (plan != null && plan.kind() == FireEvents.PlanKind.NETHER)
				base += FireConfig.PB1_NETHER_EXTRA_COST.get();
			return fireCost(p, base);
		}

		@Override
		public double displayCost(Player p) {
			return fireCost(p, FireConfig.PB1_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.PB1_COOLDOWN.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			FireEvents.planRetreat(player);
			return ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return FireEvents.executeRetreat(player);
		}
	}

	// ------------------------------------------------------------------ Powerblast 2

	static final class BlastOfFlames extends Ability {
		BlastOfFlames() {
			super(F, AbilitySlot.POWERBLAST_2, "Blast of Flames");
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.PB2_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.PB2_COOLDOWN.get());
		}

		static ServerPlayer target(ServerPlayer user) {
			int range = FireConfig.PB2_RANGE.get();
			ServerPlayer looked = Targeting.lookedAtPlayer(user);
			if (looked != null && looked.distanceTo(user) <= range && !Targeting.isAllied(user, looked))
				return looked;
			ServerPlayer best = null;
			for (ServerPlayer p : user.serverLevel().players())
				if (Targeting.isValid(user, p) && !Targeting.isAllied(user, p) && p.distanceTo(user) <= range
						&& (best == null || p.distanceToSqr(user) < best.distanceToSqr(user)))
					best = p;
			return best;
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return target(player) == null ? ActionResult.fail("No target within " + FireConfig.PB2_RANGE.get() + " blocks.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer t = target(player);
			if (t == null)
				return ActionResult.fail("Target lost.");
			QiEffects.dealDamage(player, t, FireConfig.PB2_DAMAGE.get().floatValue());
			if (QiEffects.canAffect(t, false)) {
				ignite(t);
				LightEvents.pushAway(player, t, FireConfig.PB2_KNOCKBACK_BLOCKS.get(), 2.0);
				AspectType ta = AspectData.of(t).getActive();
				if (ta == AspectType.WATER || ta == AspectType.AIR) {
					int ticks = seconds(FireConfig.PB2_DEBUFF_SECONDS.get());
					EffectHelper.apply(t, MobEffects.CONFUSION, 1, ticks, true);
					EffectHelper.apply(t, MobEffects.BLINDNESS, 1, ticks, true);
					net.zyren.cultivation.ability.core.QiCostDebuffs.apply(t, FireConfig.PB2_COST_MULTIPLIER.get(), ticks, player.serverLevel().getGameTime());
				}
			}
			player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.8f);
			return ActionResult.ok("Blast of Flames hits " + t.getGameProfile().getName() + ".");
		}
	}

	// ------------------------------------------------------------------ Ability 1

	static final class HellOnEarth extends Ability {
		HellOnEarth() {
			super(F, AbilitySlot.ABILITY_1, "HELL ON EARTH");
		}

		@Override
		public double baseCost(Player p) {
			return fireCost(p, FireConfig.AB1_COST.get());
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.AB1_COOLDOWN.get());
		}

		/** Lava, a lit (non-soul) campfire, a lit furnace / smoker / blast furnace, or normal fire. Not torches, soul fire, lanterns, portals. */
		static boolean isFlameSource(BlockState s) {
			if (s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.FIRE))
				return true;
			if (s.is(Blocks.CAMPFIRE))
				return CampfireBlock.isLitCampfire(s);
			return s.getBlock() instanceof AbstractFurnaceBlock && s.getValue(AbstractFurnaceBlock.LIT);
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			if (player.level().dimension() != Level.OVERWORLD)
				return ActionResult.fail(name() + " only works in the Overworld.");
			int r = FireConfig.AB1_SOURCE_RADIUS.get();
			BlockPos feet = player.blockPosition();
			for (BlockPos p : BlockPos.betweenClosed(feet.offset(-r, -r, -r), feet.offset(r, r, r)))
				if (p.distSqr(feet) <= (double) r * r && isFlameSource(player.level().getBlockState(p)))
					return ActionResult.ok("");
			return ActionResult.fail("You need a flame source (lava, campfire, lit furnace or fire) within " + r + " blocks.");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			long now = player.serverLevel().getGameTime();
			int radius = FireConfig.AB1_RADIUS.get();
			int ticks = seconds(FireConfig.AB1_BUFF_SECONDS.get());
			int buffed = 0;
			for (ServerPlayer p : player.serverLevel().players()) {
				if (!p.isAlive() || p.isSpectator() || p.distanceTo(player) > radius || AspectData.of(p).getActive() != F)
					continue;
				AbilityState.of(p).addBuff(new Buff("fire_hell_on_earth", F, FireConfig.AB1_ATTACK_MULTIPLIER.get(), 0, false, now + ticks));
				EffectHelper.apply(p, MobEffects.REGENERATION, FireConfig.AB1_REGEN_LEVEL.get(), ticks, true);
				FireEvents.setHellDiscount(p, now + ticks);
				buffed++;
			}
			FireWorld.hellOnEarth(player.serverLevel(), player.blockPosition(), radius);
			player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BLAZE_AMBIENT, net.minecraft.sounds.SoundSource.PLAYERS, 3f, 0.5f);
			return ActionResult.ok("HELL ON EARTH. " + buffed + " Fire user" + (buffed == 1 ? "" : "s") + " empowered.");
		}
	}

	// ------------------------------------------------------------------ Ability 2

	static final class Meteor extends Ability {
		Meteor() {
			super(F, AbilitySlot.ABILITY_2, "METEOR");
		}

		static int othersInRadius(Player p) {
			int r = FireConfig.AB2_RADIUS.get();
			int n = 0;
			for (Player o : p.level().players())
				if (o != p && o.isAlive() && !o.isSpectator() && o.distanceTo(p) <= r)
					n++;
			return n;
		}

		/** 50% of current Qi + 10 per other player in the crater radius (capped at the Qi you have). */
		@Override
		public double baseCost(Player p) {
			double base = QiData.of(p).getCoreAmount() * FireConfig.AB2_QI_FRACTION.get() + othersInRadius(p) * FireConfig.AB2_COST_PER_PLAYER.get();
			return fireCost(p, base);
		}

		@Override
		public boolean costCappedAtCurrentQi() {
			return true;
		}

		@Override
		public int cooldownTicks() {
			return seconds(FireConfig.AB2_COOLDOWN.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			FireEvents.noFallDamage(player);
			FireWorld.meteor(player.serverLevel(), player.blockPosition(), FireConfig.AB2_RADIUS.get());
			player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 6f, 0.5f);
			// "also activates powerblast 1 instantly for no qi cost"
			FireEvents.planRetreat(player);
			ActionResult escape = FireEvents.executeRetreat(player);
			return ActionResult.ok("METEOR! " + escape.message());
		}
	}
}
