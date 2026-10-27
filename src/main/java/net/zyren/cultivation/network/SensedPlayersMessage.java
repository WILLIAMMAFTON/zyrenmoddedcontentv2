package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: players this player currently senses (e.g. Fire's passive sensing Earth->Magma
 * and Electric->Plasma users). Name is "" when they're sensed but too far to identify. Sent once a
 * second while the list is non-empty, and once more (empty) when it becomes empty.
 */
public record SensedPlayersMessage(List<Entry> entries) implements CustomPacketPayload {
	public record Entry(String name, String label, double x, double z) {
	}

	public static final Type<SensedPlayersMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "sensed_players"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SensedPlayersMessage> STREAM_CODEC = StreamCodec.of(
			(buf, m) -> {
				buf.writeVarInt(m.entries.size());
				for (Entry e : m.entries) {
					buf.writeUtf(e.name(), 64);
					buf.writeUtf(e.label(), 64);
					buf.writeDouble(e.x());
					buf.writeDouble(e.z());
				}
			},
			buf -> {
				int n = Math.min(buf.readVarInt(), 64);
				List<Entry> list = new ArrayList<>();
				for (int i = 0; i < n; i++)
					list.add(new Entry(buf.readUtf(64), buf.readUtf(64), buf.readDouble(), buf.readDouble()));
				return new SensedPlayersMessage(list);
			});

	@Override
	public Type<SensedPlayersMessage> type() {
		return TYPE;
	}

	public static void send(ServerPlayer player, List<Entry> entries) {
		PacketDistributor.sendToPlayer(player, new SensedPlayersMessage(List.copyOf(entries)));
	}

	static void handle(SensedPlayersMessage msg, IPayloadContext context) {
		context.enqueueWork(() -> net.zyren.cultivation.client.ClientSensed.update(msg.entries()));
	}
}
