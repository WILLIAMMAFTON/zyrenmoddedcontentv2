package net.zyren.cultivation.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.client.ClientCorruptionState;

import java.util.List;

/** Server -> client, once a second: meditation timers and nearby Poisoned cores (core level 20+). */
public record CorruptionStatusMessage(int meditationTicks, int afterTicks, List<String> poisonedNearby) implements CustomPacketPayload {
	public static final Type<CorruptionStatusMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "corruption_status"));
	public static final StreamCodec<ByteBuf, CorruptionStatusMessage> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CorruptionStatusMessage::meditationTicks,
			ByteBufCodecs.VAR_INT, CorruptionStatusMessage::afterTicks,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), CorruptionStatusMessage::poisonedNearby,
			CorruptionStatusMessage::new);

	@Override
	public Type<CorruptionStatusMessage> type() {
		return TYPE;
	}

	public static void send(ServerPlayer player, int meditationTicks, int afterTicks, List<String> poisoned) {
		PacketDistributor.sendToPlayer(player, new CorruptionStatusMessage(meditationTicks, afterTicks, poisoned.size() > 32 ? poisoned.subList(0, 32) : poisoned));
	}

	static void handle(CorruptionStatusMessage msg, IPayloadContext context) {
		context.enqueueWork(() -> ClientCorruptionState.update(msg.meditationTicks(), msg.afterTicks(), msg.poisonedNearby()));
	}
}
