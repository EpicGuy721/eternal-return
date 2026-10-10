package com.eternalreturn.mixin.client.mobs;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fish and squid models have no baby form, so a baby (BabyVariants) is drawn at half size, right after the
 * renderer's own scaling (some fish renderers replace scale() without calling the base one).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(value = "INVOKE", shift = At.Shift.AFTER,
					target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;scale(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V"))
	private void eternalreturn$babyWaterCreature(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light,
			CallbackInfo ci) {
		if (entity instanceof WaterCreatureEntity && entity.isBaby()) {
			matrices.scale(0.5F, 0.5F, 0.5F);
		}
	}
}
