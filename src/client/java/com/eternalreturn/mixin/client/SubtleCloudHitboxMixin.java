package com.eternalreturn.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.particle.EntityEffectParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * F3+B draws no hitbox for a subtle lingering cloud (SubtlePotions: its particles have no opacity) to a player in
 * survival or adventure; creative and spectator players still see it.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class SubtleCloudHitboxMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;renderHitbox(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/entity/Entity;FFFF)V"))
	private void eternalreturn$hideSubtleCloud(MatrixStack matrices, VertexConsumer vertices, Entity entity, float tickDelta, float red, float green,
			float blue, Operation<Void> original) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (entity instanceof AreaEffectCloudEntity cloud && cloud.getParticleType() instanceof EntityEffectParticleEffect particle && particle.getAlpha() == 0.0F
				&& client.interactionManager != null && client.interactionManager.getCurrentGameMode().isSurvivalLike()) {
			return;
		}
		original.call(matrices, vertices, entity, tickDelta, red, green, blue);
	}
}
