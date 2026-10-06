package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

/**
 * Constant-width tunnels of straight segments with sharp turns at regular intervals: every segment of
 * a cave has the same length (6 to 14 blocks), and the heading swings the same angle (70 to 110
 * degrees) left and right in turn, so the tunnel zig-zags along a line. Each segment has its own gentle
 * slope. Radius is the tunnel's radius.
 */
public final class ZigZagCave implements CaveType {
	@Override
	public String id() {
		return "zigzag";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(7.0, -50, 50, 1.5, 2.5);
	}

	@Override
	public double forcedWeight() {
		return 60.0;
	}

	@Override
	public void generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		double radius = CaveBuilder.radius(random, settings);
		int segmentLength = 6 + random.nextInt(9);
		float turn = (70.0F + random.nextFloat() * 40.0F) * MathHelper.RADIANS_PER_DEGREE;
		int segments = 8 + random.nextInt(9);
		float heading = random.nextFloat() * CaveBuilder.TAU;
		float side = random.nextBoolean() ? 1.0F : -1.0F;
		double step = 0.75;
		for (int segment = 0; segment < segments; segment++) {
			float yaw = heading + side * turn / 2;
			side = -side;
			float pitch = (random.nextFloat() - 0.5F) * 0.5F;
			float flat = MathHelper.cos(pitch);
			for (double done = 0; done < segmentLength; done += step) {
				x += MathHelper.cos(yaw) * flat * step;
				y = builder.clampY(y + MathHelper.sin(pitch) * step, radius);
				z += MathHelper.sin(yaw) * flat * step;
				if (!builder.inReach(x, z, radius + 1)) {
					return;
				}
				builder.ellipsoid(x, y, z, radius, radius, radius, -0.8);
			}
		}
	}
}
