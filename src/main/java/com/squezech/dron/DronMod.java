package com.squezech.dron;

import com.squezech.dron.network.DronNetwork;
import com.squezech.dron.registry.ModEntities;
import com.squezech.dron.registry.ModItems;
import com.squezech.dron.registry.ModSounds;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (both sides) entrypoint of the Dron mod.
 */
public class DronMod implements ModInitializer {
	public static final String MOD_ID = "dron";
	public static final Logger LOGGER = LoggerFactory.getLogger("Dron");

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModEntities.init();
		ModItems.init();
		DronNetwork.initCommon();
		LOGGER.info("Dron mod loaded: FPV kamikaze drone ready for takeoff");
	}
}
