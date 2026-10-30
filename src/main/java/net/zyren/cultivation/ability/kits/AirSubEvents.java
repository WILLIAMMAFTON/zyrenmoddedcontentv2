package net.zyren.cultivation.ability.kits;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilityService;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.ability.core.Buff;
import net.zyren.cultivation.ability.core.KitResolver;
import net.zyren.cultivation.ability.core.QiCostDebuffs;
import net.zyren.cultivation.ability.core.QiEffects;
import net.zyren.cultivation.ability.core.Suffocation;
import net.zyren.cultivation.ability.core.Targeting;
import net.zyren.cultivation.config.AirConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.network.TargetPickMessage;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.AspectMastery;
import net.zyren.cultivation.system.QiCoreStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Event side of Air's sub-aspects (AirSubKits): Sky's target pick + oxygen aura, Storm's charged hits,
 * queued strikes, Storm front aura and free casts, Gale's elytra / rockets / bomber bow.
 *
 * Items handed out by abilities carry a "zyren_temp" tag (CustomData). They vanish whenever their ability
 * isn't running for the player holding them, and can never exist as a dropped item (tossed, dropped on
 * death, spilled from a broken chest: the item entity is cancelled).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class AirSubEvents {
	private static final AspectType A = AspectType.AIR;

	private AirSubEvents() {
	}

	private static boolean running(ServerPlayer p, Ability a) {
		return AbilityState.of(p).isSustained(a) && KitResolver.isCurrent(p, a);
	}

	// ================================================================== SKY: sorry mate ur forgor rent (target pick)

	private record Pick(List<UUID> options, long deadline) {
	}

	private static final Map<UUID, Pick> PICKS = new HashMap<>();

	/** Sends the pick screen. @return number of options */
	static int startPick(ServerPlayer p) {
		List<ServerPlayer> near = AirKit.unalliedWithin(p, AirConfig.SKY_AB1_RADIUS.get());
		near.sort(Comparator.comparingDouble(o -> o.distanceToSqr(p)));
		if (near.size() > AirConfig.SKY_AB1_OPTIONS.get())
			near = near.subList(0, AirConfig.SKY_AB1_OPTIONS.get());
		List<UUID> ids = new ArrayList<>();
		List<TargetPickMessage.Option> opts = new ArrayList<>();
		for (ServerPlayer o : near) {
			ids.add(o.getUUID());
			var form = KitResolver.formInUse(o);
			AspectType a = AspectData.of(o).getActive();
			String aspect = form != null ? form.fullName() : a == null ? "no aspect" : a.displayName();
			opts.add(new TargetPickMessage.Option(o.getId(), o.getGameProfile().getName() + "  -  " + aspect + "  (" + (int) o.distanceTo(p) + "m)"));
		}
		int secs = AirConfig.SKY_AB1_PICK_SECONDS.get();
		PICKS.put(p.getUUID(), new Pick(ids, p.serverLevel().getGameTime() + secs * 20L));
		TargetPickMessage.send(p, "Sorry mate, ur forgor rent", secs, opts);
		return ids.size();
	}

	/** Client clicked a target (PickTargetRequest). */
	public static void pickTarget(ServerPlayer p, int entityId) {
		Pick pick = PICKS.get(p.getUUID());
		if (pick == null)
			return;
		var e = p.serverLevel().getEntity(entityId);
		if (!(e instanceof ServerPlayer t) || !pick.options().contains(t.getUUID()))
			return;
		PICKS.remove(p.getUUID());
		suffocateChosen(p, t);
	}

	private static void pickTimeout(ServerPlayer p, Pick pick) {
		PICKS.remove(p.getUUID());
		TargetPickMessage.send(p, "", 0, List.of()); // close the screen
		ServerPlayer best = null;
		for (UUID id : pick.options()) {
			ServerPlayer o = p.server.getPlayerList().getPlayer(id);
			if (Targeting.isValid(p, o) && (best == null || o.distanceToSqr(p) < best.distanceToSqr(p)))
				best = o;
		}
		if (best != null)
			suffocateChosen(p, best);
		else
			p.displayClientMessage(Component.literal("Your target got away."), true);
	}

	private static void suffocateChosen(ServerPlayer p, ServerPlayer t) {
		if (!Targeting.isValid(p, t) || t.distanceTo(p) > AirConfig.SKY_AB1_RADIUS.get() * 2.0) {
			p.displayClientMessage(Component.literal("Your target got away."), true);
			return;
		}
		if (!QiEffects.canAffect(t, false)) {
			p.displayClientMessage(Component.literal(t.getGameProfile().getName() + " is Reinforced."), true);
			return;
		}
		Suffocation.apply(t, AirKit.seconds(AirConfig.SKY_AB1_SECONDS.get()));
		p.displayClientMessage(Component.literal(t.getGameProfile().getName() + " forgot their rent."), true);
		t.displayClientMessage(Component.literal("The air leaves your lungs..."), true);
	}

	// ================================================================== SKY: did u need oxygen? (aura)

	private static final Map<UUID, Set<UUID>> OXYGEN_NULLIFIED = new HashMap<>();

	private static void tickOxygen(ServerPlayer p, long now) {
		Set<UUID> done = OXYGEN_NULLIFIED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (ServerPlayer o : AirKit.unalliedWithin(p, AirConfig.SKY_AB2_RADIUS.get())) {
			if (!QiEffects.canAffect(o, false))
				continue;
			Suffocation.apply(o, 30);
			if (AirKit.isAirActive(o)) {
				if (done.add(o.getUUID()))
					QiEffects.nullify(o, A, 40);
				else
					AbilityState.of(o).nullify(A, now + 40); // silent refresh while inside
			}
		}
	}

	static void stopOxygen(ServerPlayer p) {
		OXYGEN_NULLIFIED.remove(p.getUUID());
	}

	static void leaveSky(ServerPlayer p) {
		if (PICKS.remove(p.getUUID()) != null)
			TargetPickMessage.send(p, "", 0, List.of());
		OXYGEN_NULLIFIED.remove(p.getUUID());
	}

	// ================================================================== STORM

	private static final Map<UUID, Integer> STORM_CHARGES = new HashMap<>();

	static void startStormCharge(ServerPlayer p, int hits) {
		AbilityState.of(p).addBuff(new Buff(AirSubKits.StormyAnime.BUFF, A, 1.0, 0, false, Long.MAX_VALUE));
		STORM_CHARGES.put(p.getUUID(), hits);
	}

	static int stormCharges(ServerPlayer p) {
		Integer n = STORM_CHARGES.get(p.getUUID());
		if (n == null)
			return 0;
		long now = p.serverLevel().getGameTime();
		AbilityState st = AbilityState.of(p);
		if (!st.hasBuff(AirSubKits.StormyAnime.BUFF, now) || !KitResolver.isCurrent(p, AirSubKits.STORMY) || st.isNullified(A, now)) {
			STORM_CHARGES.remove(p.getUUID());
			return 0;
		}
		return n;
	}

	private record Strike(UUID attacker, LivingEntity target) {
	}

	private static final List<Strike> STRIKES = new ArrayList<>();

	static void queueStrike(ServerPlayer attacker, LivingEntity target) {
		STRIKES.add(new Strike(attacker.getUUID(), target));
	}

	/**
	 * Cat 5 storm on an ally whose Qi is already full: their priciest usable ability (cost up to the max)
	 * is cast for free; if one can't fire right now (no target, already running...), the next one is tried.
	 */
	static boolean freeCastHighest(ServerPlayer ally, double maxCost) {
		if (AspectData.of(ally).getActive() == null)
			return false;
		long now = ally.serverLevel().getGameTime();
		AbilityState st = AbilityState.of(ally);
		List<Ability> candidates = new ArrayList<>();
		for (Ability a : KitResolver.abilities(ally).values()) {
			double c = a.baseCost(ally);
			if (c < 0 || c > maxCost || !a.isUnlocked(ally) || st.cooldownRemaining(a, now) > 0 || !a.missingValues().isEmpty())
				continue;
			candidates.add(a);
		}
		candidates.sort(Comparator.comparingDouble((Ability a) -> a.baseCost(ally)).reversed());
		for (Ability a : candidates) {
			ActionResult r = AbilityService.useFree(ally, a.slot());
			if (r.success()) {
				ally.displayClientMessage(Component.literal("The storm casts " + a.name() + " for you, free!"), false);
				net.zyren.cultivation.network.CultivationNetwork.sendFeedback(ally, r);
				return true;
			}
		}
		return false;
	}

	private static void tickStormFront(ServerPlayer p, long now) {
		int r = AirConfig.STORM_AB1_RADIUS.get();
		double discount = AirConfig.STORM_AB1_DISCOUNT.get();
		for (ServerPlayer o : p.serverLevel().players())
			if (o.isAlive() && !o.isSpectator() && Targeting.isAllied(p, o) && o.distanceTo(p) <= r && AspectData.of(o).getActive() == AspectType.ELECTRIC)
				QiCostDebuffs.discount(o, discount, 40, now);
	}

	static void leaveStorm(ServerPlayer p) {
		STORM_CHARGES.remove(p.getUUID());
		AbilityState.of(p).removeBuff(AirSubKits.StormyAnime.BUFF);
	}

	// ================================================================== GALE: temp items

	static final String TEMP = "zyren_temp", TEMP_GALE = "gale", TEMP_BOMBER = "bomber", DISPLACED = "zyren_gale_displaced";

	static String tempKind(ItemStack s) {
		if (s == null || s.isEmpty())
			return null;
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		if (d == null || !d.contains(TEMP))
			return null;
		return d.copyTag().getString(TEMP);
	}

	private static ItemStack temp(ItemStack s, String kind) {
		CustomData.update(DataComponents.CUSTOM_DATA, s, tag -> tag.putString(TEMP, kind));
		return s;
	}

	private static boolean entitled(ServerPlayer p, String kind) {
		return switch (kind) {
			case TEMP_GALE -> AirSubKits.galeOn(p) && !galeBlocked(p);
			case TEMP_BOMBER -> running(p, AirSubKits.BOMBER);
			default -> false;
		};
	}

	/** Removes every temp item of this kind (or all kinds when null) from the player's whole inventory. */
	static void removeTemp(ServerPlayer p, String kind) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			String k = tempKind(inv.getItem(i));
			if (k != null && (kind == null || kind.equals(k)))
				inv.setItem(i, ItemStack.EMPTY);
		}
	}

	private static net.minecraft.core.Holder<Enchantment> ench(ServerPlayer p, ResourceKey<Enchantment> key) {
		return p.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
	}

	// ================================================================== GALE: passive gear

	private static final Map<UUID, Long> GALE_BLOCKED = new HashMap<>();

	static boolean galeBlocked(ServerPlayer p) {
		Long until = GALE_BLOCKED.get(p.getUUID());
		return until != null && p.serverLevel().getGameTime() < until;
	}

	static void blockGale(ServerPlayer p, int ticks) {
		GALE_BLOCKED.merge(p.getUUID(), p.serverLevel().getGameTime() + ticks, Math::max);
		cleanupGale(p);
	}

	private static ItemStack newElytra(ServerPlayer p) {
		ItemStack e = new ItemStack(Items.ELYTRA);
		e.enchant(ench(p, Enchantments.UNBREAKING), 3);
		e.enchant(ench(p, Enchantments.MENDING), 1);
		e.enchant(ench(p, Enchantments.BINDING_CURSE), 1); // "removing the elytra is disabled"
		return temp(e, TEMP_GALE);
	}

	private static ItemStack newRockets(int count) {
		ItemStack r = new ItemStack(Items.FIREWORK_ROCKET, count);
		r.set(DataComponents.FIREWORKS, new Fireworks(AirConfig.GALE_ROCKET_FLIGHT.get(), List.of()));
		return temp(r, TEMP_GALE);
	}

	/** Gale passive (every second while on, and every 5 ticks from the tick handler): elytra worn, rockets topped up. */
	static void ensureGaleGear(ServerPlayer p) {
		if (p.isSpectator())
			return;
		var inv = p.getInventory();
		ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
		if (!TEMP_GALE.equals(tempKind(chest)) || !chest.is(Items.ELYTRA)) {
			// a stray Gale elytra somewhere else in the inventory goes; the worn chest item is stored
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.is(Items.ELYTRA) && TEMP_GALE.equals(tempKind(s)))
					inv.setItem(i, ItemStack.EMPTY);
			}
			chest = p.getItemBySlot(EquipmentSlot.CHEST);
			if (!chest.isEmpty()) {
				ItemStack moved = chest.copy();
				if (tempKind(moved) == null)
					CustomData.update(DataComponents.CUSTOM_DATA, moved, tag -> tag.putBoolean(DISPLACED, true));
				p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
				if (tempKind(moved) == null && !inv.add(moved))
					p.drop(moved, false);
			}
			p.setItemSlot(EquipmentSlot.CHEST, newElytra(p));
		}
		// rockets: exactly one stack, kept full
		int want = AirConfig.GALE_ROCKETS.get();
		boolean found = false;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.is(Items.FIREWORK_ROCKET) || !TEMP_GALE.equals(tempKind(s)))
				continue;
			if (found)
				inv.setItem(i, ItemStack.EMPTY);
			else if (s.getCount() != want)
				s.setCount(want);
			found = true;
		}
		if (!found)
			inv.add(newRockets(want));
	}

	/** Gale passive off: its gear goes, the stored chestplate comes back. */
	static void cleanupGale(ServerPlayer p) {
		removeTemp(p, TEMP_GALE);
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			CustomData d = s.get(DataComponents.CUSTOM_DATA);
			if (d == null || !d.contains(DISPLACED))
				continue;
			ItemStack restored = s.copy();
			CustomData.update(DataComponents.CUSTOM_DATA, restored, tag -> tag.remove(DISPLACED));
			if (restored.get(DataComponents.CUSTOM_DATA) != null && restored.get(DataComponents.CUSTOM_DATA).isEmpty())
				restored.remove(DataComponents.CUSTOM_DATA);
			inv.setItem(i, ItemStack.EMPTY);
			if (p.getItemBySlot(EquipmentSlot.CHEST).isEmpty())
				p.setItemSlot(EquipmentSlot.CHEST, restored);
			else if (!inv.add(restored))
				p.drop(restored, false);
		}
	}

	private static boolean hasGaleGear(ServerPlayer p) {
		var inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (TEMP_GALE.equals(tempKind(s)))
				return true;
			CustomData d = s.get(DataComponents.CUSTOM_DATA);
			if (d != null && d.contains(DISPLACED))
				return true;
		}
		return false;
	}

	/** Did I say u could fly: elytra off. A normal one drops; a Gale one is deleted and that Gale passive is off for a while. */
	static void ripElytra(ServerPlayer t) {
		ItemStack chest = t.getItemBySlot(EquipmentSlot.CHEST);
		if (TEMP_GALE.equals(tempKind(chest))) {
			blockGale(t, AirKit.seconds(AirConfig.GALE_PB1_BLOCK_SECONDS.get()));
		} else {
			ItemStack drop = chest.copy();
			t.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			t.drop(drop, true);
		}
		if (t.isFallFlying())
			t.stopFallFlying();
		t.displayClientMessage(Component.literal("Your elytra was ripped away!"), true);
	}

	static void leaveGale(ServerPlayer p) {
		cleanupGale(p);
		removeTemp(p, TEMP_BOMBER);
	}

	// ================================================================== GALE: bomber aura

	private static final String BOMBER_ARROW = "zyren_bomber_arrow";

	static void giveBomberBow(ServerPlayer p) {
		removeTemp(p, TEMP_BOMBER);
		ItemStack bow = new ItemStack(Items.BOW);
		bow.enchant(ench(p, Enchantments.INFINITY), 1);
		temp(bow, TEMP_BOMBER);
		ItemStack arrow = temp(new ItemStack(Items.ARROW), TEMP_BOMBER);
		if (!p.getInventory().add(bow) || !p.getInventory().add(arrow))
			p.displayClientMessage(Component.literal("Inventory full: make room for the bomber bow."), false);
	}

	@SubscribeEvent
	public static void onJoin(EntityJoinLevelEvent event) {
		if (event.getLevel().isClientSide())
			return;
		if (event.getEntity() instanceof ItemEntity item && tempKind(item.getItem()) != null) {
			event.setCanceled(true); // ability items never exist on the ground
			return;
		}
		if (event.getEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer owner
				&& TEMP_BOMBER.equals(tempKind(arrow.getWeaponItem())) && running(owner, AirSubKits.BOMBER))
			arrow.getPersistentData().putBoolean(BOMBER_ARROW, true);
	}

	@SubscribeEvent
	public static void onImpact(ProjectileImpactEvent event) {
		if (!(event.getProjectile() instanceof AbstractArrow arrow) || arrow.level().isClientSide() || !arrow.getPersistentData().getBoolean(BOMBER_ARROW))
			return;
		if (!(arrow.getOwner() instanceof ServerPlayer owner))
			return;
		HitResult hit = event.getRayTraceResult();
		if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof ServerPlayer t && t != owner) {
			arrow.getPersistentData().remove(BOMBER_ARROW);
			QiEffects.dealDamage(owner, t, AirConfig.GALE_AB1_DAMAGE.get().floatValue());
			if (QiEffects.canAffect(t, false)) {
				QiData q = QiData.of(t);
				double stolen = Math.min(q.getCoreAmount(), AirConfig.GALE_AB1_STEAL.get());
				if (stolen > 0) {
					q.setCoreAmount(q.getCoreAmount() - stolen);
					QiCoreStorage.addQi(owner, stolen);
				}
			}
		} else if (hit instanceof BlockHitResult bh && hit.getType() != HitResult.Type.MISS) {
			arrow.getPersistentData().remove(BOMBER_ARROW);
			var at = bh.getLocation();
			arrow.level().explode(owner, at.x, at.y, at.z, AirConfig.GALE_AB1_EXPLOSION.get().floatValue(), Level.ExplosionInteraction.NONE);
			arrow.discard();
			event.setCanceled(true);
		}
	}

	// ================================================================== damage / falls

	/** HIGH: after empowered hits (HIGHEST), before output multipliers (LOW). */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onHit(LivingDamageEvent.Pre event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target)
			return;
		if (!WaterEvents.isMelee(event.getSource(), attacker) || !AirKit.isAirActive(attacker))
			return;
		int charges = stormCharges(attacker);
		if (charges <= 0)
			return;
		event.setNewDamage((float) (event.getNewDamage() * AirConfig.STORM_A1_MULTIPLIER.get()));
		AspectMastery.onAbilityHit(attacker, A, target);
		if (--charges > 0) {
			STORM_CHARGES.put(attacker.getUUID(), charges);
		} else {
			STORM_CHARGES.remove(attacker.getUUID());
			AbilityState.of(attacker).removeBuff(AirSubKits.StormyAnime.BUFF);
		}
	}

	/** Gale passive: maces deal no damage. */
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onIncoming(LivingIncomingDamageEvent event) {
		DamageSource src = event.getSource();
		if (src.getEntity() instanceof ServerPlayer attacker && WaterEvents.isMelee(src, attacker)
				&& attacker.getMainHandItem().is(Items.MACE) && AirSubKits.galeOn(attacker))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onFall(LivingFallEvent event) {
		if (event.getEntity() instanceof ServerPlayer p && AirSubKits.galeOn(p))
			event.setCanceled(true); // Gale passive: no fall damage
	}

	// ================================================================== ticks

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer p))
			return;
		long now = p.serverLevel().getGameTime();
		long phase = now + p.getId();

		Pick pick = PICKS.get(p.getUUID());
		if (pick != null && now >= pick.deadline())
			pickTimeout(p, pick);

		if (phase % 5 == 0) {
			// temp items the player isn't entitled to (passive / ability ended, picked up from a chest...)
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				String k = tempKind(inv.getItem(i));
				if (k != null && !entitled(p, k))
					inv.setItem(i, ItemStack.EMPTY);
			}
			if (AirSubKits.galeOn(p) && !galeBlocked(p))
				ensureGaleGear(p);
			else if (hasGaleGear(p))
				cleanupGale(p);
		}
		if (phase % 10 == 0 && running(p, AirSubKits.OXYGEN))
			tickOxygen(p, now);
		if (phase % 20 == 0 && running(p, AirSubKits.STORM_FRONT))
			tickStormFront(p, now);
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (STRIKES.isEmpty())
			return;
		List<Strike> now = new ArrayList<>(STRIKES);
		STRIKES.clear();
		for (Strike s : now) {
			ServerPlayer attacker = event.getServer().getPlayerList().getPlayer(s.attacker());
			if (attacker != null && s.target().isAlive() && s.target().level() instanceof ServerLevel)
				LightningEvents.strike(attacker, s.target(), AirConfig.STORM_PB2_LIGHTNING_DAMAGE.get());
		}
	}

	// ================================================================== cleanup

	private static void clear(UUID id) {
		PICKS.remove(id);
		OXYGEN_NULLIFIED.remove(id);
		STORM_CHARGES.remove(id);
		GALE_BLOCKED.remove(id);
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		if (event.getEntity() instanceof ServerPlayer p)
			clear(p.getUUID());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer p) {
			removeTemp(p, null); // nothing temporary is saved with the player
			cleanupGale(p);
			clear(p.getUUID());
		}
	}

	public static void resetAll() {
		PICKS.clear();
		OXYGEN_NULLIFIED.clear();
		STORM_CHARGES.clear();
		GALE_BLOCKED.clear();
		STRIKES.clear();
	}
}
