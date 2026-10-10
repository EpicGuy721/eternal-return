package com.eternalreturn.mixin.client;

import com.eternalreturn.client.MovingBlockEntities;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.PistonBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks drawn by their block entity (chests, signs, banners, beds...) have no block model to draw while a
 * piston moves them, so they would vanish for the slide: draw their block entity along with it.
 */
@Mixin(PistonBlockEntityRenderer.class)
public abstract class PistonBlockEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/block/entity/PistonBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
			at = @At("HEAD"))
	private void eternalreturn$drawMovingBlockEntity(PistonBlockEntity piston, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay,
			CallbackInfo ci) {
		MovingBlockEntities.render(piston, tickDelta, matrices, vertices, light, overlay);
	}
}
