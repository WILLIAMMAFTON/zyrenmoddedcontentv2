package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.AspectKit;
import net.zyren.cultivation.ability.core.AspectPassive;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.Motion;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.EarthConfig;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * EARTH — DEFAULT kit (ASPECT GUIDE, user "rec all" 2026-11-05). Replaces the old Earth kit; only its
 * Reinforce Body is kept. Values: earth-server.toml. Terrain changes are permanent.
 *
 *   Passive      3 Qi/min. On stone / dirt: Haste II; mined blocks drop as Fortune II, break as Efficiency III.
 *   Attack 1     fist of stone: 15 Qi, 15 s, lasts 35 s. Bare-fist hits +6 and 3 blocks knockback.
 *   Heavy        sharpened blade: 35 Qi, 75 s. Next 4 sword hits x2.
 *   Powerblast 1 fortress of stone: 60 Qi, 120 s. Nearest unallied player within 30: 2-thick hollow stone shell.
 *   Powerblast 2 stone toss: 80 Qi, 450 s. Nearest unallied player within 25 thrown 10 blocks, then a hollow 5x5x5 gravel ball.
 *   Ability 1    heavens staircases: 120 Qi, 600 s. Unallied within 35 launched 50 up; a stone pillar grows under each.
 *   Ability 2    earth quake: 200 Qi, 850 s. Everyone within 30 (not you / allies): 16 damage, launched, Slowness 10 s; crater.
 */
public final class EarthDefaultKit {
	static final AspectType E = AspectType.EARTH;

	private EarthDefaultKit() {
	}

	static final Passive PASSIVE = new Passive();
	static final FistOfStone FIST = new FistOfStone();
	static final SharpenedBlade BLADE = new SharpenedBlade();

	public static AspectKit create() {
		return new AspectKit(E, PASSIVE, FIST, BLADE, new EarthKit.Reinforce(), new Fortress(), new StoneToss(), new HeavensStaircase(), new Earthquake());
	}

	// ------------------------------------------------------------------ helpers (shared with the sub-aspects)

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

