package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.AspectChoices;

/**
 * Client -> server: spend a breakthrough choice.
 * newAspect = true: gain aspectId into an empty slot; false: upgrade owned aspectId (incl. PURE).
 */
public record AspectChoiceRequest(boolean newAspect, String aspectId) implements CustomPacketPayload {
	public static final Type<AspectChoiceRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "aspect_choice"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AspectChoiceRequest> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, AspectChoiceRequest::newAspect,
			ByteBufCodecs.STRING_UTF8, AspectChoiceRequest::aspectId,
			AspectChoiceRequest::new);

	@Override
	public Type<AspectChoiceRequest> type() {
		return TYPE;
	}

	static void handle(AspectChoiceRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			AspectType type = AspectType.fromId(msg.aspectId());
			ActionResult result = msg.newAspect() ? AspectChoices.chooseNewAspect(player, type) : AspectChoices.chooseUpgrade(player, type);
			CultivationNetwork.sendFeedback(player, result);
		});
	}
}
