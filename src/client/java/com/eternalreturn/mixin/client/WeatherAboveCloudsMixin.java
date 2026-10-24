package com.eternalreturn.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No rain or snow is drawn, nor rain splashes and their sound, while the camera is above the clouds (rain falls from
 * them: RainBelowCloudsMixin). Vanilla draws weather only within a few blocks of the camera, so that is all it takes.
 */
@Mixin(WorldRenderer.class)
public abstract class WeatherAboveCloudsMixin {
	@Unique
	private static boolean eternalreturn$aboveClouds(double cameraY) {
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return false;
		}
		float clouds = world.getDimensionEffects().getCloudsHeight();
		return !Float.isNaN(clouds) && cameraY > clouds;
	}

	@Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noWeatherAboveClouds(LightmapTextureManager manager, float tickDelta, double cameraX, double cameraY, double cameraZ,
			CallbackInfo ci) {
		if (eternalreturn$aboveClouds(cameraY)) {
			ci.cancel();
		}
	}

	@Inject(method = "tickRainSplashing", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noSplashesAboveClouds(Camera camera, CallbackInfo ci) {
		if (eternalreturn$aboveClouds(camera.getPos().y)) {
			ci.cancel();
		}
	}
}
