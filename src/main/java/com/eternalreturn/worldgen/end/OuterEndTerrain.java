package com.eternalreturn.worldgen.end;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The outer End of Eternal Return worlds: everything past the void gap round the main island, in place of vanilla's
 * flat outer islands. The world is split into squares of CELL blocks; most squares hold a group of islands, placed
 * from the seed alone, so every chunk builds its own part and the parts line up in whatever order chunks generate.
 * <p>
 * A group is a main island at its own height (most in a middle band, some hanging low over the void, some high above
 * it), with stepped terraces on top, ragged undercut sides and a craggy underside hanging below; spires standing on it
 * or hanging from it; smaller islands stacked above or below it, sometimes joined to it by a pillar; and small
 * fragments of end stone drifting round it. Squares without an island hold a few fragments only.
 * <p>
 * Everything here is a pure function of the seed and the settings read when the terrain is made, so the density
 * function (OuterEndDensity), the biome function (OuterEndBiomeDensity), the megastructures and the locate command
 * all see the same islands without generating anything.
 */
public final class OuterEndTerrain {
	/** Inside this distance from 0, 0 the End is vanilla's, block for block (the main island and the void round it). */
	public static final int INNER = 1000;
	/** No island reaches nearer to 0, 0 than this. */
	public static final int START = 1100;
	public static final int CELL = 160;
	/** How far a group can reach from its square, in squares (its shapes are kept within one square of it). */
	private static final int CELL_REACH = 1;
	/** Highest block any shape may reach (the End is 256 blocks tall). */
	public static final int CEILING = 250;
	/** End cities and chorus: islands whose top is above this are barrens (an End city there would hit the ceiling). */
	public static final int CITY_MAX_TOP = 150;

	private static final Map<Long, OuterEndTerrain> BY_SEED = new ConcurrentHashMap<>();

	private final long seed;
	private final Settings settings;
	private final SimplexNoiseSampler edgeNoise;
	private final SimplexNoiseSampler detailNoise;
	private final SimplexNoiseSampler roughNoise;
	private final SimplexNoiseSampler regionNoise;
	private final Map<Long, List<Shape>> groups = new ConcurrentHashMap<>();
	private final ThreadLocal<ChunkCache> chunkCache = ThreadLocal.withInitial(ChunkCache::new);

	/** The tunable numbers, from worldgen.outerEnd in config/eternalreturn.json, read once per terrain. */
	public record Settings(boolean enabled, int minY, int maxY, int middleY, double lowShare, double highShare,
			double islandChance, double islandSize, double fragments) {
		static Settings fromConfig() {
			EternalReturnConfig.OuterEndTweaks c = EternalReturnConfig.get().worldgen.outerEnd;
			int min = MathHelper.clamp(c.islandMinY, 8, 200);
			int max = MathHelper.clamp(c.islandMaxY, min + 10, 220);
			return new Settings(c.islands, min, max, MathHelper.clamp(c.islandMiddleY, min, max), c.lowIslandShare, c.highIslandShare,
					MathHelper.clamp(c.islandChance, 0.0, 1.0), Math.max(0.2, c.islandSize), Math.max(0.0, c.fragmentDensity));
		}
	}

	private OuterEndTerrain(long seed, Settings settings) {
		this.seed = seed;
		this.settings = settings;
		this.edgeNoise = new SimplexNoiseSampler(new CheckedRandom(seed ^ 0x5DEECE66DL));
		this.detailNoise = new SimplexNoiseSampler(new CheckedRandom(seed * 31 + 7));
		this.roughNoise = new SimplexNoiseSampler(new CheckedRandom(seed * 131 + 11));
		this.regionNoise = new SimplexNoiseSampler(new CheckedRandom(seed * 17 - 3));
	}

