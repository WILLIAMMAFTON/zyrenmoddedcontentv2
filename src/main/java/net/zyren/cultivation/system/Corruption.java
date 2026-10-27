package net.zyren.cultivation.system;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;

import net.zyren.cultivation.config.CorruptionConfig;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.CoreType;
import net.zyren.cultivation.data.CorruptionData;
import net.zyren.cultivation.data.QiData;

/**
 * Corruption of Pure cores (0..max points; past the point of no return it can't be stopped;
 * at max the core becomes CORRUPTED), the Purifying Crystal, Core Resistance, cleansing, and
 * the Poisoned Qi potion.
 */
public final class Corruption {
	/** Custom data flag on an infused food: its stored Qi is Corrupted Qi. */
	public static final String CORRUPTED_QI = "corrupted_qi";
	private static final String CRYSTAL_CARRY = "blocked_carry";

	private Corruption() {
	}

	public static double max() {
		return CorruptionConfig.MAX.get();
	}

	public static double pointOfNoReturn() {
		return max() * CorruptionConfig.NO_RETURN_FRACTION.get();
	}

	/** Only formed Pure cores accumulate corruption. */
	public static boolean canCorrupt(Player p) {
		QiData q = QiData.of(p);
		return CorruptionConfig.ENABLED.get() && q.getCoreLevel() >= 1 && q.getCoreType() == CoreType.PURE;
	}

	public static boolean pastPointOfNoReturn(Player p) {
		return CorruptionData.of(p).getCorruption() > pointOfNoReturn();
	}

	// ------------------------------------------------------------------ gaining

	/** Corruption from a source (area, food, Warden...): Core Resistance and the crystal reduce it. */
	public static void gain(ServerPlayer p, double amount) {
		if (amount <= 0 || !canCorrupt(p))
			return;
		if (hasResistance(p))
			amount *= 1 - CorruptionConfig.RESISTANCE_REDUCTION.get();
		ItemStack crystal = activeCrystal(p);
		if (crystal != null && amount > 0) {
			// Can't block more than the durability it has left ("while it has durability").
			double blocked = Math.min(amount * CorruptionConfig.CRYSTAL_REDUCTION.get(), crystalCapacity(crystal));
			amount -= blocked;
			wearCrystal(p, crystal, blocked);
		}
		add(p, amount);
	}

