package com.eternalreturn.worldgen.terrain;

import com.eternalreturn.EternalReturn;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Rounds the undersides of terrain overhangs in Eternal Return worlds. Moderner Beta works out the
 * terrain's density only every 8 blocks of height and blends straight between, so where rock hangs over
 * open air its underside comes out as a near-flat plane, often 10 to 20 blocks across. Here, before caves
 * are carved, each such underside moves by a few blocks following smooth 3D noise: it arches up into
 * the rock by 0 to 6 blocks, and where there are at least 6 blocks of air below, rock hangs lower in
 * places (up to 3 blocks). It never cuts closer than 3 blocks to the top of the overhang, never leaves
 * less than 3 blocks of open air under it, and only touches open air (not water) and the overhang's own rock. Each column is handled from its own blocks and the world
 * seed's noise alone, so the result matches across chunk borders whatever order chunks generate in.
 */
public final class OverhangRounder {
	/** The most an underside arches up into the rock, in blocks. */
	private static final int MAX_RAISE = 6;
	/** The most rock hangs below the old underside, in blocks. */
	private static final int MAX_HANG = 3;
	/** Air needed under an underside before rock may hang lower there. */
	private static final int HANG_ROOM = 6;
	/** Rock left above a raised underside, at least. */
	private static final int KEEP_ROCK = 3;
	/** Open air left under a lowered underside, at least. */
	private static final int KEEP_AIR = 3;

	private OverhangRounder() {
	}

	/** Rounds the overhangs in this chunk, which has its terrain and surface but no caves yet. Returns the number of blocks changed. */
	public static int round(Chunk chunk, NoiseConfig noiseConfig, int seaLevel) {
		SimplexNoiseSampler noise = new SimplexNoiseSampler(noiseConfig.getOrCreateRandomDeriver(EternalReturn.id("overhangs")).split(0, 0, 0));
		ChunkPos chunkPos = chunk.getPos();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int changed = 0;
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int x = chunkPos.getStartX() + localX;
				int z = chunkPos.getStartZ() + localZ;
				int top = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, localX, localZ);
				int rockTop = Integer.MIN_VALUE;
				for (int y = top; y > seaLevel; y--) {
					BlockState state = chunk.getBlockState(pos.set(x, y, z));
					if (!isRock(state)) {
						rockTop = Integer.MIN_VALUE;
						continue;
					}
					if (rockTop == Integer.MIN_VALUE) {
						rockTop = y;
					}
					if (!chunk.getBlockState(pos.set(x, y - 1, z)).isOf(Blocks.AIR)) {
						continue;
					}
					// y is an underside: rock from rockTop down to y, open air below.
					int gap = 0;
					while (y - 1 - gap > chunk.getBottomY() && chunk.getBlockState(pos.set(x, y - 1 - gap, z)).isOf(Blocks.AIR)) {
						gap++;
					}
					if (chunk.getBlockState(pos.set(x, y - 1 - gap, z)).isAir()) {
						// Open to the void (or cave air already): leave it.
						y -= gap;
						rockTop = Integer.MIN_VALUE;
						continue;
					}
					int shift = shift(noise, x, y, z, gap >= HANG_ROOM);
					if (shift > 0) {
						changed += raise(chunk, pos, x, y, z, Math.min(shift, rockTop - y + 1 - KEEP_ROCK));
					} else if (shift < 0) {
						changed += lower(chunk, pos, x, y, z, Math.min(-shift, gap - KEEP_AIR));
					}
					// Carry on below the gap, for overhangs stacked under this one.
					y -= gap;
					rockTop = Integer.MIN_VALUE;
				}
			}
		}
		return changed;
	}

	/** How far this underside moves: positive up into the rock, negative down into the air (only when it may hang). */
	static int shift(SimplexNoiseSampler noise, int x, int y, int z, boolean mayHang) {
		// Each sum is about -0.75 to 0.75.
		double up = 0.8 * noise.sample(x / 10.0, y / 10.0, z / 10.0) + 0.2 * noise.sample(x / 5.0 + 17.3, y / 5.0, z / 5.0 - 41.9);
		int raise = (int) Math.round(MathHelper.clamp((up / 0.75 + 1) / 2, 0, 1) * MAX_RAISE);
		if (!mayHang) {
			return raise;
		}
		double down = noise.sample(x / 8.0 - 63.1, y / 8.0 + 9.7, z / 8.0 + 28.4) / 0.75;
		int hang = (int) Math.round(MathHelper.clamp((down - 0.3) / 0.7, 0, 1) * MAX_HANG * 2);
		return raise - hang;
	}

	/** Opens the bottom blocks of the rock above y, stopping before a block that would be left hanging with nothing to hold it. */
	private static int raise(Chunk chunk, BlockPos.Mutable pos, int x, int y, int z, int blocks) {
		while (blocks > 0 && chunk.getBlockState(pos.set(x, y + blocks, z)).getBlock() instanceof FallingBlock) {
			blocks--;
		}
		for (int i = 0; i < blocks; i++) {
			chunk.setBlockState(pos.set(x, y + i, z), Blocks.AIR.getDefaultState(), false);
		}
		return Math.max(0, blocks);
	}

	/** Hangs rock below y: the underside's own stone, or the first solid stone above it when that is soil or sand. */
	private static int lower(Chunk chunk, BlockPos.Mutable pos, int x, int y, int z, int blocks) {
		if (blocks <= 0) {
			return 0;
		}
		BlockState fill = null;
		for (int up = y; up < y + 8 && fill == null; up++) {
			BlockState state = chunk.getBlockState(pos.set(x, up, z));
			if (!isRock(state)) {
				break;
			}
			if (!(state.getBlock() instanceof FallingBlock) && !state.isOf(Blocks.GRASS_BLOCK) && !state.isOf(Blocks.MYCELIUM) && !state.isOf(Blocks.PODZOL)) {
				fill = state;
			}
		}
		if (fill == null) {
			return 0;
		}
		for (int i = 1; i <= blocks; i++) {
			chunk.setBlockState(pos.set(x, y - i, z), fill, false);
		}
		return blocks;
	}

	/** Solid terrain: anything but air and fluids (at this stage there are no plants or trees yet). */
	private static boolean isRock(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty();
	}
}
