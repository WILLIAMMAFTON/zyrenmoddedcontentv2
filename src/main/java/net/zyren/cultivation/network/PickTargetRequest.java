package net.zyren.cultivation.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: the target picked in the target-pick screen. The server checks it was one of the options. */
public record PickTargetRequest(int entityId) implements CustomPacketPayload {
	public static final Type<PickTargetRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "pick_target"));
	public static final StreamCodec<ByteBuf, PickTargetRequest> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(PickTargetRequest::new, PickTargetRequest::entityId);

	@Override
	public Type<PickTargetRequest> type() {
		return TYPE;
	}

	static void handle(PickTargetRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player)
				net.zyren.cultivation.ability.kits.AirSubEvents.pickTarget(player, msg.entityId());
		});
	}
}
