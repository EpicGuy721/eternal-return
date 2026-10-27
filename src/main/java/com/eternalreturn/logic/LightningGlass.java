package com.eternalreturn.logic;

import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldEvents;

/**
 * Lightning striking sand (LightningGlassMixin) fuses it: the struck block becomes glass, and so does about half of the
 * sand around and below it, a block out and two down.
 */
public final class LightningGlass {
	private LightningGlass() {
	}

	public static void strike(ServerWorld world, BlockPos hit, Random random) {
		if (!world.getBlockState(hit).isIn(BlockTags.SAND)) {
			return;
		}
		fuse(world, hit);
		for (BlockPos pos : BlockPos.iterate(hit.add(-1, -2, -1), hit.add(1, 0, 1))) {
			if (!pos.equals(hit) && random.nextBoolean() && world.getBlockState(pos).isIn(BlockTags.SAND)) {
				fuse(world, pos.toImmutable());
			}
		}
	}

	private static void fuse(ServerWorld world, BlockPos pos) {
		world.setBlockState(pos, Blocks.GLASS.getDefaultState());
		world.syncWorldEvent(WorldEvents.LAVA_EXTINGUISHED, pos, 0);
	}
}
