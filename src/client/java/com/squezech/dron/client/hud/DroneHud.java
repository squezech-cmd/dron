package com.squezech.dron.client.hud;

import com.squezech.dron.entity.CombatDroneEntity;
import com.squezech.dron.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Military FPV HUD of the drone controller.
 *
 * <p>Everything is drawn with {@link GuiGraphics} primitives (fills and text), so
 * it needs no extra texture slots and stays crisp at every GUI scale.
 */
public final class DroneHud {
	private static final int GREEN = 0xFF64F5B4;
	private static final int GREEN_DIM = 0xB03CB98A;
	private static final int GREEN_DARK = 0x5520704F;
	private static final int RED = 0xFFFF5148;
	private static final int AMBER = 0xFFFFC046;
	private static final int TEXT = 0xFFE9FFF6;
	private static final int TEXT_DIM = 0xFF86A99B;
	private static final int PANEL = 0xB20A1512;

	private static int fuelSync = CombatDroneEntity.MAX_FUEL;
	private static int signalSync = CombatDroneEntity.MAX_SIGNAL_BLOCKS;
	private static long lastSync;

	private DroneHud() {
	}

	public static void acceptTelemetry(int droneId, int fuel, int signal) {
		fuelSync = fuel;
		signalSync = signal;
		lastSync = System.currentTimeMillis();
	}