	/** Raw change (no reductions): used by the irreversible creep past the point of no return. */
	public static void add(ServerPlayer p, double amount) {
		if (amount <= 0 || !canCorrupt(p))
			return;
		CorruptionData c = CorruptionData.of(p);
		double before = c.getCorruption();
		double after = Math.min(max(), before + amount);
		c.setCorruption(after);
		double point = pointOfNoReturn();
		if (before == 0 && after > 0)
			warn(p, "⚠ Your Core is becoming corrupted.", ChatFormatting.GOLD);
		int stepBefore = (int) (before / (max() * 0.1)), stepAfter = (int) (after / (max() * 0.1));
		if (stepAfter > stepBefore && after < max())
			warn(p, "⚠ Your Core is becoming corrupted. " + percent(after) + "%", after > point ? ChatFormatting.DARK_RED : ChatFormatting.GOLD);
		if (before <= point && after > point) {
			p.displayClientMessage(Component.literal("⚠ YOUR CORE HAS PASSED THE POINT OF NO RETURN").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false);
			p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 0.8f);
		}
		if (after >= max())
			becomeCorrupted(p);
	}

	private static void becomeCorrupted(ServerPlayer p) {
		QiData.of(p).setCoreType(CoreType.CORRUPTED);
		CorruptionData.of(p).setCorruption(0); // no longer relevant once CORRUPTED
		if (AspectData.of(p).getActive() == AspectType.PURE)
			AspectData.of(p).setActive(null);
		p.displayClientMessage(Component.literal("Your Core is now CORRUPTED.").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
		p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 0.6f);
	}

	public static int percent(double corruption) {
		return (int) Math.floor(corruption / max() * 100);
	}

	private static void warn(ServerPlayer p, String text, ChatFormatting color) {
		p.displayClientMessage(Component.literal(text).withStyle(color), false);
	}

	// ------------------------------------------------------------------ cleansing

	/** Channel a share of current Qi into the core: 1 corruption removed per Qi. Only up to the point of no return. */
	public static ActionResult cleanse(ServerPlayer p) {
		CorruptionData c = CorruptionData.of(p);
		if (c.getCorruption() <= 0)
			return ActionResult.fail("Your Core isn't corrupted.");
		if (pastPointOfNoReturn(p))
			return ActionResult.fail("Too late: your Core is past the point of no return.");
		QiData q = QiData.of(p);
		double spend = q.getCoreAmount() * CorruptionConfig.CLEANSE_QI_FRACTION.get();
		if (spend <= 0)
			return ActionResult.fail("You have no Qi to channel.");
		QiCoreStorage.trySpendQi(p, spend);
		double removed = Math.min(c.getCorruption(), spend * CorruptionConfig.CLEANSE_PER_QI.get());
		c.setCorruption(c.getCorruption() - removed);
		return ActionResult.ok("Channelled " + QiGain.fmt(spend) + " Qi: corruption -" + QiGain.fmt(removed) + " (now " + percent(c.getCorruption()) + "%).");
	}

	// ------------------------------------------------------------------ Purifying Crystal

	public static Item crystalItem() {
		return safeItem(net.zyren.cultivation.config.LootConfig.CRYSTAL_ITEM.get());
	}

	public static boolean isCrystal(ItemStack stack) {
		return !stack.isEmpty() && stack.is(crystalItem());
	}

	/** First crystal in the inventory that still has durability (offhand counts too). */
	public static ItemStack activeCrystal(Player p) {
		for (ItemStack s : p.getInventory().items)
			if (isCrystal(s) && s.getDamageValue() < s.getMaxDamage())
				return s;
		ItemStack off = p.getOffhandItem();
		return isCrystal(off) && off.getDamageValue() < off.getMaxDamage() ? off : null;
	}

	/** First crystal in the inventory needing a recharge. */
	public static ItemStack crystalToRecharge(Player p) {
		for (ItemStack s : p.getInventory().items)
			if (isCrystal(s) && s.getDamageValue() > 0)
				return s;
		ItemStack off = p.getOffhandItem();
		return isCrystal(off) && off.getDamageValue() > 0 ? off : null;
	}

	private static double crystalCarry(ItemStack crystal) {
		CustomData data = crystal.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getDouble(CRYSTAL_CARRY);
	}

	/** Corruption the crystal can still block before it goes dark. */
	private static double crystalCapacity(ItemStack crystal) {
		double per = CorruptionConfig.CRYSTAL_CORRUPTION_PER_DURABILITY.get();
		return Math.max(0, (crystal.getMaxDamage() - crystal.getDamageValue()) * per - crystalCarry(crystal));
	}

	private static void wearCrystal(ServerPlayer p, ItemStack crystal, double blocked) {
		if (blocked <= 0)
			return; // nothing blocked: don't touch the item (avoids a needless resync every second)
		double carry = crystalCarry(crystal) + blocked;
		double per = CorruptionConfig.CRYSTAL_CORRUPTION_PER_DURABILITY.get();
		int loss = (int) Math.floor(carry / per + 1e-9);
		double rest = Math.max(0, carry - loss * per);
		CustomData.update(DataComponents.CUSTOM_DATA, crystal, t -> t.putDouble(CRYSTAL_CARRY, rest));
		if (loss > 0) {
			int newDamage = Math.min(crystal.getMaxDamage(), crystal.getDamageValue() + loss);
			crystal.setDamageValue(newDamage);
			if (newDamage >= crystal.getMaxDamage())
				p.displayClientMessage(Component.literal("Your Purifying Crystal has gone dark. Channel Qi to recharge it.").withStyle(ChatFormatting.GRAY), false);
		}
	}

	/** Channel Qi into a crystal: costs qi_per_durability per point, as many points as you can afford. */
	public static ActionResult rechargeCrystal(ServerPlayer p, ItemStack crystal) {
		if (crystal == null || !isCrystal(crystal))
			return ActionResult.fail("You have no Purifying Crystal that needs recharging.");
		int missing = crystal.getDamageValue();
		if (missing <= 0)
			return ActionResult.fail("The crystal is already full.");
		double per = CorruptionConfig.CRYSTAL_QI_PER_DURABILITY.get();
		int affordable = per <= 0 ? missing : (int) Math.floor(QiData.of(p).getCoreAmount() / per);
		int points = Math.min(missing, affordable);
		if (points <= 0)
			return ActionResult.fail("Not enough Qi (" + QiGain.fmt(per) + " per durability).");
		QiCoreStorage.trySpendQi(p, points * per);
		crystal.setDamageValue(missing - points);
		p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
		return ActionResult.ok("Crystal recharged +" + points + " (" + QiGain.fmt(points * per) + " Qi).");
	}

	// ------------------------------------------------------------------ Core Resistance

	public static Holder<MobEffect> resistanceEffect() {
		return safeEffect(CorruptionConfig.RESISTANCE_EFFECT.get());
	}

	/** Works on both sides (QiCosts runs on the client for the radial). */
	public static boolean hasResistance(Player p) {
		Holder<MobEffect> eff = resistanceEffect();
		return eff != null && p.hasEffect(eff);
	}

	public static void grantResistance(ServerPlayer p) {
		Holder<MobEffect> eff = resistanceEffect();
		if (eff != null)
			p.addEffect(new MobEffectInstance(eff, CorruptionConfig.RESISTANCE_TICKS.get(), 0, false, true, true));
	}

	public static boolean isPotion(ItemStack stack, String potionId) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.potion().isPresent()
				&& contents.potion().get().unwrapKey().map(k -> k.location().toString().equals(potionId)).orElse(false);
	}

	// ------------------------------------------------------------------ Poisoned Qi potion

	public static void drinkPoisonedQi(ServerPlayer p) {
		QiData q = QiData.of(p);
		if (q.getCoreLevel() < 1) {
			// "ill-gotten ascension": no core -> a Poisoned core forms at once
			q.setNoQi(QiBreakthrough.noQiRequired());
			QiBreakthrough.tryFirstBreakthrough(p);
			q.setCoreType(CoreType.POISONED);
			CorruptionData.of(p).setCorruption(0); // corruption only applies to Pure cores
			leavePure(p);
			p.displayClientMessage(Component.literal("A core forms... but something is wrong with it. Your Core is POISONED.").withStyle(ChatFormatting.DARK_GREEN), false);
			return;
		}
		if (q.getCoreType() != CoreType.PURE)
			return; // the spec only defines the effect on Pure cores
		int lvl = q.getCoreLevel();
		if (lvl <= 1) {
			q.setCoreType(CoreType.POISONED);
			CorruptionData.of(p).setCorruption(0); // corruption only applies to Pure cores
			leavePure(p);
			p.displayClientMessage(Component.literal("The Poisoned Qi seeps into your fragile Core. It is now POISONED.").withStyle(ChatFormatting.DARK_GREEN), false);
		} else if (lvl <= CorruptionConfig.POTION_LOW_LEVEL_MAX.get()) {
			int newLevel = Math.max(1, lvl - CorruptionConfig.POTION_LEVEL_LOSS.get());
			CoreProgression.lowerCoreLevel(p, newLevel);
			p.displayClientMessage(Component.literal("The Poisoned Qi damages your Core: level " + lvl + " -> " + newLevel + ".").withStyle(ChatFormatting.RED), false);
		} else {
			double loss = CorruptionConfig.POTION_QI_LOSS.get();
			if (q.getCoreAmount() >= loss) {
				q.setCoreAmount(q.getCoreAmount() - loss);
				p.displayClientMessage(Component.literal("Your Core burns away the Poisoned Qi: -" + QiGain.fmt(loss) + " Qi.").withStyle(ChatFormatting.RED), false);
			} else {
				CoreProgression.lowerCoreLevel(p, lvl - 1);
				p.displayClientMessage(Component.literal("Not enough Qi to burn off the poison: your Core drops to level " + (lvl - 1) + ".").withStyle(ChatFormatting.RED), false);
			}
		}
	}

	/** Poisoned / Corrupted cores can't use Pure. */
	public static void leavePure(ServerPlayer p) {
		if (AspectData.of(p).getActive() == AspectType.PURE)
			AspectData.of(p).setActive(null);
	}

	/** Config ids are free text: a malformed one must not throw every second. */
	private static net.minecraft.world.item.Item safeItem(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		return rl == null ? net.minecraft.world.item.Items.AIR : BuiltInRegistries.ITEM.get(rl);
	}

	private static Holder<MobEffect> safeEffect(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		return rl == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(rl).orElse(null);
	}

	/** Food carrying Corrupted Qi (custom data flag). */
	public static boolean isCorruptedQiFood(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean(CORRUPTED_QI);
	}

	/** Cure (back to PURE) or corrupt a core, with a message. */
	public static void setCoreType(ServerPlayer p, CoreType type, String message, ChatFormatting color) {
		QiData.of(p).setCoreType(type);
		CorruptionData.of(p).setCorruption(0);
		if (type != CoreType.PURE)
			leavePure(p);
		p.displayClientMessage(Component.literal(message).withStyle(color), false);
	}
}
