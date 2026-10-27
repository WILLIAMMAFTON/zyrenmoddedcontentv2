package net.zyren.cultivation.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.system.SubAspectForms;
import net.zyren.cultivation.system.SubAspectTiers;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Admin correction for sub-aspects (permission level 2). Deliberately separate from normal
 * acquisition: these skip level / tier / item requirements, so a sub-aspect gained by a bug or
 * exploit can be fixed quickly. Changes run through SubAspectForms.change, so whatever a removed
 * or unequipped sub-aspect started (buffs, sustained abilities...) ends with it. They still keep the data valid (a sub-aspect can only be
 * given to a player who owns its aspect; equipping requires it to be learned).
 *
 *   /qi subaspect tiers                         which sub-aspect is in which tier + unlock levels (current config)
 *   /qi subaspect list <players>                learned / equipped / form per owned aspect
 *   /qi subaspect learn <players> <sub>         give a sub-aspect (no requirements checked)
 *   /qi subaspect forget <players> <sub>        remove one (also unequips it)
 *   /qi subaspect equip <players> <sub>         equip a learned sub-aspect
 *   /qi subaspect unequip <players> <aspect>    unequip (back to normal form)
 *   /qi subaspect clear <players> <aspect|ALL>  forget every sub-aspect of an aspect (incl. pending), or all of them
 *   /qi subaspect book <players> <sub|aspect>   give a sub-aspect book (sub id) or a random-pick tome (aspect id)
 *
 * Registered under the same /qi root as CultivationCommands (Brigadier merges the two).
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2")
public final class SubAspectCommands {
	private SubAspectCommands() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		LiteralArgumentBuilder<CommandSourceStack> sub = Commands.literal("subaspect").requires(src -> src.hasPermission(2));

		sub.then(Commands.literal("tiers").executes(SubAspectCommands::tiers));

		sub.then(Commands.literal("list").then(Commands.argument("players", EntityArgument.players())
				.executes(ctx -> list(ctx, EntityArgument.getPlayers(ctx, "players")))));

