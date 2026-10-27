package net.zyren.cultivation.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;

/**
 * Small HUD readout. Shown only when qi_core_level >= 1. Client-side only; reads the values
 * MCreator syncs. Information only: interaction happens in the cultivation menu (K).
 *
 *   Qi 20 / 50
 *   [=======-----]
 *   Fire Lv 2
 *   [===--]                      (only while switching aspect)
 *   Breakthrough choice! [K]     (only while a choice is pending)
 */
@EventBusSubscriber(modid = "zyrenmoddedcontent_v2", value = Dist.CLIENT)
public final class QiHudOverlay {
	private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("zyrenmoddedcontent_v2", "qi_hud");

	// ---- Layout (top-left corner). Change these to move/resize the readout. ----
	private static final int X = 6;
	private static final int Y = 6;
	private static final int BAR_WIDTH = 81;
	private static final int BAR_HEIGHT = 5;

	// ---- Colours (ARGB) ----
	private static final int LABEL_COLOR = 0xFF7FE3D6;
	private static final int NULLIFIED_COLOR = 0xFFFF5555;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int BAR_BACKGROUND = 0x99000000;
	private static final int BAR_FILL = 0xFF3FBFB0;
	private static final int BAR_FILL_HIGHLIGHT = 0xFF8FF0E4;
	private static final int DIM_COLOR = 0xFFA0A0A0;
	private static final int SWITCH_FILL = 0xFFE0C060;
	private static final int CHOICE_COLOR = 0xFFFFE070;

	private QiHudOverlay() {
	}

