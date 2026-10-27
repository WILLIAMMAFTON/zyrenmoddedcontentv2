package net.zyren.cultivation.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.Corruption;
import net.zyren.cultivation.system.Meditation;

/** Client -> server: a cultivation-menu action button. The server checks everything. */
public record CultivationActionRequest(int action) implements CustomPacketPayload {
	public static final int MEDITATE = 0, CLEANSE = 1, RECHARGE_CRYSTAL = 2, TOGGLE_EMERGENCY = 3;

	public static final Type<CultivationActionRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "cultivation_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CultivationActionRequest> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CultivationActionRequest::action,
			CultivationActionRequest::new);

	@Override
	public Type<CultivationActionRequest> type() {
		return TYPE;
	}

	/** Shared by the menu buttons and the commands. */
	public static ActionResult perform(ServerPlayer player, int action) {
		return switch (action) {
			case MEDITATE -> Meditation.start(player);
			case CLEANSE -> Corruption.cleanse(player);
			case RECHARGE_CRYSTAL -> Corruption.rechargeCrystal(player, Corruption.crystalToRecharge(player));
			case TOGGLE_EMERGENCY -> net.zyren.cultivation.system.EmergencyCore.toggle(player);
			default -> ActionResult.fail("Unknown action.");
		};
	}

	static void handle(CultivationActionRequest msg, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player))
				return;
			ActionResult r = perform(player, msg.action());
			CultivationNetwork.sendFeedback(player, r);
			player.displayClientMessage(Component.literal(r.message()).withStyle(r.success() ? ChatFormatting.AQUA : ChatFormatting.RED), true);
		});
	}
}
