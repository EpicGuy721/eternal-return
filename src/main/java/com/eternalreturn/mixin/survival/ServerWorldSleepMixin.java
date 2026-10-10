package com.eternalreturn.mixin.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** With no night skipping, the "1/2 players sleeping" counter (multiplayer) would only mislead, so it isn't shown. */
@Mixin(ServerWorld.class)
public abstract class ServerWorldSleepMixin {
	@Inject(method = "sendSleepingStatus", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noSleepCounter(CallbackInfo ci) {
		if (!EternalReturnConfig.get().survival.bedsSkipNight) {
			ci.cancel();
		}
	}
}
