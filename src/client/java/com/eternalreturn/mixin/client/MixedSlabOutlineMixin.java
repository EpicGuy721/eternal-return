package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.MixedSlabs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * On a mixed slab (MixedSlabs), the selection box and the cracks of the player's own mining show only the half aimed at,
 * the one that breaks.
 */
@Mixin(WorldRenderer.class)
public abstract class MixedSlabOutlineMixin {
	@WrapOperation(method = "drawBlockOutline", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/BlockState;getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;"))
	private VoxelShape eternalreturn$aimedHalfOutline(BlockState state, BlockView world, BlockPos pos, ShapeContext context, Operation<VoxelShape> original,
			@Local(argsOnly = true) Entity entity) {
		if (state.isOf(MixedSlabs.MIXED_SLAB) && entity instanceof PlayerEntity player) {
			return original.call(MixedSlabs.aimedHalf(player, world, pos, state), world, pos, context);
		}
		return original.call(state, world, pos, context);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderDamage(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"))
	private void eternalreturn$crackedHalf(BlockRenderManager manager, BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
			VertexConsumer vertices, Operation<Void> original) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (state.isOf(MixedSlabs.MIXED_SLAB) && client.player != null && client.crosshairTarget instanceof BlockHitResult hit && hit.getBlockPos().equals(pos)) {
			state = MixedSlabs.aimedHalf(client.player, world, pos, state);
		}
		original.call(manager, state, pos, world, matrices, vertices);
	}
}
