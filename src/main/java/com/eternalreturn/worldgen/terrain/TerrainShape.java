package com.eternalreturn.worldgen.terrain;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.caves.CaveSystemCarver;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reshapes Eternal Return terrain where Moderner Beta works out each terrain column's height (its depth
 * and scale, from the biomes around; ModernerBetaHeightMixin). Two changes, Eternal Return worlds only:
 * <ul>
 * <li>Deep oceans: the sea floor steps down away from the coast. A shelf at about y 30 for some 50
 * blocks, a steep drop to about y 0, and further out (about 170 blocks from land) another steep drop to
 * about y -30. The distance to land comes from the biome map (a distance field per 256-block region),
 * and both edges wander with noise.</li>
 * <li>Sinkholes: very rarely the ground (mostly the sea floor) drops into a pit about 100 blocks across down to
 * the bottom of the world, which the generator floods from sea level down, as 1.18 and 1.19 sometimes did.</li>
 * </ul>
 * Because the change is made where the generator gets its column heights, the height maps, the cave
 * carver's water checks and the map tool all see it. Other worlds never change.
 */
public final class TerrainShape {
	/** Ground height for a column depth: y = 68 + 34 * depth (the preset's base size and vertical stretch). */
	static double yForDepth(double depth) {
		return 68.0 + 34.0 * depth;
	}

	static float depthForY(double y) {
		return (float) ((y - 68.0) / 34.0);
	}

	public static final double SHELF_Y = 30;
	public static final double MIDDLE_Y = 0;
	public static final double DEEP_Y = -30;
	/** Blocks from land where the shelf ends, and where the second drop is (each plus or minus some noise). */
	public static final double SHELF_EDGE = 48;
	public static final double DEEP_EDGE = 170;
	/** Sinkholes: one chance per square of this many blocks. */
	public static final int SINKHOLE_CELL = 1024;
	public static final double SINKHOLE_CHANCE = 0.35;
	/** Most sinkholes are in the sea: one planned on land is kept only this often (never on a river or beach). */
	public static final double SINKHOLE_LAND_CHANCE = 0.2;
	public static final double SINKHOLE_FLOOR_Y = -58;
	/** Terrain scale (ruggedness) inside a sinkhole. */
	private static final float SINKHOLE_SCALE = 0.3F;

	/** In noise columns (4 blocks): a region of the distance field, its sample step, and how far beyond it land is looked for. */
	private static final int REGION = 64;
	private static final int STEP = 2;
	private static final int MARGIN = 25;
	private static final int GRID = REGION / STEP + 2 * MARGIN;
	private static final float FAR = 10_000;

	private TerrainShape() {
	}

