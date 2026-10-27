package net.zyren.cultivation.system;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.config.QiGainConfig;
import net.zyren.cultivation.data.QiData;

/** Hooks every Qi gain source into game events. Amounts live in {@link QiGainConfig}. */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class QiGainEvents {
	private QiGainEvents() {
	}

	// ------------------------------------------------------------------ crafting / smelting / brewing / enchanting

	@SubscribeEvent
	public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
		// Net transformations only: un-crafting and re-crafting the same thing don't pay (AntiFarm).
		if (event.getEntity() instanceof ServerPlayer p && !event.getCrafting().isEmpty()
				&& AntiFarm.craftPays(p, event.getCrafting(), event.getInventory()))
			QiGain.grantOncePerAction(p, QiGainConfig.CRAFTED, "crafting");
	}

	/** Furnace / smoker / blast furnace output taken out: food = cooked food, anything else = smelting. */
	@SubscribeEvent
	public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || event.getSmelting().isEmpty())
			return;
		if (event.getSmelting().has(DataComponents.FOOD))
			QiGain.grantOncePerAction(p, QiGainConfig.COOKED_FOOD, "cooking");
		else
			QiGain.grantOncePerAction(p, QiGainConfig.SMELTING, "smelting");
	}

	@SubscribeEvent
	public static void onBrewed(PlayerBrewedPotionEvent event) {
		// Only potions a brew actually produced pay (not a water bottle put in and taken out).
		if (event.getEntity() instanceof ServerPlayer p) {
			if (AntiFarm.consumeBrewedMark(event.getStack()))
				QiGain.grantOncePerAction(p, QiGainConfig.BREWING, "brewing");
			AntiFarm.stripBrewedMarksSoon(p); // shift-click passes a copy: clean the real stacks too
		}
	}

	@SubscribeEvent
	public static void onEnchanted(PlayerEnchantItemEvent event) {
		if (event.getEntity() instanceof ServerPlayer p)
			QiGain.grantOncePerAction(p, QiGainConfig.ENCHANTING, "enchanting");
	}

	// ------------------------------------------------------------------ fishing / animals

	@SubscribeEvent
	public static void onFished(ItemFishedEvent event) {
		if (event.getEntity() instanceof ServerPlayer p)
			QiGain.grant(p, QiGainConfig.FISHING, "fishing");
	}

	@SubscribeEvent
	public static void onTamed(AnimalTameEvent event) {
		if (event.getTamer() instanceof ServerPlayer p)
			QiGain.grant(p, QiGainConfig.TAMING, "taming");
	}

	/** Feeding an animal food it actually eats (it can breed, or it's a baby that grows). */
	@SubscribeEvent
	public static void onFeed(PlayerInteractEvent.EntityInteract event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getTarget() instanceof Animal animal))
			return;
		ItemStack stack = event.getItemStack();
		if (stack.isEmpty() || !animal.isFood(stack))
			return;
		// Vanilla only feeds an adult that is off its breeding cooldown (age 0) and not already in love.
		if (animal.isBaby() || animal.getAge() == 0 && animal.canFallInLove())
			QiGain.grantOncePerAction(p, QiGainConfig.TAMING, "feeding");
	}

	// ------------------------------------------------------------------ eating

	@SubscribeEvent
	public static void onAte(LivingEntityUseItemEvent.Finish event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		ItemStack used = event.getItem();
		if (!used.has(DataComponents.FOOD))
			return;
		double contained = QiGain.containedQi(used);
		if (contained > 0) {
			// Fixed sequence, never left to event-handler order (user decision 2026-10-07):
			// 1) the food's Qi is consumed, which forms the core if the player had none;
			// 2) only then is corrupted Qi applied, to the (possibly newly formed) core.
			QiGain.consumeInfused(p, contained);
			if (Corruption.isCorruptedQiFood(used))
				Corruption.gain(p, contained); // its stored Qi as corruption
		}
		if (used.is(Items.ENCHANTED_GOLDEN_APPLE))
			QiGain.grant(p, QiGainConfig.ENCHANTED_GOLDEN_APPLE, "enchanted golden apple");
		else if (used.is(Items.GOLDEN_APPLE))
			QiGain.grant(p, QiGainConfig.GOLDEN_APPLE, "golden apple");
		else if (used.is(Items.GOLDEN_CARROT))
			QiGain.grant(p, QiGainConfig.GOLDEN_CARROT, "golden carrot");
		else if (used.is(Items.GLISTERING_MELON_SLICE))
			QiGain.grant(p, QiGainConfig.GLISTERING_MELON, "glistering melon");
	}

	// ------------------------------------------------------------------ mining

	/** LOWEST + not cancelled: a break another mod (e.g. a claim) cancels doesn't pay. */
	@SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
	public static void onBreak(BlockEvent.BreakEvent event) {
		if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer p) || p.isCreative())
			return;
		BlockState state = event.getState();
		QiGainConfig.Source src = state.is(Tags.Blocks.ORES_DIAMOND) ? QiGainConfig.ORE_DIAMOND
				: state.is(Tags.Blocks.ORES_GOLD) ? QiGainConfig.ORE_GOLD
				: state.is(Tags.Blocks.ORES_IRON) ? QiGainConfig.ORE_IRON
				: state.is(Tags.Blocks.ORES_REDSTONE) ? QiGainConfig.ORE_REDSTONE
				: state.is(Tags.Blocks.ORES_LAPIS) ? QiGainConfig.ORE_LAPIS : null;
		if (src == null || !p.hasCorrectToolForDrops(state, p.level(), event.getPos()))
			return;
		if (QiGainConfig.ORES_IGNORE_SILK_TOUCH.get()) {
			Holder<Enchantment> silk = p.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SILK_TOUCH);
			if (p.getMainHandItem().getEnchantmentLevel(silk) > 0)
				return;
		}
		QiGain.grant(p, src, "mining");
	}

	// ------------------------------------------------------------------ kills

	private static boolean isBoss(LivingEntity e) {
		EntityType<?> t = e.getType();
		return t == EntityType.WITHER || t == EntityType.ENDER_DRAGON || t == EntityType.WARDEN || t == EntityType.ELDER_GUARDIAN;
	}

	/** Runs before mastery's kill handler so the "used Qi on them" record is still there. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onDeath(LivingDeathEvent event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer killer))
			return;
		LivingEntity victim = event.getEntity();
		if (victim == killer || victim instanceof ArmorStand)
			return;

		if (isBoss(victim)) {
			QiGain.grant(killer, QiGainConfig.BOSS_KILL, "boss kill");
			return;
		}

		// Killing a Qi-infused player (formed core) without using Qi on them
		if (victim instanceof ServerPlayer vp && QiData.of(vp).getCoreLevel() >= 1) {
			boolean usedQi = QiEffects.isDealingQiDamage() || AspectMastery.recentlyHitWithQi(victim, killer);
			// Same victim again: only once they've progressed (higher core level) since the last bonus.
			if (!usedQi && AntiFarm.cultivatorKillPays(killer, vp)) {
				if (QiData.of(killer).getCoreLevel() < 1) {
					double noQi = QiGainConfig.QI_PLAYER_KILL_NO_QI.get();
					if (QiCultivation.addNoQi(killer, noQi))
						killer.displayClientMessage(net.minecraft.network.chat.Component.literal("+" + QiGain.fmt(noQi) + " no_qi (Qi cultivator slain)")
								.withStyle(net.minecraft.ChatFormatting.AQUA), true);
				} else {
					QiGain.grant(killer, QiData.of(vp).getCoreMax(), QiGainConfig.QI_PLAYER_KILL_CULTIVATED.get(), "Qi cultivator slain");
				}
				return;
			}
		}

		// Ability kills belong in the weapon row (qi-gain.md), even with an empty hand.
		boolean unarmed = event.getSource().getDirectEntity() == killer && killer.getMainHandItem().isEmpty() && !QiEffects.isDealingQiDamage();
		QiGain.grant(killer, unarmed ? QiGainConfig.KILL_UNARMED : QiGainConfig.KILL_WEAPON, unarmed ? "unarmed kill" : "kill");
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		QiGain.forget(event.getEntity().getUUID());
	}
}
