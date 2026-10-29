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

/**
 * What a slab or stairs hides of its neighbours, and what it collides with, is its own shape, never with the snow logged
 * in it (SnowCover), whose outline the world adds: vanilla's culling and collision shapes would take the outline as it
 * is, snow and all (the snow collides a layer lower, added by SnowStateMixin). Walls: WallSnowMixin.
 */
@Mixin({SlabBlock.class, StairsBlock.class})
public abstract class SnowShapeMixin extends Block {
	protected SnowShapeMixin(Settings settings) {
		super(settings);
	}

	@Override
	protected VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
		return this.getOutlineShape(SnowCover.bare(state), world, pos, ShapeContext.absent());
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return this.collidable ? this.getOutlineShape(SnowCover.bare(state), world, pos, context) : VoxelShapes.empty();
	}
}
