package net.zyren.cultivation.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import net.zyren.cultivation.data.QiData;

/**
 * Keybinds. Default: K opens the cultivation menu (rebindable in Options > Controls).
 *
 * The names below are shown as-is in the Controls menu, so no language file entry is needed
 * (MCreator regenerates the lang file, which would wipe manual entries).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2", value = Dist.CLIENT)
public final class CultivationKeybinds {
	public static final KeyMapping OPEN_MENU = new KeyMapping("Open Cultivation Menu", InputConstants.KEY_K, "Cultivation");
	/** Hold to open the radial ability menu, release to cast. */
	public static final KeyMapping ABILITY_WHEEL = new KeyMapping("Ability Wheel (hold)", InputConstants.KEY_R, "Cultivation");
	/** Normal form <-> equipped sub-aspect form. Instant; the server checks everything (cooldown, unlock, ...). */
	public static final KeyMapping TOGGLE_FORM = new KeyMapping("Toggle Sub-aspect Form", InputConstants.KEY_G, "Cultivation");

	private CultivationKeybinds() {
	}

	@SubscribeEvent
	public static void registerKeys(RegisterKeyMappingsEvent event) {
		event.register(OPEN_MENU);
		event.register(ABILITY_WHEEL);
		event.register(TOGGLE_FORM);
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		while (OPEN_MENU.consumeClick()) {
			if (mc.player == null || mc.screen != null)
				continue;
			if (QiData.of(mc.player).getCoreLevel() < 1 && !net.zyren.cultivation.system.EmergencyCore.inGrace(mc.player)) {
				mc.player.displayClientMessage(Component.literal("You have not formed a Qi Core yet."), true);
				continue;
			}
			mc.setScreen(new CultivationScreen());
		}
		while (ABILITY_WHEEL.consumeClick()) {
			if (mc.player == null || mc.screen != null)
				continue;
			if (QiData.of(mc.player).getCoreLevel() < 1 && !net.zyren.cultivation.system.EmergencyCore.inGrace(mc.player)) {
				mc.player.displayClientMessage(Component.literal("You have not formed a Qi Core yet."), true);
				continue;
			}
			mc.setScreen(new RadialAbilityScreen());
		}
		while (TOGGLE_FORM.consumeClick()) {
			if (mc.player == null || mc.screen != null)
				continue;
			net.neoforged.neoforge.network.PacketDistributor.sendToServer(net.zyren.cultivation.network.ToggleFormRequest.INSTANCE);
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		ClientCultivationState.reset();
		ClientAbilityState.reset();
		ClientCorruptionState.reset();
		ClientSensed.reset();
	}
}
