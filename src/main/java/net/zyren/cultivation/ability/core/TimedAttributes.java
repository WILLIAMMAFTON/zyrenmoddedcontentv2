package net.zyren.cultivation.ability.core;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Attribute changes that last a set time (e.g. "+100 max health for 3 min", "20% max-health
 * reduction for 3 min"). Modifiers are transient (not saved), and are removed when they expire,
 * on death/respawn (new entity) and on logout.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class TimedAttributes {
	private record Entry(Holder<Attribute> attribute, ResourceLocation id, long endTick) {
	}

	private static final Map<UUID, List<Entry>> ACTIVE = new HashMap<>();

	private TimedAttributes() {
	}

	public static void apply(ServerPlayer player, Holder<Attribute> attribute, String id, double amount,
			AttributeModifier.Operation op, int ticks) {
		if (ticks <= 0)
			return;
		AttributeInstance inst = player.getAttribute(attribute);
		if (inst == null)
			return;
		ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", id);
		inst.removeModifier(rl);
		inst.addTransientModifier(new AttributeModifier(rl, amount, op));
		List<Entry> list = ACTIVE.computeIfAbsent(player.getUUID(), u -> new ArrayList<>());
		list.removeIf(e -> e.id().equals(rl));
		list.add(new Entry(attribute, rl, player.serverLevel().getGameTime() + ticks));
		clampHealth(player, attribute);
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player))
			return;
		List<Entry> list = ACTIVE.get(player.getUUID());
		if (list == null || list.isEmpty())
			return;
		long now = player.serverLevel().getGameTime();
		list.removeIf(e -> {
			if (e.endTick() > now)
				return false;
			AttributeInstance inst = player.getAttribute(e.attribute());
			if (inst != null)
				inst.removeModifier(e.id());
			clampHealth(player, e.attribute());
			return true;
		});
	}

	private static void clampHealth(ServerPlayer player, Holder<Attribute> attribute) {
		if (attribute.equals(Attributes.MAX_HEALTH) && player.getHealth() > player.getMaxHealth())
			player.setHealth(player.getMaxHealth());
	}

	@SubscribeEvent
	public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
		ACTIVE.remove(event.getEntity().getUUID()); // new entity: transient modifiers are already gone
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			List<Entry> list = ACTIVE.remove(player.getUUID());
			if (list != null)
				for (Entry e : list) {
					AttributeInstance inst = player.getAttribute(e.attribute());
					if (inst != null)
						inst.removeModifier(e.id());
				}
		}
	}

	/** Ends one timed modifier early (e.g. nullify removing a Reinforce's knockback resistance). */
	public static void remove(ServerPlayer player, String id) {
		ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", id);
		List<Entry> list = ACTIVE.get(player.getUUID());
		if (list == null)
			return;
		list.removeIf(e -> {
			if (!e.id().equals(rl))
				return false;
			AttributeInstance inst = player.getAttribute(e.attribute());
			if (inst != null)
				inst.removeModifier(rl);
			clampHealth(player, e.attribute());
			return true;
		});
	}

	/** Server stopped (also between singleplayer worlds): drop all static per-world state. */
	public static void resetAll() {
		ACTIVE.clear();
	}
}
