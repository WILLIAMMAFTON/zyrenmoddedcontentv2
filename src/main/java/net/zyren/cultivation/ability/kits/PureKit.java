package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.CoreBreak;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PURE (user's Pure list, 2026-10-06). Abilities unlock with Pure level; locked ones show as "???".
 *
 *   Passive  every level 0.5 Qi/min. L2+: Pure ability costs x0.5. L3: +10 Qi / 2 min.
 *            L4: +30 Qi/min, 50% less incoming damage. L5: +10 Qi/min; when hit by a Qi-enhanced
 *            attack, 50% of the Qi the attacker spent empowers your next Qi-enhanced attack.
 *   L0 Attack 1      EEEEEEEE: 10 Qi, next hit = 50 damage + Knockback 34 + Nausea 30 s.
 *   L1 Heavy         BURNN: 20 Qi, teleport through flames to the nearest player (32 across / 30 up-down);
 *                    Fire Resistance 30 s; your next hit on them deals 60.
 *   L2 Reinforce     Protect Ye: 30 Qi, standard; nearby nullified allies are un-nullified + Regeneration XV;
 *                    allies' harmful effects removed; Strength for allies who aren't Physical.
 *   L3 Powerblast 1  Thunder: 30 Qi, lightning strikes you, total output x3; nearest player struck for 3x.
 *   L3 Powerblast 2  I Taketh: 40 Qi, next hit = 40 damage; an aspect user hit (not Reinforced) can't
 *                    use Qi for clamp(30 + highest level x 10, 40, 100) s.
 *   L4 Ability 1     My Anime: 50 Qi, 300-block Qi beam: 200 damage + Knockback 15 to every player in line.
 *   L4 Ability 2     My Turf: 100 Qi, everything non-allied within 50 dies (only a Reinforced Corrupted
 *                    core survives); 50/300 chance your Core breaks while core level <= 2.
 */
public final class PureKit {
	static final AspectType P = AspectType.PURE;

	private PureKit() {
	}

	public static AspectKit create() {
		return new AspectKit(P, new Passive(),
				new Eeeee(), new Burnn(), new ProtectYe(), new Thunder(), new ITaketh(), new MyAnime(), new MyTurf());
	}

	static int pureLevel(Player p) {
		return AspectData.of(p).getPureLevel();
	}

	/** Level 2+: Pure ability costs are reduced. Works on both sides (radial shows the real cost). */
	static double cost(Player p, double base) {
		return base > 0 && pureLevel(p) >= 2 ? base * AbilityConfig.PURE_COST_MULTIPLIER.get() : base;
	}

	/** Highest of core level, Pure level and slot aspect levels. */
	static int highestLevel(ServerPlayer p) {
		AspectData a = AspectData.of(p);
		int max = Math.max(QiData.of(p).getCoreLevel(), a.getPureLevel());
		for (int s = 1; s <= AspectData.SLOT_COUNT; s++)
			max = Math.max(max, a.getSlotLevel(s));
		return max;
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			return perSeconds(AbilityConfig.PURE_PASSIVE_QI_PER_MIN.get(), 60);
		}

		/** Runs once a second while active: the level 3+ Qi intake. */
		@Override
		public void refreshEffects(ServerPlayer player) {
			int lvl = pureLevel(player);
			double perMin = lvl >= 5 ? AbilityConfig.PURE_L5_GAIN_PER_MIN.get()
					: lvl == 4 ? AbilityConfig.PURE_L4_GAIN_PER_MIN.get()
					: lvl == 3 ? AbilityConfig.PURE_L3_GAIN_PER_MIN.get() : 0;
			if (perMin > 0)
				QiCoreStorage.addQi(player, perMin / 60.0);
		}

		@Override
		public double incomingReduction(ServerPlayer player, DamageSource source) {
			int lvl = pureLevel(player);
			if (lvl == 4 || lvl >= 5 && AbilityConfig.PURE_L5_KEEPS_REDUCTION.get())
				return AbilityConfig.PURE_L4_REDUCTION.get();
			return 0;
		}
	}

	// ------------------------------------------------------------------ abilities

	/** Base for Pure abilities: level lock + level 2 cost cut. */
	abstract static class PureAbility extends Ability {
		PureAbility(AbilitySlot slot, String name) {
			super(P, slot, name);
		}

		abstract double configCost();

		@Override
		public double baseCost(Player p) {
			return cost(p, configCost());
		}
	}

	static final class Eeeee extends PureAbility {
		Eeeee() {
			super(AbilitySlot.ATTACK_1, "EEEEEEEE");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		double configCost() {
			return AbilityConfig.PURE_ATTACK_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PURE_ATTACK_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("EEEEEEEE! Your next hit is charged.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (QiEffects.canAffect(target, false)) {
				QiEffects.knockback(target, attacker, AbilityConfig.PURE_ATTACK_KNOCKBACK.get(), false);
				EffectHelper.apply(target, MobEffects.CONFUSION, 1, AbilityConfig.PURE_ATTACK_NAUSEA_TICKS.get(), true);
			}
			return AbilityConfig.PURE_ATTACK_DAMAGE.get().floatValue();
		}
	}

	static final class Burnn extends PureAbility {
		private final Map<UUID, ServerPlayer> found = new HashMap<>();

		Burnn() {
			super(AbilitySlot.HEAVY, "BURNN");
		}

		@Override
		public int requiredLevel() {
			return AbilityConfig.PURE_LEVEL_HEAVY.get();
		}

		@Override
		double configCost() {
			return AbilityConfig.PURE_HEAVY_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PURE_HEAVY_COOLDOWN.get();
		}

		private static ServerPlayer target(ServerPlayer user) {
			double r = AbilityConfig.PURE_HEAVY_RADIUS.get(), v = AbilityConfig.PURE_HEAVY_VERTICAL.get();
			return user.serverLevel().players().stream()
					.filter(p -> Targeting.isValid(user, p) && Math.abs(p.getY() - user.getY()) <= v
							&& Math.hypot(p.getX() - user.getX(), p.getZ() - user.getZ()) <= r)
					.min(Comparator.comparingDouble(p -> p.distanceToSqr(user)))
					.orElse(null);
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			ServerPlayer t = target(player);
			if (t == null) {
				found.remove(player.getUUID());
				return ActionResult.fail("No player close enough to burn through to.");
			}
			found.put(player.getUUID(), t);
			return ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer t = found.remove(player.getUUID());
			if (t == null || !Targeting.isValid(player, t))
				return ActionResult.fail("Target lost.");
			ServerLevel level = player.serverLevel();
			level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 60, 0.4, 0.8, 0.4, 0.03);
			BlockPos dest = spotNear(level, t.blockPosition());
			if (dest != null && level.getBlockState(dest).isAir())
				level.setBlock(dest, BaseFireBlock.getState(level, dest), 3); // the flame you step out of
			Vec3 to = dest != null ? Vec3.atBottomCenterOf(dest) : t.position();
			EffectHelper.apply(player, MobEffects.FIRE_RESISTANCE, 1, AbilityConfig.PURE_HEAVY_FIRE_RES_TICKS.get(), true);
			player.teleportTo(to.x, to.y, to.z);
			player.fallDistance = 0;
			level.sendParticles(ParticleTypes.FLAME, to.x, to.y + 1, to.z, 60, 0.4, 0.8, 0.4, 0.03);
			PureEvents.markBurnTarget(player, t, AbilityConfig.PURE_HEAVY_HIT_WINDOW.get());
			return ActionResult.ok("BURNN! Hit " + t.getName().getString() + " for 60.");
		}

		/** A free standing spot 1-2 blocks from the target. */
		private static BlockPos spotNear(ServerLevel level, BlockPos center) {
			for (int r = 1; r <= 2; r++)
				for (int dx = -r; dx <= r; dx++)
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
							continue;
						for (int dy = 1; dy >= -2; dy--) {
							BlockPos p = center.offset(dx, dy, dz);
							BlockPos below = p.below();
							if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
									&& level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getFluidState(p).isEmpty()
									&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty())
								return p;
						}
					}
			return null;
		}
	}

	static final class ProtectYe extends PureAbility {
		ProtectYe() {
			super(AbilitySlot.REINFORCE, "Protect Ye");
		}

		@Override
		public int requiredLevel() {
			return AbilityConfig.PURE_LEVEL_REINFORCE.get();
		}

		@Override
		double configCost() {
			return AbilityConfig.PURE_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PURE_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.PURE_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			QiEffects.applyReinforce(player, P, AbilityConfig.PURE_REINFORCE_DURATION.get());
			long now = player.serverLevel().getGameTime();
			double r = AbilityConfig.PURE_REINFORCE_RADIUS.get();
			int ticks = AbilityConfig.PURE_REINFORCE_EFFECT_TICKS.get();
			int helped = 0;
			for (ServerPlayer ally : player.serverLevel().players()) {
				if (ally == player || !Targeting.isValid(player, ally) || !Targeting.isAllied(player, ally) || ally.distanceTo(player) > r)
					continue;
				helped++;
				AbilityState st = AbilityState.of(ally);
				if (st.isNullifiedAny(now)) {
					st.clearNullify(now);
					ally.displayClientMessage(net.minecraft.network.chat.Component.literal("Your nullification was lifted by "
							+ player.getName().getString() + ".").withStyle(net.minecraft.ChatFormatting.GREEN), false);
					EffectHelper.apply(ally, MobEffects.REGENERATION, AbilityConfig.PURE_REINFORCE_REGEN.get(), ticks, true);
				}
				List<MobEffectInstance> bad = new ArrayList<>();
				for (MobEffectInstance i : ally.getActiveEffects())
					if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
						bad.add(i);
				bad.forEach(i -> ally.removeEffect(i.getEffect()));
				if (AspectData.of(ally).getActive() != AspectType.PHYSICAL)
					EffectHelper.apply(ally, MobEffects.DAMAGE_BOOST, AbilityConfig.PURE_REINFORCE_STRENGTH.get(), ticks, true);
			}
			return ActionResult.ok("Body reinforced" + (helped > 0 ? "; " + helped + " all" + (helped == 1 ? "y" : "ies") + " protected." : "."));
		}
	}

	static final class Thunder extends PureAbility {
		Thunder() {
			super(AbilitySlot.POWERBLAST_1, "Thunder");
		}

		@Override
		public int requiredLevel() {
			return AbilityConfig.PURE_LEVEL_POWERBLAST.get();
		}

		@Override
		double configCost() {
			return AbilityConfig.PURE_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PURE_PB1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			long now = player.serverLevel().getGameTime();
			LightningEvents.strikeVisual(player.serverLevel(), player.position());
			// Strike BEFORE adding the x3 buff, or the strike was tripled again (9x instead of 3x).
			ServerPlayer target = Targeting.nearestPlayer(player);
			if (target != null)
				LightningEvents.strike(player, target, LightningKit.lightningDamage() * AbilityConfig.PURE_PB1_STRIKE_MULTIPLIER.get());
			AbilityState.of(player).addBuff(new Buff("pure_thunder", P, AbilityConfig.PURE_PB1_OUTPUT.get(), 0, false,
					now + AbilityConfig.PURE_PB1_DURATION.get()));
			return ActionResult.ok("Thunder! Your output is tripled.");
		}
	}

	static final class ITaketh extends PureAbility {
		ITaketh() {
			super(AbilitySlot.POWERBLAST_2, "I Taketh");
		}

		@Override
		public Kind kind() {
			return Kind.EMPOWER;
		}

		@Override
		public int requiredLevel() {
			return AbilityConfig.PURE_LEVEL_POWERBLAST.get();
		}

		@Override
		double configCost() {
			return AbilityConfig.PURE_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PURE_PB2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("I Taketh: your next hit steals their Qi.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			if (target instanceof ServerPlayer victim && AspectData.of(victim).getActive() != null && QiEffects.canAffect(victim, false)) {
				int lvl = highestLevel(victim);
				int seconds = Math.max(AbilityConfig.PURE_PB2_MIN_SECONDS.get(), Math.min(AbilityConfig.PURE_PB2_MAX_SECONDS.get(),
						AbilityConfig.PURE_PB2_BASE_SECONDS.get() + lvl * AbilityConfig.PURE_PB2_PER_LEVEL_SECONDS.get()));
				QiEffects.nullifyAll(victim, seconds * 20);
			}
			return AbilityConfig.PURE_PB2_DAMAGE.get().floatValue();
		}
	}

	static final class MyAnime extends PureAbility {
		MyAnime() {
			super(AbilitySlot.ABILITY_1, "My Anime");
		}

		@Override
		public int requiredLevel() {
			return AbilityConfig.PURE_LEVEL_ABILITIES.get();
		}

		@Override
		double configCost() {
			return AbilityConfig.PURE_A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PURE_A1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerLevel level = player.serverLevel();
			Vec3 eye = player.getEyePosition();
			Vec3 end = eye.add(player.getLookAngle().scale(AbilityConfig.PURE_A1_RANGE.get()));
			end = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();

			Vec3 step = end.subtract(eye);
			double len = step.length();
			for (double d = 1; d < len; d += 1.0) {
				Vec3 p = eye.add(step.scale(d / len));
				level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
			}
			int hit = 0;
			for (ServerPlayer t : level.players()) {
				if (!Targeting.isValid(player, t) || Targeting.isAllied(player, t))
					continue;
				if (t.getBoundingBox().inflate(0.3).clip(eye, end).isEmpty())
					continue;
				QiEffects.dealDamage(player, t, AbilityConfig.PURE_A1_DAMAGE.get().floatValue());
				QiEffects.knockback(t, player, AbilityConfig.PURE_A1_KNOCKBACK.get(), false);
				hit++;
			}
			return ActionResult.ok(hit == 0 ? "The beam hits nothing." : "My Anime hit " + hit + (hit == 1 ? " player!" : " players!"));
		}
	}

	static final class MyTurf extends PureAbility {
		MyTurf() {
			super(AbilitySlot.ABILITY_2, "My Turf");
		}

		@Override
		public int requiredLevel() {
			return AbilityConfig.PURE_LEVEL_ABILITIES.get();
		}

		@Override
		double configCost() {
			return AbilityConfig.PURE_A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.PURE_A2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerLevel level = player.serverLevel();
			double r = AbilityConfig.PURE_A2_RADIUS.get();
			AABB box = player.getBoundingBox().inflate(r);
			List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive()
					&& !(e instanceof ArmorStand) && e.distanceTo(player) <= r && !protectedFrom(player, e));
			for (LivingEntity e : victims)
				e.kill();
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 300, r / 4, 2, r / 4, 0.2);

			String msg = "My Turf: " + victims.size() + " fell.";
			if (QiData.of(player).getCoreLevel() <= AbilityConfig.PURE_A2_MAX_RISKY_CORE_LEVEL.get()
					&& player.getRandom().nextDouble() < AbilityConfig.PURE_A2_BREAK_CHANCE.get()
					&& net.zyren.cultivation.system.EmergencyCore.allowed(player)) { // core breaking respects the toggle
				if (CoreBreak.breakCore(player, "My Turf was too much for your Core"))
					msg += " Your Core broke.";
			}
			return ActionResult.ok(msg);
		}

		/** Allies, your own pets, creative/spectator players, and Reinforced Corrupted cores survive. */
		private static boolean protectedFrom(ServerPlayer user, LivingEntity e) {
			if (user.isAlliedTo(e))
				return true;
			if (e instanceof OwnableEntity pet && user.getUUID().equals(pet.getOwnerUUID()))
				return true;
			if (e instanceof ServerPlayer p) {
				if (p.isCreative() || p.isSpectator() || Targeting.isAllied(user, p))
					return true;
				return QiData.of(p).getCoreType() == CoreType.CORRUPTED && QiEffects.isReinforced(p);
			}
			return false;
		}
	}
}
