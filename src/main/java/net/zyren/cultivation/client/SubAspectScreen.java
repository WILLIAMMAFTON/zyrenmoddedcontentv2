package net.zyren.cultivation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.PacketDistributor;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.SubAspectData;
import net.zyren.cultivation.data.SubAspectType;
import net.zyren.cultivation.network.EquipSubAspectRequest;
import net.zyren.cultivation.system.SubAspectTiers;

import java.util.ArrayList;
import java.util.List;

/**
 * Pick which learned sub-aspect of an aspect is equipped (opened from the K menu's "Sub" button
 * on the active aspect's row). Only shows synced data and sends requests; the server decides.
 *
 * Rows: the aspect's three tiers in the server's tier order, then any learned sub-aspect that the
 * server config moved out of every tier (shown locked, so the player can see it wasn't lost).
 */
public class SubAspectScreen extends Screen {
	private static final int PANEL_WIDTH = 260;
	private static final int ROW_HEIGHT = 22;
	private static final int BUTTON_HEIGHT = 18;
	private static final int BUTTON_WIDTH = 64;

	private static final int COLOR_TITLE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFE0E0E0;
	private static final int COLOR_DIM = 0xFF808080;
	private static final int COLOR_ACTIVE = 0xFF7FE3D6;

	private final AspectType aspect;
	private final List<SubAspectType> rows = new ArrayList<>();
	private String lastSignature = "";
	private int left;
	private int top;

	public SubAspectScreen(AspectType aspect) {
		super(Component.literal(aspect.displayName() + " sub-aspects"));
		this.aspect = aspect;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		Player player = minecraft.player;
		if (player == null)
			return;
		lastSignature = signature(player);
		left = (width - PANEL_WIDTH) / 2;
		top = Math.max(6, height / 2 - 80);

		rows.clear();
		rows.addAll(SubAspectTiers.tiers(aspect)); // may contain null (empty tier)
		SubAspectData d = SubAspectData.of(player);
		for (SubAspectType s : d.learned(aspect))
			if (!rows.contains(s))
				rows.add(s);

		int level = AspectData.of(player).getLevel(aspect);
		SubAspectType equipped = d.equipped(aspect);
		int y = rowsTop();
		for (SubAspectType s : rows) {
			if (s != null) {
				boolean isEquipped = s == equipped;
				Button b = Button.builder(Component.literal(isEquipped ? "Unequip" : "Equip"),
						btn -> PacketDistributor.sendToServer(new EquipSubAspectRequest(aspect.id(), isEquipped ? "" : s.id())))
						.bounds(left + PANEL_WIDTH - BUTTON_WIDTH, y - 5, BUTTON_WIDTH, BUTTON_HEIGHT).build();
				b.active = isEquipped || d.hasLearned(s) && SubAspectTiers.levelAllows(s, level);
				addRenderableWidget(b);
			}
			y += ROW_HEIGHT;
		}

		addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(new CultivationScreen()))
				.bounds(left, y + 20, 60, BUTTON_HEIGHT).build());
	}

	@Override
	public void tick() {
		super.tick();
		Player player = minecraft.player;
		if (player == null || AspectData.of(player).getActive() != aspect) {
			onClose(); // aspect switched away (or player gone): this screen no longer applies
			return;
		}
		if (!signature(player).equals(lastSignature))
			rebuildWidgets();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Player player = minecraft.player;
		if (player == null)
			return;
		SubAspectData d = SubAspectData.of(player);
		int level = AspectData.of(player).getLevel(aspect);
		SubAspectType equipped = d.equipped(aspect);
		var form = net.zyren.cultivation.ability.core.KitResolver.formInUse(player);

		g.drawCenteredString(font, title, width / 2, top, COLOR_TITLE);
		g.drawCenteredString(font, aspect.displayName() + " Lv " + level + "   Form: " + (form == null ? "normal" : form.displayName()),
				width / 2, top + 12, COLOR_TEXT);
		g.drawCenteredString(font, "Learn all, equip one. Toggle form: [" + CultivationKeybinds.TOGGLE_FORM.getTranslatedKeyMessage().getString() + "]",
				width / 2, top + 24, COLOR_DIM);

		int y = rowsTop();
		for (int i = 0; i < rows.size(); i++) {
			SubAspectType s = rows.get(i);
			String tier = i < 3 ? "T" + (i + 1) : "--";
			if (s == null) {
				g.drawString(font, tier + "  (empty)", left, y, COLOR_DIM, false);
			} else {
				int req = SubAspectTiers.requiredLevel(s);
				boolean learned = d.hasLearned(s);
				boolean unlocked = SubAspectTiers.levelAllows(s, level);
				String status = s == equipped ? "EQUIPPED" : !learned ? "not learned" : !unlocked ? "locked" : "learned";
				int color = s == equipped ? COLOR_ACTIVE : learned && unlocked ? COLOR_TEXT : COLOR_DIM;
				g.drawString(font, tier + "  " + s.displayName(), left, y, color, false);
				g.drawString(font, req < 0 ? "Lv -" : "Lv " + req, left + 110, y, unlocked ? COLOR_TEXT : COLOR_DIM, false);
				g.drawString(font, status, left + 145, y, color, false);
			}
			y += ROW_HEIGHT;
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (CultivationKeybinds.OPEN_MENU.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private int rowsTop() {
		return top + 44;
	}

	/** Everything the buttons depend on; when it changes the buttons are rebuilt. */
	private String signature(Player player) {
		SubAspectData d = SubAspectData.of(player);
		return d.learned(aspect) + "|" + d.equipped(aspect) + "|" + d.isFormActive() + "|" + AspectData.of(player).getLevel(aspect)
				+ "|" + SubAspectTiers.tiers(aspect);
	}
}
