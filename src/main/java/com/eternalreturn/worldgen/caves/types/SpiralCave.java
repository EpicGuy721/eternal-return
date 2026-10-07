package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * A helical tunnel: it winds two to four turns, clockwise or anticlockwise, around a vertical axis
 * while dropping 30 to 70 blocks at a steady rate, leaving a solid column of rock in the middle. Each
 * turn drops at least enough to leave rock between the coils. A forking tunnel leaves each end so
 * the spiral joins the caves around it. Radius is the tube's radius; size is the coil radius (axis
 * to the middle of the tube), kept at least 4 blocks more than the tube so the column stays solid.
 * The start y is the top of the spiral.
 */
public final class SpiralCave implements CaveType {
	/** Rock left between one coil and the next, in blocks, at the least. */
	private static final double GAP = 2.5;
	private static final double TUBE_SQUASH = 0.9;

	/** Everything that fixes a spiral's shape, drawn first from its random so tests can replay it. */
	public record Plan(double centreX, double centreZ, double topY, double drop, double turns, double coilRadius, double tubeRadius,
			int direction, double startAngle) {
		/** The middle of the tube, a fraction t (0 top, 1 bottom) of the way down. */
		public Vec3d point(double t) {
			double angle = this.startAngle + this.direction * t * this.turns * Math.PI * 2;
			return new Vec3d(this.centreX + this.coilRadius * Math.cos(angle), this.topY - this.drop * t, this.centreZ + this.coilRadius * Math.sin(angle));
		}

		/** Direction of travel (yaw for Tunnel) at fraction t. */
		public float yaw(double t) {
			double angle = this.startAngle + this.direction * t * this.turns * Math.PI * 2;
			return (float) Math.atan2(this.direction * Math.cos(angle), -this.direction * Math.sin(angle));
		}

		public double length() {
			double around = this.coilRadius * this.turns * Math.PI * 2;
			return Math.sqrt(around * around + this.drop * this.drop);
		}
	}

	@Override
	public String id() {
		return "spiral";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(0.6, -20, 60, 2.0, 3.0, 7.0, 11.0);
	}

	@Override
	public double forcedWeight() {
		return 20.0;
	}

	/**
	 * The spiral starting at x, y, z: minCentreY and maxCentreY are the lowest and highest y a shape's
	 * centre may use (CaveBuilder.minY and maxY). These are the first draws from the cave's random.
	 */
	public static Plan plan(Random random, CaveTypeSettings settings, double x, double y, double z, int minCentreY, int maxCentreY) {
		double tube = CaveBuilder.radius(random, settings);
		double coil = Math.max(settings.minSize + random.nextDouble() * Math.max(0.0, settings.maxSize - settings.minSize), tube + 4.0);
		double drop = 30.0 + random.nextDouble() * 40.0;
		double turns = 2.0 + random.nextDouble() * 2.0;
		int direction = random.nextBoolean() ? 1 : -1;
		double startAngle = random.nextDouble() * Math.PI * 2;
		double top = MathHelper.clamp(y, minCentreY + tube, maxCentreY - tube);
		// Keep the bottom inside the world, and each turn deep enough to leave rock between coils.
		drop = Math.max(10.0, Math.min(drop, top - (minCentreY + tube + 2.0)));
		double perTurn = 2.0 * tube * TUBE_SQUASH + GAP;
		turns = Math.max(1.0, Math.min(turns, drop / perTurn));
		return new Plan(x, z, top, drop, turns, coil, tube, direction, startAngle);
	}

	@Override
	public Vec3d generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		Plan plan = plan(random, settings, x, y, z, builder.minY(), builder.maxY());
		double tube = plan.tubeRadius();
		int steps = (int) Math.ceil(plan.length() / 0.7);
		for (int i = 0; i <= steps; i++) {
			Vec3d point = plan.point((double) i / steps);
			builder.ellipsoid(point.x, point.y, point.z, tube, tube * TUBE_SQUASH, tube, -0.75);
		}
		// Forking tunnels leave both ends, carrying on the way the spiral was going, so it joins other caves.
		Vec3d top = plan.point(0.0);
		Tunnel up = new Tunnel(top.x, top.y, top.z, plan.yaw(0.0) + (float) Math.PI, 0.15F);
		SpaghettiCave.tunnel(builder, random, up, 1.5, 0.8 + random.nextDouble() * 1.2, 25 + random.nextInt(20), true);
		Vec3d bottom = plan.point(1.0);
		Tunnel down = new Tunnel(bottom.x, bottom.y, bottom.z, plan.yaw(1.0), -0.1F);
		SpaghettiCave.tunnel(builder, random, down, 1.5, 0.8 + random.nextDouble() * 1.2, 25 + random.nextInt(20), true);
		return top;
	}
}
