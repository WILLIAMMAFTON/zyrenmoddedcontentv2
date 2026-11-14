package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: the mode key was pressed (Electric -> Enhanced Receptors, Space -> Dimensional). The server checks everything. */
public record SwitchModeRequest() implements CustomPacketPayload {
	public static final SwitchModeRequest INSTANCE = new SwitchModeRequest();
	public static final Type<SwitchModeRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "switch_mode"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchModeRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<SwitchModeRequest> type() {
		return TYPE;
	}

	static void handle(SwitchModeRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			if (net.zyren.cultivation.ability.core.KitResolver.formInUse(player) == net.zyren.cultivation.data.SubAspectType.SPACE_DIMENSIONAL)
				net.zyren.cultivation.ability.kits.SpaceKitEvents.switchMode(player); // Space -> Dimensional travel modes
			else
				net.zyren.cultivation.ability.kits.ElectricEvents.switchMode(player);
		});
	}
}
