package com.eternalreturn.mixin.survival;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A fully grown crop can stand on grass, dirt, podzol or coarse dirt, as the wild crop patches do (CropPatchFeature),
 * in the light a crop needs. Seeds still need farmland: a crop is planted young, and only a grown one stands elsewhere,
 * so it never grows there. Every world (a block's own rule).
 */
@Mixin(CropBlock.class)
public abstract class GrownCropFootingMixin {
	@Inject(method = "canPlaceAt", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$grownOnGround(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || !((CropBlock) (Object) this).isMature(state)) {
			return;
		}
		BlockState floor = world.getBlockState(pos.down());
		if ((floor.isOf(Blocks.GRASS_BLOCK) || floor.isOf(Blocks.DIRT) || floor.isOf(Blocks.PODZOL) || floor.isOf(Blocks.COARSE_DIRT))
				&& (world.getBaseLightLevel(pos, 0) >= 8 || world.isSkyVisible(pos))) {
			cir.setReturnValue(true);
		}
	}
}