	public static void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (client.player.getVehicle() instanceof CombatDroneEntity drone
				&& System.currentTimeMillis() - lastSync > 400L) {
			fuelSync = drone.getFuel();
			signalSync = drone.getSignal();
		}
	}

	/**
	 * R key: recall the drone. The server side of {@code dron:recall} does the work.
	 */
	public static void requestRecall(Minecraft client) {
		if (client.getConnection() == null) {
			return;
		}
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				com.squezech.dron.network.DronNetwork.RECALL,
				net.fabricmc.fabric.api.networking.v1.PacketByteBufs.empty());
	}

	// ------------------------------------------------------------------ entry

	public static void render(GuiGraphics g, float tickDelta) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.options.hideGui || client.screen != null) {
			return;
		}

		if (player.getVehicle() instanceof CombatDroneEntity drone) {
			renderCockpit(g, client, player, drone, tickDelta);
			return;
		}

		boolean holding = player.getMainHandItem().is(ModItems.DRONE_REMOTE)
				|| player.getOffhandItem().is(ModItems.DRONE_REMOTE);
		if (holding) {
			renderStandby(g, client);
		}
	}

	private static void renderCockpit(GuiGraphics g, Minecraft client, LocalPlayer player, CombatDroneEntity drone,
			float tickDelta) {
		int w = client.getWindow().getGuiScaledWidth();
		int h = client.getWindow().getGuiScaledHeight();
		var font = client.font;
		int cx = w / 2;
		int cy = h / 2;

		float fuel = Mth.clamp(fuelSync / (float) CombatDroneEntity.MAX_FUEL, 0.0F, 1.0F);
		float link = Mth.clamp(signalSync / (float) CombatDroneEntity.MAX_SIGNAL_BLOCKS, 0.0F, 1.0F);
		Vec3 velocity = drone.getDeltaMovement().scale(20.0D);
		double speed = velocity.length();
		int altitude = (int) Math.max(0.0F, (float) (drone.getY() - client.level.getSeaLevel()));
		int distance = (int) drone.position().distanceTo(player.position());

		boolean lowFuel = fuel < 0.2F;
		boolean weakLink = link < 0.25F;
		boolean blink = (System.currentTimeMillis() / 300L) % 2L == 0L;

		drawScanlines(g, w, h);
		drawCornerFrame(g, w, h);

		// --- top: callsign strip + link lamp --------------------------------
		String callsign = "DRON-01  //  " + player.getGameProfile().getName().toUpperCase();
		int callW = font.width(callsign) + 24;
		panel(g, cx - callW / 2, 6, callW, 15);
		text(g, font, callsign, cx - callW / 2 + 12, 10, GREEN);

		g.fill(cx + callW / 2 - 8, 11, cx + callW / 2 - 4, 15, weakLink ? RED : GREEN);

		// --- compass tape ---------------------------------------------------
		drawCompass(g, client, cx, 26, player.getYRot());

		// --- horizon / reticle ----------------------------------------------
		drawHorizon(g, cx, cy, drone, tickDelta, lowFuel || weakLink);
		drawReticle(g, cx, cy, weakLink && blink);

		// --- left: flight data ----------------------------------------------
		int lx = 14;
		int ly = cy - 56;
		gauge(g, font, lx, ly, "СКОРОСТЬ", Math.round(speed * 72.0D) + " км/ч", speed / (float) CombatDroneEntity.MAX_SPEED, GREEN);
		gauge(g, font, lx, ly + 36, "ВЫСОТА", altitude + " м", Mth.clamp(altitude / 150.0F, 0.0F, 1.0F), GREEN);
		gauge(g, font, lx, ly + 72, "ДИСТ.", (distance / 40) + " x40 м", Mth.clamp(distance / 260.0F, 0.0F, 1.0F),
				distance > 200 ? AMBER : GREEN);

		// --- right: systems -------------------------------------------------
		int rx = w - 14 - 92;
		gauge(g, font, rx, ly, "БАТАРЕЯ", Math.round(fuel * 100.0F) + "%", fuel, lowFuel ? RED : GREEN);
		gauge(g, font, rx, ly + 36, "СИГНАЛ", signalSync + " м", link, weakLink ? RED : GREEN);
		armedStrip(g, font, rx, ly + 72, !lowFuel && !weakLink);

		// --- bottom hints ---------------------------------------------------
		String hint = "WASD — тяга    ПРОБЕЛ — вверх    SHIFT — вниз    R — отмена";
		int hintW = font.width(hint) + 22;
		panel(g, cx - hintW / 2, h - 24, hintW, 14);
		text(g, font, hint, cx - hintW / 2 + 11, h - 20, TEXT_DIM);

		// --- warnings -------------------------------------------------------
		if (weakLink && blink) {
			banner(g, font, cx, cy - 74, "ПОТЕРЯ СИГНАЛА", RED);
		} else if (lowFuel && blink) {
			banner(g, font, cx, cy - 74, "НИЗКИЙ ЗАРЯД", AMBER);
		}

		// --- recording lamp -------------------------------------------------
		if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
			g.fill(w - 28, 20, w - 22, 26, RED);
		}
		text(g, font, "REC", w - 46, 20, 0xFFFF8F88);
		text(g, font, "FPV-01", w - 46, 34, GREEN_DIM);
	}

	private static void renderStandby(GuiGraphics g, Minecraft client) {
		int w = client.getWindow().getGuiScaledWidth();
		int h = client.getWindow().getGuiScaledHeight();
		var font = client.font;
		String line = "ПУЛЬТ ДРОНА ГОТОВ";
		int width = font.width(line) + 20;
		panel(g, w / 2 - width / 2, h - 40, width, 14);
		text(g, font, line, w / 2 - width / 2 + 10, h - 36, GREEN);
	}

	// ------------------------------------------------------------------ parts

	private static void drawScanlines(GuiGraphics g, int w, int h) {
		for (int y = 0; y < h; y += 4) {
			g.fill(0, y, w, y + 1, 0x1E00120C);
		}
		for (int y = 0; y < h; y += 12) {
			g.fill(0, y, w, y + 1, 0x10000000);
		}
		// vignette
		g.fill(0, 0, w, 2, 0x66040A08);
		g.fill(0, h - 2, w, h, 0x66040A08);
		g.fill(0, 0, 2, h, 0x66040A08);
		g.fill(w - 2, 0, w, h, 0x66040A08);
	}

	private static void drawCornerFrame(GuiGraphics g, int w, int h) {
		int len = 30;
		g.fill(5, 5, 5 + len, 6, GREEN);
		g.fill(5, 5, 6, 5 + len, GREEN);
		g.fill(w - 5 - len, 5, w - 5, 6, GREEN);
		g.fill(w - 6, 5, w - 5, 5 + len, GREEN);
		g.fill(5, h - 6, 5 + len, h - 5, GREEN);
		g.fill(5, h - 5 - len, 6, h - 5, GREEN);
		g.fill(w - 5 - len, h - 6, w - 5, h - 5, GREEN);
		g.fill(w - 6, h - 5 - len, w - 5, h - 5, GREEN);

		// side ticks
		for (int y = h / 4; y < h * 3 / 4; y += 8) {
			g.fill(5, y, 9, y + 1, GREEN_DARK);
			g.fill(w - 9, y, w - 5, y + 1, GREEN_DARK);
		}
	}

	private static void drawCompass(GuiGraphics g, Minecraft client, int cx, int y, float yaw) {
		var font = client.font;
		int tapeW = 150;
		int left = cx - tapeW / 2;
		g.fill(left, y, left + tapeW, y + 12, PANEL);
		g.fill(left, y + 12, left + tapeW, y + 13, GREEN_DIM);

		String[] labels = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
		float heading = Mth.wrapDegrees(yaw);

		for (int deg = 0; deg < 360; deg += 15) {
			float diff = Mth.wrapDegrees(deg - heading);
			if (Math.abs(diff) > 72.0F) {
				continue;
			}
			int px = cx + (int) (diff / 75.0F * (tapeW / 2.0F));
			boolean major = deg % 45 == 0;
			g.fill(px, y + 2, px + 1, y + 2 + (major ? 5 : 2), major ? GREEN : GREEN_DIM);
			if (major) {
				String label = labels[deg / 45];
				text(g, font, label, px - font.width(label) / 2, y + 8, TEXT);
			}
		}

		g.fill(cx, y - 2, cx + 1, y + 14, AMBER);
		g.fill(cx - 2, y + 13, cx + 4, y + 14, AMBER);
	}

	private static void drawHorizon(GuiGraphics g, int cx, int cy, CombatDroneEntity drone, float tickDelta,
			boolean alarm) {
		float pitch = Mth.lerp(tickDelta, drone.xRotO, drone.getXRot());
		int offset = (int) (pitch * 1.4F);
		int color = alarm ? AMBER : GREEN_DIM;
		int half = 58;

		g.fill(cx - half, cy + offset, cx - 18, cy + offset + 1, color);
		g.fill(cx + 18, cy + offset, cx + half, cy + offset + 1, color);
		g.fill(cx - half, cy + offset - 5, cx - half + 14, cy + offset - 4, GREEN_DARK);
		g.fill(cx + half - 14, cy + offset - 5, cx + half, cy + offset - 4, GREEN_DARK);

		// roll tick marks
		float roll = Mth.lerp(tickDelta, drone.yRotO, drone.getYRot());
		for (int i = -2; i <= 2; i++) {
			int y = cy + offset + i * 9;
			g.fill(cx - half + 2, y, cx - half + 6, y + 1, GREEN_DARK);
		}
	}

	private static void drawReticle(GuiGraphics g, int cx, int cy, boolean alarm) {
		int c = alarm ? RED : GREEN;
		// brackets
		g.fill(cx - 16, cy - 12, cx - 7, cy - 11, c);
		g.fill(cx + 7, cy - 12, cx + 16, cy - 11, c);
		g.fill(cx - 16, cy + 11, cx - 7, cy + 12, c);
		g.fill(cx + 7, cy + 11, cx + 16, cy + 12, c);
		g.fill(cx - 16, cy - 12, cx - 15, cy - 4, c);
		g.fill(cx + 15, cy - 12, cx + 16, cy - 4, c);
		g.fill(cx - 16, cy + 4, cx - 15, cy + 12, c);
		g.fill(cx + 15, cy + 4, cx + 16, cy + 12, c);
		// centre dot + ticks
		g.fill(cx - 1, cy - 1, cx + 1, cy + 1, c);
		g.fill(cx - 5, cy, cx - 3, cy + 1, c);
		g.fill(cx + 3, cy, cx + 5, cy + 1, c);
	}

	private static void gauge(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, String label,
			String value, float ratio, int color) {
		int barW = 92;
		panel(g, x, y, barW + 6, 30);
		text(g, font, label, x + 4, y + 3, TEXT_DIM);
		text(g, font, value, x + barW + 2 - font.width(value), y + 3, TEXT);

		int by = y + 16;
		g.fill(x + 4, by, x + barW + 2, by + 7, 0x99000402);
		int filled = (int) (Mth.clamp(ratio, 0.0F, 1.0F) * (barW - 2));
		for (int i = 0; i < filled; i += 5) {
			int seg = Math.min(4, filled - i);
			g.fill(x + 5 + i, by + 1, x + 5 + i + seg, by + 6, color);
		}
		// centre notch
		g.fill(x + 4, by - 1, x + barW + 2, by, GREEN_DARK);
	}

	private static void armedStrip(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, boolean armed) {
		int barW = 92;
		panel(g, x, y, barW + 6, 30);
		text(g, font, "БОЕВЗВОД", x + 4, y + 3, TEXT_DIM);
		String state = armed ? "ВЗВЕДЁН" : "ОТКАЗ";
		text(g, font, state, x + barW + 2 - font.width(state), y + 3, armed ? GREEN : RED);

		int by = y + 16;
		g.fill(x + 4, by, x + barW + 2, by + 2, armed ? GREEN : RED);
		if (armed) {
			int pulse = (int) ((System.currentTimeMillis() / 5L) % 8);
			g.fill(x + 6 + pulse, by + 4, x + 12 + pulse, by + 6, GREEN_DIM);
			g.fill(x + barW - 6 - pulse, by + 4, x + barW - pulse, by + 6, GREEN_DIM);
		} else {
			g.fill(x + 4, by + 4, x + barW + 2, by + 6, 0x66FF5148);
		}
	}

	private static void banner(GuiGraphics g, net.minecraft.client.gui.Font font, int cx, int y, String text,
			int color) {
		int width = font.width(text) + 22;
		g.fill(cx - width / 2, y - 4, cx + width / 2, y + 12, 0xCC1A0505);
		g.fill(cx - width / 2, y - 4, cx + width / 2, y - 3, color);
		g.fill(cx - width / 2, y + 11, cx + width / 2, y + 12, color);
		text(g, font, text, cx - width / 2 + 11, y, color);
	}

	private static void panel(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, PANEL);
		g.fill(x, y, x + w, y + 1, GREEN_DIM);
		g.fill(x, y + h - 1, x + w, y + h, GREEN_DARK);
	}

	private static void text(GuiGraphics g, net.minecraft.client.gui.Font font, String s, int x, int y, int color) {
		g.drawString(font, s, x, y, color, false);
	}
}
