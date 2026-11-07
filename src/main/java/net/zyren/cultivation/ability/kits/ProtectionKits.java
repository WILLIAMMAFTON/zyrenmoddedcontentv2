package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectCleanup;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.CastRules;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.SubAspectKit;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.ProtectionConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * PROTECTION — DEFAULT kit + Healing sub-aspect (ASPECT GUIDE, user "use rec" 2026-11-06). Replaces
 * the old Protective kit; only its Reinforce Body is kept. Resistance / Nullification are disabled
 * in the guide (not built). Values: protection-server.toml. Event side: ProtectionKitEvents.
 *
 * DEFAULT: passive -10% damage · defencive aura · tank · intelegence · invaild kill · guardians oath · a worthy death.
 * HEALING: passive ally heal · lady mercy · mending pulse · resistance gift · lifeline · deaths door · i give you my all.
 */
public final class ProtectionKits {
	static final AspectType PR = AspectType.PROTECTIVE;

	private ProtectionKits() {
	}

	static final Passive PASSIVE = new Passive();
	static final Aura AURA = new Aura();
	static final Tank TANK = new Tank();
	static final Intelligence INTEL = new Intelligence();
	static final InvalidKill INVALID_KILL = new InvalidKill();
	static final Oath OATH = new Oath();
	static final WorthyDeath WORTHY = new WorthyDeath();

	public static AspectKit create() {
		CastRules.register(ProtectionKitEvents.RULES);
		ProtectionKitEvents.registerCorruptionProtection();
		return new AspectKit(PR, PASSIVE, AURA, TANK, new ProtectiveKit.Reinforce(), INTEL, INVALID_KILL, OATH, WORTHY);
	}

	// ================================================================== helpers

	static int seconds(int s) {
		return s * 20;
	}

