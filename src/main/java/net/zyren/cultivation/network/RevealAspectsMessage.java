package net.zyren.cultivation.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.client.ClientAspectReveal;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> client: Light Heavy reveal. Entity id -> the aspect that player has active right now.
 * Sent every half second while the reveal lasts; an empty map ends it.
 */
public record RevealAspectsMessage(Map<Integer, String> labels) implements CustomPacketPayload {
	public static final Type<RevealAspectsMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "reveal_aspects"));
	public static final StreamCodec<ByteBuf, RevealAspectsMessage> STREAM_CODEC = ByteBufCodecs
			.<ByteBuf, Integer, String, HashMap<Integer, String>>map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.STRING_UTF8)
			.map(RevealAspectsMessage::new, m -> new HashMap<>(m.labels()));

	@Override
	public Type<RevealAspectsMessage> type() {
		return TYPE;
	}

	public static void send(ServerPlayer player, Map<Integer, String> labels) {
		PacketDistributor.sendToPlayer(player, new RevealAspectsMessage(labels));
	}

	static void handle(RevealAspectsMessage msg, IPayloadContext context) {
		context.enqueueWork(() -> ClientAspectReveal.update(msg.labels()));
	}
}
