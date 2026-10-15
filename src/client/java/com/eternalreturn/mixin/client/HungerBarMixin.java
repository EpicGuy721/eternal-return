package com.eternalreturn.mixin.client;

import com.eternalreturn.survival.Meals;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hunger (Meals): no food bar. */
@Mixin(InGameHud.class)
public abstract class HungerBarMixin {
	@Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noFoodBar(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
		if (Meals.enabled()) {
			ci.cancel();
		}
	}
}
