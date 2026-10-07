package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;

/**
 * Big irregular caverns: a stretched main chamber with lobes of different sizes and heights around it
 * (uneven walls, alcoves), and tunnels leading out. Floor and ceiling follow smooth noise: the floor
 * rises into mounds and sinks into hollows, the ceiling bulges a little, so nothing is flat.
 * The radius (of the main chamber) follows a bell curve with a long tail: typicalRadius times
 * e^(0.35 x a normal random), kept between minRadius and maxRadius. With the defaults (typical 25, 12 to
 * 80) about two in three caverns are 18 to 35 blocks in radius, about one in 45 passes 50 and one in
 * 250 passes 65. Big caverns get more lobes and exits, and grow less in height
 * than in width. They use the per-block water rule, so one near the sea keeps a wall of rock.
 */
public final class LargeCave implements CaveType {
	/** Spread of the bell curve (log scale): 0.35 puts two caverns in three within about a third of the typical radius. */
	private static final double SPREAD = 0.35;
	/** How far the floor's depth swings with the noise: the bottom sits between 0.6 and 1.44 of the vertical radius below centre. */
	private static final double FLOOR_SWING = 0.42;
	/** Each piece's box reaches this much further than its vertical radius, to fit the deepest hollows. */
	private static final double FLOOR_ROOM = 1.5;

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
		double centreY = builder.clampY(y, vertical * FLOOR_ROOM);
		SimplexNoiseSampler noise = new SimplexNoiseSampler(random);
		chamber(builder, noise, x, centreY, z, radius * stretchX, vertical, radius * stretchZ);

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
			double lobeY = builder.clampY(centreY + lobeYOffset, lobeVertical * FLOOR_ROOM);
			chamber(builder, noise, lobeX, lobeY, lobeZ, lobeRadius, lobeVertical, lobeRadius * lobeStretch);
		}

		int exits = 3 + random.nextInt(4) + (int) (radius / 20);
		for (int i = 0; i < exits; i++) {
			float angle = random.nextFloat() * CaveBuilder.TAU;
			double exitX = x + MathHelper.cos(angle) * radius * stretchX * 0.8;
			double exitZ = z + MathHelper.sin(angle) * radius * stretchZ * 0.8;
			double exitY = centreY - vertical * 0.3 + random.nextDouble() * vertical * 0.5;
			Tunnel tunnel = new Tunnel(exitX, exitY, exitZ, angle + (random.nextFloat() - 0.5F) * 0.6F, (random.nextFloat() - 0.5F) * 0.4F);
			SpaghettiCave.tunnel(builder, random, tunnel, 1.5, 0.5 + random.nextDouble() * 1.5, 40 + random.nextInt(50), true);
		}
		return new Vec3d(x, centreY, z);
	}

	/**
	 * One piece of the cavern: an ellipsoid whose lower half is squashed or stretched by smooth noise
	 * (two scales, about 24 and 12 blocks across), so the floor undulates, and whose upper half bulges a
	 * little. The same noise runs through every piece of a cavern, so neighbouring lobes join smoothly.
	 */
	private static void chamber(CaveBuilder builder, SimplexNoiseSampler noise, double x, double y, double z, double rx, double ry, double rz) {
		builder.shape(x, y, z, rx, ry * FLOOR_ROOM, rz, true, true, (dx, dy, dz, blockY) -> {
			double worldX = x + dx * rx;
			double worldZ = z + dz * rz;
			double n = 0.78 * noise.sample(worldX / 24.0, worldZ / 24.0) + 0.22 * noise.sample(worldX / 12.0 + 31.7, worldZ / 12.0 - 12.3);
			// dy is scaled by the box (ry * FLOOR_ROOM); back to units of ry, then stretched by the noise.
			double vertical = dy * FLOOR_ROOM;
			vertical /= vertical < 0 ? 1.02 - FLOOR_SWING * n : 1.0 + 0.15 * n;
			return dx * dx + vertical * vertical + dz * dz < 1.0;
		});
	}
}
