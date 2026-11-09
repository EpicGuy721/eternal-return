package com.eternalreturn.mixin.survival;

import com.eternalreturn.worldgen.cactus.CactusShapes;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.Fertilizable;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Cactus takes bone meal (Growing): a block taller, up to 3 high; in a biome with a cactus shape (CactusShapes, Eternal
 * Return worlds), the next block of its shape instead, up to the shape's full size.
 */
@Mixin(CactusBlock.class)
public abstract class CactusBlockFertilizableMixin implements Fertilizable {
	private static BlockPos eternalreturn$top(WorldView world, BlockPos pos) {
		BlockPos top = pos;
		while (world.getBlockState(top.up()).isOf(Blocks.CACTUS)) {
			top = top.up();
		}
		return top;
	}

	private static int eternalreturn$height(WorldView world, BlockPos top) {
		int height = 1;
		while (world.getBlockState(top.down(height)).isOf(Blocks.CACTUS)) {
			height++;
		}
		return height;
	}

	@Override
	public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
		CactusShapes.Entry entry = CactusShapes.entryFor(world.getBiome(pos));
		if (entry != null) {
			BlockPos root = CactusShapes.root(world, pos);
			return root != null && CactusShapes.next(world, root, entry.shape()) != null;
		}
		BlockPos top = eternalreturn$top(world, pos);
		return eternalreturn$height(world, top) < 3 && world.isAir(top.up()) && Blocks.CACTUS.getDefaultState().canPlaceAt(world, top.up());
	}

	@Override
	public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
		return true;
	}

	@Override
	public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
		CactusShapes.Entry entry = CactusShapes.entryFor(world.getBiome(pos));
		if (entry != null) {
			BlockPos root = CactusShapes.root(world, pos);
			if (root != null) {
				CactusShapes.growNext(world, root, entry.shape());
			}
			return;
		}
		BlockPos top = eternalreturn$top(world, pos);
		if (world.isAir(top.up())) {
			world.setBlockState(top.up(), Blocks.CACTUS.getDefaultState());
		}
	}
}
