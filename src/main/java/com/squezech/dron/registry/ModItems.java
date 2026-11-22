package com.squezech.dron.registry;

import com.squezech.dron.DronMod;
import com.squezech.dron.item.DroneRemoteItem;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
	public static final Item DRONE_REMOTE = register("drone_remote",
			new DroneRemoteItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

	/** Dedicated creative tab for the mod. */
	public static final ResourceKey<CreativeModeTab> DRON_TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, DronMod.id("dron"));

	private ModItems() {
	}

	private static Item register(String name, Item item) {
		Registry.register(BuiltInRegistries.ITEM, DronMod.id(name), item);
		return item;
	}

	public static void init() {
		// Registration happens in the static initialisers above; touch them explicitly.
		if (DRONE_REMOTE == null) {
			throw new IllegalStateException("drone_remote was not registered");
		}
	}
}
