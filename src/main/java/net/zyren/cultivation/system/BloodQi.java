package net.zyren.cultivation.system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;

/**
 * BLOOD core type (claude/core-types.md, decided 2026-11-06).
 *
 *  - Normal activity sources give no usable Qi, like Soul (CoreTypes.cutsNormalUsableQi); their
 *    cultivated / no_qi still counts. Qi-infused food and the Qi-player kill reward still pay in full.
 *  - Every kill pays a Blood reward ON TOP of that (usable | cultivated):
 *    player 200|100, villager 100|40, hostile 90|45, passive 75|55, boss 500|300.
 *  - Melee kills multiply the Blood reward: sword x1.5, axe x1.3, unarmed x1. Other held items,
 *    projectiles and ability kills: x1. No anti-farm limit (decided).
 *  - Paid like any gain: before the core forms the cultivated part goes to no_qi; usable that
 *    doesn't fit becomes cultivated (normal overflow rule).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class BloodQi {
	private BloodQi() {
	}

	public static boolean isBlood(Player player) {
		return QiData.of(player).getCoreType() == CoreType.BLOOD;
	}

	/** HIGH, like the normal kill reward: "dealing Qi damage" is still known here. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onDeath(LivingDeathEvent event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || !isBlood(killer))
			return;
		LivingEntity victim = event.getEntity();
		if (victim == killer || victim instanceof ArmorStand)
			return;

		double qi, cult;
		if (QiGainEvents.isBoss(victim)) {
			qi = CultivationConfig.BLOOD_BOSS_QI.get();
			cult = CultivationConfig.BLOOD_BOSS_CULT.get();
		} else if (victim instanceof Player) {
			qi = CultivationConfig.BLOOD_PLAYER_QI.get();
			cult = CultivationConfig.BLOOD_PLAYER_CULT.get();
		} else if (victim instanceof AbstractVillager) {
			qi = CultivationConfig.BLOOD_VILLAGER_QI.get();
			cult = CultivationConfig.BLOOD_VILLAGER_CULT.get();
		} else if (victim instanceof Enemy) {
			qi = CultivationConfig.BLOOD_HOSTILE_QI.get();
			cult = CultivationConfig.BLOOD_HOSTILE_CULT.get();
		} else {
			qi = CultivationConfig.BLOOD_PASSIVE_QI.get();
			cult = CultivationConfig.BLOOD_PASSIVE_CULT.get();
		}

		String label = "blood";
		boolean melee = event.getSource().getDirectEntity() == killer && !QiEffects.isDealingQiDamage();
		if (melee) {
			ItemStack held = killer.getMainHandItem();
			double mult;
			if (held.isEmpty()) {
				mult = CultivationConfig.BLOOD_MULT_UNARMED.get();
				label = "blood, unarmed";
			} else if (held.is(ItemTags.SWORDS)) {
				mult = CultivationConfig.BLOOD_MULT_SWORD.get();
				label = "blood, sword x" + QiGain.fmt(mult);
			} else if (held.is(ItemTags.AXES)) {
				mult = CultivationConfig.BLOOD_MULT_AXE.get();
				label = "blood, axe x" + QiGain.fmt(mult);
			} else {
				mult = 1.0;
			}
			qi *= mult;
			cult *= mult;
		}
		QiGain.grantRaw(killer, qi, cult, label); // Blood's own reward is not cut by the Soul/Blood limit
	}
}
