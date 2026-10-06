package com.eternalreturn.mobs;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Aiming for ThrownEntity projectiles (ender pearls, the zombie hook). */
public final class Ballistics {
	private static final double AIR_DRAG = 0.99;

	private Ballistics() {
	}

	/**
	 * Lowest-arc launch velocity that brings a projectile from {@code from} to {@code to}, found by
	 * stepping ThrownEntity's own integration (move, then drag, then gravity) at 1 degree increments.
	 * Returns null if the target is out of reach at this speed.
	 */
	@Nullable
	public static Vec3d solve(Vec3d from, Vec3d to, double speed, double gravity) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double dy = to.y - from.y;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 1.0E-3) {
			return null;
		}

		for (int degrees = -45; degrees <= 75; degrees++) {
			double angle = Math.toRadians(degrees);
			double vh = Math.cos(angle) * speed;
			double vy = Math.sin(angle) * speed;
			double h = 0.0;
			double y = 0.0;
			for (int tick = 0; tick < 200; tick++) {
				double nextH = h + vh;
				double nextY = y + vy;
				if (nextH >= distance) {
					double yAtTarget = y + (nextY - y) * ((distance - h) / (nextH - h));
					if (yAtTarget >= dy) {
						double launchH = Math.cos(angle) * speed;
						return new Vec3d(dx / distance * launchH, Math.sin(angle) * speed, dz / distance * launchH);
					}
					break;
				}
				if (vy < 0.0 && nextY < dy - 2.0) {
					break;
				}
				h = nextH;
				y = nextY;
				vh *= AIR_DRAG;
				vy = vy * AIR_DRAG - gravity;
			}
		}
		return null;
	}
}