	/** The terrain of a seed (shared by every function and feature of that world; settings read on first use). */
	public static OuterEndTerrain forSeed(long seed) {
		if (BY_SEED.size() > 16) {
			BY_SEED.clear();
		}
		return BY_SEED.computeIfAbsent(seed, s -> new OuterEndTerrain(s, Settings.fromConfig()));
	}

	/** Forgets every terrain, so the next world reads the settings again (called when a server starts). */
	public static void reset() {
		BY_SEED.clear();
	}

	public long seed() {
		return seed;
	}

	public Settings settings() {
		return settings;
	}

	// ------------------------------------------------------------------ shapes

	/** One piece of terrain: an island, a spire or a fragment. Bounds are in blocks. */
	public abstract static sealed class Shape permits Island, Spire, Fragment {
		final int minX, maxX, minZ, maxZ, minY, maxY;

		Shape(int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
			this.minX = minX;
			this.maxX = maxX;
			this.minZ = minZ;
			this.maxZ = maxZ;
			this.minY = Math.max(1, minY);
			this.maxY = Math.min(CEILING, maxY);
		}

		boolean touches(int x0, int z0, int x1, int z1) {
			return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
		}
	}

	/**
	 * An island: an oval with a ragged edge, its top in flat steps (tiers) rising towards the middle, its sides
	 * undercut, its underside hanging in crags below. main is false for the smaller islands stacked round a main one.
	 */
	public static final class Island extends Shape {
		public final double x, z, radiusA, radiusB, cos, sin;
		public final int level, tiers, domeHeight, depth;
		public final boolean main;
		final double noiseOffset;

		Island(double x, double z, double radiusA, double radiusB, double angle, int level, int tiers, int domeHeight, int depth,
				boolean main, double noiseOffset) {
			super((int) Math.floor(x - Math.max(radiusA, radiusB) * 1.3) - 1, (int) Math.ceil(x + Math.max(radiusA, radiusB) * 1.3) + 1,
					(int) Math.floor(z - Math.max(radiusA, radiusB) * 1.3) - 1, (int) Math.ceil(z + Math.max(radiusA, radiusB) * 1.3) + 1,
					level - depth - 8, level + domeHeight + 2);
			this.x = x;
			this.z = z;
			this.radiusA = radiusA;
			this.radiusB = radiusB;
			this.cos = Math.cos(angle);
			this.sin = Math.sin(angle);
			this.level = level;
			this.tiers = tiers;
			this.domeHeight = domeHeight;
			this.depth = depth;
			this.main = main;
			this.noiseOffset = noiseOffset;
		}

		public double radius() {
			return Math.max(radiusA, radiusB);
		}
	}

	/** A spire or pillar: a leaning, tapering column of end stone from bottom to top, wide at its root. */
	public static final class Spire extends Shape {
		final double x, z, leanX, leanZ, rootRadius, tipRadius;
		final int bottom, top;
		/** true: the root is at the top (hanging from an underside); false: standing on its root. */
		final boolean hanging;

		Spire(double x, double z, int bottom, int top, double rootRadius, double tipRadius, double leanX, double leanZ, boolean hanging) {
			super((int) Math.floor(x - rootRadius - Math.abs(leanX) * (top - bottom)) - 2, (int) Math.ceil(x + rootRadius + Math.abs(leanX) * (top - bottom)) + 2,
					(int) Math.floor(z - rootRadius - Math.abs(leanZ) * (top - bottom)) - 2, (int) Math.ceil(z + rootRadius + Math.abs(leanZ) * (top - bottom)) + 2,
					bottom, top);
			this.x = x;
			this.z = z;
			this.bottom = bottom;
			this.top = top;
			this.rootRadius = rootRadius;
			this.tipRadius = tipRadius;
			this.leanX = leanX;
			this.leanZ = leanZ;
			this.hanging = hanging;
		}
	}

	/** A small lump of end stone adrift: a lumpy ellipsoid. */
	public static final class Fragment extends Shape {
		final double x, y, z, rx, ry, rz;

