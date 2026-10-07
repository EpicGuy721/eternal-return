package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;

/**
 * Release 1.6.4's caves (MapGenCaves), with its sizes and maths:
 * <ul>
 * <li>a cave system is one chunk holding a cluster of starts (1.6.4: rand(rand(rand(40) + 1) + 1),
 * here at least one), each at its own spot in the chunk and its own depth, deeper ones more likely;</li>
 * <li>one start in four opens a round, flattened room (radius 2.5 to 8.5, half as tall) and sends one to
 * four tunnels out of it; otherwise one tunnel;</li>
 * <li>a tunnel runs 85 to 112 blocks, swelling from minRadius at its ends to minRadius + width in the
 * middle; width is 0 to (maxRadius - minRadius), weighted toward the middle, and one tunnel in ten is
 * widened up to four times;</li>
 * <li>flat floors, a quarter of the steps skipped (rough walls), one tunnel in six keeping its slope.</li>
 * </ul>
 * One change: 1.6.4 split most tunnels into two thinner ones halfway; here one tunnel in five sends
 * off a thinner side branch and carries on. Weight counts cave systems (1.6.4 had about 6.7 per 100 chunks).
 */
public final class SpaghettiCave implements CaveType {
	@Override
	public String id() {
		return "spaghetti";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(5.0, -58, 85, 1.5, 4.5);
	}

	@Override
	public double forcedWeight() {
		return 25.0;
	}

	@Override
	public Vec3d generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		int chunkStartX = MathHelper.floor(x) & ~15;
		int chunkStartZ = MathHelper.floor(z) & ~15;
		int starts = 1 + random.nextInt(random.nextInt(random.nextInt(40) + 1) + 1);
		double startX = x;
		double startY = y;
		double startZ = z;
		for (int i = 0; i < starts; i++) {
			if (i > 0) {
				startX = chunkStartX + random.nextInt(16) + 0.5;
				startZ = chunkStartZ + random.nextInt(16) + 0.5;
				startY = lowBiasedY(random, settings) + 0.5;
			}
			int tunnels = 1;
			if (random.nextInt(4) == 0) {
				room(builder, random, startX, startY, startZ);
				tunnels += random.nextInt(4);
			}
			for (int t = 0; t < tunnels; t++) {
				float yaw = random.nextFloat() * CaveBuilder.TAU;
				float pitch = (random.nextFloat() - 0.5F) * 2.0F / 8.0F;
				tunnel(builder, random, new Tunnel(startX, startY, startZ, yaw, pitch), settings.minRadius, width(random, settings),
						112 - random.nextInt(28), true);
			}
		}
		return new Vec3d(x, y, z);
	}

	/** Release 1.6.4: rand(rand(120) + 8), which favours low starts; here across the configured range. */
	private static int lowBiasedY(Random random, CaveTypeSettings settings) {
		int range = Math.max(9, settings.maxY - settings.minY);
		return settings.minY + random.nextInt(random.nextInt(range - 8) + 8);
	}

	/** Release 1.6.4: rand * 2 + rand (0 to 3), one in ten multiplied by up to 4; scaled to the radius range. */
	private static double width(Random random, CaveTypeSettings settings) {
		double width = (random.nextFloat() * 2.0F + random.nextFloat()) / 3.0 * Math.max(0.0, settings.maxRadius - settings.minRadius);
		if (random.nextInt(10) == 0) {
			width *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
		}
		return width;
	}

	/** Release 1.6.4's room: one flattened ellipsoid, radius 2.5 to 8.5, half as tall, flat floor. */
	private static void room(CaveBuilder builder, Random random, double x, double y, double z) {
		double radius = 1.5 + 1.0 + random.nextFloat() * 6.0F;
		builder.ellipsoid(x + 1.0, y, z, radius, radius * 0.5, radius, -0.7);
	}

	/**
	 * One tunnel of the given length, radius endRadius at its ends and endRadius + width in the middle.
	 * Also used for the exits of other types.
	 */
	public static void tunnel(CaveBuilder builder, Random random, Tunnel tunnel, double endRadius, double width, int length, boolean mayBranch) {
		boolean steep = random.nextInt(6) == 0;
		int branchAt = random.nextInt(Math.max(1, length / 2)) + length / 4;
		boolean branches = mayBranch && width > 1.0 && random.nextInt(5) == 0;
		long branchSeed = random.nextLong();
		walk(builder, random, tunnel, endRadius, width, 0, length, steep, branches ? branchAt : -1, branchSeed);
	}

	private static void walk(CaveBuilder builder, Random random, Tunnel tunnel, double endRadius, double width, int from, int length,
			boolean steep, int branchAt, long branchSeed) {
		for (int i = from; i < length; i++) {
			double radius = endRadius + Math.sin(Math.PI * i / length) * width;
			tunnel.step(1.0);
			tunnel.wander(random, steep ? 0.92F : 0.7F, 0.1F, 2.0F, 4.0F);
			tunnel.keepWithin(builder, radius);
			if (!builder.inReach(tunnel.x, tunnel.z, radius + 1)) {
				return;
			}
			if (i == branchAt) {
				// A thinner side tunnel (1.6.4's branch width) leaves at a right angle with its own random numbers.
				Random branchRandom = new Xoroshiro128PlusPlusRandom(branchSeed);
				float side = branchRandom.nextBoolean() ? 1.0F : -1.0F;
				double branchWidth = branchRandom.nextFloat() * 0.5F + 0.5F;
				walk(builder, branchRandom, tunnel.copy(tunnel.yaw + side * CaveBuilder.TAU / 4, tunnel.pitch / 3.0F), endRadius, branchWidth,
						i, length, steep, -1, 0L);
			}
			// Release 1.6.4 skipped a quarter of its steps, which roughens the walls.
			if (random.nextInt(4) == 0) {
				continue;
			}
			builder.ellipsoid(tunnel.x, tunnel.y, tunnel.z, radius, radius, radius, -0.7);
		}
	}
}
