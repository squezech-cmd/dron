package com.squezech.dron.client;

import com.squezech.dron.DronMod;
import com.squezech.dron.client.hud.DroneHud;
import com.squezech.dron.client.render.CombatDroneRenderer;
import com.squezech.dron.client.render.DroneModel;
import com.squezech.dron.network.DronNetwork;
import com.squezech.dron.registry.ModEntities;
import com.squezech.dron.registry.ModItems;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class DronModClient implements ClientModInitializer {
	public static KeyMapping recallKey;

	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(DroneModel.LAYER, DroneModel::createLayer);
		EntityRendererRegistry.register(ModEntities.COMBAT_DRONE, CombatDroneRenderer::new);

		// Dedicated creative tab of the mod.
		CreativeModeTab tab = FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.dron.dron"))
				.icon(() -> new ItemStack(ModItems.DRONE_REMOTE))
				.displayItems((parameters, output) -> output.accept(ModItems.DRONE_REMOTE))
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DronMod.id("dron"), tab);

		recallKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.dron.recall", GLFW.GLFW_KEY_R, "key.categories.dron"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (recallKey.consumeClick()) {
				if (client.player != null && client.getConnection() != null) {
					DroneHud.requestRecall(client);
				}
			}
			DroneHud.tick(client);
		});

		ClientPlayNetworking.registerGlobalReceiver(DronNetwork.TELEMETRY,
				(client, handler, buf, responseSender) -> {
					buf.readVarInt(); // entity id, kept for future per-drone widgets
					int fuel = buf.readVarInt();
					int signal = buf.readVarInt();
					client.execute(() -> DroneHud.acceptTelemetry(0, fuel, signal));
				});

		HudRenderCallback.EVENT.register(DroneHud::render);

		DronMod.LOGGER.info("Dron client initialised: FPV HUD armed");
	}
}
