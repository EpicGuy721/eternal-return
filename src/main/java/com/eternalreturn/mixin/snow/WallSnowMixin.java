package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A wall looks its shapes up by its state, among the states it had before snow (SnowCover): a snowy wall's are its bare self's. */
@Mixin(WallBlock.class)
public abstract class WallSnowMixin {
	@Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$bareOutline(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (SnowCover.raw(state) > 0) {
			cir.setReturnValue(SnowCover.bare(state).getOutlineShape(world, pos, context));
		}
	}

	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$bareCollision(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (SnowCover.raw(state) > 0) {
			cir.setReturnValue(SnowCover.bare(state).getCollisionShape(world, pos, context));
		}
	}
}
