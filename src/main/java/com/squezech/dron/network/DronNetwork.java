package com.squezech.dron.network;

import com.squezech.dron.DronMod;
import com.squezech.dron.entity.CombatDroneEntity;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Networking of the mod.
 *
 * <ul>
 *   <li>S2C {@code dron:telemetry} — fuel / link bars for the FPV HUD.</li>
 *   <li>C2S {@code dron:recall} — the pilot presses R and the airframe is packed away.</li>
 * </ul>
 */
public final class DronNetwork {
	public static final ResourceLocation TELEMETRY = DronMod.id("telemetry");
	public static final ResourceLocation RECALL = DronMod.id("recall");

	private static int ticker;

	private DronNetwork() {
	}

	public static void initCommon() {
		ServerPlayNetworking.registerGlobalReceiver(RECALL,
				(server, player, handler, buf, responseSender) -> {
					// Read nothing: the packet is just a trigger.
					server.execute(() -> recallFor(player));
				});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++ticker % 2 != 0) {
				return;
			}

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!(player.getVehicle() instanceof CombatDroneEntity drone)) {
					continue;
				}

				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				buf.writeVarInt(drone.getId());
				buf.writeVarInt(drone.getFuel());
				buf.writeVarInt(drone.getSignal());
				ServerPlayNetworking.send(player, TELEMETRY, buf);
			}
		});
	}

	private static void recallFor(ServerPlayer player) {
		if (player.getVehicle() instanceof CombatDroneEntity drone) {
			drone.recall(player);
			return;
		}

		// Not aboard: recall the closest drone owned by this player.
		for (var level : player.server.getAllLevels()) {
			for (var entity : level.getAllEntities()) {
				if (entity instanceof CombatDroneEntity drone && drone.isOperator(player)
						&& player.distanceToSqr(drone) < 64.0D * 64.0D) {
					drone.recall(player);
					return;
				}
			}
		}
	}
}