		Fragment(double x, double y, double z, double rx, double ry, double rz) {
			super((int) Math.floor(x - rx) - 2, (int) Math.ceil(x + rx) + 2, (int) Math.floor(z - rz) - 2, (int) Math.ceil(z + rz) + 2,
					(int) Math.floor(y - ry) - 2, (int) Math.ceil(y + ry) + 2);
			this.x = x;
			this.y = y;
			this.z = z;
			this.rx = rx;
			this.ry = ry;
			this.rz = rz;
		}
	}

	// ------------------------------------------------------------------ layout

	/** Every shape of the group in a CELL x CELL square. Cached. */
	public List<Shape> group(int cellX, int cellZ) {
		long key = (long) cellX << 32 | (cellZ & 0xFFFFFFFFL);
		List<Shape> found = groups.get(key);
		if (found == null) {
			if (groups.size() > 20_000) {
				groups.clear();
			}
			found = makeGroup(cellX, cellZ);
			groups.put(key, found);
		}
		return found;
	}

	/** The main islands of the groups within reach of a square (for the locate command, the tests and the megastructures). */
	public List<Island> mainIslands(int cellX, int cellZ) {
		List<Island> islands = new ArrayList<>();
		for (Shape shape : group(cellX, cellZ)) {
			if (shape instanceof Island island && island.main) {
				islands.add(island);
			}
		}
		return islands;
	}

	private Random cellRandom(int cellX, int cellZ, long salt) {
		long h = seed ^ salt;
		h = h * 6364136223846793005L + cellX * 341873128712L;
		h = h * 6364136223846793005L + cellZ * 132897987541L;
		return new CheckedRandom(h ^ (h >>> 29));
	}

