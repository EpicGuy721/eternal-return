package com.eternalreturn.worldgen.caves;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * One kind of cave. Each type only describes its shape; where caves start, how often, the random
 * numbers and the chunk-by-chunk carving are handled by CaveSystemCarver and CaveBuilder.
 */
public interface CaveType {
	/** Config key under worldgen.caves.types, and the value debugForceCaveType takes. */
	String id();

	/** Weight (caves per 100 chunks), start depth range and radius range written to a new config. */
	CaveTypeSettings defaults();

	/** Caves per 100 chunks when debugForceCaveType picks this type: high enough to find examples anywhere. */
	double forcedWeight();

	/**
	 * Carves one cave starting at x, y, z (y already within the configured depth range). Must use
	 * only the given random, so the same start always gives the same cave. Returns a point inside the
	 * cave for the start log (usually the start itself; a cavern's centre after moving it clear of the floor).
	 */
	Vec3d generate(CaveBuilder builder, Random random, double x, double y, double z, CaveTypeSettings settings);
}
