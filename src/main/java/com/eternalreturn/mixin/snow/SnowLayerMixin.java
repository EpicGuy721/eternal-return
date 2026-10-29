package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Snow layers and the blocks that hold snow (SnowCover): a snow layer on a top that stops being whole but still reaches
 * the top in places turns into a snow cover there; snow put on a snow cover thickens it; and a snow layer makes way for
 * a slab, stairs, wall, fence or gate placed into it, which keeps the snow (SnowItemMixin).
 */
@Mixin(SnowBlock.class)
public abstract class SnowLayerMixin {
	@Inject(method = "getStateForNeighborUpdate", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$coverWhatsLeft(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos,
			CallbackInfoReturnable<BlockState> cir) {
		if (SnowCover.isCover(state) || state.canPlaceAt(world, pos)) {
			return;
		}
		BlockState cover = Snowlogging.SNOW_COVER.getDefaultState().with(SnowBlock.LAYERS, state.get(SnowBlock.LAYERS));
		if (cover.canPlaceAt(world, pos)) {
			cir.setReturnValue(cover);
		}
	}

	@Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$thickenCover(ItemPlacementContext context, CallbackInfoReturnable<BlockState> cir) {
		BlockState there = context.getWorld().getBlockState(context.getBlockPos());
		if (SnowCover.isCover(there)) {
			cir.setReturnValue(there.with(SnowBlock.LAYERS, Math.min(SnowBlock.MAX_LAYERS, there.get(SnowBlock.LAYERS) + 1)));
		}
	}

	@Inject(method = "canReplace", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$makeWayForHosts(BlockState state, ItemPlacementContext context, CallbackInfoReturnable<Boolean> cir) {
		if (!SnowCover.isCover(state) && context.getStack().getItem() instanceof BlockItem item && SnowCover.isHost(item.getBlock().getDefaultState())) {
			cir.setReturnValue(true);
		}
	}
}
