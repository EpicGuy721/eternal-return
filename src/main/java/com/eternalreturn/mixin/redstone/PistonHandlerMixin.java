package com.eternalreturn.mixin.redstone;

import com.eternalreturn.blocks.ChainLinks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Chains link blocks under pistons (ChainLinks). Vanilla asks two questions while working out what a
 * piston moves: does a moved block drag its neighbours (isBlockSticky), and is a given neighbour stuck to
 * it (isAdjacentBlockStuck). Both are answered here with the chain rules wherever the positions and
 * directions are known.
 */
@Mixin(PistonHandler.class)
public abstract class PistonHandlerMixin {
	@Shadow
	@Final
	private World world;
	@Shadow
	@Final
	private Direction motionDirection;

	@WrapOperation(method = "calculatePush", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/piston/PistonHandler;isBlockSticky(Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$chainPulls(BlockState state, Operation<Boolean> original, @Local BlockPos pos) {
		return ChainLinks.pulls(this.world, pos, state, original.call(state));
	}

	/** The line of blocks dragged behind a moving block, along the motion. */
	@WrapOperation(method = "tryMove", at = @At(value = "INVOKE", ordinal = 0, target = "Lnet/minecraft/block/piston/PistonHandler;isBlockSticky(Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$chainPullsBehind(BlockState state, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos, @Local(ordinal = 0) int i) {
		Direction behind = this.motionDirection.getOpposite();
		return ChainLinks.pullsBehind(this.world, pos.offset(behind, i - 1), state, behind, original.call(state));
	}

	@WrapOperation(method = "tryMove", at = @At(value = "INVOKE", ordinal = 1, target = "Lnet/minecraft/block/piston/PistonHandler;isBlockSticky(Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$chainPullsAhead(BlockState state, Operation<Boolean> original, @Local(ordinal = 2) BlockPos pos) {
		return ChainLinks.pulls(this.world, pos, state, original.call(state));
	}

	@WrapOperation(method = "tryMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/piston/PistonHandler;isAdjacentBlockStuck(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$chainStuckBehind(BlockState current, BlockState behind, Operation<Boolean> original) {
		return ChainLinks.stuck(current, behind, this.motionDirection.getOpposite(), original.call(current, behind));
	}

	@WrapOperation(method = "tryMoveAdjacentBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/piston/PistonHandler;isAdjacentBlockStuck(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;)Z"))
	private boolean eternalreturn$chainStuckBeside(BlockState neighbour, BlockState current, Operation<Boolean> original, @Local Direction direction) {
		return ChainLinks.stuck(current, neighbour, direction, original.call(neighbour, current));
	}
}
