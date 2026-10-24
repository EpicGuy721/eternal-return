package com.eternalreturn.worldgen.terrain;

import com.eternalreturn.EternalReturn;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Monoliths, as Alpha's terrain threw up now and then: a sheer-sided block of land rising from the ground (or the sea
 * floor) to between y 190 and y 250, flat-topped, 20 to 50 blocks wide and up to two and a half times as long, lying
 * north-south or east-west, its walls a little ragged. Extremely rare: one in each square of CELL blocks (10,000 a
 * side), placed from the world seed alone, so every chunk it touches builds its own part. Its top is the ground of the
 * biome it stands in (copied as FloatingIslands does: the topsoil, the biome's stone under it; sand over the sea), so
 * the biome's trees, plants and snow come with the chunk's decoration. Eternal Return worlds only; built when the cave
 * carver first sees a chunk, after the floating islands.
 */
public final class Monoliths {
	public static final int CELL = 10_000;
	public static final int MIN_TOP = 190;
	/** How far a monolith can reach from its centre, edges and all. */
	private static final int REACH = 72;
	private static final int BAND = 5;

	private Monoliths() {
	}

	/** A monolith: centre, half its size along x and z, its top height. */
	public record Monolith(int x, int z, int halfX, int halfZ, int top, long seed) {
	}

	/** The monolith of a CELL x CELL square: from the world seed (through the noise config) alone. */
	public static Monolith inCell(NoiseConfig noiseConfig, int cellX, int cellZ) {
		Random random = noiseConfig.getOrCreateRandomDeriver(EternalReturn.id("monoliths")).split(cellX, 0, cellZ);
		int margin = 2 * REACH;
		int x = cellX * CELL + margin + random.nextInt(CELL - 2 * margin);
		int z = cellZ * CELL + margin + random.nextInt(CELL - 2 * margin);
		int width = 10 + random.nextInt(16);
		int length = (int) Math.round(width * (1.0 + 1.5 * random.nextDouble()));
		boolean alongX = random.nextBoolean();
		int top = MIN_TOP + random.nextInt(61);
		return new Monolith(x, z, alongX ? length : width, alongX ? width : length, top, random.nextLong());
	}

	/** The monoliths that may reach into a chunk. */
	public static List<Monolith> near(NoiseConfig noiseConfig, ChunkPos chunk) {
		List<Monolith> monoliths = new ArrayList<>();
		for (int cellX = Math.floorDiv(chunk.getStartX() - REACH, CELL); cellX <= Math.floorDiv(chunk.getEndX() + REACH, CELL); cellX++) {
			for (int cellZ = Math.floorDiv(chunk.getStartZ() - REACH, CELL); cellZ <= Math.floorDiv(chunk.getEndZ() + REACH, CELL); cellZ++) {
				Monolith monolith = inCell(noiseConfig, cellX, cellZ);
				if (Math.abs(monolith.x() - (chunk.getStartX() + 8)) <= REACH + 8 && Math.abs(monolith.z() - (chunk.getStartZ() + 8)) <= REACH + 8) {
					monoliths.add(monolith);
				}
			}
		}
		return monoliths;
	}

	/** The top of a monolith's column, or Integer.MIN_VALUE outside it. */
	public static int top(Monolith monolith, SimplexNoiseSampler noise, int x, int z) {
		double u = Math.abs(x + 0.5 - monolith.x()) / monolith.halfX();
		double v = Math.abs(z + 0.5 - monolith.z()) / monolith.halfZ();
		// A box with rounded corners, its edge pushed in and out a little.
		double r = Math.pow(Math.pow(u, 4) + Math.pow(v, 4), 0.25);
		double edge = 1.0 + 0.1 * noise.sample(x / 7.0, z / 7.0) + 0.05 * noise.sample(x / 2.5 + 30, z / 2.5 - 30);
		if (r >= edge) {
			return Integer.MIN_VALUE;
		}
		// Flat on top, stepping down a block or two at the rim.
		int drop = r > edge - 0.08 ? 2 : r > edge - 0.16 ? 1 : 0;
		return monolith.top() - drop + (int) Math.round(0.8 * noise.sample(x / 11.0 - 60, z / 11.0 + 60));
	}

	/** Builds this chunk's part of any monolith near it. Returns the number of blocks placed. */
	public static int build(Chunk chunk, NoiseConfig noiseConfig, int seaLevel) {
		int placed = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (Monolith monolith : near(noiseConfig, chunk.getPos())) {
			SimplexNoiseSampler noise = new SimplexNoiseSampler(Random.create(monolith.seed()));
			for (int localX = 0; localX < 16; localX++) {
				for (int localZ = 0; localZ < 16; localZ++) {
					int x = chunk.getPos().getStartX() + localX;
					int z = chunk.getPos().getStartZ() + localZ;
					int top = top(monolith, noise, x, z);
					if (top == Integer.MIN_VALUE) {
						continue;
					}
					BlockState[] band = new BlockState[BAND];
					BlockState stone = FloatingIslands.groundBelow(chunk, pos, localX, localZ, seaLevel, band);
					// Up from the ground (or the sea floor) to the top.
					int ground = chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, localX, localZ);
					for (int y = Math.max(ground, chunk.getBottomY()); y <= top; y++) {
						BlockState at = chunk.getBlockState(pos.set(x, y, z));
						if (!at.isAir() && at.getFluidState().isEmpty()) {
							continue;
						}
						int fromTop = top - y;
						chunk.setBlockState(pos, fromTop < BAND ? band[fromTop] : stone, false);
						placed++;
					}
				}
			}
		}
		return placed;
	}

	/** Whether a monolith stands over this column, and its top there (for the tests and the map tool). */
	public static int topAt(NoiseConfig noiseConfig, int x, int z) {
		for (Monolith monolith : near(noiseConfig, new ChunkPos(x >> 4, z >> 4))) {
			int top = top(monolith, new SimplexNoiseSampler(Random.create(monolith.seed())), x, z);
			if (top != Integer.MIN_VALUE) {
				return top;
			}
		}
		return Integer.MIN_VALUE;
	}
}
