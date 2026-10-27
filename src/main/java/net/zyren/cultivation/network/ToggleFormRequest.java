package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.system.SubAspectForms;

/** Client -> server: the toggle key was pressed (normal form <-> equipped sub-aspect form). The server checks everything. */
public record ToggleFormRequest() implements CustomPacketPayload {
	public static final ToggleFormRequest INSTANCE = new ToggleFormRequest();
	public static final Type<ToggleFormRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "toggle_form"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFormRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<ToggleFormRequest> type() {
		return TYPE;
	}

	static void handle(ToggleFormRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (context.player() instanceof ServerPlayer player)
				SubAspectForms.report(player, SubAspectForms.toggle(player));
		});
	}
}
