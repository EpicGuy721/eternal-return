package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.SnowFall;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Snow blocks have no code of their own (they are plain blocks), and snow layers don't override these:
 * placing, a neighbour changing and the scheduled tick where they fall (SnowFall).
 */
@Mixin(AbstractBlock.class)
public abstract class AbstractBlockSnowMixin {
	@Inject(method = "onBlockAdded", at = @At("HEAD"))
	private void eternalreturn$snowPlaced(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify, CallbackInfo ci) {
		SnowFall.scheduleIfUnsupported(state, world, pos);
	}

	@Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"))
	private void eternalreturn$snowBlockNeighbour(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos,
			CallbackInfoReturnable<BlockState> cir) {
		SnowFall.scheduleIfUnsupported(state, world, pos);
	}

	@Inject(method = "scheduledTick", at = @At("HEAD"))
	private void eternalreturn$snowFalls(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		SnowFall.fall(state, world, pos);
	}
}
