package com.eternalreturn.mixin.client;

import com.eternalreturn.client.weather.ClientWeather;
import com.eternalreturn.client.weather.WeatherRenderer;
import com.eternalreturn.config.EternalReturnConfig;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With storms (ClientWeather): rain, snow, splashes and the sound of rain come from WeatherRenderer, and the clouds drift
 * with the wind (weather.windyRain) rather than always west at vanilla's pace.
 */
@Mixin(WorldRenderer.class)
public abstract class LocalWeatherRenderMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	private int ticks;

	@Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$storms(LightmapTextureManager manager, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
		if (ClientWeather.active(this.client.world)) {
			WeatherRenderer.render(manager, tickDelta, cameraX, cameraY, cameraZ, this.ticks);
			ci.cancel();
		}
	}

	@Inject(method = "tickRainSplashing", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$stormSplashes(Camera camera, CallbackInfo ci) {
		if (ClientWeather.active(this.client.world)) {
			WeatherRenderer.splash(camera, this.ticks);
			ci.cancel();
		}
	}

	@ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double eternalreturn$cloudsEast(double cameraX, @Local(argsOnly = true) float tickDelta) {
		// Vanilla adds its own westward drift; take it back out and drift with the wind instead.
		return this.eternalreturn$windy() ? cameraX - (this.ticks + tickDelta) * 0.03F - ClientWeather.cloudX(tickDelta) : cameraX;
	}

	@ModifyVariable(method = "renderClouds", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private double eternalreturn$cloudsSouth(double cameraZ, @Local(argsOnly = true) float tickDelta) {
		return this.eternalreturn$windy() ? cameraZ - ClientWeather.cloudZ(tickDelta) : cameraZ;
	}

	@Unique
	private boolean eternalreturn$windy() {
		return ClientWeather.active(this.client.world) && EternalReturnConfig.get().weather.windyRain;
	}
}
