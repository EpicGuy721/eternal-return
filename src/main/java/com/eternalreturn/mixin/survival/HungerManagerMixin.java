package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Meals;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hunger (Meals): the food bar stays full, and with it go natural regeneration, exhaustion and starving. */
@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {
	@Shadow
	private int foodLevel;
	@Shadow
	private float saturationLevel;
	@Shadow
	private float exhaustion;

	@Inject(method = "update", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noHunger(PlayerEntity player, CallbackInfo ci) {
		if (Meals.enabled()) {
			this.foodLevel = 20;
			this.saturationLevel = 5.0F;
			this.exhaustion = 0.0F;
			ci.cancel();
		}
	}
}
