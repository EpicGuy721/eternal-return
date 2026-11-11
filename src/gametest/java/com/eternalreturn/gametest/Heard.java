package com.eternalreturn.gametest;

import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The sounds the test server plays and the world events it sends (a block's break particles and sound: 2001, with the
 * broken state's id), as HeardMixin hears them, and the blocks shown to the players watching an entity, with it
 * (ShownMixin). Tests run side by side, so look them up by place.
 */
public final class Heard {
	public record Sound(Vec3d pos, SoundEvent sound) {
	}

	public record WorldEvent(BlockPos pos, int id, int data) {
	}

	public record Shown(BlockPos pos, BlockState state) {
	}

	private static final int KEPT = 4096;
	private static final List<Shown> SHOWN = new ArrayList<>();
	private static final List<Sound> SOUNDS = new ArrayList<>();
	private static final List<WorldEvent> EVENTS = new ArrayList<>();

	private Heard() {
	}

	public static void sound(Vec3d pos, SoundEvent sound) {
		if (SOUNDS.size() >= KEPT) {
			SOUNDS.remove(0);
		}
		SOUNDS.add(new Sound(pos, sound));
	}

	public static void event(BlockPos pos, int id, int data) {
		if (EVENTS.size() >= KEPT) {
			EVENTS.remove(0);
		}
		EVENTS.add(new WorldEvent(pos.toImmutable(), id, data));
	}

	/** The sounds played within a block of a point, oldest first. */
	public static List<SoundEvent> near(Vec3d pos) {
		return SOUNDS.stream().filter(sound -> sound.pos().isInRange(pos, 1.0)).map(Sound::sound).toList();
	}

	/** The data of the world events of an id sent at a block, oldest first. */
	public static List<Integer> events(BlockPos pos, int id) {
		return EVENTS.stream().filter(event -> event.pos().equals(pos) && event.id() == id).map(WorldEvent::data).toList();
	}

	public static void shown(BlockPos pos, BlockState state) {
		if (SHOWN.size() >= KEPT) {
			SHOWN.remove(0);
		}
		SHOWN.add(new Shown(pos.toImmutable(), state));
	}

	/** The states of a block shown to the players watching an entity (as a landing block is), oldest first. */
	public static List<BlockState> shown(BlockPos pos) {
		return SHOWN.stream().filter(shown -> shown.pos().equals(pos)).map(Shown::state).toList();
	}
}
