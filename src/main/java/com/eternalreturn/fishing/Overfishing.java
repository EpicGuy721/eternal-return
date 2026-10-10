package com.eternalreturn.fishing;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fishing the same spot again and again slows the bites there for a while (fishing.overfishing in the
 * config). Each catch is remembered with where and when it came up; its weight fades from 1 to 0 over
 * recoveryMinutes. The catches within radius of a hook add up, and every catch over freeCatches makes
 * the wait for the next bite there slowdownPerCatch longer, up to maxSlowdown times as long. Kept in
 * memory per world: a restart forgets it, as the fish would have come back by then anyway.
 */
public final class Overfishing {
	private record Catch(Vec3d pos, long time) {
	}

	private static final Map<World, List<Catch>> CATCHES = new WeakHashMap<>();

	private Overfishing() {
	}

	/** Remembers a catch that came up at pos. */
	public static synchronized void recordCatch(World world, Vec3d pos) {
		EternalReturnConfig.Overfishing cfg = EternalReturnConfig.get().fishing.overfishing;
		if (!cfg.enabled) {
			return;
		}
		List<Catch> catches = CATCHES.computeIfAbsent(world, w -> new ArrayList<>());
		long now = world.getTime();
		long recovery = recoveryTicks(cfg);
		catches.removeIf(c -> now - c.time() >= recovery || now < c.time());
		catches.add(new Catch(pos, now));
	}

	/** How many recent catches count against pos, each faded by its age. */
	public static synchronized double depletion(World world, Vec3d pos) {
		EternalReturnConfig.Overfishing cfg = EternalReturnConfig.get().fishing.overfishing;
		List<Catch> catches = CATCHES.get(world);
		if (!cfg.enabled || catches == null) {
			return 0.0;
		}
		long now = world.getTime();
		double recovery = recoveryTicks(cfg);
		double radiusSq = cfg.radius * cfg.radius;
		double total = 0.0;
		for (Catch c : catches) {
			long age = now - c.time();
			if (age >= 0 && age < recovery && c.pos().squaredDistanceTo(pos) <= radiusSq) {
				total += 1.0 - age / recovery;
			}
		}
		return total;
	}

	/** What the wait for a bite at pos is multiplied by: 1 where the spot is fresh. */
	public static double waitMultiplier(World world, Vec3d pos) {
		EternalReturnConfig.Overfishing cfg = EternalReturnConfig.get().fishing.overfishing;
		if (!cfg.enabled) {
			return 1.0;
		}
		double over = Math.max(0.0, depletion(world, pos) - cfg.freeCatches);
		return Math.min(Math.max(1.0, cfg.maxSlowdown), 1.0 + over * cfg.slowdownPerCatch);
	}

	/** Forgets every catch in this world (for tests). */
	public static synchronized void clear(World world) {
		CATCHES.remove(world);
	}

	private static long recoveryTicks(EternalReturnConfig.Overfishing cfg) {
		return Math.max(1L, Math.round(cfg.recoveryMinutes * 60.0 * 20.0));
	}
}
