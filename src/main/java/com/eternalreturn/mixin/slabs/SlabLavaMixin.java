package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lava sets a mixed slab (MixedSlabs) alight if either half would catch. */
@Mixin(LavaFluid.class)
public abstract class SlabLavaMixin {
	@WrapOperation(method = "hasBurnableBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isBurnable()Z"))
	private boolean eternalreturn$eitherHalf(BlockState state, Operation<Boolean> original, @Local(argsOnly = true) WorldView world,
			@Local(argsOnly = true) BlockPos pos) {
		return state.isOf(MixedSlabs.MIXED_SLAB) ? MixedSlabs.burnable(world, pos, state) : original.call(state);
	}
}