	private List<Shape> makeGroup(int cellX, int cellZ) {
		List<Shape> shapes = new ArrayList<>();
		if (!settings.enabled) {
			return shapes;
		}
		Random random = cellRandom(cellX, cellZ, 0x0E7D15L);
		double cx = (cellX + 0.5) * CELL;
		double cz = (cellZ + 0.5) * CELL;
		// Only whole squares beyond the gap: nothing may reach within START of 0, 0.
		if (Math.sqrt(cx * cx + cz * cz) - CELL * 1.6 < START) {
			return shapes;
		}
		double regional = regionNoise.sample(cellX / 5.0, cellZ / 5.0);
		if (random.nextDouble() >= settings.islandChance) {
			int count = (int) Math.round((2 + random.nextInt(5)) * settings.fragments);
			for (int i = 0; i < count; i++) {
				double fy = levelFor(random, regional);
				addFragment(shapes, random, cx + (random.nextDouble() - 0.5) * CELL * 0.9, fy, cz + (random.nextDouble() - 0.5) * CELL * 0.9);
			}
			return shapes;
		}

		// The main island: mostly 18 to 50 blocks across its long half, now and then a big one.
		double size = settings.islandSize;
		double radius = (random.nextDouble() < 0.12 ? 52 + random.nextDouble() * 24 : 16 + 34 * Math.pow(random.nextDouble(), 1.4)) * size;
		radius = Math.min(radius, CELL * 0.48);
		double stretch = 1.0 + random.nextDouble() * 0.7;
		double radiusA = radius;
		double radiusB = radius / stretch;
		double angle = random.nextDouble() * Math.PI;
		double margin = radius * 1.3 + 4;
		double x = cx + (random.nextDouble() - 0.5) * Math.max(0, CELL - 2 * margin);
		double z = cz + (random.nextDouble() - 0.5) * Math.max(0, CELL - 2 * margin);
		int level = levelFor(random, regional);
		int tiers = 1 + random.nextInt(radius > 30 ? 4 : 3);
		int dome = (int) MathHelper.clamp(radius * (0.22 + random.nextDouble() * 0.3), 3, 32);
		if (tiers == 1) {
			dome = Math.min(dome, 5);
		}
		int depth = (int) MathHelper.clamp(radius * (0.55 + random.nextDouble() * 0.6), 6, 60);
		level = MathHelper.clamp(level, settings.minY + Math.min(depth, 10) - 6, CEILING - dome - 4);
		depth = Math.min(depth, level - 2);
		Island main = new Island(x, z, radiusA, radiusB, angle, level, tiers, dome, depth, true, random.nextDouble() * 1000);
		shapes.add(main);

		// Smaller islands stacked above or below it.
		int satellites = random.nextInt(radius > 30 ? 5 : 3);
		List<Island> stacked = new ArrayList<>();
		for (int i = 0; i < satellites; i++) {
			double r = radius * (0.2 + random.nextDouble() * 0.28);
			double a = random.nextDouble() * Math.PI * 2;
			double d = radius * (0.25 + random.nextDouble() * 0.9);
			boolean above = random.nextBoolean();
			int dy = (18 + random.nextInt(30)) * (above ? 1 : -1);
			int sLevel = level + dy + (above ? dome : -depth / 2);
			if (sLevel > settings.maxY + 30 || sLevel < settings.minY - 8) {
				sLevel = level - dy - (above ? depth / 2 : -dome);
			}
			int sDome = (int) MathHelper.clamp(r * 0.35, 2, 10);
			int sDepth = (int) MathHelper.clamp(r * (0.6 + random.nextDouble() * 0.5), 4, 24);
			sLevel = MathHelper.clamp(sLevel, sDepth + 3, CEILING - sDome - 3);
			Island satellite = new Island(x + Math.cos(a) * d, z + Math.sin(a) * d, r, r / (1.0 + random.nextDouble() * 0.5),
					random.nextDouble() * Math.PI, sLevel, 1 + random.nextInt(2), sDome, sDepth, false, random.nextDouble() * 1000);
			shapes.add(satellite);
			stacked.add(satellite);
		}

		// Spires on the main island (some hanging from its underside), pillars up to an island stacked above.
		int spires = random.nextInt(3) + (radius > 35 ? 2 : 0);
		for (int i = 0; i < spires; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double f = random.nextDouble() * 0.6;
			double sx = x + Math.cos(a) * radiusA * f;
			double sz = z + Math.sin(a) * radiusB * f;
			double root = 2.5 + random.nextDouble() * Math.min(6, radius * 0.12);
			double leanX = (random.nextDouble() - 0.5) * 0.25;
			double leanZ = (random.nextDouble() - 0.5) * 0.25;
			if (random.nextInt(4) == 0) {
				int top = level - depth / 3;
				int bottom = Math.max(settings.minY - 10, top - 14 - random.nextInt(30));
				shapes.add(new Spire(sx, sz, bottom, top, root, 0.6, leanX, leanZ, true));
			} else {
				int bottom = level - 4;
				int top = Math.min(CEILING, level + dome + 12 + random.nextInt(44));
				shapes.add(new Spire(sx, sz, bottom, top, root, 0.5 + random.nextDouble(), leanX, leanZ, false));
			}
		}
		for (Island satellite : stacked) {
			if (satellite.level > level && random.nextInt(3) == 0) {
				double px = (satellite.x + x) / 2 + (satellite.x - x) * 0.3;
				double pz = (satellite.z + z) / 2 + (satellite.z - z) * 0.3;
				shapes.add(new Spire(px, pz, level - 3, satellite.level - satellite.depth / 3, 2.5 + random.nextDouble() * 2, 2.0, 0, 0, false));
			}
		}

		// Fragments drifting round the group.
		int fragments = (int) Math.round((5 + random.nextInt(10) + radius / 6) * settings.fragments);
		for (int i = 0; i < fragments; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = radius * (1.0 + random.nextDouble() * 1.2);
			double fx = MathHelper.clamp(x + Math.cos(a) * d, cx - CELL, cx + CELL);
			double fz = MathHelper.clamp(z + Math.sin(a) * d, cz - CELL, cz + CELL);
			double fy = level + (random.nextDouble() - 0.4) * 70;
			addFragment(shapes, random, fx, fy, fz);
		}
		return shapes;
	}

