package com.squezech.dron.client.mixin;

import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the vanilla chrome (hotbar, hearts, crosshair) for the military FPV HUD
 * while the player is at the drone's controls.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
	private static boolean dron$piloting() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.player.getVehicle() instanceof CombatDroneEntity;
	}

	@Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
	private void dron$hideHotbar(float tickDelta, GuiGraphics graphics, CallbackInfo ci) {
		if (dron$piloting()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void dron$hideCrosshair(GuiGraphics graphics, CallbackInfo ci) {
		if (dron$piloting()) {
			ci.cancel();
		}
	}

	@Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
	private void dron$hideHealth(GuiGraphics graphics, CallbackInfo ci) {
		if (dron$piloting()) {
			ci.cancel();
		}
	}
}
