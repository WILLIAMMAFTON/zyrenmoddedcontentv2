package net.zyren.cultivation.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.zyren.cultivation.ability.core.AbilityState;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.system.ActionResult;
import net.zyren.cultivation.system.AspectChoices;
import net.zyren.cultivation.system.AspectMastery;
import net.zyren.cultivation.system.AspectSwitching;
import net.zyren.cultivation.system.CoreProgression;
import net.zyren.cultivation.system.QiBreakthrough;
import net.zyren.cultivation.system.QiCoreStorage;
import net.zyren.cultivation.system.QiCultivation;

import java.util.Arrays;

/**
 * /qi infuse <amount> (every player): store Qi in the held food.
 *
 * /qi infuse <amount> is open to every player (store Qi in the held food).
 *
 * Debug/test commands (permission level 2). Each subcommand exercises one system on its own,
 * so it can be tested in isolation.
 *
 *   /qi status
 *   /qi gain no_qi <amount>        -> QiCultivation.addNoQi (auto-breakthrough at threshold)
 *   /qi gain cultivated <amount>   -> QiCultivation.addQiCultivated
 *   /qi breakthrough               -> QiBreakthrough.tryFirstBreakthrough
 *   /qi qi add <amount>            -> QiCoreStorage.addQi
 *   /qi qi spend <amount>          -> QiCoreStorage.trySpendQi
 *   /qi qi setmax <amount>         -> QiCoreStorage.setCoreMax
 *   /qi coretype <PURE|CORRUPTED|POISONED>
 *   /qi reset                      -> back to initial state (aspects too)
 *
 * Stage 2 (aspects):
 *   /qi breakthrough force         -> CoreProgression.advanceCoreLevel (level 2+ grants a choice)
 *   /qi aspect status
 *   /qi aspect grantchoice <n>     -> AspectChoices.grantChoices
 *   /qi aspect gain <aspect>       -> AspectChoices.chooseNewAspect (spends a choice)
 *   /qi aspect upgrade <aspect>    -> AspectChoices.chooseUpgrade (spends a choice; PURE allowed)
 *   /qi aspect switch <aspect>     -> AspectSwitching.requestSwitch (timed channel)
 *   /qi aspect cancel | reset
 *   /qi aspect xp <aspect> <amount>  -> AspectMastery.addXp (mastery XP, levels up)
 *
 * Abilities:
 *   /qi nullify clear [players]    -> lifts every nullification (yourself if no players given)
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class CultivationCommands {
	private static final java.util.function.Predicate<CommandSourceStack> OP = src -> src.hasPermission(2);

	private static int action(CommandContext<CommandSourceStack> ctx, int action) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		ActionResult r = net.zyren.cultivation.network.CultivationActionRequest.perform(p, action);
		if (r.success())
			ctx.getSource().sendSuccess(() -> Component.literal(r.message()), false);
		else
			ctx.getSource().sendFailure(Component.literal(r.message()));
		return r.success() ? 1 : 0;
	}

	private CultivationCommands() {
	}

	/** /qi infuse <amount>: stores Qi in ONE held food item; costs base + amount usable Qi. */
	private static int infuse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		double amount = DoubleArgumentType.getDouble(ctx, "amount");
		if (QiData.of(p).getCoreLevel() < 1) {
			ctx.getSource().sendFailure(Component.literal("You need a formed Qi Core to infuse Qi."));
			return 0;
		}
		net.minecraft.world.item.ItemStack held = p.getMainHandItem();
		if (held.isEmpty() || !held.has(net.minecraft.core.component.DataComponents.FOOD)) {
			ctx.getSource().sendFailure(Component.literal("Hold the food you want to infuse in your main hand."));
			return 0;
		}
		double cost = net.zyren.cultivation.config.QiGainConfig.INFUSE_BASE_COST.get() + amount;
		if (!QiCoreStorage.trySpendQi(p, cost)) {
			ctx.getSource().sendFailure(Component.literal("Not enough Qi (" + net.zyren.cultivation.system.QiGain.fmt(cost) + " needed)."));
			return 0;
		}
		// Infuse a single item: split it off the stack so a stack doesn't multiply the stored Qi.
		net.minecraft.world.item.ItemStack one = held.split(1);
		net.zyren.cultivation.system.QiGain.setContainedQi(one, net.zyren.cultivation.system.QiGain.containedQi(one) + amount);
		net.zyren.cultivation.system.CultivationAdvancements.onInfused(p, net.zyren.cultivation.system.QiGain.containedQi(one));
		if (held.isEmpty())
			p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, one);
		else if (!p.getInventory().add(one))
			p.drop(one, false);
		ctx.getSource().sendSuccess(() -> Component.literal("Infused " + net.zyren.cultivation.system.QiGain.fmt(amount) + " Qi (cost "
				+ net.zyren.cultivation.system.QiGain.fmt(cost) + ")."), false);
		return 1;
	}

	/** Lifts all nullifications on the given players; tells each one whose Qi was actually freed. */
	private static int clearNullify(CommandContext<CommandSourceStack> ctx, java.util.Collection<ServerPlayer> players) {
		int freed = 0;
		for (ServerPlayer p : players) {
			long now = p.serverLevel().getGameTime();
			if (AbilityState.of(p).clearNullify(now)) {
				freed++;
				p.displayClientMessage(Component.literal("Your nullification was cleared.").withStyle(ChatFormatting.GREEN), false);
			}
		}
		int n = freed;
		ctx.getSource().sendSuccess(() -> Component.literal(n == 0 ? "No one was nullified."
				: "Cleared nullification on " + n + " player" + (n == 1 ? "." : "s.")), true);
		return players.size();
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		// /qi infuse is for every player; every other /qi subcommand is a debug tool (permission level 2).
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("qi");

		// Player commands (same as the cultivation menu buttons)
		root.then(Commands.literal("meditate").executes(ctx -> action(ctx, net.zyren.cultivation.network.CultivationActionRequest.MEDITATE)));
		root.then(Commands.literal("cleanse").executes(ctx -> action(ctx, net.zyren.cultivation.network.CultivationActionRequest.CLEANSE)));
		root.then(Commands.literal("crystal").executes(ctx -> action(ctx, net.zyren.cultivation.network.CultivationActionRequest.RECHARGE_CRYSTAL)));

		// Debug: corruption
		root.then(Commands.literal("corruption").requires(OP)
				.then(Commands.literal("get").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					double c = net.zyren.cultivation.data.CorruptionData.of(p).getCorruption();
					reply(ctx, "Corruption " + (long) c + " / " + (long) net.zyren.cultivation.system.Corruption.max() + " (" + net.zyren.cultivation.system.Corruption.percent(c) + "%)");
					return 1;
				}))
				.then(Commands.literal("set").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					net.zyren.cultivation.data.CorruptionData.of(p).setCorruption(0);
					net.zyren.cultivation.system.Corruption.add(p, DoubleArgumentType.getDouble(ctx, "amount"));
					reply(ctx, "Corruption set (only Pure cores with a formed core can hold corruption).");
					return 1;
				})))
				.then(Commands.literal("food").then(Commands.argument("amount", DoubleArgumentType.doubleArg(1)).executes(ctx -> {
					// Turns the held food into Corrupted-Qi food holding <amount> Qi (for testing; real ones come from loot later).
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					net.minecraft.world.item.ItemStack held = p.getMainHandItem();
					if (held.isEmpty() || !held.has(net.minecraft.core.component.DataComponents.FOOD)) {
						ctx.getSource().sendFailure(Component.literal("Hold a food item."));
						return 0;
					}
					net.zyren.cultivation.system.QiGain.setContainedQi(held, DoubleArgumentType.getDouble(ctx, "amount"));
					net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, held,
							t -> t.putBoolean(net.zyren.cultivation.system.Corruption.CORRUPTED_QI, true));
					reply(ctx, "The held food now holds Corrupted Qi.");
					return 1;
				}))));

		root.then(Commands.literal("infuse").then(Commands.argument("amount", DoubleArgumentType.doubleArg(1))
				.executes(CultivationCommands::infuse)));

		root.then(Commands.literal("status").requires(OP).executes(CultivationCommands::status));

		root.then(Commands.literal("gain").requires(OP)
				.then(Commands.literal("no_qi").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					boolean ok = QiCultivation.addNoQi(p, DoubleArgumentType.getDouble(ctx, "amount"));
					reply(ctx, ok ? "Added no_qi." : "Not applied (core already formed, or amount was 0).");
					return status(ctx);
				})))
				.then(Commands.literal("cultivated").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					QiCultivation.addQiCultivated(p, DoubleArgumentType.getDouble(ctx, "amount"));
					return status(ctx);
				}))));

		root.then(Commands.literal("breakthrough").requires(OP).executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			if (!QiBreakthrough.tryFirstBreakthrough(p) && !CoreProgression.tryNextBreakthrough(p))
				reply(ctx, "No breakthrough available.");
			return status(ctx);
		}).then(Commands.literal("force").executes(ctx -> {
			// Core level 2+ requirements aren't designed yet, so this is how to reach them for testing.
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			if (QiData.of(p).getCoreLevel() < 1) {
				ctx.getSource().sendFailure(Component.literal("Form the core first: /qi gain no_qi 100"));
				return 0;
			}
			CoreProgression.advanceCoreLevel(p);
			return status(ctx);
		})));

		root.then(Commands.literal("aspect").requires(OP)
				.then(Commands.literal("status").executes(CultivationCommands::aspectStatus))
				.then(Commands.literal("xp").then(Commands.argument("aspect", StringArgumentType.word())
						.suggests((c, sb) -> SharedSuggestionProvider.suggest(Arrays.stream(AspectType.slottable()).map(AspectType::id), sb))
						.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							AspectType type = AspectType.fromId(StringArgumentType.getString(ctx, "aspect"));
							if (type == null || !AspectData.of(p).owns(type) || type.isSpecial()) {
								ctx.getSource().sendFailure(Component.literal("You don't own that slot aspect (Pure has no mastery XP)."));
								return 0;
							}
							AspectMastery.addXp(p, type, DoubleArgumentType.getDouble(ctx, "amount"));
							return aspectStatus(ctx);
						}))))
				.then(Commands.literal("grantchoice").then(Commands.argument("count", IntegerArgumentType.integer(1, 100)).executes(ctx -> {
					AspectChoices.grantChoices(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "count"));
					return aspectStatus(ctx);
				})))
				.then(Commands.literal("gain").then(Commands.argument("aspect", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AspectType.slottable()).map(AspectType::id), b))
						.executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							return result(ctx, AspectChoices.chooseNewAspect(p, AspectType.fromId(StringArgumentType.getString(ctx, "aspect")), true));
						})))
				.then(Commands.literal("upgrade").then(Commands.argument("aspect", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AspectType.values()).map(AspectType::id), b))
						.executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							return result(ctx, AspectChoices.chooseUpgrade(p, AspectType.fromId(StringArgumentType.getString(ctx, "aspect"))));
						})))
				.then(Commands.literal("switch").then(Commands.argument("aspect", StringArgumentType.word())
						.suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(AspectType.values()).map(AspectType::id), b))
						.executes(ctx -> {
							ServerPlayer p = ctx.getSource().getPlayerOrException();
							return result(ctx, AspectSwitching.requestSwitch(p, AspectType.fromId(StringArgumentType.getString(ctx, "aspect"))));
						})))
				.then(Commands.literal("cancel").executes(ctx -> {
					AspectSwitching.cancel(ctx.getSource().getPlayerOrException(), "Cancelled by command.");
					return aspectStatus(ctx);
				}))
				.then(Commands.literal("reset").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayerOrException();
					AspectSwitching.discard(p);
					AspectData.of(p).resetAll();
					reply(ctx, "Aspects reset.");
					return aspectStatus(ctx);
				})));

		root.then(Commands.literal("qi").requires(OP)
				.then(Commands.literal("add").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
					double added = QiCoreStorage.addQi(ctx.getSource().getPlayerOrException(), DoubleArgumentType.getDouble(ctx, "amount"));
					reply(ctx, "Added " + fmt(added) + " Qi.");
					return status(ctx);
				})))
				.then(Commands.literal("spend").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
					boolean ok = QiCoreStorage.trySpendQi(ctx.getSource().getPlayerOrException(), DoubleArgumentType.getDouble(ctx, "amount"));
					reply(ctx, ok ? "Spent." : "Not enough Qi.");
					return status(ctx);
				})))
				.then(Commands.literal("setmax").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
					QiCoreStorage.setCoreMax(ctx.getSource().getPlayerOrException(), DoubleArgumentType.getDouble(ctx, "amount"));
					return status(ctx);
				}))));

		root.then(Commands.literal("coretype").requires(OP).then(Commands.argument("type", StringArgumentType.word())
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(CoreType.values()).map(CoreType::id), builder))
				.executes(ctx -> {
					String raw = StringArgumentType.getString(ctx, "type");
					CoreType type = CoreType.fromId(raw);
					if (!type.id().equalsIgnoreCase(raw)) {
						ctx.getSource().sendFailure(Component.literal("Unknown core type: " + raw));
						return 0;
					}
					ServerPlayer tp = ctx.getSource().getPlayerOrException();
					// Same path as a real conversion: resets corruption, drops Pure if no longer allowed.
					net.zyren.cultivation.system.Corruption.setCoreType(tp, type, "Core type set to " + type.id() + ".", net.minecraft.ChatFormatting.GRAY);
					if (type != CoreType.PURE && AspectSwitching.isSwitching(tp))
						AspectSwitching.discard(tp);
					return status(ctx);
				})));

		root.then(Commands.literal("breakcore").requires(OP).executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			boolean broke = net.zyren.cultivation.system.CoreBreak.breakCore(p, "/qi breakcore");
			ctx.getSource().sendSuccess(() -> Component.literal(broke ? "Core broken." : "No core to break."), false);
			return broke ? 1 : 0;
		}));
				root.then(Commands.literal("emergency").requires(OP).then(Commands.literal("clear").executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			net.zyren.cultivation.data.EmergencyCoreData.of(p).setLastTime(0);
			ctx.getSource().sendSuccess(() -> Component.literal("Emergency Core Breakdown window cleared (also ends any annihilation grace)."), false);
			return 1;
		})));
				root.then(Commands.literal("nullify").requires(OP).then(Commands.literal("clear")
				.executes(ctx -> clearNullify(ctx, java.util.List.of(ctx.getSource().getPlayerOrException())))
				.then(Commands.argument("players", EntityArgument.players())
						.executes(ctx -> clearNullify(ctx, EntityArgument.getPlayers(ctx, "players"))))));

		root.then(Commands.literal("reset").requires(OP).executes(ctx -> {
			ServerPlayer p = ctx.getSource().getPlayerOrException();
			QiData.of(p).resetToInitialState();
			AspectSwitching.discard(p);
			AspectData.of(p).resetAll();
			// A full test reset: also corruption, the breakdown window, anti-farm records and a pending death choice.
			net.zyren.cultivation.data.CorruptionData.of(p).setCorruption(0);
			net.zyren.cultivation.data.EmergencyCoreData.of(p).setLastTime(0);
			p.getPersistentData().remove("zyren_antifarm");
			p.getPersistentData().remove("zyren_death_choice");
			net.zyren.cultivation.network.DeathChoiceMessage.send(p, java.util.List.of());
			reply(ctx, "Cultivation and aspects reset to initial state.");
			return status(ctx);
		}));

		event.getDispatcher().register(root);
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		QiData d = QiData.of(ctx.getSource().getPlayerOrException());
		reply(ctx, "no_qi=" + fmt(d.getNoQi())
				+ " | qi_core_level=" + d.getCoreLevel()
				+ " | qi_core_amount=" + fmt(d.getCoreAmount()) + "/" + fmt(d.getCoreMax())
				+ " | qi_cultivated=" + fmt(d.getCultivated())
				+ " | qi_core_type=" + d.getCoreType().id());
		return 1;
	}

	private static int aspectStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		AspectData a = AspectData.of(p);
		StringBuilder sb = new StringBuilder("Pure Lv " + a.getPureLevel());
		for (int s = 1; s <= AspectData.SLOT_COUNT; s++) {
			AspectType t = a.getSlotType(s);
			sb.append(" | ").append(s).append(": ").append(t == null ? "-" : t.id() + " Lv " + a.getSlotLevel(s) + " (" + (long) a.getSlotXp(s) + " XP)");
		}
		AspectType active = a.getActive();
		sb.append(" | active=").append(active == null ? "none" : active.id())
				.append(AspectSwitching.isSwitching(p) ? " (switching)" : "")
				.append(" | choices=").append(a.getPendingChoices());
		reply(ctx, sb.toString());
		return 1;
	}

	private static int result(CommandContext<CommandSourceStack> ctx, ActionResult r) throws CommandSyntaxException {
		if (r.success())
			reply(ctx, r.message());
		else
			ctx.getSource().sendFailure(Component.literal(r.message()));
		aspectStatus(ctx);
		return r.success() ? 1 : 0;
	}

	private static void reply(CommandContext<CommandSourceStack> ctx, String msg) {
		ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.2f", v);
	}
}
