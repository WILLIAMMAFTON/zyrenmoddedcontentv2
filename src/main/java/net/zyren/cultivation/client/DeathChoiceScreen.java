package net.zyren.cultivation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.network.PacketDistributor;

import net.zyren.cultivation.data.AspectType;
import net.zyren.cultivation.network.DeathChoiceRequest;

import java.util.List;

/** "Choose an Aspect to Lose": one button per tied aspect. Can't be closed without choosing. */
public class DeathChoiceScreen extends Screen {
	private static final int BUTTON_WIDTH = 200, BUTTON_HEIGHT = 20, GAP = 4;
	private final List<String> options;

	public DeathChoiceScreen(List<String> options) {
		super(Component.literal("Choose an Aspect to Lose"));
		this.options = options;
	}

	@Override
	protected void init() {
		int total = options.size() * (BUTTON_HEIGHT + GAP);
		int y = height / 2 - total / 2 + 10;
		for (String id : options) {
			AspectType t = AspectType.fromId(id);
			if (t == null)
				continue;
			String label = t == AspectType.PURE ? "Pure: lose 2 levels" : "Remove " + t.displayName();
			addRenderableWidget(Button.builder(Component.literal(label), b -> {
				ClientDeathChoice.clear();
				PacketDistributor.sendToServer(new DeathChoiceRequest(id));
				onClose();
			}).bounds((width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
			y += BUTTON_HEIGHT + GAP;
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int total = options.size() * (BUTTON_HEIGHT + GAP);
		int top = height / 2 - total / 2;
		g.drawCenteredString(font, title, width / 2, top - 24, 0xFFFF5555);
		g.drawCenteredString(font, "Your highest aspects are tied. The one you pick is lost.", width / 2, top - 10, 0xFFE0E0E0);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
