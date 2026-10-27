package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.ability.core.AbilityService;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.system.ActionResult;

/** Client -> server: "use the ability in this slot of my active aspect". The server checks everything. */
public record UseAbilityRequest(int slot) implements CustomPacketPayload {
	public static final Type<UseAbilityRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "use_ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UseAbilityRequest> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, UseAbilityRequest::slot,
			UseAbilityRequest::new);

	@Override
	public Type<UseAbilityRequest> type() {
		return TYPE;
	}

	static void handle(UseAbilityRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			ActionResult result = AbilityService.use(player, AbilitySlot.byOrdinal(msg.slot()));
			player.displayClientMessage(net.minecraft.network.chat.Component.literal(result.message())
					.withStyle(result.success() ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.RED), true);
		});
	}
}
