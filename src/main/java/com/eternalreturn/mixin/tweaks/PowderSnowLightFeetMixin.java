package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.survival.Growing;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feather Falling boots walk on powder snow, as leather boots do (Growing.treadsLightly). */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowLightFeetMixin {
	@Inject(method = "canWalkOnPowderSnow", at = @At("RETURN"), cancellable = true)
	private static void eternalreturn$lightFeet(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Growing.treadsLightly(entity)) {
			cir.setReturnValue(true);
		}
	}
}
