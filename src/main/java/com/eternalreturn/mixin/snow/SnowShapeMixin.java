package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Snow in a slab's or stairs' open space (SnowCover) can be aimed at and stood on, as snow layers can; but what the block
 * hides of its neighbours, how much light it lets through and what can stand against its sides stay the bare block's.
 */
@Mixin({SlabBlock.class, StairsBlock.class})
public abstract class SnowShapeMixin extends Block {
	protected SnowShapeMixin(Settings settings) {
		super(settings);
	}

	@Inject(method = "getOutlineShape", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$withSnow(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
		cir.setReturnValue(SnowCover.withOutline(state, cir.getReturnValue()));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return this.collidable ? SnowCover.withCollision(state, SnowCover.bare(state).getOutlineShape(world, pos)) : VoxelShapes.empty();
	}

	@Override
	protected VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
		return SnowCover.bare(state).getOutlineShape(world, pos);
	}

	@Override
	protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
		return super.isTransparent(SnowCover.bare(state), world, pos);
	}

	@Override
	protected VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
		return this.collidable ? SnowCover.bare(state).getOutlineShape(world, pos) : VoxelShapes.empty();
	}
}