	/** An island's height: most in a band round the middle, some low over the void, some high above the rest. */
	private int levelFor(Random random, double regional) {
		double u = random.nextDouble();
		double y;
		if (u < settings.lowShare) {
			y = settings.minY + random.nextDouble() * 25;
		} else if (u < settings.lowShare + settings.highShare) {
			y = settings.maxY - random.nextDouble() * 35;
		} else {
			y = settings.middleY + regional * 28 + (random.nextDouble() - 0.5) * 50;
		}
		return (int) Math.round(MathHelper.clamp(y, settings.minY, settings.maxY));
	}

	private void addFragment(List<Shape> shapes, Random random, double x, double y, double z) {
		double r = random.nextInt(8) == 0 ? 4 + random.nextDouble() * 4 : 1.4 + random.nextDouble() * 3;
		y = MathHelper.clamp(y, settings.minY - 10, CEILING - r - 2);
		if (y - r < 3 || Math.sqrt(x * x + z * z) - r < START) {
			return;
		}
		shapes.add(new Fragment(x, y, z, r * (0.8 + random.nextDouble() * 0.6), r * (0.5 + random.nextDouble() * 0.4), r * (0.8 + random.nextDouble() * 0.6)));
	}

	// ------------------------------------------------------------------ sampling

	/** Every shape whose bounds touch the block square x0..x1, z0..z1. */
	public List<Shape> shapesIn(int x0, int z0, int x1, int z1) {
		List<Shape> found = new ArrayList<>();
		for (int cellX = Math.floorDiv(x0, CELL) - CELL_REACH; cellX <= Math.floorDiv(x1, CELL) + CELL_REACH; cellX++) {
			for (int cellZ = Math.floorDiv(z0, CELL) - CELL_REACH; cellZ <= Math.floorDiv(z1, CELL) + CELL_REACH; cellZ++) {
				for (Shape shape : group(cellX, cellZ)) {
					if (shape.touches(x0, z0, x1, z1)) {
						found.add(shape);
					}
				}
			}
		}
		return found;
	}

	/** An island's shape in one column: where its top and underside are and how far the column is inside its edge. */
	private static final class IslandColumn {
		Island island;
		int top;
		double bottom;
		/** Blocks inside the ragged edge (negative outside). */
		double inside;
		/** 0 at the edge, 1 in the middle. */
		double t;
	}

	/** The parts of every shape over one column: islands worked out for the column, spires and fragments as they are. */
	private static final class Column {
		final List<IslandColumn> islands = new ArrayList<>(2);
		final List<Shape> others = new ArrayList<>(2);
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
	}

	/** One chunk's shapes and columns per thread: chunk generation asks for the same columns again and again. */
	private static final class ChunkCache {
		long key = Long.MIN_VALUE;
		List<Shape> shapes;
		final Column[] columns = new Column[256];
	}

	private Column column(int x, int z) {
		ChunkCache cache = chunkCache.get();
		int chunkX = x >> 4;
		int chunkZ = z >> 4;
		long key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
		if (cache.key != key) {
			cache.key = key;
			cache.shapes = shapesIn(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15);
			java.util.Arrays.fill(cache.columns, null);
		}
		int index = (x & 15) << 4 | (z & 15);
		Column column = cache.columns[index];
		if (column == null) {
			column = new Column();
			for (Shape shape : cache.shapes) {
				if (x < shape.minX || x > shape.maxX || z < shape.minZ || z > shape.maxZ) {
					continue;
				}
				if (shape instanceof Island island) {
					IslandColumn part = islandColumn(island, x, z);
					if (part == null) {
						continue;
					}
					column.islands.add(part);
					column.minY = Math.min(column.minY, (int) Math.floor(part.bottom) - 6);
					column.maxY = Math.max(column.maxY, part.top);
				} else {
					column.others.add(shape);
					column.minY = Math.min(column.minY, shape.minY);
					column.maxY = Math.max(column.maxY, shape.maxY);
				}
			}
			cache.columns[index] = column;
		}
		return column;
	}

