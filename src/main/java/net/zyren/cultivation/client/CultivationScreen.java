package net.zyren.cultivation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.PacketDistributor;

import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.network.AspectChoiceRequest;
import net.zyren.cultivation.network.CancelSwitchRequest;
import net.zyren.cultivation.network.SwitchAspectRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Cultivation menu (keybind, default K). Presentation only: every button sends a request to the
 * server, which validates it and runs the real system. Values shown come from the synced
 * player variables.
 *
 *   Core Lv 3   Core: PURE   Qi 20 / 50
 *   Pure      Lv 1   ACTIVE         [Use] [+1]
 *   Fire      Lv 2                  [Use] [+1]
 *   - empty slot -
 *   ...
 *   [=====loading bar=====] Switching to Fire...
 *   Breakthrough choices: 1   [< Water >] [Gain]
 *   (second tab "Core": corruption status, Meditate / Cleanse / Charge Crystal, Emergency Core Breakdown toggle)
 *
 * Closing the screen cancels a switch in progress. The game is NOT paused while it's open,
 * so the damage/attack interrupts still apply in singleplayer.
 */
public class CultivationScreen extends Screen {
	private static final int PANEL_WIDTH = 260;
	private static final int ROW_HEIGHT = 22;
	private static final int BUTTON_HEIGHT = 18;
	private static final int USE_WIDTH = 40;
	private static final int UPGRADE_WIDTH = 28;

	private static final int COLOR_TITLE = 0xFFFFFFFF;
	private static final int COLOR_TEXT = 0xFFE0E0E0;
	private static final int COLOR_DIM = 0xFF808080;
	private static final int COLOR_ACTIVE = 0xFF7FE3D6;
	private static final int COLOR_GOOD = 0xFF80FF80;
	private static final int COLOR_BAD = 0xFFFF8080;
	private static final int BAR_BG = 0x99000000;
	private static final int BAR_FILL = 0xFF3FBFB0;

	/** Index into the list of aspects the player could gain (cycled by the selector button). */
	private int newAspectIndex = 0;
	/** 0 = Aspects, 1 = Core. Remembered while the game is running. */
	private static int tab = 0;
	/** Snapshot of synced data used to rebuild buttons when something changes. */
	private String lastSignature = "";

	private int left;
	private int top;

	public CultivationScreen() {
		super(Component.literal("Cultivation"));
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
		AspectData a = AspectData.of(player);
		lastSignature = signature(player);

		left = (width - PANEL_WIDTH) / 2;
		top = Math.max(6, height / 2 - 105);

		// Tabs (the menu is too tall for one page at larger GUI scales)
		int tw = (PANEL_WIDTH - 4) / 2;
		Button aspectsTab = Button.builder(Component.literal("Aspects"), b -> switchTab(0)).bounds(left, top + 26, tw, BUTTON_HEIGHT).build();
		Button coreTab = Button.builder(Component.literal("Core"), b -> switchTab(1)).bounds(left + tw + 4, top + 26, tw, BUTTON_HEIGHT).build();
		aspectsTab.active = tab != 0;
		coreTab.active = tab != 1;
		addRenderableWidget(aspectsTab);
		addRenderableWidget(coreTab);

		if (tab == 0)
			initAspects(a);
		else
			initCore(player);
	}

	private void switchTab(int t) {
		tab = t;
		rebuildWidgets();
	}

	private void initAspects(AspectData a) {
		boolean canChoose = a.getPendingChoices() > 0;
		int buttonsRight = left + PANEL_WIDTH;

		// Pure row + 4 slot rows
		int y = rowsTop();
		addAspectRowButtons(AspectType.PURE, a, y, buttonsRight, canChoose);
		for (int s = 1; s <= AspectData.SLOT_COUNT; s++) {
			y += ROW_HEIGHT;
			AspectType t = a.getSlotType(s);
			if (t != null)
				addAspectRowButtons(t, a, y, buttonsRight, canChoose);
		}

		// New-aspect selector + gain button
		List<AspectType> gainable = gainable(a);
		if (canChoose && !gainable.isEmpty() && a.firstEmptySlot() != 0) {
			newAspectIndex = Math.floorMod(newAspectIndex, gainable.size());
			int cy = choicesTop();
			addRenderableWidget(Button.builder(Component.literal("< " + gainable.get(newAspectIndex).displayName() + " >"), b -> {
				newAspectIndex++;
				rebuildWidgets();
			}).bounds(left, cy + 12, 100, BUTTON_HEIGHT).build());
			AspectType pick = gainable.get(newAspectIndex);
			addRenderableWidget(Button.builder(Component.literal("Gain new aspect"), b -> PacketDistributor.sendToServer(new AspectChoiceRequest(true, pick.id())))
					.bounds(left + 104, cy + 12, 100, BUTTON_HEIGHT).build());
		}
	}

