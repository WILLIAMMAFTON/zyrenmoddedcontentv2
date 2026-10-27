package net.zyren.cultivation.client;

import net.minecraft.world.entity.player.Player;

import net.zyren.cultivation.network.SensedPlayersMessage;

import java.util.ArrayList;
import java.util.List;

/** Client copy of the sensed players (HUD only). Stale after ~3 s without an update. */
public final class ClientSensed {
	private static List<SensedPlayersMessage.Entry> entries = List.of();
	private static long receivedAt;

	private ClientSensed() {
	}

	public static void update(List<SensedPlayersMessage.Entry> list) {
		entries = list;
		receivedAt = System.currentTimeMillis();
	}

	public static void reset() {
		entries = List.of();
	}

	/** HUD lines: "Sensing Earth → Magma (Steve) 12m ↗" — arrow relative to where you're looking. */
	public static List<String> lines(Player self) {
		if (entries.isEmpty() || System.currentTimeMillis() - receivedAt > 3000)
			return List.of();
		List<String> out = new ArrayList<>();
		String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
		for (SensedPlayersMessage.Entry e : entries) {
			double dx = e.x() - self.getX(), dz = e.z() - self.getZ();
			int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
			double bearing = Math.toDegrees(Math.atan2(-dx, dz)); // MC yaw convention
			double rel = ((bearing - self.getYRot()) % 360 + 360) % 360;
			String arrow = arrows[(int) Math.round(rel / 45.0) % 8];
			out.add("Sensing " + e.label() + (e.name().isEmpty() ? "" : " (" + e.name() + ")") + " " + dist + "m " + arrow);
		}
		return out;
	}
}
