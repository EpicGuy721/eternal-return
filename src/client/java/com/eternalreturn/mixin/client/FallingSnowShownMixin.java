package com.eternalreturn.mixin.client;

import com.eternalreturn.survival.SnowFall;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.entity.FallingBlockEntityRenderer;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Falling snow stays drawn until it lands. Vanilla stops drawing a falling block once the block where it is matches it
 * (it takes that for itself, just placed), but falling snow (SnowFall) drops into a snow layer of the same depth before
 * it lands on it and adds to it: it vanished just above the pile. Snow is taken as placed only where a placed block's
 * would sit, on the floor of its space.
 */
@Mixin(FallingBlockEntityRenderer.class)
public abstract class FallingSnowShownMixin {
	@WrapOperation(method = "render(Lnet/minecraft/entity/FallingBlockEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$notYetLanded(World world, BlockPos pos, Operation<BlockState> original, @Local(argsOnly = true) FallingBlockEntity entity) {
		if (SnowFall.isSnow(entity.getBlockState()) && entity.getY() > pos.getY() + 1.0E-3) {
			return Blocks.AIR.getDefaultState();
		}
		return original.call(world, pos);
	}
}