	/** Every living thing around a point except the user and allied players (spectators skipped). */
	static List<LivingEntity> foesAround(ServerPlayer user, net.minecraft.world.phys.Vec3 center, double radius) {
		return user.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
				e -> e != user && e.isAlive() && !e.isSpectator() && e.position().distanceTo(center) <= radius
						&& !(e instanceof ServerPlayer sp && Targeting.isAllied(user, sp)));
	}

	static ActionResult noOne(double range) {
		return ActionResult.fail("No one within " + (int) range + " blocks.");
	}

	// ------------------------------------------------------------------ passive

	static boolean passiveOn(Player p) {
		return AbilityState.of(p).passiveActive() && KitResolver.passiveFor(p) == PASSIVE;
	}

	/** Passive Fortune / Efficiency apply right now (on stone / dirt). */
	static boolean groundBonus(Player p) {
		return passiveOn(p) && EarthWorld.isGround(EarthWorld.below(p));
	}

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer p) {
			return true;
		}

		@Override
		public double upkeepPerTick(ServerPlayer p) {
			return perSeconds(EarthConfig.PASSIVE_QI_PER_MINUTE.get(), 60);
		}

		@Override
		public void refreshEffects(ServerPlayer p) {
			if (!EarthWorld.isGround(EarthWorld.below(p)))
				return;
			EffectHelper.apply(p, MobEffects.DIG_SPEED, EarthConfig.PASSIVE_HASTE.get(), 50, false);
			// "as if the tool had Efficiency III": the mining-efficiency attribute (synced, so the client mines as fast),
			// minus what the held tool's own Efficiency already gives. Fortune: EarthEvents.onDrops.
			Holder<Enchantment> eff = p.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.EFFICIENCY);
			int own = p.getMainHandItem().getEnchantmentLevel(eff), want = EarthConfig.PASSIVE_EFFICIENCY.get();
			double bonus = (want > 0 ? want * want + 1 : 0) - (own > 0 ? own * own + 1 : 0);
			if (bonus > 0)
				TimedAttributes.apply(p, Attributes.MINING_EFFICIENCY, "earth_passive_efficiency", bonus, AttributeModifier.Operation.ADD_VALUE, 30);
			else
				TimedAttributes.remove(p, "earth_passive_efficiency");
		}
	}

	// ------------------------------------------------------------------ Attack 1 / Heavy (hits in EarthKitEvents)

	/** While it runs, bare-fist hits deal extra damage + knockback (EarthKitEvents). */
	static final class FistOfStone extends KitParts.Timed {
		FistOfStone() {
			super(E, AbilitySlot.ATTACK_1, "fist of stone", null, EarthConfig.A1_COST, EarthConfig.A1_COOLDOWN, EarthConfig.A1_SECONDS, false);
		}
	}

	static final class SharpenedBlade extends KitParts.Cfg {
		SharpenedBlade() {
			super(E, AbilitySlot.HEAVY, "sharpened blade", null, EarthConfig.A2_COST, EarthConfig.A2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return EarthKitEvents.BLADE.left(p) > 0 ? ActionResult.fail(name() + " is already active.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			EarthKitEvents.BLADE.start(p, EarthConfig.A2_HITS.get());
			return ActionResult.ok("Sharpened blade: next " + EarthConfig.A2_HITS.get() + " sword hits x" + EarthConfig.A2_MULTIPLIER.get() + ".");
		}
	}

	// ------------------------------------------------------------------ Powerblast 1

	static final class Fortress extends KitParts.Cfg {
		Fortress() {
			super(E, AbilitySlot.POWERBLAST_1, "fortress of stone", null, EarthConfig.PB1_COST, EarthConfig.PB1_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return unalliedWithin(p, EarthConfig.PB1_RANGE.get()).isEmpty() ? noOne(EarthConfig.PB1_RANGE.get()) : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			List<ServerPlayer> near = unalliedWithin(p, EarthConfig.PB1_RANGE.get());
			if (near.isEmpty())
				return ActionResult.fail("Target lost.");
			ServerPlayer t = near.get(0);
			if (!QiEffects.canAffect(t, false))
				return ActionResult.ok(t.getGameProfile().getName() + " is reinforced.");
			BlockPos feet = t.blockPosition();
			EarthWorld.shell(t.serverLevel(), feet, feet.above(), 2, Blocks.STONE.defaultBlockState());
			return ActionResult.ok("Fortress of stone around " + t.getGameProfile().getName() + ".");
		}
	}

	// ------------------------------------------------------------------ Powerblast 2

	static final class StoneToss extends KitParts.Cfg {
		StoneToss() {
			super(E, AbilitySlot.POWERBLAST_2, "stone toss", null, EarthConfig.PB2_COST, EarthConfig.PB2_COOLDOWN, false);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return unalliedWithin(p, EarthConfig.PB2_RANGE.get()).isEmpty() ? noOne(EarthConfig.PB2_RANGE.get()) : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			List<ServerPlayer> near = unalliedWithin(p, EarthConfig.PB2_RANGE.get());
			if (near.isEmpty())
				return ActionResult.fail("Target lost.");
			ServerPlayer t = near.get(0);
			if (!QiEffects.canAffect(t, false))
				return ActionResult.ok(t.getGameProfile().getName() + " is reinforced.");
			Motion.push(t, p.position(), EarthConfig.PB2_THROW.get(), 1.0, 0.5, null);
			EarthWorld.whenLanded(t, 100, e -> {
				if (!(e.level() instanceof ServerLevel level))
					return;
				BlockPos f = e.blockPosition(); // hollow 5x5x5: 3x3x3 inside, feet on the bottom layer of the inside
				EarthWorld.shell(level, f.offset(-1, 0, -1), f.offset(1, 2, 1), 1, Blocks.GRAVEL.defaultBlockState());
			});
			return ActionResult.ok("Stone toss: " + t.getGameProfile().getName() + ".");
		}
	}

	// ------------------------------------------------------------------ Ability 1

	static final class HeavensStaircase extends KitParts.Cfg {
		HeavensStaircase() {
			super(E, AbilitySlot.ABILITY_1, "heavens staircases", null, EarthConfig.AB1_COST, EarthConfig.AB1_COOLDOWN, true);
		}

		@Override
		public ActionResult checkUse(ServerPlayer p) {
			return unalliedWithin(p, EarthConfig.AB1_RADIUS.get()).isEmpty() ? noOne(EarthConfig.AB1_RADIUS.get()) : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			int n = 0;
			for (ServerPlayer t : unalliedWithin(p, EarthConfig.AB1_RADIUS.get())) {
				if (!QiEffects.canAffect(t, false))
					continue;
				Motion.launchUp(t, EarthConfig.AB1_UP.get());
				EarthWorld.pillarUnder(t);
				n++;
			}
			return ActionResult.ok("Heavens staircases: " + n + " launched.");
		}
	}

	// ------------------------------------------------------------------ Ability 2

	static final class Earthquake extends KitParts.Cfg {
		Earthquake() {
			super(E, AbilitySlot.ABILITY_2, "earth quake", null, EarthConfig.AB2_COST, EarthConfig.AB2_COOLDOWN, true);
		}

		@Override
		public ActionResult perform(ServerPlayer p) {
			ServerLevel level = p.serverLevel();
			float dmg = EarthConfig.AB2_DAMAGE.get().floatValue();
			int n = 0;
			for (LivingEntity e : foesAround(p, p.position(), EarthConfig.AB2_RADIUS.get())) {
				QiEffects.dealDamage(p, e, dmg);
				if (QiEffects.canAffect(e, false)) {
					Motion.launchUp(e, EarthConfig.AB2_UP.get());
					EffectHelper.apply(e, MobEffects.MOVEMENT_SLOWDOWN, 1, seconds(EarthConfig.AB2_SLOW_SECONDS.get()), true);
				}
				n++;
			}
			FireEvents.noFallDamage(p);
			EarthWorld.bowl(level, p.getBlockX(), p.getBlockY(), p.getBlockZ(), EarthConfig.AB2_CRATER_RADIUS.get(), EarthConfig.AB2_CRATER_DEPTH.get(), 0, null);
			level.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 3f, 0.5f);
			return ActionResult.ok("Earth quake! " + n + " hit.");
		}
	}
}