	/** Allied players within the radius (the user only if includeSelf). */
	static List<ServerPlayer> allies(ServerPlayer user, double radius, boolean includeSelf) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer o : user.serverLevel().players()) {
			if (o == user) {
				if (includeSelf)
					out.add(o);
				continue;
			}
			if (o.isAlive() && !o.isSpectator() && Targeting.isAllied(user, o) && o.distanceTo(user) <= radius)
				out.add(o);
		}
		return out;
	}

	static void removeReinforce(ServerPlayer p) {
		AbilityState.of(p).removeBuff(QiEffects.reinforceId(PR));
		AspectCleanup.clearReinforceExtras(p, PR);
	}

	// ================================================================== DEFAULT passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return !KitParts.passiveDisabled(p);
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(ProtectionConfig.PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public double incomingReduction(ServerPlayer p, DamageSource source) {
			return ProtectionKitEvents.excluded(source) ? 0 : ProtectionConfig.PASSIVE_REDUCTION.get();
		}
	}

	// ================================================================== DEFAULT abilities

	/** Ally damage cut + Qi per damage removed: ProtectionKitEvents. */
	static final class Aura extends KitParts.Timed {
		Aura() {
			super(PR, AbilitySlot.ATTACK_1, "defencive aura", null, ProtectionConfig.A1_COST, ProtectionConfig.A1_COOLDOWN, ProtectionConfig.A1_SECONDS, false);
		}

		@Override
		public double sustainQiPerSecond() {
			return ProtectionConfig.A1_QI_PER_MINUTE.get() / 60.0;
		}

		@Override
		boolean disablesPassive() {
			return true;
		}
	}

	static final class Tank extends KitParts.Timed {
		Tank() {
			super(PR, AbilitySlot.HEAVY, "tank", null, ProtectionConfig.A2_COST, ProtectionConfig.A2_COOLDOWN, ProtectionConfig.A2_SECONDS, false);
		}

		@Override
		public double sustainQiPerSecond() {
			return ProtectionConfig.A2_QI_PER_5S.get() / 5.0;
		}

		@Override
		boolean disablesPassive() {
			return true;
		}

		@Override
		void start(ServerPlayer p) {
			AbilityState.of(p).addBuff(new Buff("protection_tank", PR, ProtectionConfig.A2_OUTPUT.get(), 0, false, p.serverLevel().getGameTime() + sustainTicks()));
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_RESISTANCE, ProtectionConfig.A2_RESISTANCE.get(), 30, true);
		}

		@Override
		void end(ServerPlayer p) {
			AbilityState.of(p).removeBuff("protection_tank");
		}
	}

	/** Attacker info in chat: ProtectionKitEvents. */
	static final class Intelligence extends KitParts.Timed {
		Intelligence() {
			super(PR, AbilitySlot.POWERBLAST_1, "intelegence", null, ProtectionConfig.PB1_COST, ProtectionConfig.PB1_COOLDOWN, ProtectionConfig.PB1_SECONDS, false);
		}
	}

	/** Lethal guard; while armed, Reinforce Body is removed and locked (ProtectionKitEvents.RULES). */
	static final class InvalidKill extends KitParts.Cfg {
		InvalidKill() {
			super(PR, AbilitySlot.POWERBLAST_2, "invaild kill", null, ProtectionConfig.PB2_COST, ProtectionConfig.PB2_COOLDOWN, true);
		}

		@Override
		public Kind kind() {
			return Kind.LETHAL_GUARD;
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			if (AbilityState.of(p).lethalGuard() == this)
				return ActionResult.fail(name() + " is already armed.");
			return ProtectionKitEvents.worthyLocked(p) ? ActionResult.fail(name() + " can't be used during a worthy death.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			removeReinforce(p);
			return ActionResult.ok("Invalid kill armed: the next lethal hit is cancelled. Reinforce Body is locked until then.");
		}

		@Override
		public boolean onLethal(ServerPlayer p) {
			if (ProtectionKitEvents.lastHitExcluded(p) || ProtectionKitEvents.worthyLocked(p)) {
				AbilityState.of(p).setLethalGuard(this); // stays armed; this hit isn't saved
				return false;
			}
			p.displayClientMessage(net.minecraft.network.chat.Component.literal("Invalid kill!"), true);
			return true;
		}
	}

	static final class Oath extends KitParts.Timed {
		Oath() {
			super(PR, AbilitySlot.ABILITY_1, "guardians oath", null, ProtectionConfig.AB1_COST, ProtectionConfig.AB1_COOLDOWN, ProtectionConfig.AB1_SECONDS, true);
		}

		@Override
		void second(ServerPlayer p) {
			for (ServerPlayer a : allies(p, ProtectionConfig.AB1_RADIUS.get(), false))
				EffectHelper.applyPassive(a, MobEffects.REGENERATION, ProtectionConfig.AB1_REGEN.get());
		}
	}

	static final class WorthyDeath extends KitParts.Timed {
		WorthyDeath() {
			super(PR, AbilitySlot.ABILITY_2, "a worthy death", null, ProtectionConfig.AB2_COST, ProtectionConfig.AB2_COOLDOWN, ProtectionConfig.AB2_SECONDS, true);
		}

		@Override
		void start(ServerPlayer p) {
			ProtectionKitEvents.startWorthy(p);
		}

		@Override
		void end(ServerPlayer p) {
			ProtectionKitEvents.endWorthy(p);
		}
	}

	// ================================================================== HEALING

	static final SubAspectType HEALING = SubAspectType.PROTECTION_HEALING;
	static final HealingPassive HL_PASSIVE = new HealingPassive();
	static final LadyMercy MERCY = new LadyMercy();
	static final Lifeline LIFELINE = new Lifeline();
	static final DeathsDoor DOOR = new DeathsDoor();
	static final ResistanceGift GIFT = new ResistanceGift();

	public static SubAspectKit healing() {
		return new SubAspectKit(HEALING, HL_PASSIVE, MERCY, new MendingPulse(), GIFT, LIFELINE, DOOR, new GiveMyAll()).onLeave(ProtectionKitEvents::leaveHealing);
	}

	static boolean healingPassiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == HL_PASSIVE;
	}

	/** Heal the lowest-health injured ally nearby (else yourself), paying Qi per heart healed. */
	static final class HealingPassive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return 0;
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			ProtectionKitEvents.passiveHeal(p);
		}
	}

	static final class LadyMercy extends KitParts.Cfg {
		LadyMercy() {
			super(PR, AbilitySlot.ATTACK_1, "lady mercy", HEALING, ProtectionConfig.HL_A1_COST, ProtectionConfig.HL_A1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return ProtectionKitEvents.MERCY_CHARGE.left(p) > 0 ? ActionResult.fail(name() + " is already ready.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ProtectionKitEvents.MERCY_CHARGE.start(p, 1);
			return ActionResult.ok("Lady mercy: your next melee hit on an ally heals them to full.");
		}
	}

	static final class MendingPulse extends KitParts.Cfg {
		MendingPulse() {
			super(PR, AbilitySlot.HEAVY, "mending pulse", HEALING, ProtectionConfig.HL_A2_COST, ProtectionConfig.HL_A2_COOLDOWN, false);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int min = ProtectionConfig.HL_A2_MIN.get(), max = Math.max(min, ProtectionConfig.HL_A2_MAX.get()), n = 0;
			for (ServerPlayer a : allies(p, ProtectionConfig.HL_A2_RADIUS.get(), true)) {
				a.heal(min + p.getRandom().nextInt(max - min + 1));
				n++;
			}
			p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, p.getX(), p.getY() + 1, p.getZ(), 15, 3, 0.5, 3, 0);
			return ActionResult.ok("Mending pulse: " + n + " healed.");
		}
	}

	static final class ResistanceGift extends KitParts.Timed {
		ResistanceGift() {
			super(PR, AbilitySlot.POWERBLAST_1, "resistance gift", HEALING, ProtectionConfig.HL_PB1_COST, ProtectionConfig.HL_PB1_COOLDOWN, ProtectionConfig.HL_PB1_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			ProtectionKitEvents.giveGift(p);
		}
	}

	static final class Lifeline extends KitParts.Timed {
		Lifeline() {
			super(PR, AbilitySlot.POWERBLAST_2, "lifeline", HEALING, ProtectionConfig.HL_PB2_COST, ProtectionConfig.HL_PB2_COOLDOWN, ProtectionConfig.HL_PB2_SECONDS, false);
		}

		static ServerPlayer target(ServerPlayer p) {
			int r = ProtectionConfig.HL_PB2_RANGE.get();
			ServerPlayer looked = Targeting.lookedAtPlayer(p);
			if (looked != null && Targeting.isAllied(p, looked) && looked.distanceTo(p) <= r)
				return looked;
			ServerPlayer best = null;
			for (ServerPlayer a : allies(p, r, false))
				if (best == null || a.distanceToSqr(p) < best.distanceToSqr(p))
					best = a;
			return best;
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return target(p) == null ? ActionResult.fail("No ally within " + ProtectionConfig.HL_PB2_RANGE.get() + " blocks.") : ActionResult.ok("");
		}

		@Override
		void start(ServerPlayer p) {
			ServerPlayer t = target(p);
			if (t != null) {
				ProtectionKitEvents.LINKS.put(p.getUUID(), t.getUUID());
				t.displayClientMessage(net.minecraft.network.chat.Component.literal(p.getGameProfile().getName() + " linked a lifeline to you."), true);
			}
		}

		@Override
		void second(ServerPlayer p) {
			ServerPlayer t = ProtectionKitEvents.linked(p);
			if (t != null)
				EffectHelper.apply(t, MobEffects.REGENERATION, ProtectionConfig.HL_PB2_REGEN.get(), 30, true);
		}

		@Override
		void end(ServerPlayer p) {
			ProtectionKitEvents.LINKS.remove(p.getUUID());
		}
	}

	/** Saves one lethal hit near you: ProtectionKitEvents. */
	static final class DeathsDoor extends KitParts.Timed {
		DeathsDoor() {
			super(PR, AbilitySlot.ABILITY_1, "deaths door", HEALING, ProtectionConfig.HL_AB1_COST, ProtectionConfig.HL_AB1_COOLDOWN, ProtectionConfig.HL_AB1_SECONDS, false);
		}
	}

	static final class GiveMyAll extends KitParts.Cfg {
		GiveMyAll() {
			super(PR, AbilitySlot.ABILITY_2, "i give you my all.", HEALING, () -> 0, ProtectionConfig.HL_AB2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return QiData.of(p).getCoreAmount() >= 1 ? ActionResult.ok("") : ActionResult.fail("You have no Qi left to give.");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			double all = QiData.of(p).getCoreAmount();
			QiCoreStorage.trySpendQi(p, all);
			int ticks = seconds(ProtectionConfig.HL_AB2_SECONDS.get()), n = 0;
			for (ServerPlayer a : allies(p, ProtectionConfig.HL_AB2_RADIUS.get(), true)) {
				EffectHelper.apply(a, MobEffects.ABSORPTION, ProtectionConfig.HL_AB2_ABSORPTION.get(), ticks, true);
				EffectHelper.apply(a, MobEffects.DAMAGE_RESISTANCE, ProtectionConfig.HL_AB2_RESISTANCE.get(), ticks, true);
				n++;
			}
			return ActionResult.ok("I give you my all: " + Math.round(all) + " Qi, " + n + " protected.");
		}
	}
}
