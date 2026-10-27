package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.AspectSwitching;

/** Client -> server: "start switching my active aspect to X". The server validates everything. */
public record SwitchAspectRequest(String aspectId) implements CustomPacketPayload {
	public static final Type<SwitchAspectRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "switch_aspect"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchAspectRequest> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SwitchAspectRequest::aspectId,
			SwitchAspectRequest::new);

	@Override
	public Type<SwitchAspectRequest> type() {
		return TYPE;
	}

	static void handle(SwitchAspectRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player)
				AspectSwitching.requestSwitch(player, AspectType.fromId(msg.aspectId()));
		});
	}
}
