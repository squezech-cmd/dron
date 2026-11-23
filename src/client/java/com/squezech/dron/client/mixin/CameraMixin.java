package com.squezech.dron.client.mixin;

import com.squezech.dron.client.DroneClientState;
import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the camera on the drone's nose while piloting — a true FPV feed.
 *
 * <p>The vanilla camera keeps handling the zoom / aim maths; only the eye is
 * moved onto the airframe (with a subtle rotor vibration and an impact shake).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "setup", at = @At("TAIL"))
	private void dron$fpvCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick,
			CallbackInfo ci) {
		if (!(entity.getVehicle() instanceof CombatDroneEntity drone)) {
			return;
		}

		// Riding players tick their vehicle before the camera, so the live
		// position is the predicted one: no interpolation lag in the feed.
		double x = drone.getX();
		double y = drone.getY();
		double z = drone.getZ();

		float shake = DroneClientState.getImpactShake() * 0.05F;
		float hum = DroneClientState.getVibration() * 0.004F;
		long time = drone.level().getGameTime();
		double jitterX = Math.sin(time * 12.9D) * hum + Math.sin(time * 31.0D) * shake;
		double jitterY = Math.cos(time * 17.3D) * hum + Math.cos(time * 27.0D) * shake;

		// Nose eye height of the 0.45 block tall airframe.
		this.setPosition(x + jitterX, y + 0.30D + jitterY, z);
		this.setRotation(entity.getViewYRot(partialTick), entity.getViewXRot(partialTick) + shake * 40.0F);
	}
}
