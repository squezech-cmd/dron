package com.squezech.dron.network;

import com.squezech.dron.entity.CombatDroneEntity;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Client → server: the pilot pulled the trigger — detonate right here. */
public record DroneDetonateC2S() {
	public static final DroneDetonateC2S INSTANCE = new DroneDetonateC2S();

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(true);
	}

	public static DroneDetonateC2S read(FriendlyByteBuf buf) {
		buf.readBoolean();
		return INSTANCE;
	}

	public static void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler,
			FriendlyByteBuf buf, PacketSender sender) {
		read(buf);
		server.execute(() -> {
			if (player.getVehicle() instanceof CombatDroneEntity drone && drone.isOperator(player)) {
				drone.detonate(player);
			}
		});
	}
}
