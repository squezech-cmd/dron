package com.squezech.dron.network;

import com.squezech.dron.entity.CombatDroneEntity;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

/** Client → server: the pilot pressed R (safe recall, no explosion). */
public record DroneRecallC2S() {
	public static final DroneRecallC2S INSTANCE = new DroneRecallC2S();

	public void write(FriendlyByteBuf buf) {
		// Single bit of payload: the trigger itself.
		buf.writeBoolean(true);
	}

	public static DroneRecallC2S read(FriendlyByteBuf buf) {
		buf.readBoolean();
		return INSTANCE;
	}

	public static void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler,
			FriendlyByteBuf buf, PacketSender sender) {
		read(buf);
		server.execute(() -> {
			if (player.getVehicle() instanceof CombatDroneEntity drone && drone.isOperator(player)) {
				drone.recall(player);
				return;
			}
			// Not aboard: recall the closest airframe that belongs to the operator.
			for (var level : server.getAllLevels()) {
				for (Entity entity : level.getAllEntities()) {
					if (entity instanceof CombatDroneEntity drone && drone.isOperator(player)
							&& player.distanceToSqr(drone) < 48.0D * 48.0D) {
						drone.recall(player);
						return;
					}
				}
			}
		});
	}
}
