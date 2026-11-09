package com.eternalreturn.mixin.compat;

import com.eternalreturn.worldgen.landmarks.HotSprings;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Moderner Beta's snow and ice (its own climate, Eternal Return worlds' too) stay off the ground round hot springs (HotSprings). */
@Mixin(targets = "mod.bluestaggo.modernerbeta.level.biome.ClimateHelper", remap = false)
public abstract class ModernerBetaHotSpringMixin {
	@Inject(method = "shouldSnow", remap = false, at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$noSnowAtSprings(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) WorldView view,
			@Local(argsOnly = true) BlockPos pos) {
		if (HotSprings.keepsClear(view, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "shouldFreeze", remap = false, at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$noIceAtSprings(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) WorldView view,
			@Local(argsOnly = true) BlockPos pos) {
		if (HotSprings.keepsClear(view, pos)) {
			cir.setReturnValue(false);
		}
	}
}
