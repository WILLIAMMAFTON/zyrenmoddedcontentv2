package net.zyren.cultivation.ability.kits;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiCosts;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.List;

/**
 * WARRIOR (source-of-truth document).
 *
 *   Passive        While using a sword: Speed IV, Strength III. 1 Qi/s + 2 Qi per sword use.
 *   Attack 1       5 Qi. Powered attack, 2x damage.               (empowers next hit)
 *   Heavy          10 Qi. 3x damage. Full heal.                    (empowers next hit)
 *   Reinforce      5 Qi. Standard (50% reduction, Qi effects nullified). 10 s.
 *   Powerblast 1   20 Qi. Qi blast, 3x damage.
 *   Powerblast 2   "Yeet": 15 Qi. Knockback XV on opposing player.
 *   Ability 1      "Speedy Blade": 3 min, 1 Qi/s, +200% attack speed.
 *   Ability 2      30 Qi, 30 s cd. Manual: cast to arm; the next lethal hit is cancelled
 *                  and you're teleported 3 blocks backwards.
 */
public final class WarriorKit {
	private static final AspectType W = AspectType.WARRIOR;

	private WarriorKit() {
	}

	public static AspectKit create() {
		return new AspectKit(W, new Passive(),
				new Attack1(), new Heavy(), new Reinforce(), new Powerblast1(), new Powerblast2(),
				new SpeedyBlade(), new LastStep());
	}

	static boolean holdingSword(Player player) {
		return player.getMainHandItem().is(ItemTags.SWORDS);
	}

	// ------------------------------------------------------------------ passive

