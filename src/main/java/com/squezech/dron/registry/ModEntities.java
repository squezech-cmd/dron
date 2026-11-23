package com.squezech.dron.registry;

import com.squezech.dron.DronMod;
import com.squezech.dron.entity.CombatDroneEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	/**
	 * The drone is tiny (0.5 x 0.45 blocks) and ticks every second game tick so
	 * its position stays smooth at 30 blocks/s.
	 */
	public static final EntityType<CombatDroneEntity> COMBAT_DRONE = register("combat_drone",
			EntityType.Builder.<CombatDroneEntity>of(CombatDroneEntity::new, MobCategory.MISC)
					.sized(CombatDroneEntity.WIDTH, CombatDroneEntity.HEIGHT)
					.clientTrackingRange(12)
					.updateInterval(1)
					.fireImmune()
					.noSummon()
					.build("combat_drone"));

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(String name, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, DronMod.id(name));
		Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
		return type;
	}

	public static void init() {
		// Registration happens in the static initialiser above.
		if (COMBAT_DRONE == null) {
			throw new IllegalStateException("combat_drone was not registered");
		}
	}
}
