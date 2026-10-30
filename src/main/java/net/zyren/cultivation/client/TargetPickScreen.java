package net.zyren.cultivation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.network.PacketDistributor;

import net.zyren.cultivation.network.PickTargetRequest;
import net.zyren.cultivation.network.TargetPickMessage;

import java.util.List;

/** One button per target (name + aspect). Esc closes it; the server then picks the nearest when time runs out. */
public class TargetPickScreen extends Screen {
	private static final int BUTTON_WIDTH = 220, BUTTON_HEIGHT = 20, GAP = 4;
	private final List<TargetPickMessage.Option> options;
	private final long closesAt;

	public TargetPickScreen(String title, int seconds, List<TargetPickMessage.Option> options) {
		super(Component.literal(title));
		this.options = options;
		this.closesAt = System.currentTimeMillis() + seconds * 1000L;
	}

	@Override
	protected void init() {
		int total = options.size() * (BUTTON_HEIGHT + GAP);
		int y = height / 2 - total / 2 + 10;
		for (TargetPickMessage.Option o : options) {
			addRenderableWidget(Button.builder(Component.literal(o.label()), b -> {
				PacketDistributor.sendToServer(new PickTargetRequest(o.entityId()));
				onClose();
			}).bounds((width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
			y += BUTTON_HEIGHT + GAP;
		}
	}

	@Override
	public void tick() {
		if (System.currentTimeMillis() > closesAt)
			onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int total = options.size() * (BUTTON_HEIGHT + GAP);
		int top = height / 2 - total / 2;
		long left = Math.max(0, (closesAt - System.currentTimeMillis() + 999) / 1000);
		g.drawCenteredString(font, title, width / 2, top - 24, 0xFF9FD8FF);
		g.drawCenteredString(font, "Pick a target (" + left + "s, then the nearest is chosen)", width / 2, top - 10, 0xFFE0E0E0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
