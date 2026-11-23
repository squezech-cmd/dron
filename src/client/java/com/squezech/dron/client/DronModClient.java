package com.squezech.dron.client;

import com.squezech.dron.DronMod;
import com.squezech.dron.client.hud.DroneHud;
import com.squezech.dron.client.render.CombatDroneRenderer;
import com.squezech.dron.client.render.DroneModel;
import com.squezech.dron.client.sound.DroneAudio;
import com.squezech.dron.entity.CombatDroneEntity;
import com.squezech.dron.network.DronNetwork;
import com.squezech.dron.network.DroneTelemetryS2C;
import com.squezech.dron.registry.ModEntities;
import com.squezech.dron.registry.ModItems;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

public class DronModClient implements ClientModInitializer {
	public static KeyMapping recallKey;

	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(DroneModel.LAYER, DroneModel::createLayer);
		EntityRendererRegistry.register(ModEntities.COMBAT_DRONE, CombatDroneRenderer::new);

		recallKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.dron.recall", GLFW.GLFW_KEY_R, "key.categories.dron"));

		ClientTickEvents.END_CLIENT_TICK.register(DronModClient::clientTick);

		ClientPlayNetworking.registerGlobalReceiver(DronNetwork.TELEMETRY,
				(client, handler, buf, responseSender) -> {
					DroneTelemetryS2C packet = DroneTelemetryS2C.read(buf);
					client.execute(() -> DroneClientState.acceptTelemetry(packet.entityId(), packet.signal(),
							packet.fuel(), packet.jamming()));
				});

		DroneAudio.init();
		HudRenderCallback.EVENT.register(DroneHud::render);

		DronMod.LOGGER.info("Dron client initialised: FPV HUD armed");
	}

	private static void clientTick(Minecraft client) {
		LocalPlayer player = client.player;

		while (recallKey.consumeClick()) {
			if (player != null && client.getConnection() != null
					&& (player.getVehicle() instanceof CombatDroneEntity
							|| player.getMainHandItem().is(ModItems.DRONE_REMOTE))) {
				DroneClientState.requestRecall();
			}
		}

		if (player != null && player.getVehicle() instanceof CombatDroneEntity drone) {
			// Local physics at full frame rate, then push the control frame.
			DroneFlightModel.step(player, drone);
			DroneClientState.streamInput(client);
			handleAttack(client, player);
		}

		DroneClientState.tick(client);
	}

	/**
	 * Left click pulls the trigger: the warhead detonates under the airframe.
	 * A short local click plus a launch whoosh sells the strike before the
	 * server's explosion arrives.
	 */
	private static void handleAttack(Minecraft client, LocalPlayer player) {
		if (!client.options.keyAttack.isDown() || client.screen != null) {
			return;
		}
		if (player.getAttackStrengthScale(0.0F) < 0.5F) {
			return;
		}
		player.resetAttackStrengthTicker();
		DroneClientState.onThreat();
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.6F));
		ClientPlayNetworking.send(DronNetwork.DETONATE, PacketByteBufs.create());
	}
}
