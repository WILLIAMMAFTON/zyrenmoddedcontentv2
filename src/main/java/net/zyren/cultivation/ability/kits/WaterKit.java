package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import com.mojang.datafixers.util.Pair;

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
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WATER (source-of-truth document + decisions 2026-10-07).
 *
 *   Passive        2 Qi/min (1 in water/rain): Speed I, Night Vision, Feather Falling I, Water Breathing;
 *                  in water/rain also Regeneration I + Resistance I. Off within 1 block of lava/fire and
 *                  in the Nether/End.
 *   Attack 1       10 Qi: 2x total output for 30 s.
 *   Heavy          30 Qi: next hit gets Knockback XXV (enchantment-style).
 *   Reinforce      20 Qi: standard + immune to fall damage.
 *   Powerblast 1   Water Surge: 30 Qi, 2 min: Speed III, Haste II, 2x output, immune to Fire-aspect powers.
 *   Powerblast 2   Domain Expansion: 40 Qi: one-block-high flood (radius 20, drains after 30 s);
 *                  non-Water, non-allied Qi users within 20 blinded.
 *   Ability 1      Say Hi to the Fishies: 50 Qi: targeted player teleported to the nearest ocean.
 *   Ability 2      Did I Say You Can Be Here?: 100 Qi: launched at the nearest player, 5x output,
 *                  fires within 100 put out, Fire users within 50 nullified (+ Weakness II unless Reinforced).
 */
public final class WaterKit {
	static final AspectType W = AspectType.WATER;

	private WaterKit() {
	}

	public static AspectKit create() {
		return new AspectKit(W, new Passive(),
				new Attack1(), new Heavy(), new Reinforce(), new WaterSurge(), new DomainExpansion(), new Fishies(), new DidISay());
	}

	static boolean inNetherOrEnd(Level level) {
		return level.dimension() == Level.NETHER || level.dimension() == Level.END;
	}

	static boolean nearLavaOrFire(LivingEntity e) {
		return e.level().getBlockStatesIfLoaded(e.getBoundingBox().inflate(1.0))
				.anyMatch(s -> s.is(BlockTags.FIRE) || s.getFluidState().is(FluidTags.LAVA));
	}

	// ------------------------------------------------------------------ passive

	static final class Passive extends AspectPassive {
		@Override
		public boolean conditionMet(ServerPlayer player) {
			return !inNetherOrEnd(player.level()) && !nearLavaOrFire(player);
		}

		@Override
		public double upkeepPerTick(ServerPlayer player) {
			double perMin = player.isInWaterOrRain() ? AbilityConfig.WATER_PASSIVE_QI_PER_MIN_WET.get() : AbilityConfig.WATER_PASSIVE_QI_PER_MIN.get();
			return perSeconds(perMin, 60);
		}

		@Override
		public void refreshEffects(ServerPlayer player) {
			EffectHelper.apply(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.WATER_PASSIVE_SPEED.get(), 50, false);
			EffectHelper.apply(player, MobEffects.NIGHT_VISION, 1, 260, false);
			EffectHelper.apply(player, MobEffects.WATER_BREATHING, 1, 50, false);
			if (player.isInWaterOrRain()) {
				EffectHelper.applyPassive(player, MobEffects.REGENERATION, AbilityConfig.WATER_PASSIVE_WET_REGEN.get());
				EffectHelper.apply(player, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.WATER_PASSIVE_WET_RESISTANCE.get(), 50, false);
			}
		}
	}

	// ------------------------------------------------------------------ abilities

	static final class Attack1 extends Ability {
		Attack1() {
			super(W, AbilitySlot.ATTACK_1, "Attack 1");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WATER_ATTACK1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WATER_ATTACK1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			long now = player.serverLevel().getGameTime();
			AbilityState.of(player).addBuff(new Buff("water_attack1", W, AbilityConfig.WATER_ATTACK1_OUTPUT.get(), 0, false,
					now + AbilityConfig.WATER_ATTACK1_TICKS.get()));
			return ActionResult.ok("Tidal force: double damage for 30 s.");
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
			return AbilityConfig.WATER_HEAVY_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WATER_HEAVY_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			return ActionResult.ok("Wave ready: your next hit has Knockback XXV.");
		}

		@Override
		public float onEmpoweredHit(ServerPlayer attacker, LivingEntity target, float damage) {
			QiEffects.knockback(target, attacker, AbilityConfig.WATER_HEAVY_KNOCKBACK.get(), false);
			return damage;
		}
	}

	static final class Reinforce extends Ability {
		Reinforce() {
			super(W, AbilitySlot.REINFORCE, "Reinforce Body");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WATER_REINFORCE_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WATER_REINFORCE_COOLDOWN.get();
		}

		@Override
		public List<String> missingValues() {
			return unset("duration", AbilityConfig.WATER_REINFORCE_DURATION.get());
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			QiEffects.applyReinforce(player, W, AbilityConfig.WATER_REINFORCE_DURATION.get());
			return ActionResult.ok("Body reinforced. No fall damage.");
		}
	}

	static final class WaterSurge extends Ability {
		WaterSurge() {
			super(W, AbilitySlot.POWERBLAST_1, "Water Surge");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WATER_PB1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WATER_PB1_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			int ticks = AbilityConfig.WATER_PB1_DURATION.get();
			long now = player.serverLevel().getGameTime();
			EffectHelper.apply(player, MobEffects.MOVEMENT_SPEED, AbilityConfig.WATER_PB1_SPEED.get(), ticks, true);
			EffectHelper.apply(player, MobEffects.DIG_SPEED, AbilityConfig.WATER_PB1_HASTE.get(), ticks, true);
			AbilityState.of(player).addBuff(new Buff("water_surge", W, AbilityConfig.WATER_PB1_OUTPUT.get(), 0, false, now + ticks));
			WaterEvents.startSurge(player, ticks);
			return ActionResult.ok("Water Surge! 2 minutes, Fire can't touch you.");
		}
	}

