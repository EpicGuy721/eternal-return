package com.eternalreturn.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	/**
	 * Enchanting costs no levels. The method still runs so the player's
	 * enchanting seed rerolls and the table offers new options afterwards.
	 */
	@ModifyVariable(method = "applyEnchantmentCosts", at = @At("HEAD"), argsOnly = true)
	private int eternalreturn$freeEnchanting(int experienceLevels) {
		return 0;
	}
}
