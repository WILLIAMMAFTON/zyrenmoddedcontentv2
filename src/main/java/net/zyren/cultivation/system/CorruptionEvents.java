package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.EffectHelper;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.config.CorruptionConfig;
import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.CorruptionData;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.network.CorruptionStatusMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Corruption sources (areas, Warden, food, potions), Poisoned-core effects (leak, detection,
 * proximity), Purifying Crystal loot/recharge, and Pure/Corrupted/Poisoned combat rules.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class CorruptionEvents {
	/** Pure-core player -> continuous ticks near a Poisoned core. */
	private static final Map<UUID, Integer> NEAR_POISON = new HashMap<>();
	/** Player -> next tick a Poisoned-core warning may show. */
	private static final Map<UUID, Long> WARN_READY = new HashMap<>();

	private CorruptionEvents() {
	}

	// ------------------------------------------------------------------ per tick / per second

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || p.isSpectator())
			return;
		Meditation.tick(p);
		if ((p.serverLevel().getGameTime() + p.getId()) % 20 != 0)
			return;
		perSecondCorruption(p);
		perSecondPoisoned(p);
		sendStatus(p);
	}

	private static void perSecondCorruption(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		CorruptionData c = CorruptionData.of(p);
		boolean inEnd = level.dimension() == Level.END;
		boolean nearSculk = nearSculk(level, p.blockPosition());
		c.setEndTicks(inEnd ? c.getEndTicks() + 20 : 0); // continuous time; leaving resets it
		c.setSculkTicks(nearSculk ? c.getSculkTicks() + 20 : 0);
		if (!Corruption.canCorrupt(p))
			return;

		// Past the point of no return: rises on its own, can't be stopped or reduced.
		if (Corruption.pastPointOfNoReturn(p))
			Corruption.add(p, CorruptionConfig.PAST_POINT_RATE.get() / 60.0);

		double rate = 0; // highest applicable area rate only
		if (inStructure(level, p.blockPosition(), true))
			rate = Math.max(rate, CorruptionConfig.RATE_ANCIENT_CITY.get());
		if (inEnd && inStructure(level, p.blockPosition(), false))
			rate = Math.max(rate, CorruptionConfig.RATE_END_CITY.get());
		if (nearSculk && c.getSculkTicks() >= CorruptionConfig.SCULK_DELAY_TICKS.get())
			rate = Math.max(rate, CorruptionConfig.RATE_SCULK.get());
		if (inEnd && c.getEndTicks() >= CorruptionConfig.END_DELAY_TICKS.get())
			rate = Math.max(rate, CorruptionConfig.RATE_END.get());
		if (rate > 0)
			Corruption.gain(p, rate / 60.0 * Meditation.multiplier(p));
	}

	private static boolean inStructure(ServerLevel level, BlockPos pos, boolean ancientCity) {
		var key = ancientCity ? BuiltinStructures.ANCIENT_CITY : BuiltinStructures.END_CITY;
		return level.structureManager().getStructureWithPieceAt(pos, h -> h.is(key)).isValid();
	}

	private static boolean nearSculk(ServerLevel level, BlockPos center) {
		int r = CorruptionConfig.SCULK_RADIUS.get(), need = CorruptionConfig.SCULK_MIN_BLOCKS.get(), found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
			BlockState s = level.getBlockState(pos);
			if (s.is(Blocks.SCULK) || s.is(Blocks.SCULK_VEIN) || s.is(Blocks.SCULK_CATALYST) || s.is(Blocks.SCULK_SHRIEKER)
					|| s.is(Blocks.SCULK_SENSOR) || s.is(Blocks.CALIBRATED_SCULK_SENSOR))
				if (++found >= need)
					return true;
		}
		return false;
	}

	/** Poisoned cores leak Qi; nearby players get warnings / HUD entries / Nausea + Poison. */
	private static void perSecondPoisoned(ServerPlayer p) {
		QiData q = QiData.of(p);
		if (q.getCoreLevel() >= 1 && q.getCoreType() == CoreType.POISONED) {
			double leak = CorruptionConfig.POISONED_LEAK_PER_MIN.get() / 60.0;
			q.setCoreAmount(Math.max(0, q.getCoreAmount() - leak));
			return;
		}
		List<ServerPlayer> poisoned = new ArrayList<>();
		for (ServerPlayer o : p.serverLevel().players())
			if (o != p && !o.isSpectator() && o.isAlive() && QiData.of(o).getCoreLevel() >= 1 && QiData.of(o).getCoreType() == CoreType.POISONED)
				poisoned.add(o);
		long now = p.serverLevel().getGameTime();

		// Core level 5+: warning when a Poisoned core is nearby
		if (q.getCoreLevel() >= CorruptionConfig.DETECT_LEVEL.get() && now >= WARN_READY.getOrDefault(p.getUUID(), 0L)) {
			int range = CorruptionConfig.DETECT_RANGE.get();
			if (poisoned.stream().anyMatch(o -> o.distanceTo(p) <= range)) {
				p.displayClientMessage(Component.literal("☠ You sense a Poisoned Core nearby.").withStyle(ChatFormatting.DARK_GREEN), false);
				WARN_READY.put(p.getUUID(), now + CorruptionConfig.DETECT_COOLDOWN_TICKS.get());
			}
		}

		// Pure cores near a Poisoned core for long enough: Nausea + Poison
		if (q.getCoreLevel() >= 1 && q.getCoreType() == CoreType.PURE) {
			int range = CorruptionConfig.PROXIMITY_RANGE.get();
			boolean near = poisoned.stream().anyMatch(o -> o.distanceTo(p) <= range);
			int t = near ? NEAR_POISON.getOrDefault(p.getUUID(), 0) + 20 : 0;
			NEAR_POISON.put(p.getUUID(), t);
			if (near && t >= CorruptionConfig.PROXIMITY_TICKS.get()) {
				int ticks = CorruptionConfig.PROXIMITY_EFFECT_TICKS.get();
				EffectHelper.apply(p, MobEffects.CONFUSION, 1, ticks, true);
				EffectHelper.apply(p, MobEffects.POISON, 1, ticks, true);
				CultivationAdvancements.award(p, CultivationAdvancements.NUCLEAR_WASTE);
			}
		} else {
			NEAR_POISON.remove(p.getUUID());
		}
	}

	/** HUD data: meditation timers and (core level 20+) nearby Poisoned-core players. */
	private static void sendStatus(ServerPlayer p) {
		List<String> seen = new ArrayList<>();
		if (QiData.of(p).getCoreLevel() >= CorruptionConfig.HUD_LEVEL.get()) {
			int range = CorruptionConfig.HUD_RANGE.get();
			for (ServerPlayer o : p.serverLevel().players())
				if (o != p && !o.isSpectator() && QiData.of(o).getCoreLevel() >= 1 && QiData.of(o).getCoreType() == CoreType.POISONED
						&& o.distanceTo(p) <= range)
					seen.add(o.getName().getString() + " (" + (int) o.distanceTo(p) + "m)");
		}
		CorruptionStatusMessage.send(p, Meditation.ticksLeft(p), Meditation.afterTicksLeft(p), seen);
	}

	// ------------------------------------------------------------------ sources

	@SubscribeEvent
	public static void onHurt(LivingDamageEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer p && event.getSource().getEntity() != null
				&& event.getSource().getEntity().getType() == EntityType.WARDEN)
			Corruption.gain(p, CorruptionConfig.WARDEN_HIT.get());
	}

	@SubscribeEvent
	public static void onConsumed(LivingEntityUseItemEvent.Finish event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		ItemStack used = event.getItem();
		if (used.is(Items.CHORUS_FRUIT))
			Corruption.gain(p, CorruptionConfig.CHORUS_FRUIT.get());
		// Corrupted-Qi food is handled in QiGainEvents.onAte, in a fixed order (core forms first).
		if (Corruption.isPotion(used, CorruptionConfig.RESISTANCE_POTION.get()))
			Corruption.grantResistance(p);
		if (Corruption.isPotion(used, CorruptionConfig.POISONED_POTION.get()))
			Corruption.drinkPoisonedQi(p);
	}

	/** Right-clicking a Purifying Crystal channels Qi into it. */
	@SubscribeEvent
	public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || !Corruption.isCrystal(event.getItemStack()))
			return;
		ActionResult r = Corruption.rechargeCrystal(p, event.getItemStack());
		p.displayClientMessage(Component.literal(r.message()).withStyle(r.success() ? ChatFormatting.AQUA : ChatFormatting.RED), true);
	}

	/** Purifying Crystals appear only in End City and Ancient City chests. */
	@SubscribeEvent
	public static void onLootLoad(LootTableLoadEvent event) {
		String name = event.getName().toString();
		if (!name.equals("minecraft:chests/end_city_treasure") && !name.equals("minecraft:chests/ancient_city"))
			return;
		Item crystal = BuiltInRegistries.ITEM.get(ResourceLocation.parse(net.zyren.cultivation.config.LootConfig.CRYSTAL_ITEM.get()));
		if (crystal == Items.AIR)
			return;
		event.getTable().addPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
				.add(LootItem.lootTableItem(crystal))
				.when(LootItemRandomChanceCondition.randomChance(net.zyren.cultivation.config.LootConfig.CRYSTAL_LOOT_CHANCE.get().floatValue()))
				.build());
	}

	// ------------------------------------------------------------------ combat rules

	private static boolean isQiHit(DamageSource s, ServerPlayer attacker, net.minecraft.world.entity.LivingEntity target, long now, AspectType aspect) {
		if (s.is(DamageTypes.THORNS))
			return false; // thorns reflected during a Qi hit are not themselves a Qi hit
		if (QiEffects.isDealingQiDamage())
			return true;
		var armed = AbilityState.of(attacker).empoweredFor(target, now);
		return s.is(DamageTypes.PLAYER_ATTACK) && s.getDirectEntity() == attacker && armed != null && (aspect == null || armed.aspect() == aspect);
	}

	/**
	 * Runs at NORMAL: after the aspect handlers at HIGH (e.g. Pure sets absolute follow-up damage)
	 * and before the main damage pipeline at LOW (which multiplies further). Both used to be HIGH,
	 * so the result depended on class-registration order.
	 *  - Corrupted Qi deals 2x.
	 *  - Pure Qi vs a Corrupted core: 5x, and 50% chance to cure it.
	 *  - Pure Qi vs a Poisoned core: 70% chance it is cured or turns Corrupted.
	 */
	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onDamage(LivingDamageEvent.Pre event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity())
			return;
		long now = attacker.serverLevel().getGameTime();
		QiData aq = QiData.of(attacker);

		if (aq.getCoreType() == CoreType.CORRUPTED && isQiHit(event.getSource(), attacker, event.getEntity(), now, null))
			event.setNewDamage((float) (event.getNewDamage() * CultivationConfig.CORRUPTED_DAMAGE_MULTIPLIER.get()));

		if (!(event.getEntity() instanceof ServerPlayer victim) || AspectData.of(attacker).getActive() != AspectType.PURE
				|| !isQiHit(event.getSource(), attacker, event.getEntity(), now, AspectType.PURE))
			return;
		QiData vq = QiData.of(victim);
		if (vq.getCoreLevel() < 1)
			return;
		if (vq.getCoreType() == CoreType.CORRUPTED) {
			event.setNewDamage((float) (event.getNewDamage() * CultivationConfig.PURE_VS_CORRUPTED_DAMAGE_MULTIPLIER.get()));
			if (attacker.getRandom().nextDouble() < CultivationConfig.PURE_CORRUPTION_CURE_CHANCE.get())
				Corruption.setCoreType(victim, CoreType.PURE, "Pure Qi burns the corruption out of your Core. It is PURE again.", ChatFormatting.AQUA);
		} else if (vq.getCoreType() == CoreType.POISONED && attacker.getRandom().nextDouble() < CorruptionConfig.PURE_VS_POISONED_CHANCE.get()) {
			if (attacker.getRandom().nextDouble() < CorruptionConfig.PURE_VS_POISONED_CURE_SHARE.get())
				Corruption.setCoreType(victim, CoreType.PURE, "Pure Qi purges the poison. Your Core is PURE again.", ChatFormatting.AQUA);
			else
				Corruption.setCoreType(victim, CoreType.CORRUPTED, "Pure Qi clashes with the poison... your Core turns CORRUPTED.", ChatFormatting.DARK_PURPLE);
		}
	}

	// ------------------------------------------------------------------ cleanup

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		UUID id = event.getEntity().getUUID();
		Meditation.forget(id);
		NEAR_POISON.remove(id);
		WARN_READY.remove(id);
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		NEAR_POISON.clear();
		WARN_READY.clear();
	}
}
