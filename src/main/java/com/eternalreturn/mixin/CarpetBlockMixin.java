package com.eternalreturn.mixin;

import com.eternalreturn.blocks.CarpetLayers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carpets stack like snow layers, up to 16 (CarpetLayers). */
@Mixin(CarpetBlock.class)
public abstract class CarpetBlockMixin extends Block {
	protected CarpetBlockMixin(Settings settings) {
		super(settings);
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(CarpetLayers.LAYERS);
	}

	@Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$layeredShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context, CallbackInfoReturnable<VoxelShape> cir) {
		cir.setReturnValue(CarpetLayers.shape(state.get(CarpetLayers.LAYERS)));
	}

	/** The same carpet can be placed into a carpet that isn't full: on its top face, or anywhere when aimed at a neighbour. */
	@Override
	protected boolean canReplace(BlockState state, ItemPlacementContext context) {
		if (context.getStack().isOf(this.asItem()) && state.get(CarpetLayers.LAYERS) < CarpetLayers.MAX_LAYERS) {
			return !context.canReplaceExisting() || context.getSide() == Direction.UP;
		}
		return super.canReplace(state, context);
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext context) {
		BlockState existing = context.getWorld().getBlockState(context.getBlockPos());
		if (existing.isOf(this)) {
			return existing.with(CarpetLayers.LAYERS, Math.min(CarpetLayers.MAX_LAYERS, existing.get(CarpetLayers.LAYERS) + 1));
		}
		return super.getPlacementState(context);
	}
}
