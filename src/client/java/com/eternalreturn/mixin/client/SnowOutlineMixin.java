package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.client.SnowModels;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
 * On a block with snow logged in it (SnowCover), the selection box and the cracks of the player's own mining show the
 * snow or the block, whichever is aimed at. The snow's box is drawn as a snow layer's, so what other mods do to the
 * block's box (Nostalgic Tweaks' full block outlines) leaves the snow's alone; others mining it crack the block.
 */
@Mixin(WorldRenderer.class)
public abstract class SnowOutlineMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;DDDLnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V"))
	private void eternalreturn$outlineSnowAsSnow(WorldRenderer renderer, MatrixStack matrices, VertexConsumer vertices, Entity entity, double x, double y, double z,
			BlockPos pos, BlockState state, Operation<Void> original) {
		if (entity instanceof PlayerEntity player && SnowCover.aimsSnow(player, player.getWorld(), pos, state)) {
			state = SnowCover.asSnowLayer(SnowCover.layers(player.getWorld(), pos, state));
		}
		original.call(renderer, matrices, vertices, entity, x, y, z, pos, state);
	}

	@WrapOperation(method = "drawBlockOutline", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/BlockState;getOutlineShape(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/ShapeContext;)Lnet/minecraft/util/shape/VoxelShape;"))
	private VoxelShape eternalreturn$snowOrBlock(BlockState state, BlockView world, BlockPos pos, ShapeContext context, Operation<VoxelShape> original) {
		BlockState here = world.getBlockState(pos);
		int layers = SnowCover.layers(world, pos, here);
		if (layers > 0) {
			return state.isOf(Blocks.SNOW) ? SnowCover.snowOutline(here, layers) : original.call(SnowCover.bare(state), world, pos, context);
		}
		return original.call(state, world, pos, context);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderDamage(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V"))
	private void eternalreturn$crackedSnowOrBlock(BlockRenderManager manager, BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
			VertexConsumer vertices, Operation<Void> original) {
		if (SnowCover.layers(world, pos, state) == 0) {
			original.call(manager, state, pos, world, matrices, vertices);
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.crosshairTarget instanceof BlockHitResult hit && hit.getBlockPos().equals(pos) && SnowCover.aimsSnow(client.player, world, pos, state)) {
			SnowModels.onlySnow(() -> original.call(manager, state, pos, world, matrices, vertices));
		} else {
			original.call(manager, SnowCover.bare(state), pos, world, matrices, vertices);
		}
	}
}
