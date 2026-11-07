package net.zyren.cultivation.system;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;

/**
 * SOUL core type (claude/core-types.md, decided 2026-11-06).
 *
 *  - Passive usable Qi once per second from every living thing within soul_radius (default 8):
 *    players 5, villagers 3, hostile mobs 2, passive mobs 1 — per entity, stacking, no cap.
 *    Usable only (capped at max Qi, no overflow into cultivated). Needs a formed core.
 *  - Normal activity sources give no usable Qi (QiGain.grant via CoreTypes.cutsNormalUsableQi);
 *    their cultivated / no_qi still counts. Qi-infused food and the Qi-player kill reward are
 *    unaffected.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class SoulQi {
	private SoulQi() {
	}

	public static boolean active(Player player) {
		QiData q = QiData.of(player);
		return q.getCoreType() == CoreType.SOUL && q.getCoreLevel() >= 1;
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 20 != 0)
			return;
		if (!p.isAlive() || p.isSpectator() || !active(p))
			return;
		QiData q = QiData.of(p);
		if (q.getCoreAmount() >= q.getCoreMax())
			return; // already full: skip the entity search
		double gain = perSecond(p);
		if (gain > 0)
			QiCoreStorage.addQi(p, gain);
	}

	/** Usable Qi per second the player's surroundings give right now (works on the client too, for the HUD). */
	public static double perSecond(Player p) {
		double r = CultivationConfig.SOUL_RADIUS.get();
		if (r <= 0)
			return 0;
		double r2 = r * r;
		double total = 0;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(r),
				e -> e != p && e.isAlive() && !(e instanceof ArmorStand) && !e.isSpectator() && e.distanceToSqr(p) <= r2))
			total += value(e);
		return total;
	}

	private static double value(LivingEntity e) {
		if (e instanceof Player)
			return CultivationConfig.SOUL_QI_PLAYER.get();
		if (e instanceof AbstractVillager)
			return CultivationConfig.SOUL_QI_VILLAGER.get();
		if (e instanceof Enemy)
			return CultivationConfig.SOUL_QI_HOSTILE.get();
		return CultivationConfig.SOUL_QI_PASSIVE.get();
	}
}
