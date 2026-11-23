package com.squezech.dron.client.sound;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.squezech.dron.client.DroneClientState;
import com.squezech.dron.entity.CombatDroneEntity;
import com.squezech.dron.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Keeps one looping buzz per visible airframe and a radio-static bed while the
 * video link is being jammed.
 */
public final class DroneAudio {
	private static final Map<Integer, DroneBuzzSound> BUZZES = new HashMap<>();
	private static final Set<Integer> ALIVE = new HashSet<>();
	private static LinkStatic staticSound;

	private DroneAudio() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DroneAudio::tick);
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || client.player == null) {
			BUZZES.clear();
			ALIVE.clear();
			staticSound = null;
			return;
		}

		ALIVE.clear();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof CombatDroneEntity drone && drone.isArmed()) {
				ALIVE.add(drone.getId());
				if (!BUZZES.containsKey(drone.getId())) {
					DroneBuzzSound buzz = new DroneBuzzSound(drone, drone.isPiloted());
					BUZZES.put(drone.getId(), buzz);
					client.getSoundManager().play(buzz);
				}
			}
		}

		BUZZES.entrySet().removeIf(entry -> {
			if (!ALIVE.contains(entry.getKey())) {
				entry.getValue().forceStop();
				return true;
			}
			return false;
		});

		updateStatic(client);
	}

	private static void updateStatic(Minecraft client) {
		boolean jamming = client.player != null
				&& client.player.getVehicle() instanceof CombatDroneEntity
				&& (DroneClientState.isJamming() || DroneClientState.getSignalFraction() < 0.25F);

		if (jamming) {
			if (staticSound == null) {
				staticSound = new LinkStatic();
				client.getSoundManager().play(staticSound);
			}
			staticSound.update(DroneClientState.getSignalFraction());
		} else if (staticSound != null) {
			staticSound.forceStop();
			staticSound = null;
		}
	}

	/** White-noise bed of a degrading video link. */
	private static final class LinkStatic extends AbstractTickableSoundInstance {
		private float intensity = 1.0F;

		LinkStatic() {
			super(ModSounds.DRONE_STATIC, SoundSource.MASTER, RandomSource.create());
			this.looping = true;
			this.delay = 0;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.relative = true;
			this.volume = 0.0F;
			this.pitch = 1.0F;
		}

		void update(float link) {
			this.intensity = Math.max(0.05F, 1.0F - link);
			this.volume = 0.35F * this.intensity;
			this.pitch = 0.75F + this.intensity * 0.5F;
		}

		void forceStop() {
			this.stop();
		}

		@Override
		public void tick() {
			// Volume is driven by update(), nothing to do per tick.
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
