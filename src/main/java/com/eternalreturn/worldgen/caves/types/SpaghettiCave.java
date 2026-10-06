package com.eternalreturn.worldgen.caves.types;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveBuilder;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.Tunnel;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;

/**
 * Release 1.6.4-style caves: long, thin tunnels that wander in heading and height, in systems the way
 * 1.6.4 grouped them. Each start is a system of two to six tunnels leaving from points a few blocks
 * apart, which tangle into a network around the start and leave quiet stretches between systems. A
 * tunnel branches once in about one case in five. Weight counts systems; radius is a tunnel's
 * half-width at its widest.
 */
public final class SpaghettiCave implements CaveType {
	@Override
	public String id() {
		return "spaghetti";
	}

	@Override
	public CaveTypeSettings defaults() {
		return new CaveTypeSettings(24.0, -58, 85, 1.3, 2.0);
	}

	@Override
	public double forcedWeight() {
		return 60.0;
	}

	@Override
	public void generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings) {
		int tunnels = 2 + random.nextInt(5);
		for (int i = 0; i < tunnels; i++) {
			double startX = x + (random.nextDouble() - 0.5) * 12.0;
			double startY = y + (random.nextDouble() - 0.5) * 8.0;
			double startZ = z + (random.nextDouble() - 0.5) * 12.0;
			float yaw = random.nextFloat() * CaveBuilder.TAU;
			float pitch = (random.nextFloat() - 0.5F) * 0.5F;
			tunnel(builder, random, new Tunnel(startX, startY, startZ, yaw, pitch), CaveBuilder.radius(random, settings), 90 + random.nextInt(80), true);
		}
	}

	/** One winding tunnel; also used for the exits of other types. */
	public static void tunnel(CaveBuilder builder, Random random, Tunnel tunnel, double radius, int length, boolean mayBranch) {
		// One tunnel in three keeps its slope longer, so it climbs or drops further.
		float pitchKeep = random.nextInt(3) == 0 ? 0.92F : 0.75F;
		int branchAt = mayBranch && length > 6 && random.nextInt(5) == 0 ? length / 3 + random.nextInt(length / 3) : -1;
		long branchSeed = random.nextLong();
		if (branchAt < 0) {
			builder.winding(random, tunnel, length, step -> radius * CaveBuilder.endTaper(step, length), 0.9, pitchKeep, 2.0F, 4.0F);
			return;
		}
		builder.winding(random, tunnel, branchAt, step -> radius * CaveBuilder.endTaper(step, length), 0.9, pitchKeep, 2.0F, 4.0F);
		// The branch leaves at a right angle with its own random numbers; the main tunnel carries on.
		Random branchRandom = new Xoroshiro128PlusPlusRandom(branchSeed);
		float side = branchRandom.nextBoolean() ? 1.0F : -1.0F;
		int branchLength = (length - branchAt) * 3 / 4;
		Tunnel branch = tunnel.copy(tunnel.yaw + side * CaveBuilder.TAU / 4, tunnel.pitch / 3);
		builder.winding(branchRandom, branch, branchLength, step -> radius * 0.85 * CaveBuilder.endTaper(step, branchLength), 0.9, pitchKeep, 2.0F, 4.0F);
		int rest = length - branchAt;
		builder.winding(random, tunnel, rest, step -> radius * CaveBuilder.endTaper(step + branchAt, length), 0.9, pitchKeep, 2.0F, 4.0F);
	}
}
