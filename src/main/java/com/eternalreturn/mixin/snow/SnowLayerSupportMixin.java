package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A snow layer can lie on a block whose top, with the snow logged in it, is whole (SnowCover): a slab full of snow. */
@Mixin(SnowBlock.class)
public abstract class SnowLayerSupportMixin {
	@ModifyReturnValue(method = "canPlaceAt", at = @At("RETURN"))
	private boolean eternalreturn$onWholeSnow(boolean can, @Local(argsOnly = true) WorldView world, @Local(argsOnly = true) BlockPos pos) {
		if (can) {
			return true;
		}
		BlockState below = world.getBlockState(pos.down());
		return SnowCover.isHost(below) && SnowCover.whole(below, SnowCover.layers(world, pos.down(), below));
	}
}
