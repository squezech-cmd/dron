package com.squezech.dron.network;

import com.squezech.dron.entity.CombatDroneEntity;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Client → server: the pilot's look direction (the FPV aim).
 *
 * <p>The operator is a camera while flying, so the server needs the view angles
 * to point the airframe. Kept separate from {@link DroneInputC2S} so the control
 * frame stays tiny.
 */
public record DroneLookC2S(float yaw, float pitch) {
	public static DroneLookC2S read(FriendlyByteBuf buf) {
		return new DroneLookC2S(buf.readFloat(), buf.readFloat());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeFloat(this.yaw);
		buf.writeFloat(this.pitch);
	}

	public static void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler,
			FriendlyByteBuf buf, PacketSender sender) {
		DroneLookC2S packet = read(buf);
		server.execute(() -> {
			if (player.getVehicle() instanceof CombatDroneEntity drone && drone.isOperator(player)) {
				drone.updateAim(packet.yaw(), packet.pitch());
			}
		});
	}
}
