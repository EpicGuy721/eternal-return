package com.eternalreturn.worldgen.caves;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/** A point moving through the ground with a heading (yaw, around y) and slope (pitch, up positive). */
public final class Tunnel {
	public double x;
	public double y;
	public double z;
	public float yaw;
	public float pitch;
	private float yawChange;
	private float pitchChange;

	public Tunnel(double x, double y, double z, float yaw, float pitch) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	public Tunnel copy(float yaw, float pitch) {
		return new Tunnel(this.x, this.y, this.z, yaw, pitch);
	}

	/** Moves length blocks along the current heading and slope. */
	public void step(double length) {
		float flat = MathHelper.cos(this.pitch);
		this.x += MathHelper.cos(this.yaw) * flat * length;
		this.y += MathHelper.sin(this.pitch) * length;
		this.z += MathHelper.sin(this.yaw) * flat * length;
	}

	/**
	 * Release 1.6.4's cave wander: the slope relaxes toward flat (pitchKeep: 0.7 normally, 0.92 for
	 * steep tunnels), and heading and slope drift by a smoothed random change. turn scales how much of
	 * the change applies per step (0.1 in 1.6.4 caves, 0.05 in its ravines).
	 */
	public void wander(Random random, float pitchKeep, float turn, float pitchNoise, float yawNoise) {
		this.pitch *= pitchKeep;
		this.pitch += this.pitchChange * turn;
		this.yaw += this.yawChange * turn;
		this.pitchChange *= 0.9F;
		this.yawChange *= 0.75F;
		this.pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * pitchNoise;
		this.yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * yawNoise;
	}

	/** Turns the slope back toward the inside when the tunnel nears the world's floor or ceiling. */
	public void keepWithin(CaveBuilder builder, double verticalRadius) {
		if (this.y - verticalRadius < builder.minY() + 2) {
			this.y = Math.max(this.y, builder.minY() + verticalRadius);
			this.pitch = Math.abs(this.pitch) * 0.5F + 0.05F;
		} else if (this.y + verticalRadius > builder.maxY() - 2) {
			this.y = Math.min(this.y, builder.maxY() - verticalRadius);
			this.pitch = -Math.abs(this.pitch) * 0.5F - 0.05F;
		}
	}
}
