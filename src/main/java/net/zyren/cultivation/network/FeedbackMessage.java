package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.client.ClientCultivationState;

/** Server -> client: result text of a screen action (e.g. a breakthrough choice), shown in the screen. */
public record FeedbackMessage(boolean success, String message) implements CustomPacketPayload {
	public static final Type<FeedbackMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "feedback"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackMessage> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, FeedbackMessage::success,
			ByteBufCodecs.STRING_UTF8, FeedbackMessage::message,
			FeedbackMessage::new);

	@Override
	public Type<FeedbackMessage> type() {
		return TYPE;
	}

	static void handle(FeedbackMessage msg, IPayloadContext context) {
		context.enqueueWork(() -> ClientCultivationState.onFeedback(msg.success(), msg.message()));
	}
}
