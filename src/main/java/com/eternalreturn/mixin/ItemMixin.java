package com.eternalreturn.mixin;

import com.eternalreturn.logic.EnchantLevels;
import com.eternalreturn.logic.InherentEnchantments;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public abstract class ItemMixin {
	/**
	 * No glint for gear that only carries its inherent enchantments (otherwise every iron
	 * tool would shimmer), and none at all under Curse of Invisibility.
	 */
	@ModifyReturnValue(method = "hasGlint", at = @At("RETURN"))
	private boolean eternalreturn$glint(boolean original, @Local(argsOnly = true) ItemStack stack) {
		if (!original) {
			return false;
		}
		if (EnchantLevels.isConcealed(stack)) {
			return false;
		}
		return !InherentEnchantments.isOnlyInherent(stack);
	}
}