	/** "When using sword: Speed IV, Strength III, 2 Qi per sword use." Using = holding a sword in the main hand. */
	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return holdingSword(player);
		}

		/** "1 Qi per second" while using a sword (decided 2026-10-06). */
		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return perSeconds(AbilityConfig.WARRIOR_PASSIVE_QI_PER_SECOND.get(), 1);
		}

		@Override
		public double minimumQi(ServerPlayer player) {
			return AbilityConfig.WARRIOR_PASSIVE_QI_PER_SWORD_USE.get();
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.WARRIOR_PASSIVE_SPEED_LEVEL.get(), 50, false);
			EffectHelper.apply(player, MobEffects.DAMAGE_BOOST, AbilityConfig.WARRIOR_PASSIVE_STRENGTH_LEVEL.get(), 50, false);
		}

		/** "2 Qi per sword use": charged on each sword hit. */
		@Override
		public void onMeleeHit(ServerPlayer player, LivingEntity target) {
			if (holdingSword(player))
				QiCoreStorage.trySpendQi(player, QiCosts.effective(player, AbilityConfig.WARRIOR_PASSIVE_QI_PER_SWORD_USE.get()), false);
		}
	}

	// ------------------------------------------------------------------ abilities

	static final class Attack1 extends Ability {
		Attack1() {
			super(W, AbilitySlot.ATTACK_1, "Attack 1");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WARRIOR_ATTACK1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WARRIOR_ATTACK1_COOLDOWN.get();
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			return (float) (damage * AbilityConfig.WARRIOR_ATTACK1_MULTIPLIER.get());
		}
	}

	static final class Heavy extends Ability {
		Heavy() {
			super(W, AbilitySlot.HEAVY, "Heavy");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WARRIOR_HEAVY_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WARRIOR_HEAVY_COOLDOWN.get();
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (AbilityConfig.WARRIOR_HEAVY_FULL_HEAL.get()) // decided: full heal
				attacker.setHealth(attacker.getMaxHealth());
			return (float) (damage * AbilityConfig.WARRIOR_HEAVY_MULTIPLIER.get());
		}
	}

	static final class Reinforce extends Ability {
		Reinforce() {
			super(W, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WARRIOR_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WARRIOR_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.WARRIOR_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			QiEffects.applyReinforce(player, W, AbilityConfig.WARRIOR_REINFORCE_DURATION.get());
			return ActionResult.ok("Body reinforced.");
		}
	}

	/** "Qi blast. 3x damage." Base damage interpreted as the user's attack damage (flagged for confirmation). */
	static final class Powerblast1 extends Ability {
		Powerblast1() {
			super(W, AbilitySlot.POWERBLAST_1, "Powerblast 1");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WARRIOR_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WARRIOR_PB1_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return Targeting.defaultTarget(player) == null ? ActionResult.fail("No target.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer target = Targeting.defaultTarget(player);
			if (target == null)
				return ActionResult.fail("Target lost.");
			drawLine(player, target);
			float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * AbilityConfig.WARRIOR_PB1_MULTIPLIER.get());
			QiEffects.dealDamage(player, target, damage);
			return ActionResult.ok("Qi blast hits " + target.getName().getString() + ".");
		}
	}

	/** "Yeet": Knockback XV on the opposing player (default targeting). Qi cost TBD. */
	static final class Powerblast2 extends Ability {
		Powerblast2() {
			super(W, AbilitySlot.POWERBLAST_2, "Yeet");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WARRIOR_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WARRIOR_PB2_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("Qi cost", AbilityConfig.WARRIOR_PB2_COST.get());
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			ServerPlayer t = Targeting.defaultTarget(player);
			if (t == null)
				return ActionResult.fail("No target.");
			// "Knock OPPOSING player back": never a teammate (the last-damaged player can be one).
			return Targeting.isAllied(player, t) ? ActionResult.fail("You can't Yeet an ally.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer target = Targeting.defaultTarget(player);
			if (target == null || Targeting.isAllied(player, target))
				return ActionResult.fail("Target lost.");
			boolean hit = QiEffects.knockback(target, player, AbilityConfig.WARRIOR_PB2_KNOCKBACK_LEVEL.get(), false);
			return hit ? ActionResult.ok("Yeet!") : ActionResult.ok(target.getName().getString() + "'s Reinforced Body nullified it.");
		}
	}

	/** "Speedy Blade": 3 min, constant 1 Qi/s, empowers the user with +200% attack speed (decided). */
	static final class SpeedyBlade extends Ability {
		private static final ResourceLocation MODIFIER = ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "warrior_speedy_blade");

		SpeedyBlade() {
			super(W, AbilitySlot.ABILITY_1, "Speedy Blade");
		}

		@Override
		public Kind kind() {
			return Kind.SUSTAINED;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WARRIOR_ABILITY1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WARRIOR_ABILITY1_COOLDOWN.get();
		}

		@Override
		public int sustainTicks() {
			return AbilityConfig.WARRIOR_ABILITY1_DURATION.get();
		}

		@Override
		public double sustainQiPerSecond() {
			return AbilityConfig.WARRIOR_ABILITY1_QI_PER_SECOND.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
			if (speed != null) {
				speed.removeModifier(MODIFIER);
				speed.addTransientModifier(new AttributeModifier(MODIFIER, AbilityConfig.WARRIOR_ABILITY1_ATTACK_SPEED_BONUS.get(),
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			return ActionResult.ok("Speedy Blade active.");
		}

		@Override
		public void onStop(ServerPlayer player) {
			AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
			if (speed != null)
				speed.removeModifier(MODIFIER);
		}
	}

	/**
	 * Ability 2 (unnamed): manual. Cast to arm (30 Qi, 30 s cooldown from the cast). The next hit
	 * that would kill you is cancelled and you're teleported 3 blocks backwards (away from where
	 * you're looking). If there's no room, the nearest free spot along that line is used; with no
	 * room at all you stay in place but the lethal hit is still cancelled.
	 */
	static final class LastStep extends Ability {
		LastStep() {
			super(W, AbilitySlot.ABILITY_2, "Ability 2");
		}

		@Override
		public Kind kind() {
			return Kind.LETHAL_GUARD;
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WARRIOR_ABILITY2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WARRIOR_ABILITY2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("Ability 2 armed: it will save you from the next lethal hit.");
		}

		@Override
		public boolean onLethal(ServerPlayer player) {
			Vec3 look = player.getLookAngle();
			Vec3 back = new Vec3(-look.x, 0, -look.z);
			if (back.lengthSqr() < 1.0E-4)
				back = Vec3.directionFromRotation(0, player.getYRot()).scale(-1);
			back = back.normalize();
			double max = AbilityConfig.WARRIOR_ABILITY2_DISTANCE.get();
			for (double d = max; d > 0.25; d -= 0.5) {
				Vec3 dest = player.position().add(back.scale(d));
				if (player.serverLevel().noCollision(player, player.getBoundingBox().move(dest.subtract(player.position())))) {
					player.teleportTo(dest.x, dest.y, dest.z);
					break;
				}
			}
			player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			player.displayClientMessage(net.minecraft.network.chat.Component.literal("Ability 2 saved you!"), true);
			return true;
		}
	}

	/** An ability the document defines but leaves essential parts TBD; always shows "Not finalized". */
	static final class NotFinalized extends Ability {
		private final List<String> missing;

		NotFinalized(AbilitySlot slot, String name, String... missing) {
			super(W, slot, name);
			this.missing = List.of(missing);
		}

		@Override
		public double baseCost(Player p) {
			return -1;
		}

		@Override
		public List<String> missingValues() {
			return missing;
		}
	}

	// ------------------------------------------------------------------ visuals

	private static void drawLine(ServerPlayer from, ServerPlayer to) {
		Vec3 a = from.getEyePosition();
		Vec3 b = to.position().add(0, to.getBbHeight() / 2, 0);
		int steps = (int) Math.min(64, Math.max(4, a.distanceTo(b) * 2));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.lerp(b, i / (double) steps);
			from.serverLevel().sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}
}
