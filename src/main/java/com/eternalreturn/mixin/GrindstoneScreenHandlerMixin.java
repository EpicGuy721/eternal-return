package com.eternalreturn.mixin;

import com.eternalreturn.logic.InherentEnchantments;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.GrindstoneScreenHandler;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;

@Mixin(GrindstoneScreenHandler.class)
public abstract class GrindstoneScreenHandlerMixin {
	/** Grinding strips bought enchantments but leaves the material's inherent ones in place. */
	@WrapMethod(method = "grind")
	private ItemStack eternalreturn$keepInherent(ItemStack item, Operation<ItemStack> original) {
		Map<RegistryEntry<Enchantment>, Integer> inherent = InherentEnchantments.inherentPortion(item);
		ItemStack result = original.call(item);
		if (!result.isEmpty()) {
			inherent.forEach(result::addEnchantment);
		}
		return result;
	}
}
