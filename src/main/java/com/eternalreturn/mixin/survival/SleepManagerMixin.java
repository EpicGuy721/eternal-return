package com.eternalreturn.mixin.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.server.world.SleepManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Beds only set the respawn point: however many players sleep, the night never skips (survival.bedsSkipNight). */
@Mixin(SleepManager.class)
public abstract class SleepManagerMixin {
	@Inject(method = "canSkipNight", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noNightSkip(int percentage, CallbackInfoReturnable<Boolean> cir) {
		if (!EternalReturnConfig.get().survival.bedsSkipNight) {
			cir.setReturnValue(false);
		}
	}
}