	/** The edge of an island at a column: 1 is the plain oval, pushed in and out by noise (bays and points). */
	private double edge(Island island, double x, double z) {
		double o = island.noiseOffset;
		return 1.0 + 0.22 * edgeNoise.sample(x / 26.0 + o, z / 26.0 - o) + 0.08 * edgeNoise.sample(x / 9.0 - o, z / 9.0 + o);
	}

	private IslandColumn islandColumn(Island island, int bx, int bz) {
		double dx = bx + 0.5 - island.x;
		double dz = bz + 0.5 - island.z;
		double u = (dx * island.cos + dz * island.sin) / island.radiusA;
		double v = (-dx * island.sin + dz * island.cos) / island.radiusB;
		double r = Math.sqrt(u * u + v * v);
		double edge = edge(island, bx, bz);
		// Undercut sides reach a few blocks out past the edge, so keep columns just outside it too.
		double inside = (edge - r) * Math.min(island.radiusA, island.radiusB);
		if (inside < -5) {
			return null;
		}
		double t = MathHelper.clamp(1.0 - r / edge, 0.0, 1.0);
		IslandColumn part = new IslandColumn();
		part.island = island;
		part.inside = inside;
		part.t = t;
		// Terraces: the rise towards the middle, cut into flat steps whose edges wander.
		double o = island.noiseOffset;
		double warped = MathHelper.clamp(t + 0.16 * detailNoise.sample(bx / 22.0 + o, bz / 22.0), 0.0, 1.0);
		double rise = Math.pow(warped, 0.7) * island.tiers;
		int step = Math.min(island.tiers - 1, (int) Math.floor(rise));
		double stepHeight = island.domeHeight / (double) island.tiers;
		// The last block or two of each step rounds over into the step below.
		double frac = rise - step;
		double lift = (step + (frac > 0.92 ? (frac - 0.92) / 0.08 : 0.0)) * stepHeight;
		part.top = island.level + (int) Math.round(lift + 0.6 * detailNoise.sample(bx / 13.0 - o, bz / 13.0 + o));
		// The underside: thin at the rim, hanging deepest under the middle, in crags.
		double hang = island.depth * Math.pow(t, 0.55) * (0.72 + 0.28 * edgeNoise.sample(bx / 11.0 + o, bz / 11.0 + o))
				+ 3 * Math.abs(detailNoise.sample(bx / 5.0, bz / 5.0 - o));
		part.bottom = island.level - 2.5 - hang;
		return part;
	}

	/**
	 * The terrain's density at a block: positive is end stone. Roughly the distance in blocks to the nearest surface,
	 * clamped to -1..1.
	 */
	public double density(int x, int y, int z) {
		Column column = column(x, z);
		if (y < column.minY || y > column.maxY) {
			return -1.0;
		}
		double best = -1.0;
		for (IslandColumn part : column.islands) {
			best = Math.max(best, islandDensity(part, x, y, z));
			if (best >= 1.0) {
				return 1.0;
			}
		}
		for (Shape shape : column.others) {
			if (y < shape.minY || y > shape.maxY) {
				continue;
			}
			double d = shape instanceof Spire spire ? spireDensity(spire, x, y, z) : fragmentDensity((Fragment) shape, x, y, z);
			best = Math.max(best, d);
			if (best >= 1.0) {
				return 1.0;
			}
		}
		return best;
	}

