package com.squezech.dron.client.hud;

import com.squezech.dron.client.DroneClientState;
import com.squezech.dron.entity.CombatDroneEntity;
import com.squezech.dron.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Military FPV cockpit HUD of the drone controller.
 *
 * <p>Everything is drawn with {@link GuiGraphics} primitives (fills, gradients
 * and text), so no extra textures are needed and the overlay stays pixel-crisp
 * at every GUI scale. Layout is anchored around a virtual 320x180 "visor" that
 * scales with the window.
 */
public final class DroneHud {
	private DroneHud() {
	}

	public static void render(GuiGraphics graphics, float tickDelta) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.options.hideGui || client.screen != null || client.level == null) {
			return;
		}

		if (player.getVehicle() instanceof CombatDroneEntity drone) {
			new Cockpit(graphics, client, player, drone).draw();
			return;
		}

		if (player.getMainHandItem().is(ModItems.DRONE_REMOTE) || player.getOffhandItem().is(ModItems.DRONE_REMOTE)) {
			drawStandby(graphics, client);
		}
	}

	// --------------------------------------------------------------- standby

	private static void drawStandby(GuiGraphics g, Minecraft client) {
		Font font = client.font;
		int w = g.guiWidth();
		int h = g.guiHeight();
		int cx = w / 2;
		int y = h - 52;

		String title = "\u25C8  ПУЛЬТ БОЕВОГО ДРОНА  \u25C8";
		int tw = font.width(title);
		g.fillGradient(cx - tw / 2 - 12, y, cx + tw / 2 + 12, y + 30, HudPalette.PANEL_DEEP, 0x00000000 + HudPalette.PANEL);
		g.fill(cx - tw / 2 - 12, y, cx + tw / 2 + 12, y + 1, HudPalette.GREEN);
		g.drawString(font, title, cx - tw / 2, y + 4, HudPalette.GREEN, false);

		String hint = "ПКМ — запуск   \u00B7   W/A/S/D — тяга   \u00B7   ПРОБЕЛ — вверх   \u00B7   SHIFT — вниз   \u00B7   ЛКМ — подрыв   \u00B7   R — отзыв";
		int hw = font.width(hint);
		g.drawString(font, hint, cx - hw / 2, y + 17, HudPalette.TEXT_DIM, false);

		// Soft side lamps so the controller feels "armed".
		long time = System.currentTimeMillis();
		boolean blink = (time / 500L) % 2L == 0L;
		g.fill(cx - tw / 2 - 22, y + 13, cx - tw / 2 - 18, y + 17, blink ? HudPalette.GREEN : HudPalette.GREEN_DARK);
		g.fill(cx + tw / 2 + 18, y + 13, cx + tw / 2 + 22, y + 17, blink ? HudPalette.GREEN : HudPalette.GREEN_DARK);
	}

	// --------------------------------------------------------------- cockpit

	private static final class Cockpit {
		private final GuiGraphics g;
		private final Minecraft client;
		private final LocalPlayer player;
		private final CombatDroneEntity drone;
		private final Font font;

		private final int w;
		private final int h;
		private final int cx;
		private final int cy;
		private final int cx0;
		private final int cy0;

		private final float fuel;
		private final float link;
		private final double speed;
		private final int altitude;
		private final double range;

		private final boolean lowFuel;
		private final boolean weakLink;
		private final boolean jamming;
		private final boolean blink;

		Cockpit(GuiGraphics g, Minecraft client, LocalPlayer player, CombatDroneEntity drone) {
			this.g = g;
			this.client = client;
			this.player = player;
			this.drone = drone;
			this.font = client.font;

			this.w = g.guiWidth();
			this.h = g.guiHeight();
			this.cx = this.w / 2;
			this.cy = this.h / 2;

			// The visor keeps its 16:9 proportions on very wide or tall windows.
			int vh = (int) Mth.clamp(this.h * 0.92F, 120.0F, 320.0F);
			int vw = vh * 16 / 9;
			this.cx0 = this.w / 2 - vw / 2;
			this.cy0 = this.h / 2 - vh / 2;

			this.fuel = DroneClientState.getFuelFraction();
			this.link = DroneClientState.getSignalFraction();
			this.speed = drone.getDeltaMovement().length() * 20.0D;
			this.altitude = (int) Math.max(0.0F, drone.getY() - client.level.getSeaLevel());
			this.range = player.position().distanceTo(drone.position());

			this.lowFuel = this.fuel < 0.2F;
			this.weakLink = this.link < 0.25F;
			this.jamming = DroneClientState.isJamming() || this.link < 0.45F;
			this.blink = (System.currentTimeMillis() / 320L) % 2L == 0L;
		}

		void draw() {
			drawScanlines();
			drawVignette();
			drawCornerFrame();

			drawTopStrip();
			drawCompass();
			drawHorizonLadder();
			drawReticle();
			drawGauges();
			drawBoresightRail();
			drawBottomHint();
			drawWarnings();
			drawRecordingLamp();

			if (this.jamming && this.blink) {
				this.g.fill(this.cx0, this.cy0, this.cx0 + this.w, this.cy0 + this.h, 0x14FF4B44);
			}
		}

		// --- chrome ------------------------------------------------------

		private void drawScanlines() {
			for (int y = 0; y < this.h; y += 3) {
				this.g.fill(0, y, this.w, y + 1, HudPalette.SCANLINE);
			}
			// Slow rolling tear of a cheap analogue downlink.
			int tear = (int) ((System.currentTimeMillis() / 14L) % (this.h + 40)) - 20;
			this.g.fill(0, tear, this.w, tear + 2, 0x18B6FFDC);
		}

		private void drawVignette() {
			int depth = Math.min(28, Math.min(this.w, this.h) / 8);
			for (int i = 0; i < depth; i += 2) {
				int alpha = 0x40 - i * 2;
				if (alpha <= 0) {
					break;
				}
				int colour = (alpha << 24);
				this.g.fill(0, i, this.w, i + 2, colour);
				this.g.fill(0, this.h - i - 2, this.w, this.h - i, colour);
				this.g.fill(i, 0, i + 2, this.h, colour);
				this.g.fill(this.w - i - 2, 0, this.w - i, this.h, colour);
			}
		}

		private void drawCornerFrame() {
			int len = Math.min(36, this.w / 10);
			int inset = 6;
			// top-left
			this.g.fill(inset, inset, inset + len, inset + 1, HudPalette.GREEN);
			this.g.fill(inset, inset, inset + 1, inset + len, HudPalette.GREEN);
			// top-right
			this.g.fill(this.w - inset - len, inset, this.w - inset, inset + 1, HudPalette.GREEN);
			this.g.fill(this.w - inset - 1, inset, this.w - inset, inset + len, HudPalette.GREEN);
			// bottom-left
			this.g.fill(inset, this.h - inset - 1, inset + len, this.h - inset, HudPalette.GREEN);
			this.g.fill(inset, this.h - inset - len, inset + 1, this.h - inset, HudPalette.GREEN);
			// bottom-right
			this.g.fill(this.w - inset - len, this.h - inset - 1, this.w - inset, this.h - inset, HudPalette.GREEN);
			this.g.fill(this.w - inset - 1, this.h - inset - len, this.w - inset, this.h - inset, HudPalette.GREEN);

			// tick ruler down both flanks
			int midTop = this.h / 6;
			int midBottom = this.h - this.h / 6;
			for (int y = midTop; y < midBottom; y += 10) {
				boolean major = (y - midTop) % 50 == 0;
				int tick = major ? 8 : 4;
				this.g.fill(inset, y, inset + tick, y + 1, major ? HudPalette.GREEN_DIM : HudPalette.GREEN_DARK);
				this.g.fill(this.w - inset - tick, y, this.w - inset, y + 1,
						major ? HudPalette.GREEN_DIM : HudPalette.GREEN_DARK);
			}
		}

		// --- top strip ---------------------------------------------------

		private void drawTopStrip() {
			String callsign = "DRON-01 // " + this.player.getGameProfile().getName().toUpperCase();
			int callW = this.font.width(callsign) + 30;
			int x = this.cx - callW / 2;
			int y = this.cy0;

			panel(x, y, callW, 26);
			text(callsign, x + 10, y + 4, HudPalette.GREEN);

			String mode = this.jamming ? "EW JAM" : "LINK OK";
			int modeColour = this.jamming ? HudPalette.RED : HudPalette.GREEN_DIM;
			text(mode, x + 10, y + 15, modeColour);

			// Link lamp on the right side of the strip.
			int lampColour = this.weakLink ? (this.blink ? HudPalette.RED : HudPalette.RED_DIM)
					: (this.jamming ? HudPalette.AMBER : HudPalette.GREEN);
			this.g.fill(x + callW - 18, y + 6, x + callW - 8, y + 16, lampColour);
			this.g.fill(x + callW - 17, y + 7, x + callW - 9, y + 15, this.jamming ? 0x33FFC24A : 0x336CF7B4);

			// Second strip: warhead state + flight timer.
			String fuse = this.drone.isArmed() ? "\u26A0 БОЕВОЙ ВЗВОД" : "НЕ ВЗВЕДЁН";
			int fuseW = this.font.width(fuse) + 16;
			int fy = y + 28;
			int fx = this.cx - fuseW / 2;
			panel(fx, fy, fuseW, 16);
			text(fuse, fx + 8, fy + 4, this.drone.isArmed() ? HudPalette.AMBER : HudPalette.TEXT_DIM);
			if (this.drone.isArmed() && this.blink) {
				this.g.fill(fx + 1, fy + 6, fx + 3, fy + 11, HudPalette.AMBER);
			}
		}

		// --- compass -----------------------------------------------------

		private void drawCompass() {
			int tapeW = Math.min(220, this.w / 3);
			int left = this.cx - tapeW / 2;
			int y = this.cy0 + 48;

			panel(left, y, tapeW, 24);
			this.g.fill(left, y + 23, left + tapeW, y + 24, HudPalette.GREEN_DIM);

			float heading = Mth.wrapDegrees(this.player.getYRot());
			String[] labels = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};

			for (int deg = 0; deg < 360; deg += 15) {
				float diff = Mth.wrapDegrees(deg - heading);
				if (Math.abs(diff) > 75.0F) {
					continue;
				}
				int px = this.cx + (int) (diff / 75.0F * (tapeW / 2.0F) * 0.94F);
				boolean major = deg % 45 == 0;
				int height = major ? 7 : 3;
				this.g.fill(px, y + 3, px + 1, y + 3 + height, major ? HudPalette.GREEN : HudPalette.GREEN_DARK);
				if (major) {
					String label = labels[deg / 45];
					text(label, px - this.font.width(label) / 2, y + 11, HudPalette.TEXT);
				}
			}

			// Heading bug.
			this.g.fill(this.cx, y + 1, this.cx + 1, y + 22, HudPalette.AMBER);
			this.g.fill(this.cx - 3, y + 22, this.cx + 4, y + 23, HudPalette.AMBER);

			String degrees = String.format("%03d", Math.floorMod((int) Math.round(heading), 360));
			panel(this.cx - 18, y + 26, 36, 13);
			text(degrees, this.cx - this.font.width(degrees) / 2, y + 29, HudPalette.AMBER);
		}

		// --- horizon -----------------------------------------------------

		private void drawHorizonLadder() {
			float pitch = Mth.lerp(0.0F, this.drone.xRotO, this.drone.getXRot());
			float roll = Mth.lerp(0.0F, this.drone.yRotO, this.drone.getYRot());

			int pxPerDeg = Math.max(2, this.h / 120);
			int centre = this.cy + (int) (pitch * pxPerDeg);
			int colour = this.jamming ? HudPalette.AMBER : HudPalette.GREEN_DIM;
			int wide = Math.min(90, this.w / 5);

			// main line, split around the reticle
			this.g.fill(this.cx - wide, centre, this.cx - 26, centre + 1, colour);
			this.g.fill(this.cx + 26, centre, this.cx + wide, centre + 1, colour);
			this.g.fill(this.cx - wide - 8, centre - 2, this.cx - wide + 8, centre + 3, colour);
			this.g.fill(this.cx + wide - 8, centre - 2, this.cx + wide + 8, centre + 3, colour);

			// pitch ladder rungs every 10 degrees
			for (int step = -30; step <= 30; step += 10) {
				if (step == 0) {
					continue;
				}
				int y = centre - step * pxPerDeg;
				if (y < this.cy0 + 60 || y > this.cy0 + this.h - 40) {
					continue;
				}
				int half = step % 20 == 0 ? 34 : 20;
				boolean below = step < 0;
				int dash = below ? 3 : 2;
				for (int i = 0; i < half; i += dash + 2) {
					int from = this.cx - half + i;
					this.g.fill(from, y, from + dash, y + 1, HudPalette.GREEN_DARK);
					int mirror = this.cx + half - i;
					this.g.fill(mirror - dash, y, mirror, y + 1, HudPalette.GREEN_DARK);
				}
			}

			// Bank indicator arc.
			int arcR = Math.min(64, this.h / 5);
			for (int a = -60; a <= 60; a += 10) {
				double rad = Math.toRadians(a - roll);
				int x = this.cx + (int) (Math.sin(rad) * arcR);
				int y = this.cy - (int) (Math.cos(rad) * arcR);
				boolean major = Math.abs(a) == 60 || a == 0;
				int len = major ? 4 : 2;
				this.g.fill(x, y, x + len, y + 1, major ? HudPalette.GREEN : HudPalette.GREEN_DARK);
			}
		}

		private void drawReticle() {
			int c = this.weakLink && this.blink ? HudPalette.RED : HudPalette.GREEN;
			int arm = 14;
			int gap = 7;

			// Four brackets.
			this.g.fill(this.cx - arm, this.cy - arm + 6, this.cx - gap, this.cy - arm + 7, c);
			this.g.fill(this.cx + gap, this.cy - arm + 6, this.cx + arm, this.cy - arm + 7, c);
			this.g.fill(this.cx - arm, this.cy + arm - 7, this.cx - gap, this.cy + arm - 6, c);
			this.g.fill(this.cx + gap, this.cy + arm - 7, this.cx + arm, this.cy + arm - 6, c);

			this.g.fill(this.cx - arm, this.cy - arm + 6, this.cx - arm + 1, this.cy - gap, c);
			this.g.fill(this.cx + arm - 1, this.cy - arm + 6, this.cx + arm, this.cy - gap, c);
			this.g.fill(this.cx - arm, this.cy + gap, this.cx - arm + 1, this.cy + arm - 6, c);
			this.g.fill(this.cx + arm - 1, this.cy + gap, this.cx + arm, this.cy + arm - 6, c);

			// Centre dot + range ticks.
			this.g.fill(this.cx - 1, this.cy - 1, this.cx + 1, this.cy + 1, c);
			this.g.fill(this.cx - gap, this.cy, this.cx - 3, this.cy + 1, c);
			this.g.fill(this.cx + 3, this.cy, this.cx + gap, this.cy + 1, c);

			// Target diamond appears when something is under the crosshair.
			if (this.client.crosshairPickEntity != null && this.client.crosshairPickEntity != this.drone) {
				int d = 10;
				int colour = this.blink ? HudPalette.RED : HudPalette.AMBER;
				this.g.fill(this.cx - d, this.cy - d, this.cx - d + 4, this.cy - d + 1, colour);
				this.g.fill(this.cx + d - 4, this.cy - d, this.cx + d, this.cy - d + 1, colour);
				this.g.fill(this.cx - d, this.cy + d - 1, this.cx - d + 4, this.cy + d, colour);
				this.g.fill(this.cx + d - 4, this.cy + d - 1, this.cx + d, this.cy + d, colour);
			}
		}

		// --- instruments -------------------------------------------------

		private void drawGauges() {
			int left = this.cx0 + 16;
			int right = this.cx0 + this.w - 16 - 104;
			int top = this.cy - 46;

			gauge(left, top, "СКОРОСТЬ", Math.round(this.speed * 3.6D) + " КМ/Ч",
					(float) Math.min(1.0D, this.speed / (CombatDroneEntity.MAX_SPEED * 20.0D * 1.12D)),
					HudPalette.GREEN, "\u25B6");

			gauge(left, top + 34, "ВЫСОТА", this.altitude + " М",
					Mth.clamp(this.altitude / 180.0F, 0.0F, 1.0F), HudPalette.GREEN, "\u25B2");

			gauge(left, top + 68, "ДИСТ.", Math.round(this.range) + " М",
					(float) Mth.clamp(this.range / CombatDroneEntity.MAX_RANGE, 0.0D, 1.0D),
					this.range > CombatDroneEntity.MAX_RANGE * 0.72D ? HudPalette.AMBER : HudPalette.GREEN, "\u25C6");

			gauge(right, top, "БАТАРЕЯ", Math.round(this.fuel * 100.0F) + "%", this.fuel,
					this.lowFuel ? HudPalette.RED : HudPalette.GREEN, "\u26A1");

			gauge(right, top + 34, "СИГНАЛ", DroneClientState.getSignal() + "%", this.link,
					this.weakLink ? HudPalette.RED : (this.jamming ? HudPalette.AMBER : HudPalette.GREEN), "\u2248");

			armedStrip(right, top + 68);
		}

		private void gauge(int x, int y, String label, String value, float ratio, int colour, String glyph) {
			int barW = 104;
			panel(x, y, barW, 30);

			text(glyph + " " + label, x + 5, y + 4, HudPalette.TEXT_DIM);
			text(value, x + barW - 5 - this.font.width(value), y + 4, HudPalette.TEXT);

			int by = y + 17;
			this.g.fill(x + 5, by, x + barW - 5, by + 8, 0xCC04110C);
			this.g.fill(x + 5, by, x + barW - 5, by + 1, HudPalette.GREEN_DARK);

			int filled = (int) (Mth.clamp(ratio, 0.0F, 1.0F) * (barW - 12));
			for (int i = 0; i < filled; i += 6) {
				int seg = Math.min(4, filled - i);
				this.g.fill(x + 6 + i, by + 2, x + 6 + i + seg, by + 6, colour);
			}
			// needle
			int needle = x + 6 + filled;
			this.g.fill(needle, by + 1, needle + 1, by + 7, HudPalette.GREEN_BRIGHT);
		}

		private void armedStrip(int x, int y) {
			int barW = 104;
			panel(x, y, barW, 30);
			text("\u2694 ВЗРЫВАТЕЛЬ", x + 5, y + 4, HudPalette.TEXT_DIM);

			boolean armed = this.drone.isArmed() && !this.weakLink;
			String state = armed ? "ВЗВЕДЁН" : "ОТКАЗ";
			text(state, x + barW - 5 - this.font.width(state), y + 4, armed ? HudPalette.GREEN : HudPalette.RED);

			int by = y + 17;
			this.g.fill(x + 5, by, x + barW - 5, by + 2, armed ? HudPalette.GREEN : HudPalette.RED);
			if (armed) {
				int pulse = (int) ((System.currentTimeMillis() / 6L) % 40);
				this.g.fill(x + 5 + pulse, by + 4, x + 9 + pulse, by + 7, HudPalette.GREEN_DIM);
				this.g.fill(x + barW - 9 - pulse, by + 4, x + barW - 5 - pulse, by + 7, HudPalette.GREEN_DIM);
			} else {
				for (int i = 0; i < barW - 10; i += 8) {
					this.g.fill(x + 5 + i, by + 4, x + 9 + i, by + 7, HudPalette.RED_DIM);
				}
			}
		}

		private void drawBoresightRail() {
			int railW = 10;
			int railH = Math.min(120, this.h / 4);
			int x = this.cx - railW - 14;
			int y = this.cy - railH / 2;
			// Artificial horizon mirror on the left rail.
			this.g.fill(x, y, x + railW, y + railH, HudPalette.PANEL);
			float pitch = Mth.lerp(0.0F, this.drone.xRotO, this.drone.getXRot());
			int marker = y + railH / 2 + (int) (pitch / 45.0F * (railH / 2.0F));
			this.g.fill(x, marker, x + railW, marker + 2, HudPalette.AMBER);
			this.g.fill(x, y + railH / 2, x + 4, y + railH / 2 + 1, HudPalette.GREEN_DIM);

			// Altitude tape on the right of the rail.
			for (int i = 0; i <= 4; i++) {
				int ty = y + i * (railH / 4) - 1;
				this.g.fill(x + railW + 2, ty, x + railW + 6, ty + 1, HudPalette.GREEN_DARK);
			}
		}

		private void drawBottomHint() {
			String hint = "ЛКМ — ПОДРЫВ    R — ОТЗЫВ    SHIFT — СНИЖЕНИЕ    ПРОБЕЛ — НАБОР";
			int hintW = this.font.width(hint) + 24;
			int y = this.cy0 + this.h - 34;
			panel(this.cx - hintW / 2, y, hintW, 16);
			text(hint, this.cx - hintW / 2 + 12, y + 4, HudPalette.TEXT_DIM);
		}

		private void drawWarnings() {
			if (this.jamming && this.blink) {
				banner("ПОМЕХИ  \u00B7  EW JAM", HudPalette.RED, this.cy - 74);
			} else if (this.weakLink && this.blink) {
				banner("ПОТЕРЯ СИГНАЛА", HudPalette.RED, this.cy - 74);
			} else if (this.lowFuel && this.blink) {
				banner("НИЗКИЙ ЗАРЯД", HudPalette.AMBER, this.cy - 74);
			}

			if (this.range > CombatDroneEntity.MAX_RANGE * 0.86D) {
				String out = "ВЫХОД ЗА РАДИУС  " + Math.round(this.range) + " / " + (int) CombatDroneEntity.MAX_RANGE;
				int w = this.font.width(out) + 20;
				panel(this.cx - w / 2, this.cy + 60, w, 14);
				text(out, this.cx - w / 2 + 10, this.cy + 63, HudPalette.AMBER);
			}
		}

		private void drawRecordingLamp() {
			long time = System.currentTimeMillis();
			if ((time / 600L) % 2L == 0L) {
				this.g.fill(this.w - 46, this.cy0 + 10, this.w - 38, this.cy0 + 18, HudPalette.RED);
			}
			text("REC", this.w - 34, this.cy0 + 10, 0xFFFF8E88);
			text("FPV-01", this.w - 52, this.cy0 + 22, HudPalette.GREEN_DIM);
			String clock = String.format("%02d:%02d", (this.drone.tickCount / 20) / 60, (this.drone.tickCount / 20) % 60);
			text(clock, this.w - 52, this.cy0 + 33, HudPalette.TEXT_DIM);
		}

		// --- primitives --------------------------------------------------

		private void panel(int x, int y, int w, int h) {
			this.g.fillGradient(x, y, x + w, y + h, HudPalette.PANEL_DEEP, HudPalette.PANEL);
			this.g.fill(x, y, x + w, y + 1, HudPalette.GREEN_DIM);
			this.g.fill(x, y + h - 1, x + w, y + h, HudPalette.GREEN_DARK);
			this.g.fill(x, y, x + 1, y + h, HudPalette.GREEN_DARK);
			this.g.fill(x + w - 1, y, x + w, y + h, HudPalette.GREEN_DARK);
		}

		private void text(String value, int x, int y, int colour) {
			this.g.drawString(this.font, value, x, y, colour, false);
		}

		private void banner(String value, int colour, int y) {
			int width = this.font.width(value) + 26;
			int x = this.cx - width / 2;
			this.g.fill(x, y - 5, x + width, y + 13, 0xE61A0605);
			this.g.fill(x, y - 5, x + width, y - 4, colour);
			this.g.fill(x, y + 12, x + width, y + 13, colour);
			// hazard chevrons
			for (int i = 0; i < 8; i++) {
				this.g.fill(x + 4 + i * 3, y - 2, x + 6 + i * 3, y - 1, colour & 0x66FFFFFF | (colour & 0xFF000000));
			}
			text(value, x + 13, y - 1, colour);
		}
	}
}