	private void initCore(Player player) {
		// Core actions: meditate, cleanse corruption, recharge the Purifying Crystal
		if (QiData.of(player).getCoreLevel() >= 1) {
			int ay = actionsTop();
			int w = (PANEL_WIDTH - 8) / 3;
			addRenderableWidget(Button.builder(Component.literal("Meditate"),
					b -> PacketDistributor.sendToServer(new net.zyren.cultivation.network.CultivationActionRequest(net.zyren.cultivation.network.CultivationActionRequest.MEDITATE)))
					.bounds(left, ay, w, BUTTON_HEIGHT).build());
			addRenderableWidget(Button.builder(Component.literal("Cleanse Core"),
					b -> PacketDistributor.sendToServer(new net.zyren.cultivation.network.CultivationActionRequest(net.zyren.cultivation.network.CultivationActionRequest.CLEANSE)))
					.bounds(left + w + 4, ay, w, BUTTON_HEIGHT).build());
			addRenderableWidget(Button.builder(Component.literal("Charge Crystal"),
					b -> PacketDistributor.sendToServer(new net.zyren.cultivation.network.CultivationActionRequest(net.zyren.cultivation.network.CultivationActionRequest.RECHARGE_CRYSTAL)))
					.bounds(left + 2 * (w + 4), ay, w, BUTTON_HEIGHT).build());
		}

		// The one player toggle for Emergency Core Breakdown / Annihilation / self-core-breaking abilities
		boolean on = net.zyren.cultivation.data.EmergencyCoreData.of(player).isAllowed();
		addRenderableWidget(Button.builder(Component.literal("Allow Emergency Core Breakdown: " + (on ? "ON" : "OFF")),
				b -> PacketDistributor.sendToServer(new net.zyren.cultivation.network.CultivationActionRequest(net.zyren.cultivation.network.CultivationActionRequest.TOGGLE_EMERGENCY)))
				.bounds(left, emergencyTop(), PANEL_WIDTH, BUTTON_HEIGHT).build());
	}

	/** Core tab layout: status line, action buttons, toggle, notes. */
	private int actionsTop() {
		return rowsTop() + 14;
	}

	private int emergencyTop() {
		return actionsTop() + BUTTON_HEIGHT + 10;
	}

	private void addAspectRowButtons(AspectType type, AspectData a, int y, int right, boolean canChoose) {
		int x = right - USE_WIDTH - (canChoose ? UPGRADE_WIDTH + 4 : 0);
		if (a.getActive() == type && !net.zyren.cultivation.data.SubAspectType.of(type).isEmpty()) {
			// The active aspect's row: open its sub-aspect screen instead of the (pointless) Use button.
			Button sub = Button.builder(Component.literal("Sub"), b -> minecraft.setScreen(new SubAspectScreen(type)))
					.bounds(x, y - 5, USE_WIDTH, BUTTON_HEIGHT).build();
			sub.active = !ClientCultivationState.isSwitching(); // leaving this screen would cancel the switch
			addRenderableWidget(sub);
		} else {
			Button use = Button.builder(Component.literal("Use"), b -> PacketDistributor.sendToServer(new SwitchAspectRequest(type.id())))
					.bounds(x, y - 5, USE_WIDTH, BUTTON_HEIGHT).build();
			use.active = a.getActive() != type && ClientCultivationState.switchTarget() != type;
			addRenderableWidget(use);
		}
		if (canChoose) {
			addRenderableWidget(Button.builder(Component.literal("+1"), b -> PacketDistributor.sendToServer(new AspectChoiceRequest(false, type.id())))
					.bounds(right - UPGRADE_WIDTH, y - 5, UPGRADE_WIDTH, BUTTON_HEIGHT).build());
		}
	}