	/** One per Moderner Beta chunk provider. */
	public static final class State {
		final boolean eternalReturn;
		final long seed;
		final MethodHandle biomeAt;
		final SimplexNoiseSampler noise;
		final Map<Long, float[]> regions = new LinkedHashMap<>(64, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, float[]> eldest) {
				return this.size() > 256;
			}
		};
		final Map<Long, List<Sinkhole>> sinkholes = new LinkedHashMap<>(64, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, List<Sinkhole>> eldest) {
				return this.size() > 256;
			}
		};
		final Object provider;

		private State(Object provider) {
			this.provider = provider;
			boolean eternalReturn = false;
			long seed = 0;
			MethodHandle biomeAt = null;
			try {
				Class<?> base = provider.getClass();
				while (base != null && !base.getName().endsWith(".ChunkProvider")) {
					base = base.getSuperclass();
				}
				if (base != null) {
					Field generator = base.getDeclaredField("chunkGenerator");
					generator.setAccessible(true);
					Field seedField = base.getDeclaredField("seed");
					seedField.setAccessible(true);
					seed = seedField.getLong(provider);
					eternalReturn = generator.get(provider) instanceof NoiseChunkGenerator noise && CaveWorlds.usesEternalReturnSettings(noise);
					biomeAt = MethodHandles.publicLookup().findVirtual(provider.getClass(), "getExtendedBiomeId",
							MethodType.methodType(Class.forName("mod.bluestaggo.modernerbeta.util.ExtendedIdentifier", false, provider.getClass().getClassLoader()), int.class, int.class));
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				eternalReturn = false;
			}
			this.eternalReturn = eternalReturn && biomeAt != null;
			this.seed = seed;
			this.biomeAt = biomeAt;
			this.noise = new SimplexNoiseSampler(Random.create(CaveSystemCarver.mix(seed, 0x0CEA4L)));
		}

		public static State of(Object provider) {
			return new State(provider);
		}

		/** The biome id (with its height variant) Moderner Beta uses for heights at this noise column. */
		String biome(int noiseX, int noiseZ) {
			try {
				return String.valueOf(this.biomeAt.invoke(this.provider, noiseX, noiseZ));
			} catch (Throwable e) {
				return "";
			}
		}
	}

	/** A sinkhole: centre in blocks, radius in blocks; onLand: whether it is kept if its centre lies on land. */
	public record Sinkhole(int x, int z, int radius, long seed, boolean onLand) {
	}

	static boolean isOcean(String biome) {
		return biome.contains("ocean");
	}

	/** Land for the distance to the coast: anything that isn't an ocean (rivers, beaches and shores included). */
	static boolean isLandForCoast(String biome) {
		return !isOcean(biome);
	}

	/** Reshapes a column's depth and scale (column[0], column[1]) in place. */
	public static void reshape(State state, int noiseX, int noiseZ, float[] column) {
		if (!state.eternalReturn) {
			return;
		}
		EternalReturnConfig.WorldgenTweaks worldgen = EternalReturnConfig.get().worldgen;
		if (worldgen.deepOceans && isOcean(state.biome(noiseX, noiseZ))) {
			column[0] = Math.min(column[0], oceanDepth(state, noiseX, noiseZ, column[0]));
		}
		if (worldgen.sinkholes) {
			sinkhole(state, noiseX, noiseZ, column);
		}
	}

	// ------------------------------------------------------------------ deep oceans

	private static float oceanDepth(State state, int noiseX, int noiseZ, float depth) {
		double distance = coastDistance(state, noiseX, noiseZ);
		double x = noiseX * 4.0;
		double z = noiseZ * 4.0;
		double shelfEdge = SHELF_EDGE + 14 * state.noise.sample(x / 160.0, z / 160.0);
		double deepEdge = DEEP_EDGE + 35 * state.noise.sample(x / 260.0 + 91.3, z / 260.0 - 17.7);
		double y = yForDepth(depth);
		// Down from the coast to the shelf, then two steep drops.
		y = MathHelper.lerp(smoothstep(2, 26, distance), y, Math.min(y, SHELF_Y));
		y = MathHelper.lerp(smoothstep(shelfEdge - 5, shelfEdge + 5, distance), y, Math.min(y, MIDDLE_Y));
		y = MathHelper.lerp(smoothstep(deepEdge - 6, deepEdge + 6, distance), y, Math.min(y, DEEP_Y));
		return depthForY(y);
	}

	/** Blocks from this noise column to the nearest land, from the region's distance field (bilinear between samples). */
	public static double coastDistance(State state, int noiseX, int noiseZ) {
		int regionX = Math.floorDiv(noiseX, REGION);
		int regionZ = Math.floorDiv(noiseZ, REGION);
		float[] field = region(state, regionX, regionZ);
		double gx = (noiseX - regionX * REGION) / (double) STEP + MARGIN;
		double gz = (noiseZ - regionZ * REGION) / (double) STEP + MARGIN;
		int x0 = (int) Math.floor(gx);
		int z0 = (int) Math.floor(gz);
		double fx = gx - x0;
		double fz = gz - z0;
		double a = field[x0 * GRID + z0];
		double b = field[(x0 + 1) * GRID + z0];
		double c = field[x0 * GRID + z0 + 1];
		double d = field[(x0 + 1) * GRID + z0 + 1];
		return MathHelper.lerp(fz, MathHelper.lerp(fx, a, b), MathHelper.lerp(fx, c, d));
	}

	private static float[] region(State state, int regionX, int regionZ) {
		long key = ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
		synchronized (state.regions) {
			float[] cached = state.regions.get(key);
			if (cached != null) {
				return cached;
			}
		}
		float[] field = new float[GRID * GRID];
		int originX = regionX * REGION - MARGIN * STEP;
		int originZ = regionZ * REGION - MARGIN * STEP;
		for (int i = 0; i < GRID; i++) {
			for (int j = 0; j < GRID; j++) {
				field[i * GRID + j] = isLandForCoast(state.biome(originX + i * STEP, originZ + j * STEP)) ? 0 : FAR;
			}
		}
		// Two-pass chamfer distance (steps of 1 and the square root of 2 samples), then into blocks.
		float diagonal = (float) Math.sqrt(2);
		for (int i = 0; i < GRID; i++) {
			for (int j = 0; j < GRID; j++) {
				float v = field[i * GRID + j];
				if (i > 0) v = Math.min(v, field[(i - 1) * GRID + j] + 1);
				if (j > 0) v = Math.min(v, field[i * GRID + j - 1] + 1);
				if (i > 0 && j > 0) v = Math.min(v, field[(i - 1) * GRID + j - 1] + diagonal);
				if (i > 0 && j < GRID - 1) v = Math.min(v, field[(i - 1) * GRID + j + 1] + diagonal);
				field[i * GRID + j] = v;
			}
		}
		for (int i = GRID - 1; i >= 0; i--) {
			for (int j = GRID - 1; j >= 0; j--) {
				float v = field[i * GRID + j];
				if (i < GRID - 1) v = Math.min(v, field[(i + 1) * GRID + j] + 1);
				if (j < GRID - 1) v = Math.min(v, field[i * GRID + j + 1] + 1);
				if (i < GRID - 1 && j < GRID - 1) v = Math.min(v, field[(i + 1) * GRID + j + 1] + diagonal);
				if (i < GRID - 1 && j > 0) v = Math.min(v, field[(i + 1) * GRID + j - 1] + diagonal);
				field[i * GRID + j] = v;
			}
		}
		float blocksPerSample = STEP * 4;
		for (int i = 0; i < field.length; i++) {
			field[i] = Math.min(field[i] * blocksPerSample, FAR);
		}
		synchronized (state.regions) {
			state.regions.put(key, field);
		}
		return field;
	}

	// ------------------------------------------------------------------ sinkholes

	/** The sinkhole planned in a square of SINKHOLE_CELL blocks, before checking it lies on land: from the world seed alone. */
	public static Sinkhole plannedSinkhole(long worldSeed, int cellX, int cellZ) {
		Random random = Random.create(CaveSystemCarver.mix(CaveSystemCarver.mix(worldSeed, 0x51CC401EL), ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL)));
		if (random.nextDouble() >= SINKHOLE_CHANCE) {
			return null;
		}
		int margin = 64;
		int x = cellX * SINKHOLE_CELL + margin + random.nextInt(SINKHOLE_CELL - 2 * margin);
		int z = cellZ * SINKHOLE_CELL + margin + random.nextInt(SINKHOLE_CELL - 2 * margin);
		int radius = 42 + random.nextInt(17);
		long seed = random.nextLong();
		return new Sinkhole(x, z, radius, seed, random.nextDouble() < SINKHOLE_LAND_CHANCE);
	}

	/** The sinkholes of a cell: in the sea, or (onLand) on land, never with their centre on a river or beach. */
	private static List<Sinkhole> sinkholes(State state, int cellX, int cellZ) {
		long key = ((long) cellX << 32) ^ (cellZ & 0xFFFFFFFFL);
		synchronized (state.sinkholes) {
			List<Sinkhole> cached = state.sinkholes.get(key);
			if (cached != null) {
				return cached;
			}
		}
		List<Sinkhole> found = new ArrayList<>();
		Sinkhole planned = plannedSinkhole(state.seed, cellX, cellZ);
		if (planned != null) {
			String biome = state.biome(planned.x() >> 2, planned.z() >> 2);
			if (keeps(planned, biome)) {
				found.add(planned);
			}
		}
		synchronized (state.sinkholes) {
			state.sinkholes.put(key, found);
		}
		return found;
	}

	/** Whether a planned sinkhole whose centre lies in this biome is made. */
	public static boolean keeps(Sinkhole hole, String biome) {
		if (biome.contains("river") || biome.contains("beach") || biome.contains("shore")) {
			return false;
		}
		return isOcean(biome) || hole.onLand();
	}

	/** Inside a sinkhole the ground drops to its floor, and the terrain noise calms (so the floor isn't propped up by it). */
	private static void sinkhole(State state, int noiseX, int noiseZ, float[] column) {
		double x = noiseX * 4.0;
		double z = noiseZ * 4.0;
		int cellX = Math.floorDiv((int) x, SINKHOLE_CELL);
		int cellZ = Math.floorDiv((int) z, SINKHOLE_CELL);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (Sinkhole hole : sinkholes(state, cellX + dx, cellZ + dz)) {
					double ox = x - hole.x();
					double oz = z - hole.z();
					double r = Math.sqrt(ox * ox + oz * oz);
					if (r > hole.radius() * 1.4) {
						continue;
					}
					double angle = Math.atan2(oz, ox);
					double wobble = state.noise.sample(Math.cos(angle) * 1.3 + (hole.seed() & 1023), Math.sin(angle) * 1.3 + ((hole.seed() >> 10) & 1023));
					double edge = hole.radius() * (0.85 + 0.25 * wobble);
					double inside = 1.0 - smoothstep(edge - Math.min(6, edge * 0.5), edge, r);
					if (inside > 0) {
						column[0] = (float) MathHelper.lerp(inside, column[0], Math.min(column[0], depthForY(SINKHOLE_FLOOR_Y)));
						column[1] = (float) MathHelper.lerp(inside, column[1], Math.min(column[1], SINKHOLE_SCALE));
					}
				}
			}
		}
	}

	static double smoothstep(double edge0, double edge1, double x) {
		double t = MathHelper.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3 - 2 * t);
	}
}
