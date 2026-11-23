package com.squezech.dron.client.sound;

import com.squezech.dron.entity.CombatDroneEntity;
import com.squezech.dron.registry.ModSounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Continuous motor buzz that follows the airframe.
 *
 * <p>Volume and pitch track the throttle: a hovering quad hums, a full-throttle
 * dive howls. It is looped client-side, which is exactly what a real drone
 * sounds like from the ground.
 */
public class DroneBuzzSound extends AbstractTickableSoundInstance {
	private final CombatDroneEntity drone;
	private final boolean piloted;

	public DroneBuzzSound(CombatDroneEntity drone, boolean piloted) {
		super(ModSounds.DRONE_LOOP, SoundSource.NEUTRAL, RandomSource.create());
		this.drone = drone;
		this.piloted = piloted;
		this.looping = true;
		this.delay = 0;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.relative = false;
		this.volume = 0.1F;
		this.pitch = 1.0F;
		this.x = drone.getX();
		this.y = drone.getY();
		this.z = drone.getZ();
	}

	@Override
	public void tick() {
		if (this.drone.isRemoved() || !this.drone.isArmed()) {
			this.stop();
			return;
		}

		this.x = this.drone.getX();
		this.y = this.drone.getY();
		this.z = this.drone.getZ();

		float speed = this.drone.getSpeed();
		float throttle = Math.min(1.0F, speed / (float) CombatDroneEntity.MAX_SPEED);

		this.volume = (this.piloted ? 0.55F : 0.85F) * (0.55F + throttle * 0.45F);
		this.pitch = 0.82F + throttle * 0.5F;
	}

	void forceStop() {
		this.stop();
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
