package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * A donut-shaped room: a ring-shaped chamber lying flat (one in four tilted 15 to 35 degrees) around
 * a pillar of rock, which is simply the uncarved hole in the middle, standing from the room's floor to
 * its ceiling. The ring's cross-section is a little wider than tall, with a flat floor. Two or three
 * forking tunnels lead out from the outer wall. Radius is the ring's tube radius (half its width);
 * size is the ring radius (centre to the middle of the ring), kept at least 4 blocks more than the
 * tube so the pillar is at least 4 blocks thick.
 */
public final class ToroidalCave implements CaveType {
	/** Everything that fixes a room's shape, drawn first from its random so tests can replay it. */
	public record Plan(double x, double y, double z, double ringRadius, double tubeRadius, double tubeHeight, double tilt, double tiltYaw) {
		/** Room coordinates (u, w across the ring's plane, v along its axis) to a world position. */
		public Vec3d toWorld(double u, double v, double w) {
			double cosT = Math.cos(this.tilt);
			double sinT = Math.sin(this.tilt);
			double y1 = v * cosT - w * sinT;
			double z1 = v * sinT + w * cosT;
			double cosY = Math.cos(this.tiltYaw);
			double sinY = Math.sin(this.tiltYaw);
			return new Vec3d(this.x + u * cosY - z1 * sinY, this.y + y1, this.z + u * sinY + z1 * cosY);
		}

		/** Inside the ring, for a world offset from the centre. */
		public boolean contains(double ox, double oy, double oz) {
			double cosY = Math.cos(this.tiltYaw);
			double sinY = Math.sin(this.tiltYaw);
			double u = ox * cosY + oz * sinY;
			double z1 = -ox * sinY + oz * cosY;
			double cosT = Math.cos(this.tilt);
			double sinT = Math.sin(this.tilt);
			double v = oy * cosT + z1 * sinT;
			double w = -oy * sinT + z1 * cosT;
			double across = (Math.sqrt(u * u + w * w) - this.ringRadius) / this.tubeRadius;
			double up = v / this.tubeHeight;
			return up > -0.8 && across * across + up * up < 1.0;
		}

		/** Half the room's height in the world, tilt included. */
		public double verticalExtent() {
			return this.tubeHeight * Math.cos(this.tilt) + (this.ringRadius + this.tubeRadius) * Math.sin(this.tilt);
		}
	}

	@Override
	public String id() {
		return "toroidal";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(0.5, -45, 30, 3.5, 5.5, 9.0, 15.0);
	}

	@Override
	public double forcedWeight() {
		return 15.0;
	}

	/**
	 * The room starting at x, y, z: minCentreY and maxCentreY are the lowest and highest y a shape's
	 * centre may use (CaveBuilder.minY and maxY). These are the first draws from the cave's random.
	 */
	public static Plan plan(Random random, CaveTypeSettings settings, double x, double y, double z, int minCentreY, int maxCentreY) {
		double tube = CaveBuilder.radius(random, settings);
		double ring = Math.max(settings.minSize + random.nextDouble() * Math.max(0.0, settings.maxSize - settings.minSize), tube + 4.0);
		double height = tube * (0.7 + random.nextDouble() * 0.15);
		boolean tilted = random.nextInt(4) == 0;
		double tilt = tilted ? Math.toRadians(15.0 + random.nextDouble() * 20.0) : 0.0;
		double tiltYaw = random.nextDouble() * Math.PI * 2;
		Plan draft = new Plan(x, y, z, ring, tube, height, tilt, tiltYaw);
		double extent = draft.verticalExtent();
		double centreY = MathHelper.clamp(y, minCentreY + extent, Math.max(minCentreY + extent, maxCentreY - extent));
		return new Plan(x, centreY, z, ring, tube, height, tilt, tiltYaw);
	}

	@Override
	public Vec3d generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		Plan plan = plan(random, settings, x, y, z, builder.minY(), builder.maxY());
		double horizontal = plan.ringRadius() + plan.tubeRadius() + 1.0;
		double vertical = plan.verticalExtent() + 1.0;
		builder.shape(plan.x(), plan.y(), plan.z(), horizontal, vertical, horizontal, true, true,
				(dx, dy, dz, blockY) -> plan.contains(dx * horizontal, dy * vertical, dz * horizontal));

		int exits = 2 + random.nextInt(2);
		float first = random.nextFloat() * CaveBuilder.TAU;
		for (int i = 0; i < exits; i++) {
			float angle = first + i * CaveBuilder.TAU / exits + (random.nextFloat() - 0.5F) * 0.8F;
			double out = plan.ringRadius() + plan.tubeRadius() * 0.6;
			Vec3d start = plan.toWorld(out * MathHelper.cos(angle), 0.0, out * MathHelper.sin(angle));
			float yaw = (float) Math.atan2(start.z - plan.z(), start.x - plan.x());
			Tunnel tunnel = new Tunnel(start.x, start.y, start.z, yaw + (random.nextFloat() - 0.5F) * 0.5F, (random.nextFloat() - 0.5F) * 0.3F);
			SpaghettiCave.tunnel(builder, random, tunnel, 1.5, 0.8 + random.nextDouble() * 1.2, 30 + random.nextInt(25), true);
		}
		return plan.toWorld(plan.ringRadius(), 0.0, 0.0);
	}
}
