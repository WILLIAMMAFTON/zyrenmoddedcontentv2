package net.zyren.cultivation.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.network.PacketDistributor;

import net.zyren.cultivation.ability.core.Ability;
import net.zyren.cultivation.ability.core.AbilitySlot;
import net.zyren.cultivation.ability.core.QiCosts;
import net.zyren.cultivation.data.AspectData;
import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.data.QiData;
import net.zyren.cultivation.network.UseAbilityRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Radial ability menu. Hold the Ability Wheel key (default R), point the mouse at an ability,
 * release the key to cast it (or click). Releasing in the middle casts nothing.
 *
 * Presentation only: casting sends a request; the server runs every check (Qi, cooldown...).
 * The game is not paused while it's open.
 */
public class RadialAbilityScreen extends Screen {
	private static final int RADIUS = 80;
	private static final int DEADZONE = 22;
	private static final int BOX_W = 92;
	private static final int BOX_H = 26;

	private static final int COLOR_BOX = 0xB0101418;
	private static final int COLOR_BOX_HOVER = 0xD02A6B66;
	private static final int COLOR_BORDER = 0xFF3FBFB0;
	private static final int COLOR_NAME = 0xFFFFFFFF;
	private static final int COLOR_OK = 0xFF7FE3D6;
	private static final int COLOR_WARN = 0xFFFFD060;
	private static final int COLOR_BAD = 0xFFFF7070;
	private static final int COLOR_DIM = 0xFF909090;

	private final List<Ability> entries = new ArrayList<>();
	private int hovered = -1;
	private boolean done;

	public RadialAbilityScreen() {
		super(Component.literal("Abilities"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		entries.clear();
		Player player = minecraft.player;
		if (player == null)
			return;
		// Normal kit with the equipped sub-aspect's overrides applied (when in sub-aspect form).
		entries.addAll(net.zyren.cultivation.ability.core.KitResolver.abilities(player).values());
	}

	@Override
	public void tick() {
		// If the key was released before the screen could see it (quick tap), act as a release.
		long window = minecraft.getWindow().getWindow();
		var key = CultivationKeybinds.ABILITY_WHEEL.getKey();
		if (key.getType() == InputConstants.Type.KEYSYM && !InputConstants.isKeyDown(window, key.getValue()))
			release();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Player player = minecraft.player;
		if (player == null)
			return;
		int cx = width / 2, cy = height / 2;
		AspectType active = AspectData.of(player).getActive();

		// Centre: aspect + Qi
		String centre = active == null ? "No aspect active" : active.displayName();
		g.drawCenteredString(font, centre, cx, cy - 10, COLOR_OK);
		QiData q = QiData.of(player);
		g.drawCenteredString(font, "Qi " + fmt(q.getCoreAmount()) + " / " + fmt(q.getCoreMax()), cx, cy + 2, COLOR_NAME);

		if (entries.isEmpty()) {
			String msg = active == null ? "Activate an aspect in the cultivation menu (K)." : active.displayName() + " has no abilities yet.";
			g.drawCenteredString(font, msg, cx, cy + 30, COLOR_DIM);
			return;
		}

		hovered = pick(mouseX - cx, mouseY - cy);
		int n = entries.size();
		for (int i = 0; i < n; i++) {
			double angle = angleOf(i, n);
			int bx = cx + (int) Math.round(Math.cos(angle) * RADIUS) - BOX_W / 2;
			int by = cy + (int) Math.round(Math.sin(angle) * RADIUS) - BOX_H / 2;
			boolean hover = i == hovered;
			if (hover)
				g.fill(bx - 1, by - 1, bx + BOX_W + 1, by + BOX_H + 1, COLOR_BORDER);
			g.fill(bx, by, bx + BOX_W, by + BOX_H, hover ? COLOR_BOX_HOVER : COLOR_BOX);

			Ability a = entries.get(i);
			g.drawCenteredString(font, a.isUnlocked(player) ? a.name() : "???", bx + BOX_W / 2, by + 4, a.isUnlocked(player) ? COLOR_NAME : COLOR_DIM);
			drawStatus(g, player, a, bx + BOX_W / 2, by + 15);
		}

		if (hovered >= 0) {
			Ability a = entries.get(hovered);
			g.drawCenteredString(font, a.slot().displayName(), cx, cy + RADIUS + BOX_H, COLOR_DIM);
		}
		g.drawCenteredString(font, "Release to cast, middle to cancel", cx, height - 20, COLOR_DIM);
	}

	private void drawStatus(GuiGraphics g, Player player, Ability a, int x, int y) {
		if (!a.isUnlocked(player)) {
			g.drawCenteredString(font, a.requiredLevel() < 0 ? "Disabled" : "Locked (Lv " + a.requiredLevel() + ")", x, y, COLOR_DIM);
			return;
		}
		if (!a.missingValues().isEmpty()) {
			g.drawCenteredString(font, "Not finalized", x, y, COLOR_DIM);
			return;
		}
		if (ClientAbilityState.isActive(a.slot())) {
			g.drawCenteredString(font, a.kind() == Ability.Kind.LETHAL_GUARD ? "ARMED" : "ACTIVE", x, y, COLOR_OK);
			return;
		}
		if (ClientAbilityState.armedSlot() == a.slot()) {
			g.drawCenteredString(font, "ARMED", x, y, COLOR_OK);
			return;
		}
		int cd = ClientAbilityState.cooldownSeconds(a.slot());
		if (cd > 0) {
			g.drawCenteredString(font, "Cooldown " + cd + "s", x, y, COLOR_WARN);
			return;
		}
		double base = a.displayCost(player);
		double cost = QiCosts.effective(player, base);
		if (a.costCappedAtCurrentQi())
			cost = Math.min(cost, QiData.of(player).getCoreAmount());
		boolean affordable = QiData.of(player).getCoreAmount() >= cost;
		g.drawCenteredString(font, fmt(cost) + " Qi", x, y, affordable ? COLOR_OK : COLOR_BAD);
	}

	private int pick(int dx, int dy) {
		if (dx * dx + dy * dy < DEADZONE * DEADZONE)
			return -1;
		int n = entries.size();
		double angle = Math.atan2(dy, dx); // 0 = right, clockwise in screen space
		double step = 2 * Math.PI / n;
		double rel = angle - angleOf(0, n);
		int idx = (int) Math.round(rel / step);
		return Math.floorMod(idx, n);
	}

	/** Entry i's angle: first entry at the top, then clockwise. */
	private static double angleOf(int i, int n) {
		return -Math.PI / 2 + i * (2 * Math.PI / n);
	}

	private void release() {
		if (done)
			return;
		done = true;
		if (hovered >= 0 && hovered < entries.size()) {
			AbilitySlot slot = entries.get(hovered).slot();
			PacketDistributor.sendToServer(new UseAbilityRequest(slot.ordinal()));
		}
		onClose();
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (CultivationKeybinds.ABILITY_WHEEL.matches(keyCode, scanCode)) {
			release();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			hovered = pick((int) mouseX - width / 2, (int) mouseY - height / 2);
			release();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
	}
}
