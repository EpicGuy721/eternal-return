package com.eternalreturn.worldgen.terrain;

import com.eternalreturn.EternalReturn;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Rare floating islands high above Eternal Return worlds. Each square of CELL blocks holds at most one group of
 * them, placed from the world seed alone, so every chunk an island touches builds its own part and the parts line up
 * whatever order chunks generate in. A group is either one island, 16 to 300 blocks long (the big ones rarer: half
 * are under about 80), or a cluster: a main island up to 160 across with two to six smaller ones round it, each at its own
 * height. An island is an oval (round to twice as long as it's wide) lying any way, with a ragged edge, its top a
 * little domed (the bigger, the more, with low hills on the big ones) between y MIN_TOP and MAX_TOP; its underside
 * narrows to a point well below the top (the bigger the island, the deeper), never lower than LOWEST_BOTTOM, above
 * the highest ground. Each column is made of the ground straight below it, copied while the chunk has its terrain and
 * surface but nothing else yet: the topsoil and the biome's stone, so a desert island is sand over sandstone and an
 * autumnal forest island grass over andesite. Over the sea it is a sandy island. The biome's trees, plants and snow
 * then come with the chunk's decoration, as on the ground. Built when the cave carver first sees a chunk, after
 * OverhangRounder (which would otherwise trim it).
 */
public final class FloatingIslands {
	public static final int CELL = 1024;
	public static final double CHANCE = 0.7;
	public static final int MIN_TOP = 195;
	public static final int MAX_TOP = 290;
	/** No island hangs lower than this (the highest ground is at about y 145). */
	public static final int LOWEST_BOTTOM = 165;
	/** How far an island reaches from its group's middle at most (a group stays inside its square). */
	private static final int SPREAD = 250;
	private static final int BAND = 6;

	private FloatingIslands() {
	}

	/**
	 * An island: centre, half its length (radius) and half its width (minor) in blocks, the way its length lies
	 * (angle, radians), top height, how far its point hangs below the top.
	 */
	public record Island(int x, int z, int radius, int minor, double angle, int top, int depth, long seed) {
	}

	/** The islands of a CELL x CELL square (none, one or a cluster): from the world seed (through the noise config) alone. */
	public static List<Island> inCell(NoiseConfig noiseConfig, int cellX, int cellZ) {
		Random random = noiseConfig.getOrCreateRandomDeriver(EternalReturn.id("floating_islands")).split(cellX, 0, cellZ);
		List<Island> islands = new ArrayList<>();
		if (random.nextDouble() >= CHANCE) {
			return islands;
		}
		int x = cellX * CELL + SPREAD + random.nextInt(CELL - 2 * SPREAD);
		int z = cellZ * CELL + SPREAD + random.nextInt(CELL - 2 * SPREAD);
		if (random.nextBoolean()) {
			// One island, radius 8 to 150, small ones commoner.
			islands.add(island(random, x, z, 8 + (int) (142 * Math.pow(random.nextDouble(), 2.2)), -1, 0));
			return islands;
		}
		// A cluster: a main island (radius 8 to 80) and smaller ones round it, each at its own height.
		// (low enough for the others to be above it as well as below)
		Island main = island(random, x, z, 8 + (int) (72 * Math.pow(random.nextDouble(), 1.8)), -1, 40);
		islands.add(main);
		int others = 2 + random.nextInt(5);
		for (int i = 0; i < others * 4 && islands.size() <= others; i++) {
			int radius = 5 + (int) (Math.min(40, main.radius()) * Math.pow(random.nextDouble(), 1.5));
			double angle = random.nextDouble() * MathHelper.TAU;
			double distance = main.radius() + radius + 8 + random.nextInt(60);
			int ix = x + (int) Math.round(Math.cos(angle) * distance);
			int iz = z + (int) Math.round(Math.sin(angle) * distance);
			if (Math.abs(ix - x) + radius > SPREAD || Math.abs(iz - z) + radius > SPREAD || crowded(islands, ix, iz, radius)) {
				continue;
			}
			islands.add(island(random, ix, iz, radius, main.top() + random.nextInt(81) - 40, 0));
		}
		return islands;
	}

	/** An island of this size here, its top near the one given (or anywhere up to headroom under MAX_TOP, given -1). */
	private static Island island(Random random, int x, int z, int radius, int nearTop, int headroom) {
		int minor = Math.max(4, (int) Math.round(radius * (0.5 + 0.5 * random.nextDouble())));
		double angle = random.nextDouble() * Math.PI;
		int depth = MathHelper.clamp((int) Math.round(radius * 0.55 + 4 + random.nextInt(10)), 8, 85);
		int lowest = Math.max(MIN_TOP, LOWEST_BOTTOM + depth);
		int top = nearTop < 0 ? lowest + random.nextInt(Math.max(1, MAX_TOP - headroom - lowest + 1)) : MathHelper.clamp(nearTop, lowest, MAX_TOP);
		return new Island(x, z, radius, minor, angle, top, depth, random.nextLong());
	}

	/** Whether another island of the cluster is already too near to fit one here. */
	private static boolean crowded(List<Island> islands, int x, int z, int radius) {
		for (Island other : islands) {
			double gap = Math.sqrt(MathHelper.square(other.x() - x) + MathHelper.square(other.z() - z));
			if (gap < other.radius() + radius + 6) {
				return true;
			}
		}
		return false;
	}

	/** The islands that may reach into a chunk. */
	public static List<Island> near(NoiseConfig noiseConfig, ChunkPos chunk) {
		List<Island> islands = new ArrayList<>();
		int reach = SPREAD + 20;
		for (int cellX = Math.floorDiv(chunk.getStartX() - reach, CELL); cellX <= Math.floorDiv(chunk.getEndX() + reach, CELL); cellX++) {
			for (int cellZ = Math.floorDiv(chunk.getStartZ() - reach, CELL); cellZ <= Math.floorDiv(chunk.getEndZ() + reach, CELL); cellZ++) {
				for (Island island : inCell(noiseConfig, cellX, cellZ)) {
					int extent = (int) Math.ceil(island.radius() * 1.3) + 1;
					if (island.x() + extent >= chunk.getStartX() && island.x() - extent <= chunk.getEndX()
							&& island.z() + extent >= chunk.getStartZ() && island.z() - extent <= chunk.getEndZ()) {
						islands.add(island);
					}
				}
			}
		}
		return islands;
	}

	/** Builds this chunk's part of every island near it. Returns the number of blocks placed. */
	public static int build(Chunk chunk, NoiseConfig noiseConfig, int seaLevel) {
		List<Island> islands = near(noiseConfig, chunk.getPos());
		if (islands.isEmpty()) {
			return 0;
		}
		int placed = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		// The ground under each column, read before any island goes in.
		BlockState[][] bands = new BlockState[256][];
		BlockState[] stones = new BlockState[256];
		for (Island island : islands) {
			SimplexNoiseSampler noise = new SimplexNoiseSampler(Random.create(island.seed()));
			for (int localX = 0; localX < 16; localX++) {
				for (int localZ = 0; localZ < 16; localZ++) {
					int x = chunk.getPos().getStartX() + localX;
					int z = chunk.getPos().getStartZ() + localZ;
					Column column = column(island, noise, x, z);
					if (column == null) {
						continue;
					}
					int index = localX << 4 | localZ;
					if (bands[index] == null) {
						bands[index] = new BlockState[BAND];
						stones[index] = groundBelow(chunk, pos, localX, localZ, seaLevel, bands[index]);
					}
					BlockState[] band = bands[index];
					BlockState stone = stones[index];
					for (int y = column.bottom; y <= column.top; y++) {
						int fromTop = column.top - y;
						BlockState state = fromTop < BAND ? band[fromTop] : stone;
						if (y == column.bottom && state.getBlock() instanceof FallingBlock) {
							// Nothing loose at the very bottom, where it would fall.
							state = state.isOf(Blocks.SAND) || state.isOf(Blocks.SANDSTONE) ? Blocks.SANDSTONE.getDefaultState()
									: state.isOf(Blocks.RED_SAND) ? Blocks.RED_SANDSTONE.getDefaultState() : stone;
						}
						if (chunk.getBlockState(pos.set(x, y, z)).isAir()) {
							chunk.setBlockState(pos, state, false);
							placed++;
						}
					}
				}
			}
		}
		return placed;
	}

	/** One island column: lowest and highest block, or null outside the island. */
	public record Column(int bottom, int top) {
	}

	public static Column column(Island island, SimplexNoiseSampler noise, int x, int z) {
		double dx = x + 0.5 - island.x();
		double dz = z + 0.5 - island.z();
		double cos = Math.cos(island.angle());
		double sin = Math.sin(island.angle());
		// Where the column is across the oval: 1 at its (unragged) edge.
		double along = (dx * cos + dz * sin) / island.radius();
		double across = (dz * cos - dx * sin) / island.minor();
		double r = Math.sqrt(along * along + across * across);
		if (r > 1.3) {
			return null;
		}
		double theta = Math.atan2(across, along);
		double size = Math.sqrt(island.radius() * (double) island.minor());
		// A ragged edge: big bays and points, and (on a big island) smaller ones along them.
		double edge = 0.82 + 0.16 * noise.sample(Math.cos(theta) * 1.4, Math.sin(theta) * 1.4)
				+ 0.08 * Math.min(1.0, size / 40.0) * noise.sample(Math.cos(theta) * 4.5 + 30, Math.sin(theta) * 4.5 - 30);
		if (r >= edge) {
			return null;
		}
		double u = r / edge;
		double dome = Math.min(10.0, 2.5 + size * 0.05);
		double hills = Math.min(5.0, size / 25.0) * noise.sample(x / 35.0 + 15, z / 35.0 - 15);
		int top = island.top() + (int) Math.round(dome * (1 - u * u) + 1.2 * noise.sample(x / 9.0 + 40, z / 9.0 - 40) + hills);
		double lumps = (2.5 + Math.min(6.0, size / 20.0) * noise.sample(x / 14.0 - 20, z / 14.0 + 20)) * (1 - u);
		double hang = 2 + (island.depth() - 2) * Math.pow(1 - u, 1.6) + lumps * noise.sample(x / 6.0 - 70, z / 6.0 + 70);
		int depth = Math.max(2, (int) Math.round(hang));
		return new Column(Math.max(LOWEST_BOTTOM, top - depth + 1), top);
	}

	/**
	 * The ground straight below: its top blocks of topsoil, as many as band holds (skipping nothing, so badlands
	 * keep their bands) into band, and returns the stone under it. Over water: sand, then sandstone, over stone.
	 * (Monoliths too.)
	 */
	static BlockState groundBelow(Chunk chunk, BlockPos.Mutable pos, int localX, int localZ, int seaLevel, BlockState[] band) {
		int x = chunk.getPos().getStartX() + localX;
		int z = chunk.getPos().getStartZ() + localZ;
		// From below where any island could hang (no ground reaches that high).
		int top = Math.min(chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, localX, localZ), LOWEST_BOTTOM - 1);
		while (top > chunk.getBottomY() && chunk.getBlockState(pos.set(x, top, z)).isAir()) {
			top--;
		}
		BlockState surface = chunk.getBlockState(pos.set(x, top, z));
		if (!surface.getFluidState().isEmpty()) {
			for (int i = 0; i < band.length; i++) {
				band[i] = i < 3 ? Blocks.SAND.getDefaultState() : Blocks.SANDSTONE.getDefaultState();
			}
			return Blocks.STONE.getDefaultState();
		}
		BlockState stone = chunk.getBlockState(pos.set(x, top - 12, z));
		if (stone.isAir() || !stone.getFluidState().isEmpty() || stone.getBlock() instanceof FallingBlock) {
			stone = Blocks.STONE.getDefaultState();
		}
		for (int i = 0; i < band.length; i++) {
			BlockState below = chunk.getBlockState(pos.set(x, top - i, z));
			band[i] = below.isAir() || !below.getFluidState().isEmpty() ? stone : below;
		}
		return stone;
	}
}
