package com.eternalreturn.mixin.client;

import com.eternalreturn.client.climate.ClientFog;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.source.BiomeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fog tints by biome in Eternal Return worlds (ClientFog): the fog colour vanilla blends from the biomes around the
 * camera takes each tinted biome's own day and night colour instead of its JSON fog colour.
 */
@Mixin(BackgroundRenderer.class)
public abstract class FogTintMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/CubicSampler;sampleColor(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/CubicSampler$RgbFetcher;)Lnet/minecraft/util/math/Vec3d;"))
	private static Vec3d eternalreturn$tint(Vec3d pos, CubicSampler.RgbFetcher fetcher, Operation<Vec3d> original,
			@Local(argsOnly = true) ClientWorld world, @Local(argsOnly = true) float tickDelta) {
		if (!ClientFog.active(world)) {
			return original.call(pos, fetcher);
		}
		float daylight = MathHelper.clamp(MathHelper.cos(world.getSkyAngle(tickDelta) * (float) (Math.PI * 2)) * 2.0F + 0.5F, 0.0F, 1.0F);
		BiomeAccess biomes = world.getBiomeAccess();
		return original.call(pos, (CubicSampler.RgbFetcher) (x, y, z) -> {
			Vec3d tinted = ClientFog.color(world, biomes.getBiomeForNoiseGen(x, y, z), daylight);
			return tinted != null ? tinted : fetcher.fetch(x, y, z);
		});
	}
}
