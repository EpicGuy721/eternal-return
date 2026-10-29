package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.SnowCover;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** On a block with snow logged in it (SnowCover), the selection box shows the snow or the block, whichever is aimed at. */
@Mixin(WorldRenderer.class)
public abstract class SnowOutlineMixin {
	@WrapOperation(method = "drawBlockOutline", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/BlockState;getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;"))
	private VoxelShape eternalreturn$snowOrBlock(BlockState state, BlockView world, BlockPos pos, ShapeContext context, Operation<VoxelShape> original,
			@Local(argsOnly = true) Entity entity) {
		int layers = SnowCover.layers(world, pos, state);
		if (layers > 0 && entity instanceof PlayerEntity player) {
			return SnowCover.aimsSnow(player, world, pos, state) ? SnowCover.snowOutline(state, layers) : original.call(SnowCover.bare(state), world, pos, context);
		}
		return original.call(state, world, pos, context);
	}
}
