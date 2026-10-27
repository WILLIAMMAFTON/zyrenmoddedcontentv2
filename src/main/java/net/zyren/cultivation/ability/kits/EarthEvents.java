package net.zyren.cultivation.ability.kits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AbilityConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Earth's world interactions: Fortune III drops, wall-impact and stone-contact watches,
 * Powerblast 2 shaking and the delayed stone launch, Resistance XV on stone during Reinforce.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class EarthEvents {
	/** Attack 1: target slammed into a wall within this many ticks of the hit. */
	private static final int IMPACT_WINDOW = 30;

	private record Impact(ServerPlayer attacker, float damage, Vec3 dir, int ticksLeft) {
	}

	private record StoneWatch(int stunTicks, int ticksLeft) {
	}

	private record Shake(int ticksLeft) {
	}

	private record StoneLaunch(ServerPlayer user, Vec3 center, double radius, long fireTick) {
	}

	private static final Map<UUID, Impact> IMPACTS = new HashMap<>();
	private static final Map<UUID, StoneWatch> STONE_WATCH = new HashMap<>();
	private static final Map<UUID, Shake> SHAKES = new HashMap<>();
	private static final List<StoneLaunch> LAUNCHES = new ArrayList<>();

	private EarthEvents() {
	}

	// ------------------------------------------------------------------ passive: Fortune III

	@SubscribeEvent
	public static void onDrops(BlockDropsEvent event) {
		if (!(event.getBreaker() instanceof ServerPlayer player) || player.isCreative())
			return;
		if (AspectData.of(player).getActive() != AspectType.EARTH || !AbilityState.of(player).passiveActive())
			return;
		ItemStack tool = event.getTool();
		if (tool.isEmpty())
			return;
		ServerLevel level = event.getLevel();
		var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> fortune = enchantments.getHolderOrThrow(Enchantments.FORTUNE);
		Holder<Enchantment> silk = enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH);
		int wanted = AbilityConfig.EARTH_PASSIVE_FORTUNE.get();
		if (tool.getEnchantmentLevel(silk) > 0 || tool.getEnchantmentLevel(fortune) >= wanted)
			return;

		ItemStack fortuneTool = tool.copy();
		fortuneTool.enchant(fortune, wanted);
		List<ItemStack> drops = Block.getDrops(event.getState(), level, event.getPos(), event.getBlockEntity(), player, fortuneTool);
		event.getDrops().clear();
		BlockPos pos = event.getPos();
		for (ItemStack stack : drops)
			event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
	}

	// ------------------------------------------------------------------ watches

	static void watchWallImpact(ServerPlayer attacker, LivingEntity target, float damage) {
		Vec3 dir = target.position().subtract(attacker.position()).multiply(1, 0, 1);
		if (dir.lengthSqr() < 1.0E-4)
			dir = attacker.getLookAngle().multiply(1, 0, 1);
		IMPACTS.put(target.getUUID(), new Impact(attacker, damage, dir.normalize(), IMPACT_WINDOW));
	}

	static void watchStoneContact(LivingEntity target, int stunTicks) {
		STONE_WATCH.put(target.getUUID(), new StoneWatch(stunTicks, 40));
	}

	static void shake(LivingEntity target, int ticks) {
		SHAKES.put(target.getUUID(), new Shake(ticks));
	}

	static void scheduleStoneLaunch(ServerPlayer user, double radius, int delayTicks) {
		LAUNCHES.add(new StoneLaunch(user, user.position(), radius, user.serverLevel().getGameTime() + delayTicks));
	}

	/** Blocks that don't count for Attack 1's wall impact (document's exclusion list). */
	private static boolean qualifiesForImpact(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		if (s.isAir() || !s.getFluidState().isEmpty() || s.getCollisionShape(level, pos).isEmpty())
			return false;
		return !(s.is(BlockTags.SAND) || s.is(Blocks.HAY_BLOCK) || s.is(BlockTags.BEDS) || s.is(BlockTags.LEAVES) || s.is(Blocks.LILY_PAD));
	}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof LivingEntity e) || !(e.level() instanceof ServerLevel level))
			return;
		UUID id = e.getUUID();

		Impact impact = IMPACTS.get(id);
		if (impact != null) {
			// Impact = a qualifying solid block right in front of the target, in the knockback direction.
			Vec3 ahead = e.position().add(impact.dir().scale(e.getBbWidth() / 2 + 0.4));
			boolean hit = qualifiesForImpact(level, BlockPos.containing(ahead.x, e.getY() + 0.1, ahead.z))
					|| qualifiesForImpact(level, BlockPos.containing(ahead.x, e.getY() + e.getBbHeight() * 0.8, ahead.z));
			if (hit) {
				IMPACTS.remove(id);
				if (impact.attacker().isAlive() && QiEffects.canAffect(e, false)) {
					QiEffects.dealDamage(impact.attacker(), e, impact.damage());
					Stun.apply(e, AbilityConfig.EARTH_ATTACK1_STUN_TICKS.get());
				}
			} else if (impact.ticksLeft() <= 1) {
				IMPACTS.remove(id);
			} else {
				IMPACTS.put(id, new Impact(impact.attacker(), impact.damage(), impact.dir(), impact.ticksLeft() - 1));
			}
		}

		StoneWatch watch = STONE_WATCH.get(id);
		if (watch != null) {
			boolean touching = EarthKit.onStone(e) || e.horizontalCollision && touchingStoneSideways(level, e);
			if (touching) {
				STONE_WATCH.remove(id);
				Stun.apply(e, watch.stunTicks());
			} else if (watch.ticksLeft() <= 1) {
				STONE_WATCH.remove(id);
			} else {
				STONE_WATCH.put(id, new StoneWatch(watch.stunTicks(), watch.ticksLeft() - 1));
			}
		}

		Shake shake = SHAKES.get(id);
		if (shake != null) {
			if (shake.ticksLeft() <= 0) {
				SHAKES.remove(id);
			} else {
				if (shake.ticksLeft() % 5 == 0) { // a jolt of 1-2 blocks in a random direction
					double angle = e.getRandom().nextDouble() * Math.PI * 2;
					double strength = 0.5 + e.getRandom().nextDouble() * 0.4;
					e.setDeltaMovement(Math.cos(angle) * strength, e.getDeltaMovement().y, Math.sin(angle) * strength);
					e.hurtMarked = true;
				}
				SHAKES.put(id, new Shake(shake.ticksLeft() - 1));
			}
		}
	}

	private static boolean touchingStoneSideways(ServerLevel level, LivingEntity e) {
		AABB box = e.getBoundingBox().inflate(0.1, 0, 0.1);
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ)))
			if (EarthKit.isStone(level.getBlockState(p)))
				return true;
		return false;
	}

	// ------------------------------------------------------------------ per-player ticks

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		long now = player.serverLevel().getGameTime();

		// Reinforce Body: Resistance XV while standing on stone.
		if (now % 10 == 0 && AbilityState.of(player).hasBuff(QiEffects.reinforceId(AspectType.EARTH), now) && EarthKit.onStone(player))
			EffectHelper.apply(player, MobEffects.DAMAGE_RESISTANCE, AbilityConfig.EARTH_REINFORCE_STONE_RESISTANCE.get(), 20, false);

		// Powerblast 2 delayed launches owned by this player.
		LAUNCHES.removeIf(l -> {
			if (l.user() != player || now < l.fireTick())
				return false;
			AABB area = new AABB(l.center(), l.center()).inflate(l.radius());
			for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, area,
					en -> en != player && en.isAlive() && en.distanceToSqr(l.center()) <= l.radius() * l.radius() && EarthKit.onStone(en)
							&& !(en instanceof ServerPlayer sp && Targeting.isAllied(player, sp))))
				if (QiEffects.canAffect(e, false))
					EarthKit.launch(e, AbilityConfig.EARTH_PB2_LAUNCH_HEIGHT.get());
			return true;
		});
		// Drop launches whose owner left.
		LAUNCHES.removeIf(l -> l.user().isRemoved());
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		IMPACTS.clear();
		STONE_WATCH.clear();
		SHAKES.clear();
		LAUNCHES.clear();
	}
}