	private double islandDensity(IslandColumn part, int x, int y, int z) {
		if (y > part.top + 1 || y < part.bottom - 6) {
			return -1.0;
		}
		double fromTop = part.top + 0.5 - y;
		// Rough 3D noise on the sides and underside: overhangs, lips and hollows, never on the flat tops.
		double rough = roughNoise.sample(x / 12.0, y / 8.0, z / 12.0) + 0.4 * roughNoise.sample(x / 5.0, y / 4.0, z / 5.0);
		double fromBottom = y - part.bottom + 2.5 * rough;
		// The sides draw in under the rim (undercut), more the further below it.
		double below = Math.max(0, part.island.level - y);
		double fromSide = part.inside + 3.5 * rough - Math.min(4, below * 0.18);
		if (y > part.island.level) {
			// Above the rim the sides are the terrace walls: kept straight.
			fromSide = part.inside + 0.8 * rough + 1;
		}
		double d = Math.min(fromTop, Math.min(fromBottom, fromSide));
		return MathHelper.clamp(d / 2.0, -1.0, 1.0);
	}

	private double spireDensity(Spire spire, int x, int y, int z) {
		double along = (y - spire.bottom) / (double) Math.max(1, spire.top - spire.bottom);
		// Radius: wide at the root, tapering to the tip (a hanging spire's root is its top).
		double fromRoot = spire.hanging ? 1.0 - along : along;
		double radius = spire.rootRadius + (spire.tipRadius - spire.rootRadius) * Math.pow(fromRoot, 0.8);
		double sx = spire.x + spire.leanX * (y - spire.bottom);
		double sz = spire.z + spire.leanZ * (y - spire.bottom);
		double dx = x + 0.5 - sx;
		double dz = z + 0.5 - sz;
		double d = radius - Math.sqrt(dx * dx + dz * dz) + 0.9 * roughNoise.sample(x / 4.0, y / 6.0, z / 4.0);
		d = Math.min(d, Math.min(y - spire.bottom + 0.5, spire.top + 0.5 - y));
		return MathHelper.clamp(d / 1.5, -1.0, 1.0);
	}

	private double fragmentDensity(Fragment fragment, int x, int y, int z) {
		double dx = (x + 0.5 - fragment.x) / fragment.rx;
		double dy = (y + 0.5 - fragment.y) / fragment.ry;
		double dz = (z + 0.5 - fragment.z) / fragment.rz;
		double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double d = (1.0 - r + 0.3 * roughNoise.sample(x / 3.5 + 50, y / 3.5, z / 3.5)) * Math.min(fragment.rx, fragment.ry) * 1.5;
		return MathHelper.clamp(d, -1.0, 1.0);
	}

	// ------------------------------------------------------------------ queries for biomes, features and commands

	/** The highest island top over a column (ignoring spires and fragments), or Integer.MIN_VALUE where there is none. */
	public int islandTop(int x, int z) {
		int top = Integer.MIN_VALUE;
		for (IslandColumn part : column(x, z).islands) {
			if (part.inside > 0) {
				top = Math.max(top, part.top);
			}
		}
		return top;
	}

	/**
	 * The value the End's biome source reads (the router's erosion) at a chunk's middle: highlands (chorus, End cities,
	 * return gateways) in the middle of a main island, midlands on the rest of an island, barrens over fragments and the
	 * void and on islands too high for an End city. Never small_end_islands, whose vanilla islands would sit in a flat
	 * layer at y 55 to 70.
	 */
	public double biomeValue(int x, int z) {
		double value = -0.15;
		for (IslandColumn part : column(x, z).islands) {
			if (part.inside <= 0 || part.top > CITY_MAX_TOP) {
				continue;
			}
			value = Math.max(value, part.island.main && part.t > 0.3 ? 0.5 : 0.0);
		}
		return value;
	}

	/** The highest solid block of the terrain over a column, worked out from the density (for features and tests). */
	public int surface(int x, int z) {
		Column column = column(x, z);
		for (int y = Math.min(CEILING, column.maxY); y >= Math.max(1, column.minY); y--) {
			if (density(x, y, z) > 0) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}
}
