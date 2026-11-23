package com.squezech.dron.network;

import com.squezech.dron.entity.CombatDroneEntity;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Client → server: the pilot's raw control frame.
 *
 * <p>Minecraft does not ship a player's movement input to the server, so the
 * client streams its own keys at 20 Hz, mirroring vanilla's player packet rate.
 */
public record DroneInputC2S(byte forward, byte strafe, boolean up, boolean down) {
	public static final int RATE = 1;

	public static DroneInputC2S read(FriendlyByteBuf buf) {
		return new DroneInputC2S(buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readBoolean());
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeByte(this.forward);
		buf.writeByte(this.strafe);
		buf.writeBoolean(this.up);
		buf.writeBoolean(this.down);
	}

	public static void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler,
			FriendlyByteBuf buf, PacketSender sender) {
		DroneInputC2S packet = read(buf);
		server.execute(() -> {
			if (player.getVehicle() instanceof CombatDroneEntity drone && drone.isOperator(player)) {
				drone.applyInput(packet.forward(), packet.strafe(), packet.up(), packet.down(), player.getYRot(),
						player.getXRot());
			}
		});
	}
}
