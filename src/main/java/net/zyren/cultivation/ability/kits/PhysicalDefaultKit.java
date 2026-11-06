package net.zyren.cultivation.ability.kits;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.CastRules;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.PhysicalConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PHYSICAL — DEFAULT kit (ASPECT GUIDE, user "rec" 2026-11-06). Replaces the old Physical kit; only
 * its Reinforce Body is kept. Values: physical-server.toml. Event side: PhysicalKitEvents.
 *
 *   Passive      3 Qi / 5 min. Strength I, Haste V ("attack speed 5"), Speed I.
 *   Attack 1     EAT MY FIST: 15 Qi, 120 s, 60 s. Each landed melee hit adds +2 to the next; reset when hurt.
 *   Heavy        JUMPIN BLOW: 31 Qi, 120 s. Up 8; then at an unallied player in sight; 12 on contact.
 *   Powerblast 1 DASH: 20 Qi, 120 s. 8 blocks where you move; stops 1 block before an unallied player.
 *   Powerblast 2 DEFLECTING BLOW: 84 Qi, 380 s, 60 s or 2 deflections; 50% sent back.
 *   Ability 1    EHANCED REFLEXES: 150 Qi, 680 s, 60 s. Passive off; Strength II, Haste X, Speed II; DASH + Reinforce free.
 *   Ability 2    my fist, ur fase: 200 Qi, 800 s. Teleport to a player in sight; next hit: thrown 14, 12 through Reinforce, locks.
 */
public final class PhysicalDefaultKit {
	static final AspectType P = AspectType.PHYSICAL;

	private PhysicalDefaultKit() {
	}

	static final Passive PASSIVE = new Passive();
	static final EatMyFist EAT_MY_FIST = new EatMyFist();
	static final JumpinBlow JUMPIN_BLOW = new JumpinBlow();
	static final Dash DASH = new Dash();
	static final DeflectingBlow DEFLECT = new DeflectingBlow();
	static final EnhancedReflexes REFLEXES = new EnhancedReflexes();
	static final MyFist MY_FIST = new MyFist();

	public static AspectKit create() {
		CastRules.register(PhysicalKitEvents.RULES);
		return new AspectKit(P, PASSIVE, EAT_MY_FIST, JUMPIN_BLOW, new PhysicalKit.Reinforce(), DASH, DEFLECT, REFLEXES, MY_FIST);
	}

	// ================================================================== helpers (shared with the sub-aspects)

	static int seconds(int s) {
		return s * 20;
	}

	static boolean unarmed(Player p) {
		return p.getMainHandItem().isEmpty();
	}

