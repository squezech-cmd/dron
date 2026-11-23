package com.squezech.dron.mixin;

import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The drone is flown with the player's look controls (like a camera), so while
 * the operator sits at the controls:
 *
 * <ul>
 *   <li>Shift is the "descend" key instead of the dismount key;</li>
 *   <li>the pilot's footsteps stay silent — the player is not walking.</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void dron$keepPilotOnDrone(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (self.getVehicle() instanceof CombatDroneEntity) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getFrictionInfluencedSpeed", at = @At("HEAD"), cancellable = true)
	private void dron$silentFootsteps(float friction, CallbackInfoReturnable<Float> cir) {
		Player self = (Player) (Object) this;
		if (self.getVehicle() instanceof CombatDroneEntity) {
			cir.setReturnValue(0.0F);
		}
	}
}
