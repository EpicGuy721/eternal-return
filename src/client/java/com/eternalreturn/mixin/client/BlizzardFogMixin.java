package com.eternalreturn.mixin.client;

import com.eternalreturn.client.weather.ClientWeather;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a blizzard (ClientWeather) the fog closes in to about 24 blocks and pales towards snow-grey. */
@Mixin(BackgroundRenderer.class)
public abstract class BlizzardFogMixin {
	@Shadow
	private static float red;

	@Shadow
	private static float green;

	@Shadow
	private static float blue;

	@Inject(method = "render", at = @At("TAIL"))
	private static void eternalreturn$paleFog(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
		float blizzard = ClientWeather.active(world) ? ClientWeather.blizzard(tickDelta) : 0.0F;
		if (blizzard <= 0.0F || camera.getSubmersionType() != CameraSubmersionType.NONE) {
			return;
		}
		// Greyed and a little lighter, as bright as the time of day allows.
		float grey = Math.min(1.0F, (red + green + blue) / 3.0F * 1.15F + 0.05F);
		float k = blizzard * 0.7F;
		red = MathHelper.lerp(k, red, grey);
		green = MathHelper.lerp(k, green, grey);
		blue = MathHelper.lerp(k, blue, Math.min(1.0F, grey * 1.05F));
		RenderSystem.clearColor(red, green, blue, 0.0F);
	}

	@Inject(method = "applyFog", at = @At("TAIL"))
	private static void eternalreturn$closeFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta,
			CallbackInfo ci) {
		ClientWorld world = MinecraftClient.getInstance().world;
		float blizzard = ClientWeather.active(world) ? ClientWeather.blizzard(tickDelta) : 0.0F;
		if (blizzard <= 0.0F || camera.getSubmersionType() != CameraSubmersionType.NONE) {
			return;
		}
		float end = RenderSystem.getShaderFogEnd();
		RenderSystem.setShaderFogEnd(MathHelper.lerp(blizzard, end, Math.min(end, 24.0F)));
		RenderSystem.setShaderFogStart(MathHelper.lerp(blizzard, RenderSystem.getShaderFogStart(), 0.0F));
	}
}