		sub.then(Commands.literal("learn").then(Commands.argument("players", EntityArgument.players())
				.then(Commands.argument("sub", StringArgumentType.word()).suggests(SubAspectCommands::suggestSubs)
						.executes(ctx -> learn(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "sub"))))));

		sub.then(Commands.literal("forget").then(Commands.argument("players", EntityArgument.players())
				.then(Commands.argument("sub", StringArgumentType.word()).suggests(SubAspectCommands::suggestSubs)
						.executes(ctx -> forget(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "sub"))))));

		sub.then(Commands.literal("equip").then(Commands.argument("players", EntityArgument.players())
				.then(Commands.argument("sub", StringArgumentType.word()).suggests(SubAspectCommands::suggestSubs)
						.executes(ctx -> equip(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "sub"))))));

		sub.then(Commands.literal("unequip").then(Commands.argument("players", EntityArgument.players())
				.then(Commands.argument("aspect", StringArgumentType.word()).suggests(SubAspectCommands::suggestAspects)
						.executes(ctx -> unequip(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "aspect"))))));

		sub.then(Commands.literal("clear").then(Commands.argument("players", EntityArgument.players())
				.then(Commands.argument("aspect", StringArgumentType.word()).suggests(SubAspectCommands::suggestAspectsOrAll)
						.executes(ctx -> clear(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "aspect"))))));

		sub.then(Commands.literal("book").then(Commands.argument("players", EntityArgument.players())
				.then(Commands.argument("what", StringArgumentType.word()).suggests(SubAspectCommands::suggestSubsAndAspects)
						.executes(ctx -> book(ctx, EntityArgument.getPlayers(ctx, "players"), StringArgumentType.getString(ctx, "what"))))));

		event.getDispatcher().register(Commands.literal("qi").then(sub));
	}

	// ---------------------------------------------------------------- handlers

	private static int tiers(CommandContext<CommandSourceStack> ctx) {
		for (AspectType a : SubAspectType.parents()) {
			List<SubAspectType> t = SubAspectTiers.tiers(a);
			StringBuilder sb = new StringBuilder(a.displayName()).append(":");
			for (int i = 0; i < t.size(); i++) {
				SubAspectType s = t.get(i);
				int lvl = s == null ? -1 : SubAspectTiers.requiredLevel(s);
				sb.append("  T").append(i + 1).append(" ")
						.append(s == null ? "(empty)" : s.displayName())
						.append(s == null ? "" : lvl < 0 ? " [locked]" : " [Lv" + lvl + "]");
			}
			String line = sb.toString();
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) {
		for (ServerPlayer p : players) {
			AspectData a = AspectData.of(p);
			SubAspectData d = SubAspectData.of(p);
			SubAspectType form = d.activeForm();
			reply(ctx, p.getGameProfile().getName() + " | active: " + (a.getActive() == null ? "none" : a.getActive().displayName())
					+ " | form: " + (form == null ? "normal" : form.fullName()));
			for (AspectType t : owned(a)) {
				List<SubAspectType> learned = d.learned(t);
				SubAspectType eq = d.equipped(t);
				StringBuilder sb = new StringBuilder("  ").append(t.displayName()).append(" (Lv").append(a.getLevel(t)).append("): ");
				if (learned.isEmpty())
					sb.append("none learned");
				for (int i = 0; i < learned.size(); i++) {
					SubAspectType s = learned.get(i);
					sb.append(i == 0 ? "" : ", ").append(s.displayName());
					if (s == eq)
						sb.append(" [equipped]");
					if (!SubAspectTiers.levelAllows(s, a.getLevel(t)))
						sb.append(" [locked]");
				}
				reply(ctx, sb.toString());
			}
			List<SubAspectType> pend = d.pending();
			if (!pend.isEmpty()) {
				StringBuilder sb = new StringBuilder("  waiting for their aspect: ");
				for (int i = 0; i < pend.size(); i++)
					sb.append(i == 0 ? "" : ", ").append(pend.get(i).fullName());
				reply(ctx, sb.toString());
			}
		}
		return players.size();
	}

	private static int learn(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String raw) {
		SubAspectType s = SubAspectType.fromId(raw);
		if (s == null)
			return fail(ctx, "Unknown sub-aspect: " + raw);
		int n = 0;
		for (ServerPlayer p : players) {
			if (!AspectData.of(p).owns(s.parent()))
				fail(ctx, p.getGameProfile().getName() + " doesn't have the " + s.parent().displayName() + " aspect.");
			else if (!SubAspectData.of(p).learn(s))
				fail(ctx, p.getGameProfile().getName() + " already knows " + s.fullName() + ".");
			else
				n++;
		}
		int done = n;
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + s.fullName() + " to " + done + " player(s)."), true);
		return n;
	}

	private static int forget(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String raw) {
		SubAspectType s = SubAspectType.fromId(raw);
		if (s == null)
			return fail(ctx, "Unknown sub-aspect: " + raw);
		int n = 0;
		for (ServerPlayer p : players) {
			boolean[] ok = {false};
			SubAspectForms.change(p, () -> ok[0] = SubAspectData.of(p).unlearn(s));
			if (ok[0] || SubAspectData.of(p).unlearnPending(s))
				n++;
		}
		int done = n;
		ctx.getSource().sendSuccess(() -> Component.literal("Removed " + s.fullName() + " from " + done + " player(s)."), true);
		return n;
	}

	private static int equip(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String raw) {
		SubAspectType s = SubAspectType.fromId(raw);
		if (s == null)
			return fail(ctx, "Unknown sub-aspect: " + raw);
		int n = 0;
		for (ServerPlayer p : players) {
			boolean[] ok = {false};
			SubAspectForms.change(p, () -> ok[0] = SubAspectData.of(p).equip(s.parent(), s));
			if (ok[0])
				n++;
			else
				fail(ctx, p.getGameProfile().getName() + " hasn't learned " + s.fullName() + ".");
		}
		int done = n;
		ctx.getSource().sendSuccess(() -> Component.literal("Equipped " + s.fullName() + " for " + done + " player(s)."), true);
		return n;
	}

	private static int unequip(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String raw) {
		AspectType a = AspectType.fromId(raw);
		if (a == null)
			return fail(ctx, "Unknown aspect: " + raw);
		int n = 0;
		for (ServerPlayer p : players) {
			if (SubAspectData.of(p).equipped(a) == null)
				continue;
			SubAspectForms.change(p, () -> SubAspectData.of(p).equip(a, null));
			n++;
		}
		int done = n;
		ctx.getSource().sendSuccess(() -> Component.literal("Unequipped " + a.displayName() + " sub-aspect for " + done + " player(s)."), true);
		return n;
	}

	private static int clear(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String raw) {
		boolean all = raw.equalsIgnoreCase("ALL");
		AspectType a = all ? null : AspectType.fromId(raw);
		if (!all && a == null)
			return fail(ctx, "Unknown aspect: " + raw + " (or use ALL)");
		for (ServerPlayer p : players) {
			SubAspectData d = SubAspectData.of(p);
			SubAspectForms.change(p, () -> {
				if (all)
					for (AspectType t : owned(AspectData.of(p)))
						d.clearAspect(t);
				else
					d.clearAspect(a);
			});
			d.clearPending(a); // a == null for ALL
		}
		ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + (all ? "all" : a.displayName()) + " sub-aspects for "
				+ players.size() + " player(s)."), true);
		return players.size();
	}

	private static int book(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, String raw) {
		SubAspectType s = SubAspectType.fromId(raw);
		AspectType a = s == null ? AspectType.fromId(raw) : null;
		if (s == null && (a == null || SubAspectType.of(a).isEmpty()))
			return fail(ctx, "Unknown sub-aspect or aspect with sub-aspects: " + raw);
		for (ServerPlayer p : players) {
			net.minecraft.world.item.ItemStack stack = s != null ? net.zyren.cultivation.system.SubAspectBooks.makeBook(s)
					: net.zyren.cultivation.system.SubAspectBooks.makeTome(a);
			if (stack.isEmpty())
				return fail(ctx, "The item zyrenmoddedcontent_v2:" + (s != null ? "sub_aspect_book" : "aspect_tome") + " doesn't exist yet: create it in MCreator.");
			if (!p.getInventory().add(stack))
				p.drop(stack, false);
		}
		String what = s != null ? "Book of " + s.fullName() : a.displayName() + " Tome";
		ctx.getSource().sendSuccess(() -> Component.literal("Gave " + what + " to " + players.size() + " player(s)."), true);
		return players.size();
	}

	// ---------------------------------------------------------------- helpers

	/** Pure (always) + the slot aspects, in slot order. */
	private static List<AspectType> owned(AspectData a) {
		List<AspectType> list = new java.util.ArrayList<>();
		list.add(AspectType.PURE);
		for (int s = 1; s <= AspectData.SLOT_COUNT; s++)
			if (a.getSlotType(s) != null)
				list.add(a.getSlotType(s));
		return list;
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSubs(
			CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
		return SharedSuggestionProvider.suggest(Arrays.stream(SubAspectType.values()).map(SubAspectType::id), b);
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSubsAndAspects(
			CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
		return SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(SubAspectType.parents().stream().map(AspectType::id),
				Arrays.stream(SubAspectType.values()).map(SubAspectType::id)), b);
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestAspects(
			CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
		return SharedSuggestionProvider.suggest(SubAspectType.parents().stream().map(AspectType::id), b);
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestAspectsOrAll(
			CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
		return SharedSuggestionProvider.suggest(java.util.stream.Stream.concat(java.util.stream.Stream.of("ALL"),
				SubAspectType.parents().stream().map(AspectType::id)), b);
	}

	private static void reply(CommandContext<CommandSourceStack> ctx, String msg) {
		ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
	}

	private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
		ctx.getSource().sendFailure(Component.literal(msg).withStyle(ChatFormatting.RED));
		return 0;
	}
}
