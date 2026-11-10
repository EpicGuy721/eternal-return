package com.eternalreturn.mixin.client;

import com.eternalreturn.client.climate.ClientFog;
import com.eternalreturn.config.EternalReturnConfig;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Deep sea fog (worldgen expansion 2, phase 5): in Eternal Return worlds, under water below y 30, the water's fog darkens
 * and closes in the deeper the camera is, to near black and about a third of its distance at y -30 (the abyss).
 */
@Mixin(BackgroundRenderer.class)
public abstract class DeepSeaFogMixin {
	@Shadow
	private static float red;
	@Shadow
	private static float green;
	@Shadow
	private static float blue;

	/** 0 at y 30 and above, 1 at y -30 and below. */
	private static float eternalreturn$depth(Camera camera) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (camera.getSubmersionType() != CameraSubmersionType.WATER || client.world == null || !ClientFog.active(client.world)
				|| !EternalReturnConfig.get().worldgen.deepSeaFog) {
			return 0;
		}
		return MathHelper.clamp((float) ((30 - camera.getPos().y) / 60.0), 0, 1);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private static void eternalreturn$darken(CallbackInfo info, @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) ClientWorld world) {
		float depth = eternalreturn$depth(camera);
		if (depth > 0) {
			float keep = MathHelper.lerp(depth, 1.0F, 0.12F);
			red *= keep;
			green *= keep;
			blue *= keep;
			RenderSystem.clearColor(red, green, blue, 0.0F);
		}
	}

	@Inject(method = "applyFog", at = @At("TAIL"))
	private static void eternalreturn$close(CallbackInfo info, @Local(argsOnly = true) Camera camera) {
		float depth = eternalreturn$depth(camera);
		if (depth > 0) {
			RenderSystem.setShaderFogEnd(RenderSystem.getShaderFogEnd() * MathHelper.lerp(depth, 1.0F, 0.35F));
		}
	}
}
