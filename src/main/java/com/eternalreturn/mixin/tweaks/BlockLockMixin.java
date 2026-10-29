package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.logic.BlockLocks;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A locked block (BlockLocks) ignores its neighbours' changes (but for snow on it) and its own ticks. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockLockMixin {
	@Shadow
	protected abstract BlockState asBlockState();

	@Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$keepShape(Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos,
			CallbackInfoReturnable<BlockState> cir) {
		// Snow on it (SnowCover) still comes and goes.
		BlockState kept = cir.getReturnValue().isOf(this.asBlockState().getBlock()) ? SnowCover.withSnowOf(this.asBlockState(), cir.getReturnValue()) : this.asBlockState();
		if (cir.getReturnValue() != kept && BlockLocks.isLocked(world, pos)) {
			cir.setReturnValue(kept);
			// A player's game may have guessed the change itself (breaking the block beside it): set it right.
			if (world instanceof ServerWorld server) {
				server.getChunkManager().markForUpdate(pos);
			}
		}
	}

	@Inject(method = "neighborUpdate", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$ignoreNeighbours(World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify, CallbackInfo ci) {
		if (BlockLocks.isLocked(world, pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noRandomTicks(ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (BlockLocks.isLocked(world, pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "scheduledTick", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noScheduledTicks(ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (BlockLocks.isLocked(world, pos)) {
			ci.cancel();
		}
	}
}
