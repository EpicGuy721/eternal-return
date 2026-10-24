package com.eternalreturn.worldgen;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;

/**
 * Snow on trees at chunk borders (MC-119599; worldgen.snowOnBorderTrees), as Simple Snowy Fix does. A chunk is snowed on
 * (vanilla's freeze_top_layer) before the trees of the chunks around it grow, so a tree that reaches over from a
 * neighbour lands on snowy ground with bare leaves. After each tree grows in the world's generation (TreeSnowMixin),
 * every column it may cover gets what vanilla gives a tree within one chunk: snow on its top leaves where the biome
 * snows, and no snow (nor snowy grass) on the ground under them. Columns not yet snowed on are left to vanilla, which
 * does the same when their turn comes.
 */
public final class SnowyTrees {
	/** How far a tree's leaves can reach from where it grows. */
	private static final int REACH = 8;

	private SnowyTrees() {
	}

	public static void afterTree(StructureWorldAccess world, BlockPos origin) {
		BlockPos.Mutable top = new BlockPos.Mutable();
		BlockPos.Mutable below = new BlockPos.Mutable();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					continue;
				}
				top.set(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z);
				BlockState leaves = world.getBlockState(below.set(top, 0, -1, 0));
				if (!leaves.isIn(BlockTags.LEAVES)) {
					continue;
				}
				Biome biome = world.getBiome(top).value();
				if (!biome.canSetSnow(world, top)) {
					continue;
				}
				// Under the leaves to the ground: a snow layer there was put down before the tree came.
				below.move(0, -1, 0);
				while (below.getY() > world.getBottomY() && (world.getBlockState(below).isAir() || world.getBlockState(below).isIn(BlockTags.LEAVES)
						|| world.getBlockState(below).isIn(BlockTags.LOGS))) {
					below.move(0, -1, 0);
				}
				if (world.getBlockState(below).isOf(Blocks.SNOW)) {
					world.setBlockState(below, Blocks.AIR.getDefaultState(), 2);
					BlockState ground = world.getBlockState(below.move(0, -1, 0));
					if (ground.contains(Properties.SNOWY)) {
						world.setBlockState(below, ground.with(Properties.SNOWY, false), 2);
					}
				} else {
					// Nothing snowed on this column yet: vanilla will, in its turn.
					continue;
				}
				world.setBlockState(top, Blocks.SNOW.getDefaultState(), 2);
			}
		}
	}
}
