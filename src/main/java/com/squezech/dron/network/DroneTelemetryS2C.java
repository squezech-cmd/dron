package com.squezech.dron.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server → client: FPV telemetry for the cockpit HUD.
 *
 * @param entityId the drone
 * @param signal   command link quality, 0..100 %
 * @param fuel     remaining battery, 0..{@code CombatDroneEntity.MAX_FUEL}
 * @param jamming  1 while electronic warfare is pressing on the link
 */
public record DroneTelemetryS2C(int entityId, int signal, int fuel, boolean jamming) {
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(this.entityId);
		buf.writeVarInt(this.signal);
		buf.writeVarInt(this.fuel);
		buf.writeBoolean(this.jamming);
	}

	public static DroneTelemetryS2C read(FriendlyByteBuf buf) {
		return new DroneTelemetryS2C(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
	}
}
