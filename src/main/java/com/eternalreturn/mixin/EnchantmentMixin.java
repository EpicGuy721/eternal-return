package com.eternalreturn.mixin;

import com.eternalreturn.config.EternalReturnConfig;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Raises max level to 5 for enchantments that capped at 2-4. Single-level ones (Infinity, Silk Touch...) are untouched. */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
	@ModifyReturnValue(method = "getMaxLevel", at = @At("RETURN"))
	private int eternalreturn$raiseMaxLevel(int original) {
		EternalReturnConfig cfg = EternalReturnConfig.get();
		if (cfg.raiseMaxLevels && original > 1 && original < cfg.raisedMaxLevel) {
			return cfg.raisedMaxLevel;
		}
		return original;
	}
}
