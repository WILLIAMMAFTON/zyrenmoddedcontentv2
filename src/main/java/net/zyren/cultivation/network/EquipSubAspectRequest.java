package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.SubAspectForms;

/**
 * Client -> server: "equip this sub-aspect of this aspect" (subId "" = unequip). Sent by the
 * sub-aspect screen. The server checks ownership, learned and unlock level.
 */
public record EquipSubAspectRequest(String aspectId, String subId) implements CustomPacketPayload {
	public static final Type<EquipSubAspectRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "equip_sub_aspect"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EquipSubAspectRequest> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, EquipSubAspectRequest::aspectId,
			ByteBufCodecs.STRING_UTF8, EquipSubAspectRequest::subId,
			EquipSubAspectRequest::new);

	@Override
	public Type<EquipSubAspectRequest> type() {
		return TYPE;
	}

	static void handle(EquipSubAspectRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			SubAspectType sub = SubAspectType.fromId(msg.subId());
			if (sub == null && !msg.subId().isEmpty())
				return; // unknown id from a modified client: ignore
			SubAspectForms.report(player, SubAspectForms.equip(player, AspectType.fromId(msg.aspectId()), sub));
		});
	}
}
