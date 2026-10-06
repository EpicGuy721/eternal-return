package com.eternalreturn.mixin.client;

import com.eternalreturn.duck.AnvilCapView;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
	/** Hide "Enchantment Cost: N" and "Too Expensive!": the anvil is free. */
	@ModifyExpressionValue(method = "drawForeground",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/AnvilScreenHandler;getLevelCost()I"),
			require = 0)
	private int eternalreturn$hideCost(int original) {
		return 0;
	}

	/** Explain an empty output slot when the result would exceed the item's enchant capacity. */
	@Inject(method = "drawForeground", at = @At("TAIL"))
	private void eternalreturn$drawCapWarning(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
		AnvilScreenHandler handler = ((AnvilScreen) (Object) this).getScreenHandler();
		if (!(handler instanceof AnvilCapView view) || view.eternalreturn$getRefusedCap() < 0) {
			return;
		}
		TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
		Text text = Text.translatable("container.eternalreturn.over_capacity",
				view.eternalreturn$getRefusedPoints(), view.eternalreturn$getRefusedCap());
		int right = 176 - 8; // anvil background width minus margin, same spot vanilla uses for the cost
		int x = right - textRenderer.getWidth(text) - 2;
		context.fill(x - 2, 67, right, 79, 0x4F000000);
		context.drawTextWithShadow(textRenderer, text, x, 69, 0xFF6060);
	}
}
