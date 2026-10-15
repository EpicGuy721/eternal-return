package com.eternalreturn.mixin.client;

import com.eternalreturn.survival.BeaconPower;
import net.minecraft.client.gui.widget.ClickableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A beacon gives one effect (BeaconPower): the beacon screen's second-effect button ("II") never shows. */
@Mixin(targets = "net.minecraft.client.gui.screen.ingame.BeaconScreen$LevelTwoEffectButtonWidget")
public abstract class BeaconLevelTwoButtonMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$hidden(int level, CallbackInfo ci) {
		if (BeaconPower.enabled()) {
			((ClickableWidget) (Object) this).visible = false;
		}
	}
}
