package net.zyren.cultivation.system;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.config.CultivationConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Awards the user's MCreator advancements (all use the "impossible" trigger, so they are only
 * granted from here). Names are the advancement file names in data/zyrenmoddedcontent_v2/advancement.
 *
 *   acention                     core formed
 *   abrokencore                  your core breaks
 *   animalpals                   feed your own tamed animal something it eats
 *   eataqiinfusedmunchie         eat a Qi-infused food
 *   isaynowacend                 form your core by eating a Qi-infused item
 *   insufficentpowerfuk          an ability or passive fails because you're out of usable Qi
 *   werediditgo                  a food item holds 14k+ Qi after you infuse it
 *   iaminsane                    your core becomes Corrupted
 *   thepoweritsaddictiveohhh     use the Physical aspect while your core is Corrupted
 *   godhood                      reach max core level (highest configured level + 1)
 *   puresuffering                killed by a Corrupted-core player of core level 10+
 *   godhelpme                    get nullified
 *   whattfbroooo                 killed by a player within 10 min after your core broke
 *   whatawaste                   kill a player whose core broke within the last 10 min
 *   whoneedstobeagodtowin        within 20 min after your core broke, kill a player with a core
 *   whospilledthenuclearwastenow affected by a Poisoned core's proximity (waits for that mechanic)
 *   thatdoesntfeellikeacention   have a Poisoned core
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class CultivationAdvancements {
	public static final String ACENTION = "acention", BROKEN_CORE = "abrokencore", ANIMAL_PALS = "animalpals",
			INFUSED_MUNCHIE = "eataqiinfusedmunchie", INFUSED_ASCEND = "isaynowacend", NO_POWER = "insufficentpowerfuk",
			WHERE_DID_IT_GO = "werediditgo", INSANE = "iaminsane", ADDICTIVE = "thepoweritsaddictiveohhh", GODHOOD = "godhood",
			PURE_SUFFERING = "puresuffering", GOD_HELP_ME = "godhelpme", WHOLE_NEW_FAILURE = "whattfbroooo",
			WHAT_A_WASTE = "whatawaste", NO_QI_NO_PROBLEM = "whoneedstobeagodtowin", NUCLEAR_WASTE = "whospilledthenuclearwastenow",
			NOT_ASCENSION = "thatdoesntfeellikeacention";

	/** Player -> game time their core last broke (in memory; resets on restart). */
	private static final Map<UUID, Long> CORE_BROKEN_AT = new HashMap<>();

	private CultivationAdvancements() {
	}

	/** Grants an advancement (all remaining criteria). Missing advancement = ignored. */
	public static void award(ServerPlayer player, String name) {
		if (player.server == null)
			return;
		AdvancementHolder holder = player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", name));
		if (holder == null)
			return;
		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
		if (progress.isDone())
			return;
		for (String criterion : progress.getRemainingCriteria())
			player.getAdvancements().award(holder, criterion);
	}

	// ------------------------------------------------------------------ hooks called by systems

	public static void onCoreBroken(ServerPlayer player) {
		CORE_BROKEN_AT.put(player.getUUID(), player.serverLevel().getGameTime());
		award(player, BROKEN_CORE);
	}

	public static void onNullified(ServerPlayer player) {
		award(player, GOD_HELP_ME);
	}

	public static void onOutOfQi(ServerPlayer player) {
		award(player, NO_POWER);
	}

	public static void onAteInfused(ServerPlayer player, boolean formedCore) {
		award(player, INFUSED_MUNCHIE);
		if (formedCore)
			award(player, INFUSED_ASCEND);
	}

	public static void onInfused(ServerPlayer player, double containedAfter) {
		if (containedAfter >= CultivationConfig.ADV_WHERE_DID_IT_GO_QI.get())
			award(player, WHERE_DID_IT_GO);
	}

	public static void onAbilityUsed(ServerPlayer player, AspectType aspect) {
		if (aspect == AspectType.PHYSICAL && QiData.of(player).getCoreType() == CoreType.CORRUPTED)
			award(player, ADDICTIVE);
	}

	/** Highest configured core level + 1 (the level-up from the last line); 0 if none configured. */
	public static int maxCoreLevel() {
		int max = 0;
		for (String line : CultivationConfig.CORE_LEVELS.get()) {
			try {
				max = Math.max(max, Integer.parseInt(line.trim().split(":")[0].trim()));
			} catch (RuntimeException ignored) {
				// invalid lines are rejected by the config validator
			}
		}
		return max > 0 ? max + 1 : 0;
	}

	private static boolean brokeWithin(ServerPlayer p, int minutes) {
		Long at = CORE_BROKEN_AT.get(p.getUUID());
		return at != null && p.serverLevel().getGameTime() - at <= minutes * 1200L;
	}

	// ------------------------------------------------------------------ state checks (once per second)

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || (p.serverLevel().getGameTime() + p.getId()) % 20 != 0)
			return;
		QiData q = QiData.of(p);
		if (q.getCoreLevel() >= 1)
			award(p, ACENTION);
		int max = maxCoreLevel();
		if (max > 0 && q.getCoreLevel() >= max)
			award(p, GODHOOD);
		CoreType type = q.getCoreType();
		if (type == CoreType.CORRUPTED) {
			award(p, INSANE);
			if (AspectData.of(p).getActive() == AspectType.PHYSICAL && AbilityState.of(p).passiveActive())
				award(p, ADDICTIVE);
		} else if (type == CoreType.POISONED) {
			award(p, NOT_ASCENSION);
		}
	}

	// ------------------------------------------------------------------ events

	@SubscribeEvent
	public static void onInteract(PlayerInteractEvent.EntityInteract event) {
		if (!(event.getEntity() instanceof ServerPlayer p) || !(event.getTarget() instanceof TamableAnimal pet))
			return;
		ItemStack stack = event.getItemStack();
		if (!stack.isEmpty() && pet.isTame() && pet.isOwnedBy(p) && pet.isFood(stack))
			award(p, ANIMAL_PALS);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer victim) || !(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == victim)
			return;
		QiData k = QiData.of(killer);
		if (k.getCoreType() == CoreType.CORRUPTED && k.getCoreLevel() >= CultivationConfig.ADV_PURE_SUFFERING_LEVEL.get())
			award(victim, PURE_SUFFERING);
		if (brokeWithin(victim, CultivationConfig.ADV_FAILURE_MINUTES.get()))
			award(victim, WHOLE_NEW_FAILURE);
		if (brokeWithin(victim, CultivationConfig.ADV_WHAT_A_WASTE_MINUTES.get()))
			award(killer, WHAT_A_WASTE);
		if (QiData.of(victim).getCoreLevel() >= 1 && brokeWithin(killer, CultivationConfig.ADV_NO_QI_NO_PROBLEM_MINUTES.get()))
			award(killer, NO_QI_NO_PROBLEM);
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		CORE_BROKEN_AT.clear();
	}
}
