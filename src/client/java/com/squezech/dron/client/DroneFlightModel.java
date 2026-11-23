package com.squezech.dron.client;

import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side copy of the airframe flight model.
 *
 * <p>Minecraft only streams vehicle positions at ~20 Hz, which is far too coarse
 * for a 30 blocks/s quad. The pilot therefore integrates the exact same physics
 * locally every frame and lets the server be the authority on hits, fuel and
 * the command link.
 */
public final class DroneFlightModel {
	private DroneFlightModel() {
	}

	/** Advance the local airframe by one client tick. */
	public static void step(LocalPlayer player, CombatDroneEntity drone) {
		float yaw = player.getYRot();
		float forward = player.input.forwardImpulse;
		float strafe = player.input.leftImpulse;
		boolean up = player.input.jumping;
		boolean down = player.input.shiftKeyDown;
		boolean booster = drone.hasBooster();

		double rad = Math.toRadians(yaw);
		Vec3 dirForward = new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
		Vec3 dirRight = new Vec3(Math.cos(rad), 0.0D, Math.sin(rad));

		double thrustMul = booster ? 1.28D : 1.0D;
		Vec3 thrust = dirForward.scale(forward * CombatDroneEntity.THRUST * thrustMul)
				.add(dirRight.scale(strafe * CombatDroneEntity.STRAFE * thrustMul));

		if (up != down) {
			thrust = thrust.add(0.0D, (up ? CombatDroneEntity.LIFT : -CombatDroneEntity.LIFT)
					* (booster ? 1.2D : 1.0D), 0.0D);
		}
		if (forward == 0.0F && strafe == 0.0F && !up && !down) {
			thrust = thrust.add(0.0D, CombatDroneEntity.LIFT * 0.55D, 0.0D);
		}

		Vec3 velocity = drone.getDeltaMovement().add(thrust).scale(CombatDroneEntity.DRAG);
		if (velocity.y < -CombatDroneEntity.MAX_FALL) {
			velocity = new Vec3(velocity.x, -CombatDroneEntity.MAX_FALL, velocity.z);
		}

		double maxSpeed = CombatDroneEntity.MAX_SPEED * (booster ? 1.12D : 1.0D);
		double speed = velocity.length();
		if (speed > maxSpeed) {
			velocity = velocity.scale(maxSpeed / speed);
		}

		drone.setDeltaMovement(velocity);
		Vec3 next = drone.position().add(velocity);
		drone.setPos(next.x, next.y, next.z);
		// Collapse the interpolation window: this client is the authority for the
		// airframe it is flying, the server only lands the final impact point.
		drone.setOldPosAndRot();
		drone.setYRot(yaw);
		drone.setXRot(Mth.clamp(player.getXRot() * 0.55F, -42.0F, 42.0F));

		// The pilot's invisible body rides along, so the local player never
		// desyncs or gets teleported back by the server.
		player.setPos(drone.getX(), drone.getY() + drone.getBbHeight() * 0.62D, drone.getZ());
		player.setYRot(yaw);
		player.setXRot(player.getXRot());
	}
}
