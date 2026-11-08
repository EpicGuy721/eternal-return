package com.eternalreturn.mixin.compat;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moderner Beta remembers its per-chunk biome and height maps for the last 512 chunks. A render distance of 12 or
 * more, a pre-generator or C2ME's many threads work on more chunks than that at once, so the maps were thrown away and
 * worked out again over and over. Caches of the default size keep 2048 chunks instead (a few megabytes each). They
 * only remember what was worked out, so nothing generated changes. (docs/performance.md)
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.util.chunk.AuxChunkCache", remap = false)
public abstract class ModernerBetaAuxChunkCacheMixin {
	@Shadow(remap = false)
	@Final
	@Mutable
	private int capacity;

	@Inject(method = "<init>", at = @At("RETURN"), remap = false)
	private void eternalreturn$larger(CallbackInfo ci) {
		if (this.capacity == 512) {
			this.capacity = 2048;
		}
	}
}
