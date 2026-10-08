package com.eternalreturn.mixin.compat;

import com.eternalreturn.compat.ModernerBetaHooks;
import com.eternalreturn.worldgen.stone.BiomeStoneSurface;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moderner Beta's surface pass takes host stones for stone in chunks where biome stone ran (see
 * BiomeStoneSurface). Moderner Beta is runtime-only, so the target is named, not compiled against.
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProviderNoise", remap = false)
public abstract class ModernerBetaSurfaceMixin {
	@Inject(method = "isBlockSuitableForSurface", at = @At("HEAD"), cancellable = true, remap = false)
	private void eternalreturn$hostStoneIsStone(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		ModernerBetaHooks.SUITABLE_CHECKS.incrementAndGet();
		if (BiomeStoneSurface.isHostStoneInActiveChunk(state)) {
			cir.setReturnValue(false);
		}
	}
}
