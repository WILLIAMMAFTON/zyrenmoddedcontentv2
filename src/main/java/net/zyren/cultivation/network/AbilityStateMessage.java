package net.zyren.cultivation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.client.ClientAbilityState;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;

/**
 * Server -> client: ability timers for the HUD and radial menu (cooldowns of the active
 * aspect's slots, the armed empowered slot, Reinforce time left, passive on/off).
 * Sent only when something changes; the client counts timers down itself.
 */
public record AbilityStateMessage(int[] cooldowns, int armedSlot, int reinforceTicks, boolean passiveActive, int activeMask, int[] nullifyTicks, float xpNeeded) implements CustomPacketPayload {
	public static final Type<AbilityStateMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "ability_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityStateMessage> STREAM_CODEC = StreamCodec.of(
			(buf, m) -> {
				buf.writeVarInt(m.cooldowns.length);
				for (int c : m.cooldowns)
					buf.writeVarInt(c);
				buf.writeVarInt(m.armedSlot + 1);
				buf.writeVarInt(m.reinforceTicks);
				buf.writeBoolean(m.passiveActive);
				buf.writeVarInt(m.activeMask);
				buf.writeVarInt(m.nullifyTicks.length);
				for (int t : m.nullifyTicks)
					buf.writeVarInt(t);
				buf.writeFloat(m.xpNeeded);
			},
			buf -> {
				int n = Math.min(buf.readVarInt(), 64);
				int[] cds = new int[n];
				for (int i = 0; i < n; i++)
					cds[i] = buf.readVarInt();
				int armed = buf.readVarInt() - 1;
				int reinforce = buf.readVarInt();
				boolean passive = buf.readBoolean();
				int mask = buf.readVarInt();
				int m = Math.min(buf.readVarInt(), 64);
				int[] nul = new int[m];
				for (int i = 0; i < m; i++)
					nul[i] = buf.readVarInt();
				return new AbilityStateMessage(cds, armed, reinforce, passive, mask, nul, buf.readFloat());
			});

	@Override
	public Type<AbilityStateMessage> type() {
		return TYPE;
	}

	public static void send(ServerPlayer player, AbilityState state, long now) {
		AspectType active = AspectData.of(player).getActive();
		AbilitySlot[] slots = AbilitySlot.values();
		int[] cds = new int[slots.length];
		if (active != null)
			for (int i = 0; i < slots.length; i++)
				cds[i] = (int) Math.min(Integer.MAX_VALUE, state.cooldownRemaining(active, slots[i], now));
		Ability armed = state.armedAbility();
		AspectType[] aspects = AspectType.values();
		int[] nul = new int[aspects.length];
		for (int i = 0; i < aspects.length; i++)
			nul[i] = (int) Math.min(Integer.MAX_VALUE, state.nullifyRemaining(aspects[i], now));
		PacketDistributor.sendToPlayer(player, new AbilityStateMessage(cds, armed == null ? -1 : armed.slot().ordinal(),
				(int) Math.min(Integer.MAX_VALUE, state.reinforceRemaining(now)), state.passiveActive(), state.activeSlotsMask(), nul,
				(float) net.zyren.cultivation.system.AspectMastery.xpNeededFor(player, active)));
	}

	static void handle(AbilityStateMessage msg, IPayloadContext context) {
		context.enqueueWork(() -> ClientAbilityState.update(msg.cooldowns(), msg.armedSlot(), msg.reinforceTicks(), msg.passiveActive(), msg.activeMask(), msg.nullifyTicks(), msg.xpNeeded()));
	}
}
