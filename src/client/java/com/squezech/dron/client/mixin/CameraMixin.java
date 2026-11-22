package com.squezech.dron.client.mixin;

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
 * Puts the camera on the drone's nose while piloting it — a true FPV feed.
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
		if (entity.getVehicle() instanceof CombatDroneEntity drone) {
			double x = drone.xo + (drone.getX() - drone.xo) * partialTick;
			double y = drone.yo + (drone.getY() - drone.yo) * partialTick;
			double z = drone.zo + (drone.getZ() - drone.zo) * partialTick;

			this.setPosition(x, y + 0.22D, z);
			this.setRotation(entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
		}
	}
}
