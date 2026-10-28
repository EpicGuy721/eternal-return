package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.MixedSlabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fire sees a mixed slab (MixedSlabs) as its most flammable half: it catches, spreads and burns there at that half's
 * rate, and burning takes only that half, leaving the other. The solid ground fire needs is still the whole block.
 */
@Mixin(FireBlock.class)
public abstract class SlabFireMixin {
	@WrapOperation(method = {"getStateForPosition", "areBlocksAroundFlammable"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/BlockView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$flammableHalf(BlockView world, BlockPos pos, Operation<BlockState> original) {
		return MixedSlabs.fireView(world, pos, original.call(world, pos));
	}

	@WrapOperation(method = "getBurnChance(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)I",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$flammableHalfNearby(WorldView world, BlockPos pos, Operation<BlockState> original) {
		return MixedSlabs.fireView(world, pos, original.call(world, pos));
	}

	/** (Only the third look below a fire: whether it burns on; the others ask about the ground.) */
	@WrapOperation(method = "scheduledTick", at = @At(value = "INVOKE", ordinal = 2,
			target = "Lnet/minecraft/server/world/ServerWorld;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$flammableHalfBelow(ServerWorld world, BlockPos pos, Operation<BlockState> original) {
		return MixedSlabs.fireView(world, pos, original.call(world, pos));
	}

	@WrapOperation(method = "trySpreadingFire", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$burningHalf(World world, BlockPos pos, Operation<BlockState> original) {
		return MixedSlabs.fireView(world, pos, original.call(world, pos));
	}

	@WrapOperation(method = "trySpreadingFire", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
	private boolean eternalreturn$burnHalfToFire(World world, BlockPos pos, BlockState fire, int flags, Operation<Boolean> original) {
		return eternalreturn$burnHalf(world, pos) || original.call(world, pos, fire, flags);
	}

	@WrapOperation(method = "trySpreadingFire", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;removeBlock(Lnet/minecraft/util/math/BlockPos;Z)Z"))
	private boolean eternalreturn$burnHalfAway(World world, BlockPos pos, boolean move, Operation<Boolean> original) {
		return eternalreturn$burnHalf(world, pos) || original.call(world, pos, move);
	}

	/** A mixed slab burning loses its flammable half (the other is left where it was). */
	@Unique
	private static boolean eternalreturn$burnHalf(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!state.isOf(MixedSlabs.MIXED_SLAB)) {
			return false;
		}
		BlockState burnt = MixedSlabs.fireView(world, pos, state);
		BlockState left = burnt == state ? null : MixedSlabs.otherHalf(world, pos, state, burnt);
		return left != null && world.setBlockState(pos, left, Block.NOTIFY_ALL);
	}
}
