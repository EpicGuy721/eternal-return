package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.SnowBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A snow layer can lie on a slab or stairs whose open space is full of snow (SnowCover), as on a full snow layer. */
@Mixin(SnowBlock.class)
public abstract class SnowLayerSupportMixin {
	@ModifyReturnValue(method = "canPlaceAt", at = @At("RETURN"))
	private boolean eternalreturn$onFullSnow(boolean can, @Local(argsOnly = true) WorldView world, @Local(argsOnly = true) BlockPos pos) {
		return can || SnowCover.full(world.getBlockState(pos.down()));
	}
}
