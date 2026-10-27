package net.zyren.cultivation.system;

import net.minecraft.server.level.ServerPlayer;

import net.zyren.cultivation.config.SubAspectConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.data.SubAspectData;
import net.zyren.cultivation.data.SubAspectType;

import java.util.ArrayList;
import java.util.List;

/**
 * The ONE normal way to learn a sub-aspect (books and tomes call this; admin commands don't).
 *
 * Rules (user, 2026-10-27):
 *   - already known (learned, or waiting for its aspect) -> refused, the book is kept;
 *   - aspect owned -> learned into its slot;
 *   - aspect not owned -> remembered, and learned automatically when the aspect is gained;
 *   - if it is still LOCKED after learning (aspect not owned, or aspect level below the tier's
 *     level) -> the player gets +50 cultivated Qi and their usable Qi filled to max (config),
 *     and the sub-aspect stays locked until they reach the level.
 * A tome picks a random sub-aspect of its aspect among those the player doesn't know yet (only
 * sub-aspects that sit in a tier under the current server config).
 */
public final class SubAspectLearning {
	private SubAspectLearning() {
	}

	/** A sub-aspect book. @return success = consume the book */
	public static ActionResult learn(ServerPlayer player, SubAspectType sub) {
		if (sub == null)
			return ActionResult.fail("This book is blank.");
		SubAspectData d = SubAspectData.of(player);
		if (d.knows(sub))
			return ActionResult.fail("You already know " + sub.fullName() + ".");

		AspectType aspect = sub.parent();
		boolean owned = AspectData.of(player).owns(aspect);
		if (owned)
			d.learn(sub);
		else
			d.addPending(sub);

		boolean unlocked = owned && SubAspectTiers.levelAllows(sub, AspectData.of(player).getLevel(aspect));
		if (unlocked)
			return ActionResult.ok("You learned " + sub.fullName() + ". Equip it in the cultivation menu (K → Sub).");

		String reward = lockedReward(player);
		int req = SubAspectTiers.requiredLevel(sub);
		String why = !owned ? "it unlocks once you have the " + aspect.displayName() + " aspect"
				+ (req > 0 ? " at level " + req : "")
				: req < 0 ? "it is locked on this server" : "it unlocks at " + aspect.displayName() + " level " + req;
		return ActionResult.ok("You learned " + sub.fullName() + ", but " + why + "." + reward);
	}

	/** A major-aspect tome: a random sub-aspect of that aspect the player doesn't know yet. */
	public static ActionResult learnRandom(ServerPlayer player, AspectType aspect) {
		if (aspect == null)
			return ActionResult.fail("This tome is blank.");
		SubAspectData d = SubAspectData.of(player);
		List<SubAspectType> options = new ArrayList<>();
		for (SubAspectType s : SubAspectTiers.tiers(aspect))
			if (s != null && !d.knows(s))
				options.add(s);
		if (options.isEmpty())
			return ActionResult.fail("You already know every " + aspect.displayName() + " sub-aspect this tome could teach.");
		return learn(player, options.get(player.getRandom().nextInt(options.size())));
	}

	/** +cultivated Qi and full usable Qi (config). @return text for the message */
	private static String lockedReward(ServerPlayer player) {
		double qi;
		boolean fill;
		try {
			qi = SubAspectConfig.LOCKED_LEARN_CULTIVATED_QI.get();
			fill = SubAspectConfig.LOCKED_LEARN_FILL_QI.get();
		} catch (IllegalStateException notLoaded) {
			qi = 50;
			fill = true;
		}
		StringBuilder sb = new StringBuilder();
		if (qi > 0 && QiCultivation.addQiCultivated(player, qi))
			sb.append(" +").append(QiGain.fmt(qi)).append(" cultivated Qi");
		if (fill) {
			QiData q = QiData.of(player);
			if (q.getCoreMax() > 0 && q.getCoreAmount() < q.getCoreMax()) {
				q.setCoreAmount(q.getCoreMax());
				sb.append(sb.length() == 0 ? " " : ", ").append("Qi fully restored");
			}
		}
		return sb.length() == 0 ? "" : sb.append(".").toString();
	}
}
