package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Big irregular caverns: a flattened, stretched main chamber with lobes of different sizes and heights
 * around it (uneven walls, alcoves, a lumpy ceiling), a mostly flat floor, and tunnels leading out.
 * The radius (of the main chamber) follows a bell curve with a long tail: typicalRadius times
 * e^(0.35 x a normal random), kept between minRadius and maxRadius. With the defaults (typical 25, 12 to
 * 80) about two in three caverns are 18 to 35 blocks in radius, about one in 45 passes 50 and one in
 * 250 passes 65. Big caverns get more lobes and exits, and grow less in height
 * than in width. They use the per-block water rule, so one near the sea keeps a wall of rock.
 */
public final class LargeCave implements CaveType {
	/** Spread of the bell curve (log scale): 0.35 puts two caverns in three within about a third of the typical radius. */
	private static final double SPREAD = 0.35;

	@Override
	public String id() {
		return "large";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(1.0, -48, 15, 12.0, 80.0, 25.0);
	}

	@Override
	public double forcedWeight() {
		return 4.0;
	}

	/** The main chamber's radius: the first number a cavern draws (the cave search in the map tool relies on that). */
	public static double radius(Random random, CaveTypeSettings settings) {
		return settings.typicalRadius > 0
				? MathHelper.clamp(settings.typicalRadius * Math.exp(SPREAD * random.nextGaussian()), settings.minRadius, settings.maxRadius)
				: CaveBuilder.radius(random, settings);
	}

	@Override
	public Vec3d generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		double radius = radius(random, settings);
		double vertical = Math.min(radius * (0.45 + random.nextDouble() * 0.2), 8.0 + radius * 0.3);
		double stretchX = 0.75 + random.nextDouble() * 0.5;
		double stretchZ = 0.75 + random.nextDouble() * 0.5;
		double centreY = builder.clampY(y, vertical);
		builder.ellipsoid(x, centreY, z, radius * stretchX, vertical, radius * stretchZ, -0.55, true);

		int lobes = 5 + random.nextInt(6) + (int) (radius / 10);
		for (int i = 0; i < lobes; i++) {
			float angle = random.nextFloat() * CaveBuilder.TAU;
			double distance = radius * (0.35 + random.nextDouble() * 0.5);
			double lobeRadius = radius * (0.3 + random.nextDouble() * 0.35);
			double lobeVertical = Math.min(lobeRadius * (0.5 + random.nextDouble() * 0.3), vertical);
			double lobeStretch = 0.8 + random.nextDouble() * 0.4;
			double lobeYOffset = (random.nextDouble() - 0.4) * vertical * 0.8;
			double lobeX = x + MathHelper.cos(angle) * distance * stretchX;
			double lobeZ = z + MathHelper.sin(angle) * distance * stretchZ;
			// The biggest caverns would push lobes past what neighbouring chunks can see; pull them in.
			while (!builder.inReach(lobeX, lobeZ, Math.max(lobeRadius, lobeRadius * lobeStretch)) && distance > 1.0) {
				distance *= 0.8;
				lobeRadius *= 0.9;
				lobeX = x + MathHelper.cos(angle) * distance * stretchX;
				lobeZ = z + MathHelper.sin(angle) * distance * stretchZ;
			}
			double lobeY = builder.clampY(centreY + lobeYOffset, lobeVertical);
			builder.ellipsoid(lobeX, lobeY, lobeZ, lobeRadius, lobeVertical, lobeRadius * lobeStretch, -0.5, true);
		}

		int exits = 2 + random.nextInt(4) + (int) (radius / 25);
		for (int i = 0; i < exits; i++) {
			float angle = random.nextFloat() * CaveBuilder.TAU;
			double exitX = x + MathHelper.cos(angle) * radius * stretchX * 0.8;
			double exitZ = z + MathHelper.sin(angle) * radius * stretchZ * 0.8;
			double exitY = centreY - vertical * 0.3 + random.nextDouble() * vertical * 0.5;
			Tunnel tunnel = new Tunnel(exitX, exitY, exitZ, angle + (random.nextFloat() - 0.5F) * 0.6F, (random.nextFloat() - 0.5F) * 0.4F);
			SpaghettiCave.tunnel(builder, random, tunnel, 1.5, 0.5 + random.nextDouble() * 1.5, 40 + random.nextInt(50), false);
		}
		return new Vec3d(x, centreY, z);
	}
}
