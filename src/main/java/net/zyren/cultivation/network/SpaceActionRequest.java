package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server, Space kits. The server checks everything.
 * action 1 = 'Space Anchor Recall' key (spatial anchor); 2 = sneak + right-click air with an empty
 * hand (Space -> Dimensional passive travel; the server never hears about empty-hand air clicks).
 */
public record SpaceActionRequest(int action) implements CustomPacketPayload {
	public static final int RECALL = 1, JUMP = 2;
	public static final Type<SpaceActionRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "space_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SpaceActionRequest> STREAM_CODEC = StreamCodec.of(
			(buf, msg) -> buf.writeVarInt(msg.action()), buf -> new SpaceActionRequest(buf.readVarInt()));

	@Override
	public Type<SpaceActionRequest> type() {
		return TYPE;
	}

	static void handle(SpaceActionRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			if (msg.action() == RECALL)
				net.zyren.cultivation.ability.kits.SpaceKitEvents.recallAnchor(player);
			else if (msg.action() == JUMP)
				net.zyren.cultivation.ability.kits.SpaceKitEvents.dimensionalJump(player);
		});
	}
}
