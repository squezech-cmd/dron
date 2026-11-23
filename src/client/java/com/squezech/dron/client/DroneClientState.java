package com.squezech.dron.client;

import com.squezech.dron.entity.CombatDroneEntity;
import com.squezech.dron.network.DronNetwork;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;

/**
 * Client-side flight state: battery / link indicators fed by the server
 * telemetry, plus the 20 Hz control-frame uplink.
 */
public final class DroneClientState {
	private static int signal = 100;
	private static int fuel = CombatDroneEntity.MAX_FUEL;
	private static boolean jamming;
	private static long lastTelemetry;
	private static boolean wasPiloting;
	private static float impactShake;
	private static float vibration;

	private DroneClientState() {
	}

	public static void acceptTelemetry(int droneId, int newSignal, int newFuel, boolean jammed) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || !(player.getVehicle() instanceof CombatDroneEntity drone)
				|| drone.getId() != droneId) {
			return;
		}
		signal = Mth.clamp(newSignal, 0, 100);
		fuel = Math.max(0, newFuel);
		jamming = jammed;
		lastTelemetry = System.currentTimeMillis();
	}

	/** Called once per client tick. */
	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		boolean piloting = player != null && player.getVehicle() instanceof CombatDroneEntity;

		if (!piloting) {
			if (wasPiloting) {
				// Dust-off shake when the airframe goes down.
				impactShake = 9.0F;
				wasPiloting = false;
			}
			signal = 100;
			fuel = CombatDroneEntity.MAX_FUEL;
			jamming = false;
			vibration = Math.max(0.0F, vibration - 0.1F);
			impactShake = Math.max(0.0F, impactShake - 0.5F);
			return;
		}

		wasPiloting = true;
		if (lastTelemetry != 0L && System.currentTimeMillis() - lastTelemetry > 900L) {
			// Server never answered: fail safe instead of showing a stale green.
			signal = Math.min(signal, 35);
		}
		vibration = Math.max(0.0F, vibration - 0.1F);
		impactShake = Math.max(0.0F, impactShake - 0.5F);
	}

	/** Send the pilot's control frame and aim to the server (20 Hz). */
	public static void streamInput(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.getConnection() == null) {
			return;
		}
		if (!(player.getVehicle() instanceof CombatDroneEntity)) {
			return;
		}

		FriendlyByteBuf frame = PacketByteBufs.create();
		frame.writeByte(Math.round(Mth.clamp(player.input.forwardImpulse, -1.0F, 1.0F) * 100.0F));
		frame.writeByte(Math.round(Mth.clamp(player.input.leftImpulse, -1.0F, 1.0F) * 100.0F));
		frame.writeBoolean(player.input.jumping);
		frame.writeBoolean(player.input.shiftKeyDown);
		ClientPlayNetworking.send(DronNetwork.INPUT, frame);

		FriendlyByteBuf aim = PacketByteBufs.create();
		aim.writeFloat(player.getYRot());
		aim.writeFloat(player.getXRot());
		ClientPlayNetworking.send(DronNetwork.LOOK, aim);
	}

	public static void requestRecall() {
		ClientPlayNetworking.send(DronNetwork.RECALL, PacketByteBufs.empty());
	}

	public static void onThreat() {
		vibration = 1.0F;
	}

	public static int getSignal() {
		return signal;
	}

	public static int getFuel() {
		return fuel;
	}

	public static float getFuelFraction() {
		return Mth.clamp(fuel / (float) CombatDroneEntity.MAX_FUEL, 0.0F, 1.0F);
	}

	public static float getSignalFraction() {
		return Mth.clamp(signal / 100.0F, 0.0F, 1.0F);
	}

	public static boolean isJamming() {
		return jamming;
	}

	public static float getVibration() {
		return vibration;
	}

	public static float getImpactShake() {
		return impactShake;
	}
}
