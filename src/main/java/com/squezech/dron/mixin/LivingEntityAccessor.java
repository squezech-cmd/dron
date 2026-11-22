package com.squezech.dron.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the vanilla jump input of a rider so the drone can climb. */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("jumping")
	boolean dron$isJumping();
}