	@Override
	public void tick() {
		super.tick();
		Player player = minecraft.player;
		if (player == null) {
			onClose();
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
		QiData q = QiData.of(player);
		AspectData a = AspectData.of(player);

		g.drawCenteredString(font, title, width / 2, top, COLOR_TITLE);
		g.drawCenteredString(font, "Core Lv " + q.getCoreLevel() + "   Core: " + q.getCoreType().id() + "   Qi " + fmt(q.getCoreAmount()) + " / " + fmt(q.getCoreMax()),
				width / 2, top + 12, COLOR_TEXT);

		int msgY = tab == 0 ? renderAspects(g, a) : renderCore(g, player, q);

		String msg = ClientCultivationState.message();
		if (!msg.isEmpty())
			g.drawCenteredString(font, msg, width / 2, msgY, ClientCultivationState.messageSuccess() ? COLOR_GOOD : COLOR_BAD);
	}

	/** @return y for the feedback message */
	private int renderAspects(GuiGraphics g, AspectData a) {
		// Aspect rows
		int y = rowsTop();
		drawAspectRow(g, AspectType.PURE, a.getPureLevel(), a, y, "always");
		for (int s = 1; s <= AspectData.SLOT_COUNT; s++) {
			y += ROW_HEIGHT;
			AspectType t = a.getSlotType(s);
			if (t == null)
				g.drawString(font, "- empty slot " + s + " -", left, y, COLOR_DIM, false);
			else
				drawAspectRow(g, t, a.getSlotLevel(s), a, y, null);
		}

		// Switch loading bar
		int barY = y + ROW_HEIGHT - 6;
		if (ClientCultivationState.isSwitching()) {
			int barW = PANEL_WIDTH;
			int filled = (int) Math.round(barW * ClientCultivationState.switchProgress());
			g.fill(left - 1, barY - 1, left + barW + 1, barY + 7, BAR_BG);
			g.fill(left, barY, left + filled, barY + 6, BAR_FILL);
			g.drawCenteredString(font, "Switching to " + ClientCultivationState.switchTarget().displayName() + "... (damage, attacking, using Qi or closing cancels)", width / 2, barY + 9, COLOR_TEXT);
		}

		// Breakthrough choices
		int cy = choicesTop();
		if (a.getPendingChoices() > 0) {
			g.drawString(font, "Breakthrough choices: " + a.getPendingChoices(), left, cy, COLOR_GOOD, false);
			if (a.firstEmptySlot() == 0)
				g.drawString(font, "Slots full: use +1 to upgrade", left, cy + 12, COLOR_DIM, false);
		} else {
			g.drawString(font, "No breakthrough choices pending.", left, cy, COLOR_DIM, false);
		}
		return cy + 34;
	}

	/** @return y for the feedback message */
	private int renderCore(GuiGraphics g, Player player, QiData q) {
		// Corruption + meditation status above the action buttons
		if (q.getCoreLevel() >= 1) {
			double corruption = net.zyren.cultivation.data.CorruptionData.of(player).getCorruption();
			double max = net.zyren.cultivation.config.CorruptionConfig.MAX.get();
			String status = q.getCoreType() == net.zyren.cultivation.data.CoreType.PURE
					? "Corruption " + (int) Math.floor(corruption / max * 100) + "% (" + (int) corruption + "/" + (int) max + ")"
					: "Core: " + q.getCoreType().id();
			int med = ClientCorruptionState.meditationSeconds();
			if (med > 0)
				status += "   Meditating " + med / 60 + ":" + String.format("%02d", med % 60);
			g.drawCenteredString(font, status, width / 2, rowsTop(), corruption > max * net.zyren.cultivation.config.CorruptionConfig.NO_RETURN_FRACTION.get() ? COLOR_BAD : COLOR_DIM);
		} else {
			g.drawCenteredString(font, "No Qi Core.", width / 2, rowsTop(), COLOR_DIM);
		}

		int y = emergencyTop() + BUTTON_HEIGHT + 6;
		g.drawCenteredString(font, "ON: using Ability 1/2 without enough Qi costs a core level", width / 2, y, COLOR_DIM);
		g.drawCenteredString(font, "(twice in 30 min destroys the Core). OFF: never.", width / 2, y + 11, COLOR_DIM);
		y += 26;
		long grace = net.zyren.cultivation.system.EmergencyCore.graceTicksLeft(player);
		if (grace > 0) {
			long gs = grace / 20;
			g.drawCenteredString(font, "Core annihilated: aspects fade in " + gs / 60 + ":" + String.format("%02d", gs % 60), width / 2, y, COLOR_BAD);
			y += 12;
		}
		return y + 4;
	}

	private void drawAspectRow(GuiGraphics g, AspectType type, int level, AspectData a, int y, String note) {
		boolean active = a.getActive() == type;
		g.drawString(font, type.displayName(), left, y, active ? COLOR_ACTIVE : COLOR_TEXT, false);
		g.drawString(font, "Lv " + level, left + 80, y, COLOR_TEXT, false);
		if (active)
			g.drawString(font, "ACTIVE", left + 120, y, COLOR_ACTIVE, false);
		else if (note != null)
			g.drawString(font, note, left + 120, y, COLOR_DIM, false);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// Pressing the menu key again closes the menu.
		if (CultivationKeybinds.OPEN_MENU.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void removed() {
		// Closing the menu interrupts an aspect switch in progress.
		if (ClientCultivationState.isSwitching())
			PacketDistributor.sendToServer(CancelSwitchRequest.INSTANCE);
		super.removed();
	}

	private int rowsTop() {
		return top + 54;
	}

	private int choicesTop() {
		return rowsTop() + ROW_HEIGHT * (AspectData.SLOT_COUNT + 1) + 14;
	}

	private static List<AspectType> gainable(AspectData a) {
		List<AspectType> list = new ArrayList<>();
		for (AspectType t : AspectType.slottable())
			if (!a.owns(t) && net.zyren.cultivation.system.AspectChoices.offered(t))
				list.add(t);
		return list;
	}

	/** Everything the buttons depend on; when it changes the buttons are rebuilt. */
	private static String signature(Player player) {
		AspectData a = AspectData.of(player);
		StringBuilder sb = new StringBuilder();
		sb.append(a.getActive()).append('|').append(a.getPendingChoices()).append('|').append(ClientCultivationState.switchTarget())
				.append('|').append(QiData.of(player).getCoreLevel() >= 1)
				.append('|').append(net.zyren.cultivation.data.EmergencyCoreData.of(player).isAllowed());
		for (int s = 1; s <= AspectData.SLOT_COUNT; s++)
			sb.append('|').append(a.getSlotType(s));
		return sb.toString();
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
	}
}
