package net.zyren.cultivation.ability.kits;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.AspectMastery;

/**
 * Mastery XP sources 3 and 4: kills, and aspect-flavour activities.
 *
 * Ongoing activities (once per second, aspect must be ACTIVE): Fire in fire/lava, Lightning in a
 * thunderstorm, Water underwater or in rain, Dark at night or in darkness, Light in daytime under
 * open sky.
 * Actions (aspect must be OWNED): Earth mining stone-type blocks, Warrior sword hits, Physical
 * unarmed hits, Lightning being struck by lightning, Protective taking a hit while active.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class MasteryEvents {
	private MasteryEvents() {
	}

	private static double perSecond() {
		return CultivationConfig.MASTERY_XP_PER_SECOND_FLAVOUR.get();
	}

	private static double perAction() {
		return CultivationConfig.MASTERY_XP_PER_ACTION_FLAVOUR.get();
	}

	// ------------------------------------------------------------------ ongoing activities

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		long now = p.serverLevel().getGameTime();
		if (now % 20 != p.getId() % 20)
			return;
		AspectType active = AspectData.of(p).getActive();
		if (active == null || p.isSpectator() || !p.isAlive())
			return;
		boolean doing = switch (active) {
			case FIRE -> FireKit.inFireOrLava(p);
			case ELECTRIC -> p.level().isThundering();
			case WATER -> p.isUnderWater() || p.isInWaterOrRain();
			case DARK -> DarkKit.isNight(p.level()) || DarkKit.inDarkness(p);
			case LIGHT -> LightKit.isDaytime(p.level()) && p.level().canSeeSky(p.blockPosition().above());
			default -> false;
		};
		if (doing)
			AspectMastery.addXp(p, active, perSecond());
	}

	// ------------------------------------------------------------------ actions

	@SubscribeEvent
	public static void onBreak(BlockEvent.BreakEvent event) {
		if (event.getPlayer() instanceof ServerPlayer p && !p.isCreative() && EarthKit.isStone(event.getState()))
			AspectMastery.addXp(p, AspectType.EARTH, perAction());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDamaged(LivingDamageEvent.Post event) {
		LivingEntity victim = event.getEntity();
		var source = event.getSource();

		// Melee flavour for the attacker (plain melee, not ability damage)
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim
				&& source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker && !QiEffects.isDealingQiDamage()
				&& !AspectMastery.qiHitThisTick(victim, attacker)) { // an empowered hit is not "plain" melee
			var hand = attacker.getMainHandItem();
			if (hand.is(ItemTags.SWORDS))
				AspectMastery.addXp(attacker, AspectType.WARRIOR, perAction());
			else if (hand.isEmpty())
				AspectMastery.addXp(attacker, AspectType.PHYSICAL, perAction());
		}

		if (victim instanceof ServerPlayer p) {
			if (AspectData.of(p).getActive() == AspectType.PROTECTIVE && source.getEntity() != null)
				AspectMastery.addXp(p, AspectType.PROTECTIVE, perAction());
		}
	}

	/** Lightning: being struck by a real (not visual-only) bolt. Fires even while immune (visual-only bolts never strike). */
	@SubscribeEvent
	public static void onStruck(EntityStruckByLightningEvent event) {
		// A bolt hits the entities around it on several ticks: count each bolt once per player.
		if (event.getEntity() instanceof ServerPlayer p && !Integer.valueOf(event.getLightning().getId()).equals(LAST_BOLT.put(p.getUUID(), event.getLightning().getId())))
			AspectMastery.addXp(p, AspectType.ELECTRIC, perAction());
	}

	private static final java.util.Map<java.util.UUID, Integer> LAST_BOLT = new java.util.HashMap<>();

	/** Server stopped: drop static state. */
	public static void resetAll() {
		LAST_BOLT.clear();
	}

	// ------------------------------------------------------------------ kills

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
		AspectMastery.onDeath(event.getEntity(), killer);
	}
}