	/** Unallied players within the radius, nearest first. */
	static List<ServerPlayer> unalliedWithin(ServerPlayer user, double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer o : user.serverLevel().players())
			if (Targeting.isValid(user, o) && !Targeting.isAllied(user, o) && o.distanceTo(user) <= radius)
				out.add(o);
		out.sort(Comparator.comparingDouble(o -> o.distanceToSqr(user)));
		return out;
	}

	/** Looked-at unallied player within range, else the nearest unallied one in sight, else null. */
	static ServerPlayer lookedOrNearestVisible(ServerPlayer p, double range) {
		ServerPlayer looked = Targeting.lookedAtPlayer(p);
		if (looked != null && !Targeting.isAllied(p, looked) && looked.distanceTo(p) <= range)
			return looked;
		for (ServerPlayer o : unalliedWithin(p, range))
			if (p.hasLineOfSight(o))
				return o;
		return null;
	}

	static ActionResult noOne(double range) {
		return ActionResult.fail("No unallied player in sight within " + (int) range + " blocks.");
	}

	static Vec3 horizontal(Vec3 v) {
		Vec3 h = new Vec3(v.x, 0, v.z);
		return h.lengthSqr() < 1.0E-6 ? Vec3.ZERO : h.normalize();
	}

	/** Direction the player was last moving (last second), else where they look; horizontal. */
	static Vec3 moveDirection(ServerPlayer p) {
		Vec3 moved = PhysicalKitEvents.recentMove(p);
		Vec3 d = moved != null ? moved : horizontal(p.getLookAngle());
		return d == Vec3.ZERO ? horizontal(Vec3.directionFromRotation(0, p.getYRot())) : d;
	}

	/**
	 * Moves the player up to {@code blocks} along {@code dir} (teleport, steps of 0.25). Stops at walls
	 * (a 1-block step is climbed), and 1 block before an unallied player if {@code stopAtEnemies}.
	 * @return blocks moved
	 */
	static double slide(ServerPlayer p, Vec3 dir, double blocks, boolean stopAtEnemies) {
		dir = horizontal(dir);
		if (dir == Vec3.ZERO || blocks <= 0)
			return 0;
		ServerLevel level = p.serverLevel();
		List<ServerPlayer> enemies = stopAtEnemies ? unalliedWithin(p, blocks + 3) : List.of();
		Vec3 at = p.position();
		double moved = 0;
		for (double d = 0.25; d <= blocks + 1.0E-6; d += 0.25) {
			Vec3 next = at.add(dir.scale(0.25));
			AABB box = p.getBoundingBox().move(next.subtract(p.position()));
			if (!level.noCollision(p, box)) {
				AABB up = box.move(0, 1, 0);
				if (!level.noCollision(p, up))
					break;
				next = next.add(0, 1, 0);
				box = up;
			}
			boolean blocked = false;
			for (ServerPlayer e : enemies)
				if (e.getBoundingBox().inflate(1.0).intersects(box)) {
					blocked = true;
					break;
				}
			if (blocked)
				break;
			at = next;
			moved = d;
		}
		if (moved > 0) {
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
			p.teleportTo(at.x, at.y, at.z);
			p.fallDistance = 0;
		}
		return moved;
	}

	// ================================================================== passive

	static boolean passiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == PASSIVE;
	}

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return !KitParts.passiveDisabled(p);
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(PhysicalConfig.PASSIVE_QI_PER_5MIN.get(), 300);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, PhysicalConfig.PASSIVE_STRENGTH.get(), 50, false);
			EffectHelper.apply(p, MobEffects.DIG_SPEED, PhysicalConfig.PASSIVE_HASTE.get(), 50, false); // "attack speed" = Haste (project convention)
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, PhysicalConfig.PASSIVE_SPEED.get(), 50, false);
		}
	}

	// ================================================================== Attack 1

	/** Hit combo counted in PhysicalKitEvents. */
	static final class EatMyFist extends KitParts.Timed {
		EatMyFist() {
			super(P, AbilitySlot.ATTACK_1, "EAT MY FIST", null, PhysicalConfig.A1_COST, PhysicalConfig.A1_COOLDOWN, PhysicalConfig.A1_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			PhysicalKitEvents.resetCombo(p);
		}
	}

	// ================================================================== Heavy

	static final class JumpinBlow extends KitParts.Cfg {
		JumpinBlow() {
			super(P, AbilitySlot.HEAVY, "JUMPIN BLOW", null, PhysicalConfig.A2_COST, PhysicalConfig.A2_COOLDOWN, false);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = lookedOrNearestVisible(p, PhysicalConfig.A2_RANGE.get());
			Motion.launchUp(p, PhysicalConfig.A2_UP.get());
			if (t == null)
				return ActionResult.ok("Jumpin blow (no one in sight).");
			PhysicalKitEvents.fly(p, t, 10, 1.5, 0, PhysicalConfig.A2_DAMAGE.get().floatValue(), null);
			return ActionResult.ok("Jumpin blow → " + t.getGameProfile().getName() + ".");
		}
	}

	// ================================================================== Powerblast 1

	static final class Dash extends KitParts.Cfg {
		Dash() {
			super(P, AbilitySlot.POWERBLAST_1, "DASH", null, PhysicalConfig.PB1_COST, PhysicalConfig.PB1_COOLDOWN, false);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			double moved = slide(p, moveDirection(p), PhysicalConfig.PB1_BLOCKS.get(), true);
			return ActionResult.ok(moved > 0 ? "Dash!" : "Dash blocked.");
		}
	}

	// ================================================================== Powerblast 2

	/** Deflections counted in PhysicalKitEvents. */
	static final class DeflectingBlow extends KitParts.Timed {
		DeflectingBlow() {
			super(P, AbilitySlot.POWERBLAST_2, "DEFLECTING BLOW", null, PhysicalConfig.PB2_COST, PhysicalConfig.PB2_COOLDOWN, PhysicalConfig.PB2_SECONDS, false);
		}

		@Override
		void start(ServerPlayer p) {
			PhysicalKitEvents.DEFLECTS.put(p.getUUID(), PhysicalConfig.PB2_DEFLECTS.get());
		}

		@Override
		void end(ServerPlayer p) {
			PhysicalKitEvents.DEFLECTS.remove(p.getUUID());
		}
	}

	// ================================================================== Ability 1

	/** Free DASH / Reinforce: PhysicalKitEvents.RULES. */
	static final class EnhancedReflexes extends KitParts.Timed {
		EnhancedReflexes() {
			super(P, AbilitySlot.ABILITY_1, "EHANCED REFLEXES", null, PhysicalConfig.AB1_COST, PhysicalConfig.AB1_COOLDOWN, PhysicalConfig.AB1_SECONDS, true);
		}

		@Override
		boolean disablesPassive() {
			return true;
		}

		@Override
		void second(ServerPlayer p) {
			EffectHelper.apply(p, MobEffects.DAMAGE_BOOST, PhysicalConfig.AB1_STRENGTH.get(), 30, true);
			EffectHelper.apply(p, MobEffects.DIG_SPEED, PhysicalConfig.AB1_HASTE.get(), 30, true);
			EffectHelper.apply(p, MobEffects.MOVEMENT_SPEED, PhysicalConfig.AB1_SPEED.get(), 30, true);
		}
	}

	// ================================================================== Ability 2

	static final class MyFist extends KitParts.Cfg {
		MyFist() {
			super(P, AbilitySlot.ABILITY_2, "my fist, ur fase", null, PhysicalConfig.AB2_COST, PhysicalConfig.AB2_COOLDOWN, true);
		}

		/** A free spot about 1.2 blocks from the target (towards the user first). */
		static Vec3 spotNear(ServerPlayer p, ServerPlayer t) {
			Vec3 toUser = horizontal(p.position().subtract(t.position()));
			double base = toUser == Vec3.ZERO ? 0 : Math.atan2(toUser.z, toUser.x);
			for (int i = 0; i < 8; i++) {
				double a = base + (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2) * Math.PI / 4;
				Vec3 at = t.position().add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2);
				if (p.serverLevel().noCollision(p, p.getBoundingBox().move(at.subtract(p.position()))))
					return at;
			}
			return null;
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			ServerPlayer t = lookedOrNearestVisible(p, PhysicalConfig.AB2_RANGE.get());
			if (t == null)
				return noOne(PhysicalConfig.AB2_RANGE.get());
			return spotNear(p, t) == null ? ActionResult.fail("No room next to " + t.getGameProfile().getName() + ".") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerPlayer t = lookedOrNearestVisible(p, PhysicalConfig.AB2_RANGE.get());
			Vec3 at = t == null ? null : spotNear(p, t);
			if (at == null)
				return ActionResult.fail("Target lost.");
			p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1, p.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
			p.teleportTo(at.x, at.y, at.z);
			p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
			p.fallDistance = 0;
			PhysicalKitEvents.armMyFist(p, t);
			return ActionResult.ok("my fist, ur fase: hit " + t.getGameProfile().getName() + " within " + PhysicalConfig.AB2_WINDOW_SECONDS.get() + " s!");
		}
	}

	/** Every living thing around a point except the user (allies optional). */
	static List<LivingEntity> around(ServerPlayer user, Vec3 center, double radius, boolean includeAllies) {
		return user.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
				e -> e != user && e.isAlive() && !e.isSpectator() && e.position().distanceTo(center) <= radius
						&& (includeAllies || !(e instanceof ServerPlayer sp && Targeting.isAllied(user, sp))));
	}
}
