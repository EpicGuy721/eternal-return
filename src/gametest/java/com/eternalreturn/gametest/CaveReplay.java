package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveSystemCarver;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds where caves of one type start without generating anything, by replaying the random numbers
 * the carver gets: Moderner Beta seeds the carver's random per chunk ("early release" seeding: a legacy
 * random from the world seed gives two salts, then chunk x * salt + chunk z * salt ^ seed), the cave
 * engine takes one long from it and salts it with the type's name, then draws whether a cave starts and
 * where. The returned random is ready for the type's own first draws (its plan). Exact when at most one
 * cave starts per chunk, which every forced and default weight gives (weight 100 or less).
 */
public final class CaveReplay {
	public record Start(int x, int y, int z, Random random) {
	}

	private CaveReplay() {
	}

	public static List<Start> starts(ServerWorld world, String typeId, double weight, CaveTypeSettings settings, int chunkX0, int chunkZ0, int size) {
		long seed = world.getSeed();
		LocalRandom random = new LocalRandom(seed);
		long saltX = random.nextLong();
		long saltZ = random.nextLong();
		double expected = weight / 100.0;
		List<Start> starts = new ArrayList<>();
		for (int chunkX = chunkX0; chunkX < chunkX0 + size; chunkX++) {
			for (int chunkZ = chunkZ0; chunkZ < chunkZ0 + size; chunkZ++) {
				random.setSeed((long) chunkX * saltX + (long) chunkZ * saltZ ^ seed);
				Random typeRandom = new Xoroshiro128PlusPlusRandom(CaveSystemCarver.mix(random.nextLong(), typeId.hashCode()));
				int count = (int) expected + (typeRandom.nextDouble() < expected - (int) expected ? 1 : 0);
				if (count == 0) {
					continue;
				}
				int x = chunkX * 16 + typeRandom.nextInt(16);
				int z = chunkZ * 16 + typeRandom.nextInt(16);
				int y = settings.minY + typeRandom.nextInt(Math.max(1, settings.maxY - settings.minY + 1));
				starts.add(new Start(x, y, z, typeRandom));
			}
		}
		return starts;
	}

	/** Lowest and highest centre y a shape may use in this world (CaveBuilder.minY and maxY). */
	public static int minCentreY(ServerWorld world) {
		return world.getBottomY() + 4;
	}

	public static int maxCentreY(ServerWorld world) {
		return world.getTopY() - 11;
	}
}
