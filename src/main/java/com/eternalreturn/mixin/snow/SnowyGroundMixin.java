package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.SnowyBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Grass, podzol and mycelium under a plant with snow logged in it (SnowCover) are snowy, as under a snow layer. */
@Mixin(SnowyBlock.class)
public abstract class SnowyGroundMixin {
	@ModifyReturnValue(method = "isSnow", at = @At("RETURN"))
	private static boolean eternalreturn$snowInThePlant(boolean snow, @Local(argsOnly = true) BlockState state) {
		return snow || SnowCover.marked(state) && SnowCover.isFlora(state);
	}
}
