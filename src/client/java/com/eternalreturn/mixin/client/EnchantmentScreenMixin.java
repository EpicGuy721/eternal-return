package com.eternalreturn.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {
	/**
	 * The screen greys out options and shows "Level Requirement" based on the player's level.
	 * Levels no longer matter, so present the player as always qualifying.
	 * Both owners are listed because the field is read through a ClientPlayerEntity reference.
	 * require = 0: purely cosmetic, so a conflicting UI mod cannot crash the game over it.
	 */
	@ModifyExpressionValue(method = {"drawBackground", "render"},
			at = {
					@At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;experienceLevel:I"),
					@At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;experienceLevel:I")
			},
			require = 0)
	private int eternalreturn$alwaysQualifies(int original) {
		return Integer.MAX_VALUE;
	}
}
