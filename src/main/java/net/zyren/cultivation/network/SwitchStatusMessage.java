package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.client.ClientCultivationState;
import net.zyren.cultivation.data.AspectType;

/** Server -> client: progress of an aspect switch, so the screen can draw the loading bar. */
public record SwitchStatusMessage(int state, String aspectId, int durationTicks, String message) implements CustomPacketPayload {
	public static final int STARTED = 0;
	public static final int COMPLETED = 1;
	public static final int CANCELLED = 2;
	public static final int REFUSED = 3;

	public static final Type<SwitchStatusMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "switch_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchStatusMessage> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SwitchStatusMessage::state,
			ByteBufCodecs.STRING_UTF8, SwitchStatusMessage::aspectId,
			ByteBufCodecs.VAR_INT, SwitchStatusMessage::durationTicks,
			ByteBufCodecs.STRING_UTF8, SwitchStatusMessage::message,
			SwitchStatusMessage::new);

	public static SwitchStatusMessage started(AspectType target, int durationTicks) {
		return new SwitchStatusMessage(STARTED, target.id(), durationTicks, "Switching to " + target.displayName() + "...");
	}

	public static SwitchStatusMessage completed(AspectType target) {
		return new SwitchStatusMessage(COMPLETED, target.id(), 0, "Now channeling " + target.displayName() + ".");
	}

	public static SwitchStatusMessage cancelled(AspectType target, String reason) {
		return new SwitchStatusMessage(CANCELLED, target.id(), 0, reason);
	}

	public static SwitchStatusMessage refused(AspectType target, String reason) {
		return new SwitchStatusMessage(REFUSED, target == null ? "" : target.id(), 0, reason);
	}

	@Override
	public Type<SwitchStatusMessage> type() {
		return TYPE;
	}

	static void handle(SwitchStatusMessage msg, IPayloadContext context) {
		// ClientCultivationState has no client-only imports, so referencing it here is server-safe.
		context.enqueueWork(() -> ClientCultivationState.onSwitchStatus(msg.state(), AspectType.fromId(msg.aspectId()), msg.durationTicks(), msg.message()));
	}
}
