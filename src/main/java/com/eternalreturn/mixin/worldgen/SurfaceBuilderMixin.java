package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.stone.BiomeStoneSurface;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Each chunk's surface starts unmarked; biome stone's rules mark it if they run (see BiomeStoneSurface). */
@Mixin(SurfaceBuilder.class)
public abstract class SurfaceBuilderMixin {
	@Inject(method = "buildSurface", at = @At("HEAD"))
	private void eternalreturn$unmarkChunk(CallbackInfo ci) {
		BiomeStoneSurface.unmarkChunk();
	}
}
