package net.mcreator.zyrenmoddedcontentv2.network;

import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

import net.mcreator.zyrenmoddedcontentv2.ZyrenmoddedcontentV2Mod;

import java.util.function.Supplier;

@EventBusSubscriber
public class ZyrenmoddedcontentV2ModVariables {
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ZyrenmoddedcontentV2Mod.MODID);
	public static final Supplier<AttachmentType<PlayerVariables>> PLAYER_VARIABLES = ATTACHMENT_TYPES.register("player_variables", () -> AttachmentType.serializable(PlayerVariables::new).build());

	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		ZyrenmoddedcontentV2Mod.addNetworkMessage(PlayerVariablesSyncMessage.TYPE, PlayerVariablesSyncMessage.STREAM_CODEC, PlayerVariablesSyncMessage::handleData);
	}

	@SubscribeEvent
	public static void onPlayerLoggedInSyncPlayerVariables(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			PacketDistributor.sendToPlayer(player, new PlayerVariablesSyncMessage(player.getData(PLAYER_VARIABLES)));
	}

	@SubscribeEvent
	public static void onPlayerRespawnedSyncPlayerVariables(PlayerEvent.PlayerRespawnEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			PacketDistributor.sendToPlayer(player, new PlayerVariablesSyncMessage(player.getData(PLAYER_VARIABLES)));
	}

	@SubscribeEvent
	public static void onPlayerChangedDimensionSyncPlayerVariables(PlayerEvent.PlayerChangedDimensionEvent event) {
		if (event.getEntity() instanceof ServerPlayer player)
			PacketDistributor.sendToPlayer(player, new PlayerVariablesSyncMessage(player.getData(PLAYER_VARIABLES)));
	}

	@SubscribeEvent
	public static void onPlayerTickUpdateSyncPlayerVariables(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player && player.getData(PLAYER_VARIABLES)._syncDirty) {
			PacketDistributor.sendToPlayer(player, new PlayerVariablesSyncMessage(player.getData(PLAYER_VARIABLES)));
			player.getData(PLAYER_VARIABLES)._syncDirty = false;
		}
	}

	@SubscribeEvent
	public static void clonePlayer(PlayerEvent.Clone event) {
		PlayerVariables original = event.getOriginal().getData(PLAYER_VARIABLES);
		PlayerVariables clone = new PlayerVariables();
		clone.no_qi = original.no_qi;
		clone.qi_core_amount = original.qi_core_amount;
		clone.qi_core_max = original.qi_core_max;
		clone.qi_cultivated = original.qi_cultivated;
		clone.Qi_core_level = original.Qi_core_level;
		clone.qi_core_type = original.qi_core_type;
		clone.aspect_1_type = original.aspect_1_type;
		clone.aspect_2_type = original.aspect_2_type;
		clone.aspect_3_type = original.aspect_3_type;
		clone.aspect_4_type = original.aspect_4_type;
		clone.aspect_1_level = original.aspect_1_level;
		clone.aspect_2_level = original.aspect_2_level;
		clone.aspect_3_level = original.aspect_3_level;
		clone.aspect_4_level = original.aspect_4_level;
		clone.pure_level = original.pure_level;
		clone.active_aspect = original.active_aspect;
		clone.pending_breakthrough_choices = original.pending_breakthrough_choices;
		clone.aspect_1_xp = original.aspect_1_xp;
		clone.aspect_2_xp = original.aspect_2_xp;
		clone.aspect_3_xp = original.aspect_3_xp;
		clone.aspect_4_xp = original.aspect_4_xp;
		clone.core_corruption = original.core_corruption;
		clone.end_exposure_ticks = original.end_exposure_ticks;
		clone.sculk_exposure_ticks = original.sculk_exposure_ticks;
		clone.allow_emergency_core_breakdown = original.allow_emergency_core_breakdown;
		clone.last_emergency_core_breakdown_time = original.last_emergency_core_breakdown_time;
		clone.aspect_1_sub_learned = original.aspect_1_sub_learned;
		clone.aspect_2_sub_learned = original.aspect_2_sub_learned;
		clone.aspect_3_sub_learned = original.aspect_3_sub_learned;
		clone.aspect_4_sub_learned = original.aspect_4_sub_learned;
		clone.aspect_1_sub_equipped = original.aspect_1_sub_equipped;
		clone.aspect_2_sub_equipped = original.aspect_2_sub_equipped;
		clone.aspect_3_sub_equipped = original.aspect_3_sub_equipped;
		clone.aspect_4_sub_equipped = original.aspect_4_sub_equipped;
		clone.pure_sub_learned = original.pure_sub_learned;
		clone.pure_sub_equipped = original.pure_sub_equipped;
		clone.sub_form_active = original.sub_form_active;
		clone.pending_sub_learned = original.pending_sub_learned;
		if (!event.isWasDeath()) {
		}
		event.getEntity().setData(PLAYER_VARIABLES, clone);
	}

	public static class PlayerVariables implements INBTSerializable<CompoundTag> {
		boolean _syncDirty = false;
		public double no_qi = 0;
		public double qi_core_amount = 0;
		public double qi_core_max = 0;
		public double qi_cultivated = 0;
		public double Qi_core_level = 0;
		public String qi_core_type = "PURE";
		public String aspect_1_type = "\"\"";
		public String aspect_2_type = "\"\"";
		public String aspect_3_type = "\"\"";
		public String aspect_4_type = "\"\"";
		public double aspect_1_level = 0;
		public double aspect_2_level = 0;
		public double aspect_3_level = 0;
		public double aspect_4_level = 0;
		public double pure_level = 0;
		public String active_aspect = "\"\"";
		public double pending_breakthrough_choices = 0;
		public double aspect_1_xp = 0;
		public double aspect_2_xp = 0;
		public double aspect_3_xp = 0;
		public double aspect_4_xp = 0;
		public double core_corruption = 0;
		public double end_exposure_ticks = 0;
		public double sculk_exposure_ticks = 0;
		public boolean allow_emergency_core_breakdown = false;
		public double last_emergency_core_breakdown_time = 0;
		public String aspect_1_sub_learned = "\"\"";
		public String aspect_2_sub_learned = "\"\"";
		public String aspect_3_sub_learned = "\"\"";
		public String aspect_4_sub_learned = "\"\"";
		public String aspect_1_sub_equipped = "\"\"";
		public String aspect_2_sub_equipped = "\"\"";
		public String aspect_3_sub_equipped = "\"\"";
		public String aspect_4_sub_equipped = "\"\"";
		public String pure_sub_learned = "\"\"";
		public String pure_sub_equipped = "\"\"";
		public String sub_form_active = "";
		public String pending_sub_learned = "\"\"";

		@Override
		public CompoundTag serializeNBT(HolderLookup.Provider lookupProvider) {
			CompoundTag nbt = new CompoundTag();
			nbt.putDouble("no_qi", no_qi);
			nbt.putDouble("qi_core_amount", qi_core_amount);
			nbt.putDouble("qi_core_max", qi_core_max);
			nbt.putDouble("qi_cultivated", qi_cultivated);
			nbt.putDouble("Qi_core_level", Qi_core_level);
			nbt.putString("qi_core_type", qi_core_type);
			nbt.putString("aspect_1_type", aspect_1_type);
			nbt.putString("aspect_2_type", aspect_2_type);
			nbt.putString("aspect_3_type", aspect_3_type);
			nbt.putString("aspect_4_type", aspect_4_type);
			nbt.putDouble("aspect_1_level", aspect_1_level);
			nbt.putDouble("aspect_2_level", aspect_2_level);
			nbt.putDouble("aspect_3_level", aspect_3_level);
			nbt.putDouble("aspect_4_level", aspect_4_level);
			nbt.putDouble("pure_level", pure_level);
			nbt.putString("active_aspect", active_aspect);
			nbt.putDouble("pending_breakthrough_choices", pending_breakthrough_choices);
			nbt.putDouble("aspect_1_xp", aspect_1_xp);
			nbt.putDouble("aspect_2_xp", aspect_2_xp);
			nbt.putDouble("aspect_3_xp", aspect_3_xp);
			nbt.putDouble("aspect_4_xp", aspect_4_xp);
			nbt.putDouble("core_corruption", core_corruption);
			nbt.putDouble("end_exposure_ticks", end_exposure_ticks);
			nbt.putDouble("sculk_exposure_ticks", sculk_exposure_ticks);
			nbt.putBoolean("allow_emergency_core_breakdown", allow_emergency_core_breakdown);
			nbt.putDouble("last_emergency_core_breakdown_time", last_emergency_core_breakdown_time);
			nbt.putString("aspect_1_sub_learned", aspect_1_sub_learned);
			nbt.putString("aspect_2_sub_learned", aspect_2_sub_learned);
			nbt.putString("aspect_3_sub_learned", aspect_3_sub_learned);
			nbt.putString("aspect_4_sub_learned", aspect_4_sub_learned);
			nbt.putString("aspect_1_sub_equipped", aspect_1_sub_equipped);
			nbt.putString("aspect_2_sub_equipped", aspect_2_sub_equipped);
			nbt.putString("aspect_3_sub_equipped", aspect_3_sub_equipped);
			nbt.putString("aspect_4_sub_equipped", aspect_4_sub_equipped);
			nbt.putString("pure_sub_learned", pure_sub_learned);
			nbt.putString("pure_sub_equipped", pure_sub_equipped);
			nbt.putString("sub_form_active", sub_form_active);
			nbt.putString("pending_sub_learned", pending_sub_learned);
			return nbt;
		}

		@Override
		public void deserializeNBT(HolderLookup.Provider lookupProvider, CompoundTag nbt) {
			no_qi = nbt.getDouble("no_qi");
			qi_core_amount = nbt.getDouble("qi_core_amount");
			qi_core_max = nbt.getDouble("qi_core_max");
			qi_cultivated = nbt.getDouble("qi_cultivated");
			Qi_core_level = nbt.getDouble("Qi_core_level");
			qi_core_type = nbt.getString("qi_core_type");
			aspect_1_type = nbt.getString("aspect_1_type");
			aspect_2_type = nbt.getString("aspect_2_type");
			aspect_3_type = nbt.getString("aspect_3_type");
			aspect_4_type = nbt.getString("aspect_4_type");
			aspect_1_level = nbt.getDouble("aspect_1_level");
			aspect_2_level = nbt.getDouble("aspect_2_level");
			aspect_3_level = nbt.getDouble("aspect_3_level");
			aspect_4_level = nbt.getDouble("aspect_4_level");
			pure_level = nbt.getDouble("pure_level");
			active_aspect = nbt.getString("active_aspect");
			pending_breakthrough_choices = nbt.getDouble("pending_breakthrough_choices");
			aspect_1_xp = nbt.getDouble("aspect_1_xp");
			aspect_2_xp = nbt.getDouble("aspect_2_xp");
			aspect_3_xp = nbt.getDouble("aspect_3_xp");
			aspect_4_xp = nbt.getDouble("aspect_4_xp");
			core_corruption = nbt.getDouble("core_corruption");
			end_exposure_ticks = nbt.getDouble("end_exposure_ticks");
			sculk_exposure_ticks = nbt.getDouble("sculk_exposure_ticks");
			allow_emergency_core_breakdown = nbt.getBoolean("allow_emergency_core_breakdown");
			last_emergency_core_breakdown_time = nbt.getDouble("last_emergency_core_breakdown_time");
			aspect_1_sub_learned = nbt.getString("aspect_1_sub_learned");
			aspect_2_sub_learned = nbt.getString("aspect_2_sub_learned");
			aspect_3_sub_learned = nbt.getString("aspect_3_sub_learned");
			aspect_4_sub_learned = nbt.getString("aspect_4_sub_learned");
			aspect_1_sub_equipped = nbt.getString("aspect_1_sub_equipped");
			aspect_2_sub_equipped = nbt.getString("aspect_2_sub_equipped");
			aspect_3_sub_equipped = nbt.getString("aspect_3_sub_equipped");
			aspect_4_sub_equipped = nbt.getString("aspect_4_sub_equipped");
			pure_sub_learned = nbt.getString("pure_sub_learned");
			pure_sub_equipped = nbt.getString("pure_sub_equipped");
			sub_form_active = nbt.getString("sub_form_active");
			pending_sub_learned = nbt.getString("pending_sub_learned");
		}

		public void markSyncDirty() {
			_syncDirty = true;
		}
	}

	public record PlayerVariablesSyncMessage(PlayerVariables data) implements CustomPacketPayload {
		public static final Type<PlayerVariablesSyncMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ZyrenmoddedcontentV2Mod.MODID, "player_variables_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, PlayerVariablesSyncMessage> STREAM_CODEC = StreamCodec
				.of((RegistryFriendlyByteBuf buffer, PlayerVariablesSyncMessage message) -> buffer.writeNbt(message.data().serializeNBT(buffer.registryAccess())), (RegistryFriendlyByteBuf buffer) -> {
					PlayerVariablesSyncMessage message = new PlayerVariablesSyncMessage(new PlayerVariables());
					message.data.deserializeNBT(buffer.registryAccess(), buffer.readNbt());
					return message;
				});

		@Override
		public Type<PlayerVariablesSyncMessage> type() {
			return TYPE;
		}

		public static void handleData(final PlayerVariablesSyncMessage message, final IPayloadContext context) {
			if (context.flow() == PacketFlow.CLIENTBOUND && message.data != null) {
				context.enqueueWork(() -> context.player().getData(PLAYER_VARIABLES).deserializeNBT(context.player().registryAccess(), message.data.serializeNBT(context.player().registryAccess()))).exceptionally(e -> {
					context.connection().disconnect(Component.literal(e.getMessage()));
					return null;
				});
			}
		}
	}
}