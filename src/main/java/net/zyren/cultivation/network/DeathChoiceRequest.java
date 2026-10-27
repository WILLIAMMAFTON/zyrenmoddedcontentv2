package net.zyren.cultivation.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.system.DeathPenalty;

/** Client -> server: the aspect picked in the death-choice GUI. The server validates it. */
public record DeathChoiceRequest(String aspectId) implements CustomPacketPayload {
	public static final Type<DeathChoiceRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "death_choice_pick"));
	public static final StreamCodec<ByteBuf, DeathChoiceRequest> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(DeathChoiceRequest::new, DeathChoiceRequest::aspectId);

	@Override
	public Type<DeathChoiceRequest> type() {
		return TYPE;
	}

	static void handle(DeathChoiceRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player)
				DeathPenalty.choose(player, msg.aspectId());
		});
	}
}
