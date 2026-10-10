package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.SnowFall;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A snow layer with nothing under it falls (SnowFall) instead of vanishing. */
@Mixin(SnowBlock.class)
public abstract class SnowBlockMixin {
	@Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$fall(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos,
			CallbackInfoReturnable<BlockState> cir) {
		if (SnowFall.scheduleIfUnsupported(state, world, pos)) {
			cir.setReturnValue(state);
		}
	}
}
