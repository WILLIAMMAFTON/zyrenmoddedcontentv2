package net.zyren.cultivation.system;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Stun;
import net.zyren.cultivation.ability.core.Thorns;
import net.zyren.cultivation.ability.core.TimedAttributes;
import net.zyren.cultivation.ability.kits.EarthEvents;
import net.zyren.cultivation.ability.kits.FireEvents;
import net.zyren.cultivation.ability.kits.LightEvents;
import net.zyren.cultivation.ability.kits.LightningEvents;
import net.zyren.cultivation.ability.kits.PhysicalEvents;
import net.zyren.cultivation.ability.kits.ProtectiveEvents;
import net.zyren.cultivation.ability.kits.PureEvents;
import net.zyren.cultivation.ability.kits.WaterEvents;

/**
 * Static server-side state (cooldowns, timers stamped with game time, jobs holding a ServerLevel)
 * must not outlive the server. In singleplayer the same JVM and player UUID carry over to the
 * next world opened, so without this, e.g. cooldowns from a world at day 100 would last hours in
 * a new world. Also runs the periodic mastery kill-credit prune.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class ServerStateReset {
	private ServerStateReset() {
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		AbilityState.resetAll();
		net.zyren.cultivation.ability.core.EffectHelper.resetAll();
		Stun.resetAll();
		TimedAttributes.resetAll();
		Thorns.resetAll();
		LightningEvents.resetAll();
		PureEvents.resetAll();
		FireEvents.resetAll();
		ProtectiveEvents.resetAll();
		WaterEvents.resetAll();
		net.zyren.cultivation.ability.kits.WaterSubEvents.resetAll();
		EarthEvents.resetAll();
		PhysicalEvents.resetAll();
		LightEvents.resetAll();
		CorruptionEvents.resetAll();
		CoreCrater.resetAll();
		QiGain.resetAll();
		CultivationAdvancements.resetAll();
		AspectSwitching.resetAll();
		AspectMastery.resetAll();
		SubAspectForms.resetAll();
		net.zyren.cultivation.ability.core.QiCostDebuffs.resetAll();
		Meditation.resetAll();
		AntiFarm.resetAll();
		DeathPenalty.resetAll();
		net.zyren.cultivation.ability.core.SearchThrottle.resetAll();
		net.zyren.cultivation.ability.kits.MasteryEvents.resetAll();
	}

	/** Mastery kill credit: prune stale entries once a minute (was tied to one player's entity id). */
	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		net.zyren.cultivation.ability.core.EffectHelper.flushKnockback();
		long now = event.getServer().overworld().getGameTime();
		if (now % 1200 == 0)
			AspectMastery.prune(now);
	}
}
