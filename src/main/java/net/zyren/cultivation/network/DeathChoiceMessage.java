package net.zyren.cultivation.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.client.ClientDeathChoice;
import net.zyren.cultivation.data.AspectType;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: the aspects the player must choose between after a tied death. Empty = close. */
public record DeathChoiceMessage(List<String> options) implements CustomPacketPayload {
	public static final Type<DeathChoiceMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "death_choice"));
	public static final StreamCodec<ByteBuf, DeathChoiceMessage> STREAM_CODEC = ByteBufCodecs.STRING_UTF8
			.apply(ByteBufCodecs.list()).map(DeathChoiceMessage::new, DeathChoiceMessage::options);

	@Override
	public Type<DeathChoiceMessage> type() {
		return TYPE;
	}

	public static void send(ServerPlayer player, List<AspectType> options) {
		List<String> ids = new ArrayList<>();
		for (AspectType t : options)
			ids.add(t.id());
		PacketDistributor.sendToPlayer(player, new DeathChoiceMessage(ids));
	}

	static void handle(DeathChoiceMessage msg, IPayloadContext context) {
		context.enqueueWork(() -> ClientDeathChoice.set(msg.options()));
	}
}
