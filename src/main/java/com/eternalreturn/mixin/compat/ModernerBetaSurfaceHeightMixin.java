package com.eternalreturn.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moderner Beta's surface rules look up the column's surface height for each block they test (its precise surface
 * level). One chunk's sampler asks for the same column many times in a row, so the last answer is kept and handed back
 * for the same column. The same height, without the locked cache lookup each time. (docs/performance.md)
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.chunk.ModernBetaChunkNoiseSampler$SamplerImpl", remap = false)
public abstract class ModernerBetaSurfaceHeightMixin {
	@Unique
	private boolean eternalreturn$has;
	@Unique
	private int eternalreturn$x;
	@Unique
	private int eternalreturn$z;
	@Unique
	private int eternalreturn$height;

	@Inject(method = "estimateSurfaceHeight", at = @At("HEAD"), cancellable = true, remap = false)
	private void eternalreturn$same(int x, int z, CallbackInfoReturnable<Integer> cir) {
		if (this.eternalreturn$has && x == this.eternalreturn$x && z == this.eternalreturn$z) {
			cir.setReturnValue(this.eternalreturn$height);
		}
	}

	@Inject(method = "estimateSurfaceHeight", at = @At("RETURN"), remap = false)
	private void eternalreturn$keep(int x, int z, CallbackInfoReturnable<Integer> cir) {
		this.eternalreturn$x = x;
		this.eternalreturn$z = z;
		this.eternalreturn$height = cir.getReturnValueI();
		this.eternalreturn$has = true;
	}
}
