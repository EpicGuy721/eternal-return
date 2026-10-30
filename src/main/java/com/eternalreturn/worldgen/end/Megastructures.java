package com.eternalreturn.worldgen.end;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSetKeys;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shattered obsidian megastructures in the outer End of Eternal Return worlds: rings, tilted monoliths, cubes,
 * pyramids, arches and clusters of shards, each built from geometric shapes (signed distance functions) and then
 * broken. Planes cut it into pieces, and each piece past a cut is either gone or drifted off and tilted, floating
 * nearby; round bites are taken out of it; thin cracks run through it; rubble lies on the ground round it. It is
 * obsidian with crying obsidian in veins and patches (more along the breaks), and amethyst clusters and buds grow out
 * of the broken faces. Some rest on an island, some are sunk into one, some float free.
 * <p>
 * Placed from the world seed alone, at most one in each square of worldgen.outerEnd.megastructureSpacing blocks, so
 * every chunk builds its own part (MegastructureFeature) and the parts line up in any order. None comes near the exit
 * gateways' landing ring (within EXCLUSION of 0, 0) or near a place an End city could start.
 */
public final class Megastructures {
	/** No megastructure reaches within this of 0, 0: the exit gateways land between about 1,024 and 1,300 out. */
	public static final int EXCLUSION = 1400;
	/** How far an End city can sprawl from its start, in blocks (its towers, ship and bridges). */
	private static final int CITY_CLEARANCE = 96;

	private static final Map<Long, Megastructures> BY_SEED = new ConcurrentHashMap<>();

	private final OuterEndTerrain terrain;
	private final long seed;
	private final Settings settings;
	@Nullable
	private final CityPlacement cities;
	private final SimplexNoiseSampler materialNoise;
	private final SimplexNoiseSampler crackNoise;
	private final Map<Long, java.util.Optional<Structure>> cells = new ConcurrentHashMap<>();

	/** The tunable numbers, from worldgen.outerEnd in config/eternalreturn.json. */
	public record Settings(boolean enabled, int spacing, double chance, double massiveChance, double cryingRatio, double amethyst) {
		static Settings fromConfig() {
			EternalReturnConfig.OuterEndTweaks c = EternalReturnConfig.get().worldgen.outerEnd;
			return new Settings(c.megastructures, MathHelper.clamp(c.megastructureSpacing, 160, 4096), MathHelper.clamp(c.megastructureChance, 0.0, 1.0),
					MathHelper.clamp(c.massiveChance, 0.0, 1.0), MathHelper.clamp(c.cryingObsidianRatio, 0.0, 1.0), Math.max(0.0, c.amethystDensity));
		}
	}

	/** Where End cities can start: their structure set's spread, worked out as the game does. */
	private record CityPlacement(RandomSpreadStructurePlacement placement, long structureSeed) {
	}

	private Megastructures(OuterEndTerrain terrain, long seed, @Nullable CityPlacement cities) {
		this.terrain = terrain;
		this.seed = seed;
		this.settings = Settings.fromConfig();
		this.cities = cities;
		this.materialNoise = new SimplexNoiseSampler(new CheckedRandom(seed * 7 + 3));
		this.crackNoise = new SimplexNoiseSampler(new CheckedRandom(seed * 13 + 5));
	}

	/** The megastructures of an End world in Eternal Return, or null in any other world. */
	@Nullable
	public static Megastructures of(ServerWorld world) {
		OuterEndTerrain terrain = world.getRegistryKey() == World.END
				? OuterEndDensity.terrainOf(world.getChunkManager().getNoiseConfig().getNoiseRouter().finalDensity()) : null;
		if (terrain == null) {
			return null;
		}
		long key = world.getSeed() ^ terrain.seed() * 31;
		Megastructures known = BY_SEED.get(key);
		if (known == null) {
			if (BY_SEED.size() > 16) {
				BY_SEED.clear();
			}
			known = new Megastructures(terrain, world.getSeed(), cityPlacement(world));
			BY_SEED.put(key, known);
		}
		return known;
	}

	public static void reset() {
		BY_SEED.clear();
	}