	@SubscribeEvent
	public static void registerLayers(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER_ID, QiHudOverlay::render);
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || player.isSpectator())
			return;

		QiData data = QiData.of(player);
		if (data.getCoreLevel() < 1 && !net.zyren.cultivation.system.EmergencyCore.inGrace(player))
			return; // no core: nothing to show (except during the post-annihilation grace)

		double amount = data.getCoreAmount();
		double max = data.getCoreMax();
		Font font = mc.font;

		// Text line: "Qi 20 / 50"
		String label = "Qi ";
		g.drawString(font, label, X, Y, LABEL_COLOR, true);
		g.drawString(font, max > 0 ? fmt(amount) + " / " + fmt(max) : fmt(amount), X + font.width(label), Y, TEXT_COLOR, true);

		// Bar
		int barY = Y + font.lineHeight + 1;
		g.fill(X - 1, barY - 1, X + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, BAR_BACKGROUND);
		double ratio = max > 0 ? Math.min(1.0, Math.max(0.0, amount / max)) : 0.0;
		int filled = (int) Math.round(BAR_WIDTH * ratio);
		if (filled > 0) {
			g.fill(X, barY, X + filled, barY + BAR_HEIGHT, BAR_FILL);
			g.fill(X, barY, X + filled, barY + 1, BAR_FILL_HIGHLIGHT);
		}

		// Active aspect: "Fire Lv 2" (or "No aspect active")
		int lineY = barY + BAR_HEIGHT + 4;
		AspectData aspects = AspectData.of(player);
		AspectType active = aspects.getActive();
		if (active != null) {
			String mastery = "";
			int slot = aspects.findSlot(active);
			if (slot != 0 && ClientAbilityState.xpNeeded() > 0)
				mastery = "  " + (long) Math.floor(aspects.getSlotXp(slot)) + "/" + (long) Math.ceil(ClientAbilityState.xpNeeded()) + " XP";
			var form = net.zyren.cultivation.ability.core.KitResolver.formInUse(player);
			g.drawString(font, active.displayName() + (form == null ? "" : " \u2192 " + form.displayName()) + " Lv " + aspects.getLevel(active) + mastery
					+ (ClientAbilityState.passiveActive() ? "  (passive on)" : ""), X, lineY, LABEL_COLOR, true);
		}
		else
			g.drawString(font, "No aspect active", X, lineY, DIM_COLOR, true);

		// Ability status: armed empowered hit, Reinforce Body timer
		var armed = ClientAbilityState.armedSlot();
		if (armed != null) {
			lineY += font.lineHeight + 1;
			g.drawString(font, armed.displayName() + " armed: next hit", X, lineY, CHOICE_COLOR, true);
		}
		for (var slot : net.zyren.cultivation.ability.core.AbilitySlot.values()) {
			if (!ClientAbilityState.isActive(slot))
				continue;
			var ability = net.zyren.cultivation.ability.core.AbilityService.activeAbility(player, slot);
			if (ability == null)
				continue;
			lineY += font.lineHeight + 1;
			boolean guard = ability.kind() == net.zyren.cultivation.ability.core.Ability.Kind.LETHAL_GUARD;
			g.drawString(font, ability.name() + (guard ? " armed" : " active"), X, lineY, CHOICE_COLOR, true);
		}
		int reinforce = ClientAbilityState.reinforceSeconds();
		if (reinforce > 0) {
			lineY += font.lineHeight + 1;
			g.drawString(font, "Reinforced " + reinforce + "s", X, lineY, LABEL_COLOR, true);
		}

		// Corruption (Pure cores), meditation, Poisoned cores nearby (core level 20+)
		double corruption = net.zyren.cultivation.data.CorruptionData.of(player).getCorruption();
		if (corruption > 0) {
			double cMax = net.zyren.cultivation.config.CorruptionConfig.MAX.get();
			boolean lost = corruption > cMax * net.zyren.cultivation.config.CorruptionConfig.NO_RETURN_FRACTION.get();
			lineY += font.lineHeight + 1;
			g.drawString(font, "Corruption " + (int) Math.floor(corruption / cMax * 100) + "%" + (lost ? "  IRREVERSIBLE" : ""), X, lineY,
					lost ? NULLIFIED_COLOR : CHOICE_COLOR, true);
		}
		int med = ClientCorruptionState.meditationSeconds(), after = ClientCorruptionState.afterSeconds();
		if (med > 0 || after > 0) {
			lineY += font.lineHeight + 1;
			int s = med > 0 ? med : after;
			g.drawString(font, (med > 0 ? "Meditating " : "Meditation focus ") + s / 60 + ":" + String.format("%02d", s % 60), X, lineY, LABEL_COLOR, true);
		}
		long grace = net.zyren.cultivation.system.EmergencyCore.graceTicksLeft(player);
		if (grace > 0) {
			lineY += font.lineHeight + 1;
			long gs = grace / 20;
			g.drawString(font, "Core annihilated: aspects fade " + gs / 60 + ":" + String.format("%02d", gs % 60), X, lineY, NULLIFIED_COLOR, true);
		}
				for (String who : ClientCorruptionState.poisonedNearby()) {
			lineY += font.lineHeight + 1;
			g.drawString(font, "\u2620 Poisoned core: " + who, X, lineY, 0xFF55FF55, true);
		}

		// Nullification: always visible while it lasts
		int sealed = ClientAbilityState.sealedSeconds();
		if (sealed > 0) {
			lineY += font.lineHeight + 1;
			g.drawString(font, "QI SEALED " + sealed + "s", X, lineY, NULLIFIED_COLOR, true);
		} else {
			for (AspectType a : AspectType.values()) {
				int s = ClientAbilityState.nullifiedSeconds(a);
				if (s <= 0)
					continue;
				lineY += font.lineHeight + 1;
				g.drawString(font, a.displayName() + " NULLIFIED " + s + "s", X, lineY, NULLIFIED_COLOR, true);
			}
		}

		// Sensed players (e.g. Fire passive sensing Earth -> Magma / Electric -> Plasma users)
		for (String line : ClientSensed.lines(player)) {
			lineY += font.lineHeight + 1;
			g.drawString(font, line, X, lineY, 0xFFFF9944, true);
		}

		// Aspect switch in progress: thin bar under the aspect line
		if (ClientCultivationState.isSwitching()) {
			int sy = lineY + font.lineHeight + 1;
			g.fill(X - 1, sy - 1, X + BAR_WIDTH + 1, sy + 3, BAR_BACKGROUND);
			g.fill(X, sy, X + (int) Math.round(BAR_WIDTH * ClientCultivationState.switchProgress()), sy + 2, SWITCH_FILL);
			lineY = sy + 4;
		}

		// Pending breakthrough choice hint
		if (aspects.getPendingChoices() > 0) {
			String key = CultivationKeybinds.OPEN_MENU.getTranslatedKeyMessage().getString();
			g.drawString(font, "Breakthrough choice! [" + key + "]", X, lineY + font.lineHeight + 1, CHOICE_COLOR, true);
		}
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
	}
}
