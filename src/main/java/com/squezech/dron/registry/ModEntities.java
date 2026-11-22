package com.squezech.dron.registry;

import com.squezech.dron.DronMod;
import com.squezech.dron.entity.CombatDroneEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<CombatDroneEntity> COMBAT_DRONE = register("combat_drone",
			EntityType.Builder.<CombatDroneEntity>of(CombatDroneEntity::new, MobCategory.MISC)
					.sized(0.55F, 0.55F)
					.clientTrackingRange(10)
					.updateInterval(2)
					.fireImmune()
					.build("combat_drone"));

	private ModEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, DronMod.id(name));
		BuiltInRegistries.register(BuiltInRegistries.ENTITY_TYPE, key, type);
		return type;
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(COMBAT_DRONE, CombatDroneEntity.createAttributes());
		// Touch the class so it is loaded on both sides.
		if (COMBAT_DRONE == null) {
			throw new IllegalStateException("combat_drone was not registered");
		}
	}
}