	@Nullable
	private static CityPlacement cityPlacement(ServerWorld world) {
		StructurePlacementCalculator calculator = world.getChunkManager().getStructurePlacementCalculator();
		RegistryEntry<net.minecraft.structure.StructureSet> set = world.getRegistryManager().get(RegistryKeys.STRUCTURE_SET)
				.getEntry(StructureSetKeys.END_CITIES).orElse(null);
		if (set == null || !calculator.getStructureSets().contains(set)
				|| !(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) {
			return null;
		}
		return new CityPlacement(spread, calculator.getStructureSeed());
	}

	public OuterEndTerrain terrain() {
		return terrain;
	}

	public Settings settings() {
		return settings;
	}

	// ------------------------------------------------------------------ geometry

	/** A rotation (3 x 3, row major) and a centre: world to local and back. */
	static final class Frame {
		final double[] m;
		final double cx, cy, cz;

		Frame(double[] m, double cx, double cy, double cz) {
			this.m = m;
			this.cx = cx;
			this.cy = cy;
			this.cz = cz;
		}

		/** World to local: the transpose of the rotation. */
		void toLocal(double x, double y, double z, double[] out) {
			double dx = x - cx, dy = y - cy, dz = z - cz;
			out[0] = m[0] * dx + m[3] * dy + m[6] * dz;
			out[1] = m[1] * dx + m[4] * dy + m[7] * dz;
			out[2] = m[2] * dx + m[5] * dy + m[8] * dz;
		}

		/** Half the world box of a local box of these half sizes. */
		double[] worldHalf(double hx, double hy, double hz) {
			return new double[]{
					Math.abs(m[0]) * hx + Math.abs(m[1]) * hy + Math.abs(m[2]) * hz,
					Math.abs(m[3]) * hx + Math.abs(m[4]) * hy + Math.abs(m[5]) * hz,
					Math.abs(m[6]) * hx + Math.abs(m[7]) * hy + Math.abs(m[8]) * hz};
		}
	}

	static double[] multiply(double[] a, double[] b) {
		double[] r = new double[9];
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
			}
		}
		return r;
	}

	/** Rotation by angle about a unit axis. */
	static double[] rotation(double ax, double ay, double az, double angle) {
		double c = Math.cos(angle), s = Math.sin(angle), t = 1 - c;
		return new double[]{
				t * ax * ax + c, t * ax * ay - s * az, t * ax * az + s * ay,
				t * ax * ay + s * az, t * ay * ay + c, t * ay * az - s * ax,
				t * ax * az - s * ay, t * ay * az + s * ax, t * az * az + c};
	}

	/** A yaw, then a tilt of up to maxTilt about a level axis. */
	static double[] tilted(Random random, double maxTilt) {
		double yaw = random.nextDouble() * Math.PI * 2;
		double axis = random.nextDouble() * Math.PI * 2;
		double[] tilt = rotation(Math.cos(axis), 0, Math.sin(axis), random.nextDouble() * maxTilt);
		return multiply(tilt, rotation(0, 1, 0, yaw));
	}

	/** Any rotation at all (a tumbled block). */
	static double[] tumbled(Random random) {
		double[] a = rotation(0, 1, 0, random.nextDouble() * Math.PI * 2);
		double[] b = rotation(1, 0, 0, random.nextDouble() * Math.PI * 2);
		return multiply(rotation(0, 0, 1, random.nextDouble() * Math.PI * 2), multiply(b, a));
	}

	/** A signed distance in local coordinates: negative inside, roughly in blocks. */
	interface Sdf {
		double at(double x, double y, double z);
	}

	static double box(double x, double y, double z, double hx, double hy, double hz) {
		double qx = Math.abs(x) - hx, qy = Math.abs(y) - hy, qz = Math.abs(z) - hz;
		double outside = Math.sqrt(Math.max(qx, 0) * Math.max(qx, 0) + Math.max(qy, 0) * Math.max(qy, 0) + Math.max(qz, 0) * Math.max(qz, 0));
		return outside + Math.min(Math.max(qx, Math.max(qy, qz)), 0);
	}

	/** A shell of the given thickness inside a shape. */
	static Sdf hollow(Sdf solid, double thickness) {
		return (x, y, z) -> {
			double d = solid.at(x, y, z);
			return Math.max(d, -(d + thickness));
		};
	}

	/** One solid of a structure: a shape in its own frame, with the half sizes of its local box. */
	record Solid(Sdf sdf, Frame frame, double hx, double hy, double hz) {
	}

	/** A cut: blocks with (p - point) . normal > 0 are on the far side. */
	record Plane(double px, double py, double pz, double nx, double ny, double nz) {
		double side(double x, double y, double z) {
			return (x - px) * nx + (y - py) * ny + (z - pz) * nz;
		}
	}

	/**
	 * What became of the part past a cut: gone, or moved. A moved piece is rotated about the cut's point and then
	 * shifted; to find what is at a block, the block is taken back (inverse) to where the piece was.
	 */
	record Piece(Plane cut, boolean gone, double[] rotation, double dx, double dy, double dz) {
		void back(double x, double y, double z, double[] out) {
			double rx = x - dx - cut.px(), ry = y - dy - cut.py(), rz = z - dz - cut.pz();
			out[0] = rotation[0] * rx + rotation[3] * ry + rotation[6] * rz + cut.px();
			out[1] = rotation[1] * rx + rotation[4] * ry + rotation[7] * rz + cut.py();
			out[2] = rotation[2] * rx + rotation[5] * ry + rotation[8] * rz + cut.pz();
		}
	}

	/** A round bite taken out of the structure. */
	record Bite(double x, double y, double z, double radius) {
	}

	/** The kinds, by name for the locate command and the tests. */
	public enum Kind {
		RING, MONOLITH, CUBE, PYRAMID, ARCH, SHARDS
	}

	/** How it sits: on an island, sunk into one, or floating free. */
	public enum Setting {
		RESTING, EMBEDDED, FLOATING
	}

	/** A megastructure: its solids, its breaks, its rubble, and the world box everything stays in. */
	public static final class Structure {
		public final Kind kind;
		public final Setting setting;
		public final boolean massive;
		public final double x, y, z, size;
		public final int minX, maxX, minY, maxY, minZ, maxZ;
		final List<Solid> solids;
		final List<Piece> pieces;
		final List<Bite> bites;
		final List<Solid> rubble;
		final double crackScale;

		Structure(Kind kind, Setting setting, boolean massive, double x, double y, double z, double size, List<Solid> solids, List<Piece> pieces,
				List<Bite> bites, List<Solid> rubble, int[] bounds) {
			this.kind = kind;
			this.setting = setting;
			this.massive = massive;
			this.x = x;
			this.y = y;
			this.z = z;
			this.size = size;
			this.solids = solids;
			this.pieces = pieces;
			this.bites = bites;
			this.rubble = rubble;
			this.crackScale = Math.max(6, size * 0.35);
			this.minX = bounds[0];
			this.maxX = bounds[1];
			this.minY = bounds[2];
			this.maxY = bounds[3];
			this.minZ = bounds[4];
			this.maxZ = bounds[5];
		}

		public boolean touchesChunk(ChunkPos chunk) {
			return maxX >= chunk.getStartX() - 1 && minX <= chunk.getEndX() + 1 && maxZ >= chunk.getStartZ() - 1 && minZ <= chunk.getEndZ() + 1;
		}
	}

	// ------------------------------------------------------------------ layout

	/** The megastructure of a square, if it has one. Cached. */
	@Nullable
	public Structure inCell(int cellX, int cellZ) {
		long key = (long) cellX << 32 | (cellZ & 0xFFFFFFFFL);
		java.util.Optional<Structure> found = cells.get(key);
		if (found == null) {
			if (cells.size() > 4096) {
				cells.clear();
			}
			found = java.util.Optional.ofNullable(settings.enabled ? make(cellX, cellZ) : null);
			cells.put(key, found);
		}
		return found.orElse(null);
	}

	/** Every megastructure whose box touches a chunk (or the block round it). */
	public List<Structure> near(ChunkPos chunk) {
		List<Structure> found = new ArrayList<>();
		int spacing = settings.spacing;
		for (int cellX = Math.floorDiv(chunk.getStartX(), spacing) - 1; cellX <= Math.floorDiv(chunk.getEndX(), spacing) + 1; cellX++) {
			for (int cellZ = Math.floorDiv(chunk.getStartZ(), spacing) - 1; cellZ <= Math.floorDiv(chunk.getEndZ(), spacing) + 1; cellZ++) {
				Structure structure = inCell(cellX, cellZ);
				if (structure != null && structure.touchesChunk(chunk)) {
					found.add(structure);
				}
			}
		}
		return found;
	}

	private Random cellRandom(int cellX, int cellZ) {
		long h = seed ^ 0x0B5D1A7EL;
		h = h * 6364136223846793005L + cellX * 341873128712L;
		h = h * 6364136223846793005L + cellZ * 132897987541L;
		return new CheckedRandom(h ^ (h >>> 29));
	}

	@Nullable
	private Structure make(int cellX, int cellZ) {
		Random random = cellRandom(cellX, cellZ);
		if (random.nextDouble() >= settings.chance) {
			return null;
		}
		int spacing = settings.spacing;
		boolean massive = random.nextDouble() < settings.massiveChance;
		double size = massive ? 50 + random.nextDouble() * 40 : 15 + 25 * Math.pow(random.nextDouble(), 1.3);
		// Everything a structure has (pieces drifted off, rubble) stays within about 1.9 sizes of its centre; keep that
		// inside the square's neighbours, so a chunk only looks one square each way.
		size = Math.min(size, spacing / 2.0 / 1.9);
		double reach = size * 1.9;
		double margin = Math.min(reach, spacing / 2.0 - 1);
		// Three in four stand by an island (on it, in it or over it): try a few spots for one with ground under it.
		boolean wantsGround = random.nextDouble() < 0.75;
		double x = 0, z = 0;
		for (int attempt = 0; attempt < 24; attempt++) {
			x = cellX * spacing + margin + random.nextDouble() * Math.max(1, spacing - 2 * margin);
			z = cellZ * spacing + margin + random.nextDouble() * Math.max(1, spacing - 2 * margin);
			if (!wantsGround || terrain.islandTop((int) Math.floor(x), (int) Math.floor(z)) != Integer.MIN_VALUE) {
				break;
			}
		}
		if (Math.sqrt(x * x + z * z) - reach < EXCLUSION) {
			return null;
		}
		// Keep clear of End cities: shrink once, then give up.
		if (nearCity(x, z, reach)) {
			size *= 0.55;
			reach = size * 1.9;
			massive = false;
			if (nearCity(x, z, reach)) {
				return null;
			}
		}
		Kind kind = pickKind(random);
		return build(random, kind, massive, x, z, size);
	}

	private static Kind pickKind(Random random) {
		int roll = random.nextInt(100);
		if (roll < 17) return Kind.RING;
		if (roll < 34) return Kind.MONOLITH;
		if (roll < 48) return Kind.CUBE;
		if (roll < 62) return Kind.PYRAMID;
		if (roll < 76) return Kind.ARCH;
		return Kind.SHARDS;
	}

	/** Whether an End city could start within reach (plus its own sprawl) of a spot: a start chunk in a city biome. */
	private boolean nearCity(double x, double z, double reach) {
		if (cities == null) {
			return false;
		}
		double clear = reach + CITY_CLEARANCE;
		int spacingChunks = cities.placement.getSpacing();
		int minRegionX = Math.floorDiv((int) Math.floor((x - clear) / 16), spacingChunks);
		int maxRegionX = Math.floorDiv((int) Math.floor((x + clear) / 16), spacingChunks);
		int minRegionZ = Math.floorDiv((int) Math.floor((z - clear) / 16), spacingChunks);
		int maxRegionZ = Math.floorDiv((int) Math.floor((z + clear) / 16), spacingChunks);
		for (int rx = minRegionX; rx <= maxRegionX; rx++) {
			for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
				ChunkPos start = cities.placement.getStartChunk(cities.structureSeed, rx * spacingChunks, rz * spacingChunks);
				double sx = start.getStartX() + 8, sz = start.getStartZ() + 8;
				if ((sx - x) * (sx - x) + (sz - z) * (sz - z) > clear * clear) {
					continue;
				}
				// End cities only start in highlands and midlands, the biome of the chunk's middle.
				if ((long) start.x * start.x + (long) start.z * start.z > 4096L && terrain.biomeValue((int) sx, (int) sz) >= -0.0625) {
					return true;
				}
			}
		}
		return false;
	}

	private Structure build(Random random, Kind kind, boolean massive, double x, double z, double size) {
		// The ground under it: the terrain's top at its middle.
		int ground = terrain.surface((int) Math.floor(x), (int) Math.floor(z));
		List<Solid> solids = new ArrayList<>();
		double[] m;
		// Local shapes about 0, 0, 0; the frame centre's height is set once the setting is known.
		switch (kind) {
			case RING -> {
				double major = size;
				double minor = Math.max(2.5, size * (0.11 + random.nextDouble() * 0.08));
				boolean square = random.nextBoolean();
				m = tilted(random, Math.toRadians(20 + random.nextDouble() * 65));
				solids.add(new Solid((px, py, pz) -> {
					double radial = Math.sqrt(px * px + pz * pz) - major;
					return square ? Math.max(Math.abs(radial), Math.abs(py)) - minor : Math.sqrt(radial * radial + py * py) - minor;
				}, null, major + minor, minor, major + minor));
			}
			case MONOLITH -> {
				double hx = Math.max(3, size * (0.2 + random.nextDouble() * 0.12));
				double hy = size * (0.85 + random.nextDouble() * 0.4);
				double hz = Math.max(2, size * (0.06 + random.nextDouble() * 0.06));
				m = tilted(random, Math.toRadians(6 + random.nextDouble() * 26));
				solids.add(new Solid((px, py, pz) -> box(px, py, pz, hx, hy, hz), null, hx, hy, hz));
			}
			case CUBE -> {
				double h = size * (0.45 + random.nextDouble() * 0.15);
				m = random.nextBoolean() ? tumbled(random) : tilted(random, Math.toRadians(25));
				Sdf cube = (px, py, pz) -> box(px, py, pz, h, h, h);
				solids.add(new Solid(h > 13 ? hollow(cube, Math.max(3, h * 0.13)) : cube, null, h, h, h));
			}
			case PYRAMID -> {
				double height = size * (0.9 + random.nextDouble() * 0.4);
				double base = size * (0.65 + random.nextDouble() * 0.2);
				boolean inverted = random.nextInt(4) == 0;
				m = tilted(random, Math.toRadians(inverted ? 30 : 18));
				double slope = height / Math.sqrt(height * height + base * base);
				Sdf pyramid = (px, py, pz) -> {
					double y = (inverted ? -py : py) + height / 2;
					double half = base * (1 - y / height);
					double side = (Math.max(Math.abs(px), Math.abs(pz)) - half) * slope;
					return Math.max(side, Math.max(-y, y - height));
				};
				solids.add(new Solid(size > 20 ? hollow(pyramid, Math.max(3, size * 0.1)) : pyramid, null, base, height / 2, base));
			}
			case ARCH -> {
				double radius = size * 0.75;
				double thick = Math.max(2.5, size * 0.12);
				double deep = Math.max(2.5, size * (0.12 + random.nextDouble() * 0.08));
				double legs = size * (0.4 + random.nextDouble() * 0.4);
				m = tilted(random, Math.toRadians(4 + random.nextDouble() * 14));
				// The arch stands in its local x-y plane: a half ring over two legs.
				solids.add(new Solid((px, py, pz) -> {
					double arc = py >= 0 ? Math.max(Math.abs(Math.sqrt(px * px + py * py) - radius) - thick, Math.abs(pz) - deep) : Double.MAX_VALUE;
					double leg = Math.min(box(px - radius, py + legs / 2, pz, thick, legs / 2, deep), box(px + radius, py + legs / 2, pz, thick, legs / 2, deep));
					return Math.min(arc, leg);
				}, null, radius + thick, Math.max(radius + thick, legs), deep));
			}
			default -> {
				// Shards: long pointed crystals of obsidian jutting from one root at angles.
				m = rotation(0, 1, 0, random.nextDouble() * Math.PI * 2);
				int count = 3 + random.nextInt(5);
				for (int i = 0; i < count; i++) {
					double length = size * (0.5 + random.nextDouble() * 0.8);
					double width = Math.max(1.6, size * (0.07 + random.nextDouble() * 0.08));
					double[] dir = tilted(random, Math.toRadians(10 + random.nextDouble() * 55));
					// A bipyramid along local y, from the root up: widest a fifth of the way along.
					double waist = length * 0.2;
					Sdf shard = (px, py, pz) -> {
						double w = py < waist ? width * Math.max(0, (py + length * 0.15) / (waist + length * 0.15)) : width * (length - py) / (length - waist);
						return Math.max((Math.abs(px) + Math.abs(pz)) * 0.7071 - w * 0.7071, Math.max(-length * 0.15 - py, py - length));
					};
					solids.add(new Solid(shard, new Frame(multiply(m, dir), 0, 0, 0), width, length, width));
				}
			}
		}

		// Its height: on, in or above the island under it, or adrift where there is none.
		double[] half = halfExtents(solids, m);
		Setting setting;
		double y;
		if (ground != Integer.MIN_VALUE) {
			double roll = random.nextDouble();
			if (kind == Kind.SHARDS || roll < 0.42) {
				setting = Setting.EMBEDDED;
				y = kind == Kind.SHARDS ? ground - 4 : ground - half[1] * (0.25 + random.nextDouble() * 0.3);
			} else if (roll < 0.85) {
				setting = Setting.RESTING;
				y = ground + half[1] - 2;
			} else {
				setting = Setting.FLOATING;
				y = ground + half[1] + 12 + random.nextDouble() * 30;
			}
		} else {
			setting = Setting.FLOATING;
			y = 70 + random.nextDouble() * 120;
		}
		double top = kind == Kind.SHARDS ? y + half[1] * 2 : y + half[1];
		if (top > OuterEndTerrain.CEILING - 2) {
			y -= top - (OuterEndTerrain.CEILING - 2);
		}
		double bottom = kind == Kind.SHARDS ? y - half[1] * 0.3 : y - half[1];
		if (bottom < 6) {
			y += 6 - bottom;
		}
		double cy = y;
		List<Solid> placed = new ArrayList<>();
		for (Solid solid : solids) {
			double[] matrix = solid.frame() == null ? m : solid.frame().m;
			placed.add(new Solid(solid.sdf(), new Frame(matrix, x, cy, z), solid.hx(), solid.hy(), solid.hz()));
		}

		// The breaks: one to three cuts (two to four on a massive one), each piece past a cut gone or drifted off.
		List<Piece> pieces = new ArrayList<>();
		int cuts = (massive ? 2 : 1) + random.nextInt(3);
		for (int i = 0; i < cuts; i++) {
			double nx = random.nextGaussian(), ny = random.nextGaussian() * 0.6, nz = random.nextGaussian();
			double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
			nx /= length;
			ny /= length;
			nz /= length;
			double offset = size * (0.15 + random.nextDouble() * 0.45);
			Plane cut = new Plane(x + nx * offset, cy + ny * offset, z + nz * offset, nx, ny, nz);
			// At most one piece is gone altogether; the rest drift off.
			if (random.nextDouble() < 0.35 && pieces.stream().noneMatch(Piece::gone)) {
				pieces.add(new Piece(cut, true, null, 0, 0, 0));
			} else {
				double away = 3 + random.nextDouble() * size * 0.45;
				double drop = (random.nextDouble() - 0.6) * size * 0.4;
				double[] axis = {random.nextGaussian(), random.nextGaussian(), random.nextGaussian()};
				double axisLength = Math.sqrt(axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]);
				double[] turn = rotation(axis[0] / axisLength, axis[1] / axisLength, axis[2] / axisLength, (random.nextDouble() - 0.5) * 0.8);
				double dy = MathHelper.clamp(ny * away + drop, 6 - (cy - half[1]), OuterEndTerrain.CEILING - 4 - (cy + half[1]));
				pieces.add(new Piece(cut, false, turn, nx * away, dy, nz * away));
			}
		}

		// Bites out of the surface.
		List<Bite> bites = new ArrayList<>();
		int biteCount = 2 + random.nextInt(massive ? 6 : 4);
		for (int i = 0; i < biteCount; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double b = Math.acos(2 * random.nextDouble() - 1);
			double d = size * (0.55 + random.nextDouble() * 0.45);
			bites.add(new Bite(x + Math.cos(a) * Math.sin(b) * d * half[0] / size, cy + Math.cos(b) * d * half[1] / size,
					z + Math.sin(a) * Math.sin(b) * d * half[2] / size, size * (0.12 + random.nextDouble() * 0.2) + 1.5));
		}

		// Rubble on the ground round it (or adrift by it, over the void).
		List<Solid> rubble = new ArrayList<>();
		int rubbleCount = (int) ((massive ? 30 : 8) + random.nextInt(massive ? 40 : 18));
		for (int i = 0; i < rubbleCount; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double d = size * (0.4 + random.nextDouble() * 1.1);
			double rx = x + Math.cos(a) * d, rz = z + Math.sin(a) * d;
			// On island ground (not a spire tip or a drifting fragment), or adrift over the void.
			int floor = terrain.islandTop((int) Math.floor(rx), (int) Math.floor(rz));
			double h = (massive ? 0.8 + random.nextDouble() * 3 : 0.6 + random.nextDouble() * 1.8);
			double ry = floor != Integer.MIN_VALUE && floor > cy - half[1] - 40 ? floor + h * 0.4 : cy + (random.nextDouble() - 0.5) * half[1] * 1.4;
			double hx = h * (0.7 + random.nextDouble() * 0.6), hy = h * (0.5 + random.nextDouble() * 0.5), hz = h * (0.7 + random.nextDouble() * 0.6);
			rubble.add(new Solid((px, py, pz) -> box(px, py, pz, hx, hy, hz), new Frame(tumbled(random), rx, ry, rz), hx, hy, hz));
		}

		int[] bounds = bounds(placed, pieces, rubble);
		return new Structure(kind, setting, massive, x, cy, z, size, placed, pieces, bites, rubble, bounds);
	}

	/** Half the world box of all solids about the centre (shards from their root). */
	private static double[] halfExtents(List<Solid> solids, double[] m) {
		double[] half = new double[3];
		for (Solid solid : solids) {
			double[] matrix = solid.frame() == null ? m : solid.frame().m;
			double[] w = new Frame(matrix, 0, 0, 0).worldHalf(solid.hx(), solid.hy(), solid.hz());
			for (int i = 0; i < 3; i++) {
				half[i] = Math.max(half[i], w[i]);
			}
		}
		return half;
	}

	private static int[] bounds(List<Solid> solids, List<Piece> pieces, List<Solid> rubble) {
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		double shift = 0;
		for (Piece piece : pieces) {
			if (!piece.gone()) {
				shift = Math.max(shift, Math.sqrt(piece.dx() * piece.dx() + piece.dy() * piece.dy() + piece.dz() * piece.dz()));
			}
		}
		for (Solid solid : solids) {
			// A turned piece can reach out as far as the solid's corner from its centre, whichever way it turns.
			double r = 1.4 * Math.sqrt(solid.hx() * solid.hx() + solid.hy() * solid.hy() + solid.hz() * solid.hz());
			double[] w = solid.frame().worldHalf(solid.hx(), solid.hy(), solid.hz());
			double reachX = pieces.isEmpty() ? w[0] : r, reachY = pieces.isEmpty() ? w[1] : r, reachZ = pieces.isEmpty() ? w[2] : r;
			// Shards hang off their root, not their middle: count their full length each way.
			double extra = solid.hy() > solid.hx() * 3 ? solid.hy() : 0;
			minX = Math.min(minX, solid.frame().cx - reachX - extra - shift);
			maxX = Math.max(maxX, solid.frame().cx + reachX + extra + shift);
			minY = Math.min(minY, solid.frame().cy - reachY - extra - shift);
			maxY = Math.max(maxY, solid.frame().cy + reachY + extra + shift);
			minZ = Math.min(minZ, solid.frame().cz - reachZ - extra - shift);
			maxZ = Math.max(maxZ, solid.frame().cz + reachZ + extra + shift);
		}
		for (Solid solid : rubble) {
			double r = Math.sqrt(solid.hx() * solid.hx() + solid.hy() * solid.hy() + solid.hz() * solid.hz());
			minX = Math.min(minX, solid.frame().cx - r);
			maxX = Math.max(maxX, solid.frame().cx + r);
			minY = Math.min(minY, solid.frame().cy - r);
			maxY = Math.max(maxY, solid.frame().cy + r);
			minZ = Math.min(minZ, solid.frame().cz - r);
			maxZ = Math.max(maxZ, solid.frame().cz + r);
		}
		return new int[]{(int) Math.floor(minX) - 1, (int) Math.ceil(maxX) + 1, Math.max(1, (int) Math.floor(minY) - 1),
				Math.min(OuterEndTerrain.CEILING + 4, (int) Math.ceil(maxY) + 1), (int) Math.floor(minZ) - 1, (int) Math.ceil(maxZ) + 1};
	}

	// ------------------------------------------------------------------ sampling

	/** Nothing there. */
	public static final int EMPTY = 0;
	public static final int OBSIDIAN = 1;
	public static final int CRYING = 2;
	/** Added to a solid block on a broken face: a cut, a bite or a crack (amethyst grows from these). */
	public static final int DAMAGED = 4;

	/** What a structure has at a block: EMPTY, or OBSIDIAN or CRYING, plus DAMAGED. */
	public int sample(Structure s, int bx, int by, int bz, double[] scratch) {
		double x = bx + 0.5, y = by + 0.5, z = bz + 0.5;
		for (Solid solid : s.rubble) {
			solid.frame().toLocal(x, y, z, scratch);
			if (solid.sdf().at(scratch[0], scratch[1], scratch[2]) <= 0) {
				return material(x, y, z, false);
			}
		}
		// The body (on the near side of every cut), then each piece (taken back to where it broke from).
		int found = sampleBody(s, x, y, z, s.pieces.size(), scratch);
		if (found != EMPTY) {
			return found;
		}
		for (int i = 0; i < s.pieces.size(); i++) {
			Piece piece = s.pieces.get(i);
			if (piece.gone()) {
				continue;
			}
			piece.back(x, y, z, scratch);
			double qx = scratch[0], qy = scratch[1], qz = scratch[2];
			if (piece.cut().side(qx, qy, qz) <= 0) {
				continue;
			}
			found = sampleBody(s, qx, qy, qz, i, scratch);
			if (found != EMPTY) {
				return found;
			}
		}
		return EMPTY;
	}

	/** The unbroken body at a point, kept only on the near side of the first `cuts` cuts. */
	private int sampleBody(Structure s, double x, double y, double z, int cuts, double[] scratch) {
		boolean damaged = false;
		for (int i = 0; i < cuts; i++) {
			double side = s.pieces.get(i).cut().side(x, y, z);
			if (side > 0) {
				return EMPTY;
			}
			damaged |= side > -1.6;
		}
		// When this is a piece, the cut it broke along is a broken face too.
		if (cuts < s.pieces.size() && Math.abs(s.pieces.get(cuts).cut().side(x, y, z)) < 1.6) {
			damaged = true;
		}
		double inside = Double.MAX_VALUE;
		for (Solid solid : s.solids) {
			solid.frame().toLocal(x, y, z, scratch);
			inside = Math.min(inside, solid.sdf().at(scratch[0], scratch[1], scratch[2]));
			if (inside <= -1.5) {
				break;
			}
		}
		if (inside > 0) {
			return EMPTY;
		}
		for (Bite bite : s.bites) {
			double dx = x - bite.x(), dy = y - bite.y(), dz = z - bite.z();
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz) - bite.radius() * (1 + 0.25 * crackNoise.sample(x / 5.0, y / 5.0, z / 5.0));
			if (d < 0) {
				return EMPTY;
			}
			damaged |= d < 1.6;
		}
		// Cracks: thin sheets where a noise crosses zero, running right through.
		double crack = Math.abs(crackNoise.sample(x / s.crackScale, y / s.crackScale, z / s.crackScale)
				+ 0.3 * crackNoise.sample(x / 4.0 + 100, y / 4.0, z / 4.0));
		if (crack < 0.03) {
			return EMPTY;
		}
		damaged |= crack < 0.08;
		return material(x, y, z, damaged) | (damaged ? DAMAGED : 0);
	}

	/**
	 * Obsidian or crying obsidian: crying in veins (where one noise is near zero) and patches (where another is high),
	 * their share set by cryingRatio, and more along the breaks, as if it wept where it broke.
	 */
	private int material(double x, double y, double z, boolean damaged) {
		double ratio = settings.cryingRatio;
		if (ratio <= 0) {
			return OBSIDIAN;
		}
		double vein = Math.abs(materialNoise.sample(x / 11.0, y / 9.0, z / 11.0));
		double patch = materialNoise.sample(x / 17.0 + 300, y / 17.0, z / 17.0 - 300);
		boolean crying = vein < ratio * 0.12 || patch > 1.0 - ratio * 1.6 || damaged && hash(x, y, z) < ratio * 1.5;
		return crying ? CRYING : OBSIDIAN;
	}

	/** A number from 0 to 1 for a block, from the seed alone. */
	double hash(double x, double y, double z) {
		long h = seed ^ (long) Math.floor(x) * 0x9E3779B97F4A7C15L ^ (long) Math.floor(y) * 0xC2B2AE3D27D4EB4FL ^ (long) Math.floor(z) * 0x165667B19E3779F9L;
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}
}
