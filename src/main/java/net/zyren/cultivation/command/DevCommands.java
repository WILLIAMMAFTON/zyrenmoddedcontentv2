package net.zyren.cultivation.command;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.system.SubAspectForms;

import java.util.Collection;
import java.util.List;

/**
 * Dev / testing commands (permission level 2):
 *   /qi cooldowns clear [players]   every ability cooldown + the form-toggle cooldown end now (yourself if no players)
 *   /qi dev legacywarrior <slot> <level>   puts an OLD-SAVE Warrior aspect in a slot (1-4) so the Warrior -> Physical
 *                                          migration can be tested: relog afterwards
 * Registered under the same /qi root (Brigadier merges it with CultivationCommands).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class DevCommands {
	private DevCommands() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("qi").then(Commands.literal("cooldowns").requires(src -> src.hasPermission(2))
				.then(Commands.literal("clear")
						.executes(ctx -> clear(ctx, List.of(ctx.getSource().getPlayerOrException())))
						.then(Commands.argument("players", EntityArgument.players())
								.executes(ctx -> clear(ctx, EntityArgument.getPlayers(ctx, "players")))))));
		event.getDispatcher().register(Commands.literal("qi").then(Commands.literal("dev").requires(src -> src.hasPermission(2))
				.then(Commands.literal("legacywarrior")
						.then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4))
								.then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 1000))
										.executes(ctx -> {
											ServerPlayer p = ctx.getSource().getPlayerOrException();
											int slot = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "slot");
											int level = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "level");
											net.zyren.cultivation.data.AspectData.of(p).setSlot(slot, net.zyren.cultivation.data.AspectType.WARRIOR, level);
											ctx.getSource().sendSuccess(() -> Component.literal("Slot " + slot + " now holds a legacy Warrior (Lv " + level
													+ "). Relog to run the migration."), false);
											return 1;
										}))))));
	}

	private static int clear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
		for (ServerPlayer p : players) {
			AbilityState.of(p).clearCooldowns();
			SubAspectForms.clearCooldown(p);
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Cooldowns cleared for " + players.size() + " player(s)."), true);
		return players.size();
	}
}
