package com.eternalreturn.mixin.compat;

import com.eternalreturn.compat.ModernerBetaHooks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moderner Beta's snow-and-freeze step (it takes over vanilla's freeze_top_layer in its worlds, Eternal Return's
 * included): while it runs, the world it decorates is noted, so its snow check (ModernerBetaSnowCheckMixin) lays snow in
 * plants, slabs, stairs, walls, fences and gates as vanilla's step does (FreezeTopLayerMixin). Moderner Beta is
 * runtime-only, so the target is named, not compiled against.
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.feature.BetaSnowAndFreezeFeature", remap = false)
public abstract class ModernerBetaSnowMixin {
	@Inject(method = "setFreezeTopLayer", at = @At("HEAD"), remap = false)
	private static void eternalreturn$start(StructureWorldAccess world, BlockPos pos, BiomeSource source, boolean surfaceRules, CallbackInfo ci) {
		ModernerBetaHooks.SNOWING.set(world);
	}

	@Inject(method = "setFreezeTopLayer", at = @At("RETURN"), remap = false)
	private static void eternalreturn$end(StructureWorldAccess world, BlockPos pos, BiomeSource source, boolean surfaceRules, CallbackInfo ci) {
		ModernerBetaHooks.SNOWING.remove();
	}
}
