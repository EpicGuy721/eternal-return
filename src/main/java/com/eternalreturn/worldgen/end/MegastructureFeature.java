package com.eternalreturn.worldgen.end;

import com.mojang.serialization.Codec;
import net.minecraft.block.AmethystClusterBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Builds one chunk's part of every megastructure near it (Megastructures), once per chunk, in Eternal Return's End
 * only (anywhere else it does nothing). It writes into its own chunk only, so the parts of a structure line up whatever
 * order chunks are decorated in. It runs before the End's chorus plants and return gateways (so those grow and sit on
 * top of it, never inside it) and replaces only air and end stone.
 */
public final class MegastructureFeature extends Feature<DefaultFeatureConfig> {
	private static final Direction[] DIRECTIONS = Direction.values();

	public MegastructureFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		Megastructures megastructures = Megastructures.of(world.toServerWorld());
		if (megastructures == null) {
			return false;
		}
		ChunkPos chunkPos = new ChunkPos(context.getOrigin());
		boolean placed = false;
		for (Megastructures.Structure structure : megastructures.near(chunkPos)) {
			placed |= build(world.getChunk(chunkPos.x, chunkPos.z), chunkPos, megastructures, structure) > 0;
		}
		return placed;
	}

	/** Builds a structure's part in a chunk. Returns the number of blocks placed. */
	public static int build(Chunk chunk, ChunkPos chunkPos, Megastructures megastructures, Megastructures.Structure s) {
		// The structure over this chunk and one block round it (for the faces amethyst grows on), worked out once.
		int x0 = Math.max(chunkPos.getStartX() - 1, s.minX), x1 = Math.min(chunkPos.getEndX() + 1, s.maxX);
		int z0 = Math.max(chunkPos.getStartZ() - 1, s.minZ), z1 = Math.min(chunkPos.getEndZ() + 1, s.maxZ);
		int y0 = Math.max(chunk.getBottomY() + 1, s.minY), y1 = Math.min(chunk.getTopY() - 2, s.maxY);
		if (x0 > x1 || z0 > z1 || y0 > y1) {
			return 0;
		}
		int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
		byte[] grid = new byte[sx * sy * sz];
		double[] scratch = new double[3];
		boolean any = false;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					int value = megastructures.sample(s, x, y, z, scratch);
					grid[((x - x0) * sz + (z - z0)) * sy + (y - y0)] = (byte) value;
					any |= value != Megastructures.EMPTY;
				}
			}
		}
		if (!any) {
			return 0;
		}
		BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
		BlockState crying = Blocks.CRYING_OBSIDIAN.getDefaultState();
		double amethyst = megastructures.settings().amethyst();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int placed = 0;
		int cx0 = Math.max(x0, chunkPos.getStartX()), cx1 = Math.min(x1, chunkPos.getEndX());
		int cz0 = Math.max(z0, chunkPos.getStartZ()), cz1 = Math.min(z1, chunkPos.getEndZ());
		for (int x = cx0; x <= cx1; x++) {
			for (int z = cz0; z <= cz1; z++) {
				for (int y = y0; y <= y1; y++) {
					int value = grid[((x - x0) * sz + (z - z0)) * sy + (y - y0)];
					BlockState at = chunk.getBlockState(pos.set(x, y, z));
					if ((value & 3) != Megastructures.EMPTY) {
						if (at.isAir() || at.isOf(Blocks.END_STONE)) {
							chunk.setBlockState(pos, (value & 3) == Megastructures.CRYING ? crying : obsidian, false);
							placed++;
						}
						continue;
					}
					// Amethyst grows into open air from a broken face next to it.
					if (amethyst <= 0 || !at.isAir()) {
						continue;
					}
					for (Direction direction : DIRECTIONS) {
						int nx = x - direction.getOffsetX(), ny = y - direction.getOffsetY(), nz = z - direction.getOffsetZ();
						if (nx < x0 || nx > x1 || ny < y0 || ny > y1 || nz < z0 || nz > z1) {
							continue;
						}
						int neighbour = grid[((nx - x0) * sz + (nz - z0)) * sy + (ny - y0)];
						if ((neighbour & 3) == Megastructures.EMPTY || (neighbour & Megastructures.DAMAGED) == 0) {
							continue;
						}
						double roll = megastructures.hash(x + 0.5, y + 0.5 + direction.ordinal() * 1000, z + 0.5);
						if (roll < amethyst * 0.22) {
							chunk.setBlockState(pos, bud(roll / (amethyst * 0.22)).with(AmethystClusterBlock.FACING, direction), false);
							placed++;
						}
						break;
					}
				}
			}
		}
		return placed;
	}

	/** Mostly full clusters, then large, medium and small buds. */
	private static BlockState bud(double roll) {
		if (roll < 0.4) return Blocks.AMETHYST_CLUSTER.getDefaultState();
		if (roll < 0.65) return Blocks.LARGE_AMETHYST_BUD.getDefaultState();
		if (roll < 0.85) return Blocks.MEDIUM_AMETHYST_BUD.getDefaultState();
		return Blocks.SMALL_AMETHYST_BUD.getDefaultState();
	}
}
