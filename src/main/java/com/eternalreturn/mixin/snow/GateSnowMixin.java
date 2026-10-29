package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Opening a gate with snow on it (SnowCover), by hand, redstone or a blast, knocks the snow off. */
@Mixin(FenceGateBlock.class)
public abstract class GateSnowMixin {
	@WrapOperation(method = "onUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean eternalreturn$openedByHand(World world, BlockPos pos, BlockState state, int flags, Operation<Boolean> original,
			@Local(argsOnly = true) PlayerEntity player) {
		return original.call(world, pos, SnowCover.gateMoved(world, pos, state, player), flags);
	}

	@WrapOperation(method = "neighborUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean eternalreturn$openedByRedstone(World world, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
		return original.call(world, pos, SnowCover.gateMoved(world, pos, state, null), flags);
	}

	@WrapOperation(method = "onExploded", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$openedByABlast(World world, BlockPos pos, BlockState state, Operation<Boolean> original) {
		return original.call(world, pos, SnowCover.gateMoved(world, pos, state, null));
	}
}
