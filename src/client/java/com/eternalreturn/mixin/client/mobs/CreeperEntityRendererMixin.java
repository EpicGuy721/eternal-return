package com.eternalreturn.mixin.client.mobs;

import net.minecraft.client.render.entity.CreeperEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skeleton models are bipeds, which already draw a baby body when isBaby() is true. The creeper
 * model has no baby form, so shrink the whole thing (charge overlay included) to match its hitbox.
 */
@Mixin(CreeperEntityRenderer.class)
public abstract class CreeperEntityRendererMixin {
	@Inject(method = "scale(Lnet/minecraft/entity/mob/CreeperEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
	private void eternalreturn$babyScale(CreeperEntity creeper, MatrixStack matrices, float tickDelta, CallbackInfo ci) {
		if (creeper.isBaby()) {
			matrices.scale(0.5F, 0.5F, 0.5F);
		}
	}
}
