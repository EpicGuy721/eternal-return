package com.eternalreturn.mixin.client;

import com.eternalreturn.survival.BeaconPower;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A beacon gives one effect (BeaconPower): in place of the "Secondary Power" label, the range and effect time the
 * item in the payment slot would buy (the second-effect button itself stays hidden: BeaconLevelTwoButtonMixin).
 */
@Mixin(BeaconScreen.class)
public abstract class BeaconScreenMixin extends HandledScreen<BeaconScreenHandler> {
	protected BeaconScreenMixin(BeaconScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
	}

	@WrapOperation(method = "drawForeground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V", ordinal = 1))
	private void eternalreturn$rangeLabel(DrawContext context, TextRenderer renderer, Text text, int x, int y, int color, Operation<Void> original) {
		if (!BeaconPower.enabled()) {
			original.call(context, renderer, text, x, y, color);
			return;
		}
		int tier = BeaconPower.tierOf(this.handler.getSlot(0).getStack());
		if (tier > 0) {
			int seconds = BeaconPower.MAX_TICKS[tier] / 20;
			String time = seconds % 60 == 0 ? seconds / 60 + " min" : seconds >= 60 ? seconds / 60 + " min " + seconds % 60 + " s" : seconds + " s";
			original.call(context, renderer, Text.translatable("gui.eternalreturn.beacon.range", BeaconPower.range(tier)), x, y, color);
			original.call(context, renderer, Text.translatable("gui.eternalreturn.beacon.time", time), x, y + 12, color);
		}
	}
}
