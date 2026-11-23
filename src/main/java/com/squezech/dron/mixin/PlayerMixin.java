package com.squezech.dron.mixin;

import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The drone is flown with the player's look controls, so while the operator is
 * at the controls Shift is the "descend" key instead of the dismount key.
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
}
