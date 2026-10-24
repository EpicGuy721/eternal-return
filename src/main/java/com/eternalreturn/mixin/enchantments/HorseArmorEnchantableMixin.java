package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.items.HorseArmor;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.AnimalArmorItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Horse armor (not wolf armor) can be enchanted at a table, with its material's enchantability (HorseArmor). */
@Mixin(AnimalArmorItem.class)
public abstract class HorseArmorEnchantableMixin {
	@ModifyReturnValue(method = "isEnchantable", at = @At("RETURN"))
	private boolean eternalreturn$horseArmorEnchantable(boolean enchantable, ItemStack stack) {
		return enchantable || (stack.isIn(HorseArmor.TAG) && stack.getMaxCount() == 1 && stack.contains(DataComponentTypes.MAX_DAMAGE));
	}
}
