package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.system.AspectSwitching;

/** Client -> server: the cultivation screen was closed, so cancel any switch in progress. */
public record CancelSwitchRequest() implements CustomPacketPayload {
	public static final CancelSwitchRequest INSTANCE = new CancelSwitchRequest();
	public static final Type<CancelSwitchRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "cancel_switch"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CancelSwitchRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<CancelSwitchRequest> type() {
		return TYPE;
	}

	static void handle(CancelSwitchRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player)
				AspectSwitching.cancel(player, "Interrupted: you closed the cultivation menu.");
		});
	}
}
