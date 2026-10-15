package com.eternalreturn.mixin.survival;

import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;

/** Nether wart takes bone meal (Growing): a stage older each time. */
@Mixin(NetherWartBlock.class)
public abstract class NetherWartFertilizableMixin implements Fertilizable {
	@Override
	public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
		return state.get(NetherWartBlock.AGE) < NetherWartBlock.MAX_AGE;
	}

	@Override
	public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
		return true;
	}

	@Override
	public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state.with(NetherWartBlock.AGE, Math.min(NetherWartBlock.MAX_AGE, state.get(NetherWartBlock.AGE) + 1)), 2);
	}
}
