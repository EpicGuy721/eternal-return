package com.eternalreturn.mixin.compat;

import com.eternalreturn.worldgen.stone.BiomeStoneSurface;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Around Moderner Beta's Release-style surface pass: keep biome stone under its bare patches (see BiomeStoneSurface). */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.chunk.provider.ChunkProviderNoise3D", remap = false)
public abstract class ModernerBetaSurfaceExtraMixin {
	@Inject(method = "provideSurfaceExtra", at = @At("HEAD"), remap = false)
	private void eternalreturn$beforeSurfacePass(ChunkRegion region, StructureAccessor structures, Chunk chunk, @Coerce Object biomeSource,
			NoiseConfig noiseConfig, CallbackInfo ci) {
		BiomeStoneSurface.beforeSurfacePass(chunk);
	}

	@Inject(method = "provideSurfaceExtra", at = @At("RETURN"), remap = false)
	private void eternalreturn$afterSurfacePass(ChunkRegion region, StructureAccessor structures, Chunk chunk, @Coerce Object biomeSource,
			NoiseConfig noiseConfig, CallbackInfo ci) {
		BiomeStoneSurface.afterSurfacePass(chunk);
	}
}
