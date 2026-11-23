package com.squezech.dron.network;

import java.util.ArrayList;
import java.util.List;

import com.squezech.dron.DronMod;
import com.squezech.dron.entity.CombatDroneEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Networking of the mod.
 *
 * <ul>
 *   <li>C2S {@code dron:input} — pilot control frame, 20 Hz.</li>
 *   <li>C2S {@code dron:look} — pilot aim (yaw / pitch), 20 Hz.</li>
 *   <li>C2S {@code dron:recall} — safe recall (R).</li>
 *   <li>S2C {@code dron:telemetry} — battery / link / jammer state, 10 Hz.</li>
 * </ul>
 */
public final class DronNetwork {
	public static final ResourceLocation INPUT = DronMod.id("input");
	public static final ResourceLocation LOOK = DronMod.id("look");
	public static final ResourceLocation RECALL = DronMod.id("recall");
	public static final ResourceLocation DETONATE = DronMod.id("detonate");
	public static final ResourceLocation TELEMETRY = DronMod.id("telemetry");

	private static final List<ServerPlayer> PILOTS = new ArrayList<>();

	private DronNetwork() {
	}

	public static void initCommon() {
		ServerPlayNetworking.registerGlobalReceiver(INPUT, DroneInputC2S::receive);
		ServerPlayNetworking.registerGlobalReceiver(LOOK, DroneLookC2S::receive);
		ServerPlayNetworking.registerGlobalReceiver(RECALL, DroneRecallC2S::receive);
		ServerPlayNetworking.registerGlobalReceiver(DETONATE, DroneDetonateC2S::receive);

		// Telemetry runs at 10 Hz: enough for a smooth HUD, cheap on bandwidth.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 2 != 0) {
				return;
			}

			PILOTS.clear();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.getVehicle() instanceof CombatDroneEntity
						&& ServerPlayNetworking.canSend(player, TELEMETRY)) {
					PILOTS.add(player);
				}
			}

			for (ServerPlayer player : PILOTS) {
				if (!(player.getVehicle() instanceof CombatDroneEntity drone)) {
					continue;
				}
				FriendlyByteBuf buf = PacketByteBufs.create();
				new DroneTelemetryS2C(drone.getId(), drone.getSignal(), drone.getFuel(),
						drone.getSignal() < 45).write(buf);
				ServerPlayNetworking.send(player, TELEMETRY, buf);
			}
		});
	}
}
