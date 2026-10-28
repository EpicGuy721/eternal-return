package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player breaking a mixed slab breaks the half aimed at (MixedSlabBlock.onBreak says which) and leaves the other; the
 * tool wear, the drops and the stat that follow are the broken half's.
 */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class MixedSlabBreakMixin {
	@WrapOperation(method = "tryBreakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;removeBlock(Lnet/minecraft/util/math/BlockPos;Z)Z"))
	private boolean eternalreturn$leaveTheOtherHalf(ServerWorld world, BlockPos pos, boolean move, Operation<Boolean> original,
			@Local(ordinal = 0) BlockState one, @Local(ordinal = 1) BlockState other) {
		// The block as it was and the state onBreak gave back (the half), in whichever order they come.
		BlockState state = world.getBlockState(pos);
		BlockState broken = one.isOf(MixedSlabs.MIXED_SLAB) ? other : one;
		if (state.isOf(MixedSlabs.MIXED_SLAB) && !broken.isOf(MixedSlabs.MIXED_SLAB) && MixedSlabs.removeHalf(world, pos, state, broken)) {
			return true;
		}
		return original.call(world, pos, move);
	}
}
