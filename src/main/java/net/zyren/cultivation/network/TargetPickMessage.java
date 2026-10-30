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
 * Server -> client: pick one target (Air -> Sky "sorry mate ur forgor rent"). Each option is an entity
 * id + a label (name and aspect). The player has {@code seconds} to choose; after that the server picks
 * the nearest itself. An empty list closes the screen.
 */
public record TargetPickMessage(String title, int seconds, List<Option> options) implements CustomPacketPayload {
	public record Option(int entityId, String label) {
	}

	public static final Type<TargetPickMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "target_pick"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TargetPickMessage> STREAM_CODEC = StreamCodec.of(
			(buf, m) -> {
				buf.writeUtf(m.title(), 64);
				buf.writeVarInt(m.seconds());
				buf.writeVarInt(m.options().size());
				for (Option o : m.options()) {
					buf.writeVarInt(o.entityId());
					buf.writeUtf(o.label(), 96);
				}
			},
			buf -> {
				String title = buf.readUtf(64);
				int seconds = buf.readVarInt();
				int n = Math.min(buf.readVarInt(), 20);
				List<Option> list = new ArrayList<>();
				for (int i = 0; i < n; i++)
					list.add(new Option(buf.readVarInt(), buf.readUtf(96)));
				return new TargetPickMessage(title, seconds, list);
			});

	@Override
	public Type<TargetPickMessage> type() {
		return TYPE;
	}

	public static void send(ServerPlayer player, String title, int seconds, List<Option> options) {
		PacketDistributor.sendToPlayer(player, new TargetPickMessage(title, seconds, List.copyOf(options)));
	}

	static void handle(TargetPickMessage msg, IPayloadContext context) {
		context.enqueueWork(() -> net.zyren.cultivation.client.ClientTargetPick.open(msg.title(), msg.seconds(), msg.options()));
	}
}
