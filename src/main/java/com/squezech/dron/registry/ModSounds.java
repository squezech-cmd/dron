package com.squezech.dron.registry;

import com.squezech.dron.DronMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Registers the mod's custom sounds.
 *
 * <p>All sounds are 8-bit / 22.05 kHz mono OGG files (see assets/dron/sounds),
 * which every Minecraft 1.20.1 client can decode.
 */
public final class ModSounds {
	/** Looping buzzing of the quadcopter motors. */
	public static final SoundEvent DRONE_LOOP = SoundEvent.createVariableRangeEvent(DronMod.id("drone_loop"));
	/** One-shot buzz used as the idle cue. */
	public static final SoundEvent DRONE_BUZZ = SoundEvent.createVariableRangeEvent(DronMod.id("drone_buzz"));
	/** Warhead detonation. */
	public static final SoundEvent DRONE_EXPLODE = SoundEvent.createVariableRangeEvent(DronMod.id("drone_explode"));
	/** Launch whistle of the drone when tossed out of the controller. */
	public static final SoundEvent DRONE_LAUNCH = SoundEvent.createVariableRangeEvent(DronMod.id("drone_launch"));
	/** Pitched UI blips of the remote controller. */
	public static final SoundEvent DRONE_BEEP = SoundEvent.createVariableRangeEvent(DronMod.id("drone_beep"));
	/** Radio static when the video link is unstable. */
	public static final SoundEvent DRONE_STATIC = SoundEvent.createVariableRangeEvent(DronMod.id("drone_static"));

	private ModSounds() {
	}

	public static void init() {
		register("drone_loop", DRONE_LOOP);
		register("drone_buzz", DRONE_BUZZ);
		register("drone_explode", DRONE_EXPLODE);
		register("drone_launch", DRONE_LAUNCH);
		register("drone_beep", DRONE_BEEP);
		register("drone_static", DRONE_STATIC);
	}

	private static void register(String name, SoundEvent event) {
		Registry.register(BuiltInRegistries.SOUND_EVENT, DronMod.id(name), event);
	}
}
