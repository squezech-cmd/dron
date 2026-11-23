package com.squezech.dron.registry;

import com.squezech.dron.DronMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Registers the mod's custom sounds (all plain Vorbis OGG, 22.05 kHz mono).
 */
public final class ModSounds {
	/** Idle / flight buzzing of the four motors (looped client-side). */
	public static final SoundEvent DRONE_LOOP = SoundEvent.createVariableRangeEvent(DronMod.id("drone_loop"));
	/** Short buzz burst used for the idle cue of a parked airframe. */
	public static final SoundEvent DRONE_BUZZ = SoundEvent.createVariableRangeEvent(DronMod.id("drone_buzz"));
	/** Warhead detonation (layered on top of the vanilla explosion). */
	public static final SoundEvent DRONE_EXPLODE = SoundEvent.createVariableRangeEvent(DronMod.id("drone_explode"));
	/** Launch whoosh when the airframe is tossed out of the controller. */
	public static final SoundEvent DRONE_LAUNCH = SoundEvent.createVariableRangeEvent(DronMod.id("drone_launch"));
	/** Pitched UI blips of the controller. */
	public static final SoundEvent DRONE_BEEP = SoundEvent.createVariableRangeEvent(DronMod.id("drone_beep"));
	/** Radio static of an unstable video link. */
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
