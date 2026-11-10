package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowyBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Grass, podzol and mycelium under snow logged in what stands on them (SnowCover: a plant, or a fence, wall or gate
 * whose snow lies on the ground around its posts) are snowy, as under a snow layer: set when the snow comes, cleared
 * when it goes, and when such ground is placed under it.
 */
@Mixin(SnowyBlock.class)
public abstract class SnowyGroundMixin {
	@ModifyReturnValue(method = "isSnow", at = @At("RETURN"))
	private static boolean eternalreturn$snowInThePlant(boolean snow, @Local(argsOnly = true) BlockState state) {
		return snow || SnowCover.marked(state) && SnowCover.isFlora(state);
	}

	@ModifyReturnValue(method = "getStateForNeighborUpdate", at = @At("RETURN"))
	private BlockState eternalreturn$snowLoggedAbove(BlockState state, @Local(argsOnly = true) Direction direction,
			@Local(argsOnly = true) WorldAccess world, @Local(ordinal = 0, argsOnly = true) BlockPos pos) {
		if (direction == Direction.UP && state.contains(SnowyBlock.SNOWY) && !state.get(SnowyBlock.SNOWY) && Snowlogging.snowOnGround(world, pos)) {
			return state.with(SnowyBlock.SNOWY, true);
		}
		return state;
	}

	@ModifyReturnValue(method = "getPlacementState", at = @At("RETURN"))
	private BlockState eternalreturn$placedUnderSnow(BlockState state, @Local(argsOnly = true) ItemPlacementContext ctx) {
		if (state != null && state.contains(SnowyBlock.SNOWY) && !state.get(SnowyBlock.SNOWY) && Snowlogging.snowOnGround(ctx.getWorld(), ctx.getBlockPos())) {
			return state.with(SnowyBlock.SNOWY, true);
		}
		return state;
	}
}
