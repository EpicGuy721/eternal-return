package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.Slabs;
import com.eternalreturn.blocks.SnowCover;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every slab can stand on edge (Slabs.AXIS, flat by default) and takes a different slab into its other half, making a
 * mixed slab (MixedSlabs); placed in an empty space, a slab is vanilla's.
 */
@Mixin(SlabBlock.class)
public abstract class SlabBlockMixin extends Block {
	protected SlabBlockMixin(Settings settings) {
		super(settings);
	}

	@Inject(method = "appendProperties", at = @At("TAIL"))
	private void eternalreturn$axis(StateManager.Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(Slabs.AXIS);
	}

	@Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$verticalShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
		if (state.contains(Slabs.AXIS) && state.get(Slabs.AXIS) != Direction.Axis.Y) {
			cir.setReturnValue(Slabs.shape(state));
		}
	}

	@Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$mixedSlab(ItemPlacementContext context, CallbackInfoReturnable<BlockState> cir) {
		BlockState mixed = MixedSlabs.placementState(context, this);
		if (mixed != null) {
			cir.setReturnValue(SnowCover.settle(mixed, context.getWorld(), context.getBlockPos()));
		}
	}

	/** A slab filled to a pair loses the snow in its open half (snow on the pair's top is the snow layer above it). */
	@Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$filledSnow(ItemPlacementContext context, CallbackInfoReturnable<BlockState> cir) {
		if (cir.getReturnValue() != null) {
			cir.setReturnValue(SnowCover.settle(cir.getReturnValue(), context.getWorld(), context.getBlockPos()));
		}
	}

	@Inject(method = "canReplace", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$fillOtherHalf(BlockState state, ItemPlacementContext context, CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(Slabs.canFill(state, context));
	}

	@Override
	protected BlockState rotate(BlockState state, BlockRotation rotation) {
		return Slabs.rotate(state, rotation);
	}

	@Override
	protected BlockState mirror(BlockState state, BlockMirror mirror) {
		return Slabs.mirror(state, mirror);
	}
}
