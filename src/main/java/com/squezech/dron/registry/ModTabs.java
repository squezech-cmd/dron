package com.squezech.dron.registry;

import com.squezech.dron.DronMod;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Dedicated creative tab. Registered from the common initialiser so a dedicated
 * server never receives an unregistered creative-tab id from clients.
 */
public final class ModTabs {
	public static final CreativeModeTab DRON_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
			DronMod.id("dron"),
			FabricItemGroup.builder()
					.title(Component.translatable("itemGroup.dron.dron"))
					.icon(() -> new ItemStack(ModItems.DRONE_REMOTE))
					.displayItems((parameters, output) -> output.accept(ModItems.DRONE_REMOTE))
					.build());

	private ModTabs() {
	}

	public static void init() {
		if (DRON_TAB == null) {
			throw new IllegalStateException("dron creative tab was not registered");
		}
	}
}
