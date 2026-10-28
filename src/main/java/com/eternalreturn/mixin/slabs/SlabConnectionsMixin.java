package com.eternalreturn.mixin.slabs;

import com.eternalreturn.blocks.Slabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fences, walls and panes (glass and iron bars) join a vertical slab (Slabs) on its narrow edges as well as its back, as
 * they would a wall; not on its open side.
 */
@Mixin({FenceBlock.class, WallBlock.class, PaneBlock.class})
public abstract class SlabConnectionsMixin {
	@WrapOperation(method = {"getPlacementState", "getStateForNeighborUpdate", "getStateWithNeighbor"}, require = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isSideSolidFullSquare(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z"))
	private boolean eternalreturn$slabEdges(BlockState state, BlockView world, BlockPos pos, Direction face, Operation<Boolean> original) {
		return original.call(state, world, pos, face) || Slabs.joinsAtEdge(state, face);
	}
}