	static final class DomainExpansion extends Ability {
		DomainExpansion() {
			super(W, AbilitySlot.POWERBLAST_2, "Domain Expansion");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WATER_PB2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WATER_PB2_COOLDOWN.get();
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			double r = AbilityConfig.WATER_PB2_RADIUS.get();
			int placed = player.serverLevel().dimensionType().ultraWarm() ? 0 : WaterEvents.flood(player, r);
			int blinded = 0;
			for (ServerPlayer t : player.serverLevel().players()) {
				if (!Targeting.isValid(player, t) || Targeting.isAllied(player, t) || t.distanceTo(player) > r)
					continue;
				if (QiData.of(t).getCoreLevel() < 1 || AspectData.of(t).getActive() == W)
					continue; // only non-Water Qi users
				if (QiEffects.applyEffect(t, MobEffects.BLINDNESS, 1, AbilityConfig.WATER_PB2_BLIND_TICKS.get(), false))
					blinded++;
			}
			return ActionResult.ok("Domain Expansion: " + placed + " blocks flooded, " + blinded + " blinded.");
		}
	}

	static final class Fishies extends Ability {
		private final Map<UUID, ServerPlayer> targets = new HashMap<>();
		private final Map<UUID, BlockPos> oceans = new HashMap<>();

		Fishies() {
			super(W, AbilitySlot.ABILITY_1, "Say Hi to the Fishies");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WATER_A1_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WATER_A1_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			ServerPlayer t = Targeting.defaultTarget(player);
			if (t == null)
				return ActionResult.fail("No target (hit or look at a player).");
			if (!QiEffects.canAffect(t, false))
				return ActionResult.fail(t.getName().getString() + " is Reinforced.");
			ServerLevel level = t.serverLevel();
			if (net.zyren.cultivation.ability.core.SearchThrottle.blocked(player, "fishies"))
				return ActionResult.fail("No ocean nearby. Try again in a few seconds.");
			Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(b -> b.is(BiomeTags.IS_OCEAN), t.blockPosition(),
					AbilityConfig.WATER_A1_SEARCH_RADIUS.get(), 32, 64);
			if (found == null)
				net.zyren.cultivation.ability.core.SearchThrottle.failed(player, "fishies", 100);
			if (found == null)
				return ActionResult.fail("No ocean within " + AbilityConfig.WATER_A1_SEARCH_RADIUS.get() + " blocks.");
			targets.put(player.getUUID(), t);
			oceans.put(player.getUUID(), found.getFirst());
			return ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			ServerPlayer t = targets.remove(player.getUUID());
			BlockPos ocean = oceans.remove(player.getUUID());
			if (t == null || ocean == null || !Targeting.isValid(player, t))
				return ActionResult.fail("Target lost.");
			ServerLevel level = t.serverLevel();
			level.getChunk(ocean.getX() >> 4, ocean.getZ() >> 4); // load it so the surface height is real
			int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, ocean.getX(), ocean.getZ());
			t.teleportTo(ocean.getX() + 0.5, y, ocean.getZ() + 0.5);
			t.fallDistance = 0;
			return ActionResult.ok("Say hi to the fishies, " + t.getName().getString() + "!");
		}
	}

	static final class DidISay extends Ability {
		DidISay() {
			super(W, AbilitySlot.ABILITY_2, "Did I Say You Can Be Here?");
		}

		@Override
		public double baseCost(Player p) {
			return AbilityConfig.WATER_A2_COST.get();
		}

		@Override
		public int cooldownTicks() {
			return AbilityConfig.WATER_A2_COOLDOWN.get();
		}

		@Override
		public ActionResult checkUse(ServerPlayer player) {
			return inNetherOrEnd(player.level()) ? ActionResult.fail("Water has no power here.") : ActionResult.ok("");
		}

		@Override
		public ActionResult perform(ServerPlayer player) {
			long now = player.serverLevel().getGameTime();
			AbilityState.of(player).addBuff(new Buff("water_did_i_say", W, AbilityConfig.WATER_A2_OUTPUT.get(), 0, false,
					now + AbilityConfig.WATER_A2_OUTPUT_TICKS.get()));
			ServerPlayer target = Targeting.nearestPlayer(player);
			if (target != null)
				WaterEvents.launchAt(player, target);
			WaterEvents.extinguish(player, AbilityConfig.WATER_A2_EXTINGUISH_RADIUS.get());

			double r = AbilityConfig.WATER_A2_NULLIFY_RADIUS.get();
			int n = 0;
			for (ServerPlayer t : player.serverLevel().players()) {
				if (!Targeting.isValid(player, t) || Targeting.isAllied(player, t) || t.distanceTo(player) > r
						|| AspectData.of(t).getActive() != AspectType.FIRE)
					continue;
				QiEffects.nullify(t, AspectType.FIRE, AbilityConfig.WATER_A2_NULLIFY_TICKS.get());
				QiEffects.applyEffect(t, MobEffects.WEAKNESS, AbilityConfig.WATER_A2_WEAKNESS.get(), AbilityConfig.WATER_A2_WEAKNESS_TICKS.get(), false);
				n++;
			}
			return ActionResult.ok("Did I say you can be here? 5x damage" + (n > 0 ? "; " + n + " Fire user" + (n == 1 ? "" : "s") + " doused." : "."));
		}
	}
}
