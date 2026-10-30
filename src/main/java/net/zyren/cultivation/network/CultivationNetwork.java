package net.zyren.cultivation.network;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.zyren.cultivation.system.ActionResult;

/**
 * Registers the cultivation packets. Screens and keybinds only ever SEND REQUESTS; the server
 * validates them and runs the normal systems. The client never changes values itself.
 *
 * Bump PROTOCOL_VERSION when any packet's format changes, so mismatched client/server versions
 * are rejected cleanly at login.
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class CultivationNetwork {
	private static final String PROTOCOL_VERSION = "12"; // 12: target-pick packets (Air -> Sky)

	private CultivationNetwork() {
	}

	@SubscribeEvent
	public static void register(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar r = event.registrar(PROTOCOL_VERSION);
		// client -> server
		r.playToServer(SwitchAspectRequest.TYPE, SwitchAspectRequest.STREAM_CODEC, SwitchAspectRequest::handle);
		r.playToServer(CancelSwitchRequest.TYPE, CancelSwitchRequest.STREAM_CODEC, CancelSwitchRequest::handle);
		r.playToServer(AspectChoiceRequest.TYPE, AspectChoiceRequest.STREAM_CODEC, AspectChoiceRequest::handle);
		r.playToServer(UseAbilityRequest.TYPE, UseAbilityRequest.STREAM_CODEC, UseAbilityRequest::handle);
		r.playToServer(CultivationActionRequest.TYPE, CultivationActionRequest.STREAM_CODEC, CultivationActionRequest::handle);
		r.playToServer(DeathChoiceRequest.TYPE, DeathChoiceRequest.STREAM_CODEC, DeathChoiceRequest::handle);
		r.playToServer(ToggleFormRequest.TYPE, ToggleFormRequest.STREAM_CODEC, ToggleFormRequest::handle);
		r.playToServer(EquipSubAspectRequest.TYPE, EquipSubAspectRequest.STREAM_CODEC, EquipSubAspectRequest::handle);
		r.playToServer(PickTargetRequest.TYPE, PickTargetRequest.STREAM_CODEC, PickTargetRequest::handle);
		// server -> client
		r.playToClient(SwitchStatusMessage.TYPE, SwitchStatusMessage.STREAM_CODEC, SwitchStatusMessage::handle);
		r.playToClient(FeedbackMessage.TYPE, FeedbackMessage.STREAM_CODEC, FeedbackMessage::handle);
		r.playToClient(AbilityStateMessage.TYPE, AbilityStateMessage.STREAM_CODEC, AbilityStateMessage::handle);
		r.playToClient(RevealAspectsMessage.TYPE, RevealAspectsMessage.STREAM_CODEC, RevealAspectsMessage::handle);
		r.playToClient(CorruptionStatusMessage.TYPE, CorruptionStatusMessage.STREAM_CODEC, CorruptionStatusMessage::handle);
		r.playToClient(DeathChoiceMessage.TYPE, DeathChoiceMessage.STREAM_CODEC, DeathChoiceMessage::handle);
		r.playToClient(SensedPlayersMessage.TYPE, SensedPlayersMessage.STREAM_CODEC, SensedPlayersMessage::handle);
		r.playToClient(TargetPickMessage.TYPE, TargetPickMessage.STREAM_CODEC, TargetPickMessage::handle);
	}

	public static void sendStatus(ServerPlayer player, SwitchStatusMessage msg) {
		PacketDistributor.sendToPlayer(player, msg);
	}

	public static void sendFeedback(ServerPlayer player, ActionResult result) {
		PacketDistributor.sendToPlayer(player, new FeedbackMessage(result.success(), result.message()));
	}
}
