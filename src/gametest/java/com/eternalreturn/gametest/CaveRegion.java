package com.eternalreturn.gametest;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A square of chunks generated up to the carving step (no features yet, so every cave block, lava
 * block and gap is the carvers' doing), with a one-chunk ring around it so borders can be checked.
 * Measures what the cave tests need. Blocks are read from the chunks directly: asking the world
 * would generate them in full.
 */
public final class CaveRegion {
	/**
	 * A land square (seed 173164: 99% land, centred near -1280, -1152) used for density numbers and
	 * the cave slices, 24 chunks across. Spawn is mostly ocean in this seed.
	 */
	public static final int LAND_CHUNK_X0 = -92;
	public static final int LAND_CHUNK_Z0 = -84;
	public static final int LAND_SIZE = 24;
	public final ServerWorld world;
	public final int chunkX0;
	public final int chunkZ0;
	public final int size;
	private final Map<Long, Chunk> chunks = new HashMap<>();
	private final BlockPos.Mutable pos = new BlockPos.Mutable();

	// Measured over the inner square.
	public long caveAir;
	public long carvedLava;
	public long undergroundVolume;
	/** Air of any kind, or lava at the lava level, below the surface: comparable across carvers (Moderner Beta's carve plain air). */
	public long openBelowSurface;
	public long caveAirAtOrBelowLavaLevel;
	public long caveAirTooHigh;
	public long lavaAboveLavaLevel;
	public long bedrockFloorGaps;
	public long breachesIntoOpenWater;
	public long touchingOtherWater;
	public long borderPairs;
	public long borderMismatches;
	public long interiorPairs;
	public long interiorMismatches;
	public final Map<Integer, long[]> bands = new LinkedHashMap<>();
	public long generationNanos;

	/** Generates the square (and its ring) in row order, leaving out skip, which is generated last if given. */
	public CaveRegion(ServerWorld world, int chunkX0, int chunkZ0, int size, ChunkPos last) {
		this.world = world;
		this.chunkX0 = chunkX0;
		this.chunkZ0 = chunkZ0;
		this.size = size;
		long started = System.nanoTime();
		for (int cz = chunkZ0 - 1; cz <= chunkZ0 + size; cz++) {
			for (int cx = chunkX0 - 1; cx <= chunkX0 + size; cx++) {
				if (last != null && last.x == cx && last.z == cz) {
					continue;
				}
				this.chunks.put(ChunkPos.toLong(cx, cz), world.getChunk(cx, cz, ChunkStatus.CARVERS, true));
			}
		}
		if (last != null) {
			this.chunks.put(last.toLong(), world.getChunk(last.x, last.z, ChunkStatus.CARVERS, true));
		}
		this.generationNanos = System.nanoTime() - started;
	}

	public int chunkCount() {
		return (this.size + 2) * (this.size + 2);
	}

	public BlockState at(int x, int y, int z) {
		Chunk chunk = this.chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
		return chunk == null ? Blocks.VOID_AIR.getDefaultState() : chunk.getBlockState(this.pos.set(x, y, z));
	}

	private static boolean isCave(BlockState state, int y, int lavaY) {
		return state.isOf(Blocks.CAVE_AIR) || (state.isOf(Blocks.LAVA) && y <= lavaY);
	}

	/** Open fraction of the underground volume (below the generator's surface height). */
	public double openFraction() {
		return this.undergroundVolume == 0 ? 0 : (double) this.openBelowSurface / this.undergroundVolume;
	}

	public double borderMismatchRate() {
		return this.borderPairs == 0 ? 0 : (double) this.borderMismatches / this.borderPairs;
	}

	public double interiorMismatchRate() {
		return this.interiorPairs == 0 ? 0 : (double) this.interiorMismatches / this.interiorPairs;
	}

	/**
	 * Counts everything over the inner square. lavaY is the carvers' lava level. Seam pairs compare cave
	 * presence in neighbouring columns across a chunk border (x 15 to 16) and inside a chunk (x 7 to 8),
	 * along both axes, between y minY and seamTopY.
	 */
	public CaveRegion measure(int lavaY, int seamTopY) {
		ChunkGenerator generator = this.world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = this.world.getChunkManager().getNoiseConfig();
		int bottom = this.world.getBottomY();
		int top = this.world.getTopY();
		int seaLevel = generator.getSeaLevel();
		int x0 = this.chunkX0 * 16;
		int z0 = this.chunkZ0 * 16;
		int x1 = x0 + this.size * 16;
		int z1 = z0 + this.size * 16;
		for (int x = x0; x < x1; x++) {
			for (int z = z0; z < z1; z++) {
				int surface = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, this.world, noise);
				if (!this.at(x, bottom, z).isOf(Blocks.BEDROCK)) {
					this.bedrockFloorGaps++;
				}
				for (int y = bottom + 1; y < top; y++) {
					BlockState state = this.at(x, y, z);
					boolean below = y < surface;
					long[] band = this.bands.computeIfAbsent(Math.floorDiv(y, 16) * 16, key -> new long[2]);
					boolean lava = state.isOf(Blocks.LAVA);
					if (below) {
						this.undergroundVolume++;
						band[1]++;
						if (state.isAir() || (lava && y <= lavaY)) {
							this.openBelowSurface++;
							band[0]++;
						}
					}
					if (lava) {
						if (y > lavaY) {
							this.lavaAboveLavaLevel++;
						} else {
							this.carvedLava++;
						}
					}
					if (!state.isOf(Blocks.CAVE_AIR)) {
						continue;
					}
					this.caveAir++;
					if (y <= lavaY) {
						this.caveAirAtOrBelowLavaLevel++;
					}
					if (y >= top - 8) {
						this.caveAirTooHigh++;
					}
					this.checkWater(x + 1, y, z, seaLevel);
					this.checkWater(x - 1, y, z, seaLevel);
					this.checkWater(x, y, z + 1, seaLevel);
					this.checkWater(x, y, z - 1, seaLevel);
					this.checkWater(x, y + 1, z, seaLevel);
					this.checkWater(x, y - 1, z, seaLevel);
				}
			}
		}
		// Seams, along x then along z.
		for (int axis = 0; axis < 2; axis++) {
			for (int along = (axis == 0 ? z0 : x0); along < (axis == 0 ? z1 : x1); along++) {
				for (int chunk = 0; chunk < this.size; chunk++) {
					int base = (axis == 0 ? x0 : z0) + chunk * 16;
					for (int y = bottom + 1; y < seamTopY; y++) {
						this.interiorPairs++;
						if (this.caveAtAxis(axis, base + 7, along, y, lavaY) != this.caveAtAxis(axis, base + 8, along, y, lavaY)) {
							this.interiorMismatches++;
						}
						if (chunk < this.size - 1) {
							this.borderPairs++;
							if (this.caveAtAxis(axis, base + 15, along, y, lavaY) != this.caveAtAxis(axis, base + 16, along, y, lavaY)) {
								this.borderMismatches++;
							}
						}
					}
				}
			}
		}
		return this;
	}

	private boolean caveAtAxis(int axis, int a, int along, int y, int lavaY) {
		return axis == 0 ? isCave(this.at(a, y, along), y, lavaY) : isCave(this.at(along, y, a), y, lavaY);
	}

	/** Water next to a cave block: "open" when water fills its column from there up to sea level (an ocean, river or lake). */
	private void checkWater(int x, int y, int z, int seaLevel) {
		if (!this.at(x, y, z).getFluidState().isIn(FluidTags.WATER)) {
			return;
		}
		for (int above = y + 1; above < seaLevel; above++) {
			if (!this.at(x, above, z).getFluidState().isIn(FluidTags.WATER)) {
				this.touchingOtherWater++;
				return;
			}
		}
		this.breachesIntoOpenWater++;
	}

	/** The chunk as generated in this region (at the carving step). */
	public Chunk chunk(int cx, int cz) {
		return this.chunks.get(ChunkPos.toLong(cx, cz));
	}

	/** "y: open%" per 16-block band, for the log. */
	public String bandSummary() {
		StringBuilder out = new StringBuilder();
		this.bands.forEach((y, counts) -> {
			if (counts[1] > 0) {
				out.append(y).append(':').append(String.format("%.1f%%", 100.0 * counts[0] / counts[1])).append(' ');
			}
		});
		return out.toString().trim();
	}
}
