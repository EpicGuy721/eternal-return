package com.eternalreturn.mixin.redstone;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecarts can go redstone.minecartSpeedMultiplier (3) times vanilla's top speed, on any rail. */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartSpeedMixin {
	@Inject(method = "getMaxSpeed", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$faster(CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(cir.getReturnValueD() * EternalReturnConfig.get().redstone.minecartSpeedMultiplier);
	}
}
