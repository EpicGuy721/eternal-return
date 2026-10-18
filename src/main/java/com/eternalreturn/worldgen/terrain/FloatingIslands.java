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
 * Rare floating islands high above Eternal Return worlds. Each square of CELL blocks has at most one,
 * placed from the world seed alone, so every chunk an island touches builds its own part and the parts
 * line up whatever order chunks generate in. An island is 10 to 60 blocks across, its top between y 228
 * and y 255, a little domed, with a ragged edge; its underside narrows to a point 10 to 40 blocks below
 * the top. Each column is made of the ground straight below it, copied while the chunk has its terrain and
 * surface but nothing else yet: the topsoil and the biome's stone, so a desert island is sand over
 * sandstone and an autumnal forest island grass over andesite. Over the sea it is a sandy island. The
 * biome's trees, plants and snow then come with the chunk's decoration, as on the ground.
 * Built when the cave carver first sees a chunk, after OverhangRounder (which would otherwise trim it).
 */
public final class FloatingIslands {
	public static final int CELL = 512;
	public static final double CHANCE = 0.6;
	public static final int MIN_TOP = 228;
	private static final int BAND = 6;

	private FloatingIslands() {
	}

	/** An island: centre and radius in blocks, top height, how far its point hangs below the top. */
	public record Island(int x, int z, int radius, int top, int depth, long seed) {
	}

	/** The island of a CELL x CELL square, or null: from the world seed (through the noise config) alone. */
	public static Island inCell(NoiseConfig noiseConfig, int cellX, int cellZ) {
		Random random = noiseConfig.getOrCreateRandomDeriver(EternalReturn.id("floating_islands")).split(cellX, 0, cellZ);
		if (random.nextDouble() >= CHANCE) {
			return null;
		}
		int margin = 48;
		int x = cellX * CELL + margin + random.nextInt(CELL - 2 * margin);
		int z = cellZ * CELL + margin + random.nextInt(CELL - 2 * margin);
		// Radius 5 to 30, small ones commoner.
		int radius = 5 + (int) (25 * Math.pow(random.nextDouble(), 1.6));
		int top = MIN_TOP + random.nextInt(28);
		int depth = MathHelper.clamp((int) Math.round(radius * 1.2 + random.nextInt(10)), 10, 40);
		return new Island(x, z, radius, top, depth, random.nextLong());
	}

	/** The islands that may reach into a chunk. */
	public static List<Island> near(NoiseConfig noiseConfig, ChunkPos chunk) {
		List<Island> islands = new ArrayList<>();
		int reach = 40;
		for (int cellX = Math.floorDiv(chunk.getStartX() - reach, CELL); cellX <= Math.floorDiv(chunk.getEndX() + reach, CELL); cellX++) {
			for (int cellZ = Math.floorDiv(chunk.getStartZ() - reach, CELL); cellZ <= Math.floorDiv(chunk.getEndZ() + reach, CELL); cellZ++) {
				Island island = inCell(noiseConfig, cellX, cellZ);
				if (island != null) {
					islands.add(island);
				}
			}
		}
		return islands;
	}

	/** Builds this chunk's part of every island near it. Returns the number of blocks placed. */
	public static int build(Chunk chunk, NoiseConfig noiseConfig, int seaLevel) {
		int placed = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (Island island : near(noiseConfig, chunk.getPos())) {
			SimplexNoiseSampler noise = new SimplexNoiseSampler(Random.create(island.seed()));
			for (int localX = 0; localX < 16; localX++) {
				for (int localZ = 0; localZ < 16; localZ++) {
					int x = chunk.getPos().getStartX() + localX;
					int z = chunk.getPos().getStartZ() + localZ;
					Column column = column(island, noise, x, z);
					if (column == null) {
						continue;
					}
					BlockState[] band = new BlockState[BAND];
					BlockState stone = groundBelow(chunk, pos, localX, localZ, seaLevel, band);
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
		double r = Math.sqrt(dx * dx + dz * dz);
		if (r > island.radius() * 1.3) {
			return null;
		}
		double angle = Math.atan2(dz, dx);
		double edge = island.radius() * (0.82 + 0.22 * noise.sample(Math.cos(angle) * 1.4, Math.sin(angle) * 1.4));
		if (r >= edge) {
			return null;
		}
		double u = r / edge;
		int top = island.top() + (int) Math.round(2.5 * (1 - u * u) + 1.2 * noise.sample(x / 9.0 + 40, z / 9.0 - 40));
		double hang = 2 + (island.depth() - 2) * Math.pow(1 - u, 1.7) + 2.5 * noise.sample(x / 6.0 - 70, z / 6.0 + 70) * (1 - u);
		int depth = Math.max(2, (int) Math.round(hang));
		return new Column(top - depth + 1, top);
	}

	/**
	 * The ground straight below: its top BAND blocks of topsoil (skipping nothing, so badlands keep their
	 * bands) into band, and returns the stone under it. Over water: sand, then sandstone, over stone.
	 */
	private static BlockState groundBelow(Chunk chunk, BlockPos.Mutable pos, int localX, int localZ, int seaLevel, BlockState[] band) {
		int x = chunk.getPos().getStartX() + localX;
		int z = chunk.getPos().getStartZ() + localZ;
		// From below where any island could hang (no ground reaches that high).
		int top = Math.min(chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, localX, localZ), MIN_TOP - 50);
		while (top > chunk.getBottomY() && chunk.getBlockState(pos.set(x, top, z)).isAir()) {
			top--;
		}
		BlockState surface = chunk.getBlockState(pos.set(x, top, z));
		if (!surface.getFluidState().isEmpty()) {
			for (int i = 0; i < BAND; i++) {
				band[i] = i < 3 ? Blocks.SAND.getDefaultState() : Blocks.SANDSTONE.getDefaultState();
			}
			return Blocks.STONE.getDefaultState();
		}
		BlockState stone = chunk.getBlockState(pos.set(x, top - 12, z));
		if (stone.isAir() || !stone.getFluidState().isEmpty() || stone.getBlock() instanceof FallingBlock) {
			stone = Blocks.STONE.getDefaultState();
		}
		for (int i = 0; i < BAND; i++) {
			BlockState below = chunk.getBlockState(pos.set(x, top - i, z));
			band[i] = below.isAir() || !below.getFluidState().isEmpty() ? stone : below;
		}
		return stone;
	}
}
