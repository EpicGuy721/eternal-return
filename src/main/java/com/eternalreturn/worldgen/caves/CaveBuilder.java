package com.eternalreturn.worldgen.caves;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * What a cave type draws with. A cave is simulated in full from its start chunk (the source) every
 * time a chunk within range is carved; only the parts that fall inside that chunk are written.
 * Shapes must stay within REACH blocks of the source chunk's centre: the generator only offers a
 * chunk the caves of sources up to 8 chunks away, so anything further would be cut off at a chunk
 * border. Types stop a tunnel once inReach says no.
 */
public final class CaveBuilder {
	/** How far (per axis, in blocks) a shape may reach from its source chunk's centre. */
	public static final int REACH = 120;
	public static final float TAU = (float) (Math.PI * 2.0);

	private final ChunkCarver target;
	private final double sourceX;
	private final double sourceZ;

	public CaveBuilder(ChunkCarver target, ChunkPos source) {
		this.target = target;
		this.sourceX = source.getStartX() + 8;
		this.sourceZ = source.getStartZ() + 8;
	}

	/** Lowest and highest y a shape's centre should use: 3 blocks inside the carving limits. */
	public int minY() {
		return this.target.minY() + 3;
	}

	public int maxY() {
		return this.target.maxY() - 3;
	}

	/** Whether a shape of horizontal radius r centred at x, z stays within reach of the source chunk. */
	public boolean inReach(double x, double z, double r) {
		return Math.abs(x - this.sourceX) + r <= REACH && Math.abs(z - this.sourceZ) + r <= REACH;
	}

	/** A radius from the type's configured range. */
	public static double radius(Random random, CaveTypeSettings settings) {
		return settings.minRadius + random.nextDouble() * Math.max(0.0, settings.maxRadius - settings.minRadius);
	}

	/** y moved so that a shape of vertical radius ry stays inside the world's carving limits. */
	public double clampY(double y, double ry) {
		return MathHelper.clamp(y, this.minY() + ry, Math.max(this.minY() + ry, this.maxY() - ry));
	}

	/** Ellipsoid (see ChunkCarver.ellipsoid). Ignored when out of reach. */
	public void ellipsoid(double x, double y, double z, double rx, double ry, double rz, double floor) {
		this.ellipsoid(x, y, z, rx, ry, rz, floor, false);
	}

	/** Ellipsoid with the per-block water rule when perBlockFluids is set (for very big shapes). */
	public void ellipsoid(double x, double y, double z, double rx, double ry, double rz, double floor, boolean perBlockFluids) {
		if (this.inReach(x, z, Math.max(rx, rz)) && this.target.touches(x - rx - 1, x + rx + 1, z - rz - 1, z + rz + 1)) {
			this.target.ellipsoid(x, y, z, rx, ry, rz, floor, perBlockFluids);
		}
	}

	/** Any shape (see ChunkCarver.carve). Ignored when out of reach. */
	public void shape(double x, double y, double z, double rx, double ry, double rz, boolean withinCircle, ChunkCarver.Shape shape) {
		if (this.inReach(x, z, Math.max(rx, rz)) && this.target.touches(x - rx - 1, x + rx + 1, z - rz - 1, z + rz + 1)) {
			this.target.carve(x, y, z, rx, ry, rz, withinCircle, shape);
		}
	}

	/**
	 * A winding tunnel in the style of Release 1.6.4's caves, walked one block per step for length
	 * steps: an ellipsoid of horizontal radius radius(step) and vertical radius radius * vScale at
	 * every step, with a flat floor (Release 1.6.4 cut its tunnels at 0.7 of the radius below centre).
	 * Stops early once out of reach.
	 */
	public void winding(Random random, Tunnel tunnel, int length, java.util.function.IntToDoubleFunction radius, double vScale,
			float pitchKeep, float pitchNoise, float yawNoise) {
		for (int i = 0; i < length; i++) {
			double r = radius.applyAsDouble(i);
			tunnel.step(1.0);
			tunnel.wander(random, pitchKeep, 0.1F, pitchNoise, yawNoise);
			tunnel.keepWithin(this, r * vScale);
			if (!this.inReach(tunnel.x, tunnel.z, r + 1)) {
				return;
			}
			this.ellipsoid(tunnel.x, tunnel.y, tunnel.z, r, r * vScale, r, -0.7);
		}
	}

	/** 1 in the middle of a tunnel, falling to about 0.65 over its first and last sixths. */
	public static double endTaper(int step, int length) {
		double t = Math.sin(Math.PI * (step + 1) / (length + 1));
		return 0.65 + 0.35 * Math.min(1.0, t * 2.0);
	}
}
