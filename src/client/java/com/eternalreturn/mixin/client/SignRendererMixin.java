package com.eternalreturn.mixin.client;

import com.eternalreturn.blocks.InvisibleSigns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** An invisible sign draws only its text (InvisibleSigns); hanging signs go through the same code. */
@Mixin(SignBlockEntityRenderer.class)
public abstract class SignRendererMixin {
	@WrapOperation(method = "render(Lnet/minecraft/block/entity/SignBlockEntity;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/block/BlockState;Lnet/minecraft/block/AbstractSignBlock;Lnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/SignBlockEntityRenderer;renderSign(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/block/WoodType;Lnet/minecraft/client/model/Model;)V"))
	private void eternalreturn$hideBoard(SignBlockEntityRenderer renderer, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay,
			WoodType wood, Model model, Operation<Void> original, @Local(argsOnly = true) SignBlockEntity sign) {
		if (!InvisibleSigns.isInvisible(sign)) {
			original.call(renderer, matrices, vertices, light, overlay, wood, model);
		}
	}
}
