package com.squezech.dron.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An FPV operator has no third-person hands: the remote controller itself must
 * not float in front of the feed.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void dron$hideHandsInFpv(PoseStack poseStack, Camera camera, float partialTick, CallbackInfo ci) {
		if (camera.getEntity() != null && camera.getEntity().getVehicle() instanceof CombatDroneEntity) {
			ci.cancel();
		}
	}
}
