package com.eternalreturn.mixin;

import com.eternalreturn.logic.InherentEnchantments;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SmithingTransformRecipe.class)
public abstract class SmithingTransformRecipeMixin {
	/** Diamond to netherite: swap the diamond's free levels for netherite's, keep what was paid for. */
	@ModifyReturnValue(
			method = "craft(Lnet/minecraft/recipe/input/SmithingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;",
			at = @At("RETURN"))
	private ItemStack eternalreturn$swapInherent(ItemStack result,
			@Local(argsOnly = true) SmithingRecipeInput input,
			@Local(argsOnly = true) RegistryWrapper.WrapperLookup lookup) {
		if (!result.isEmpty()) {
			// slot 1 of a smithing input is the base item
			InherentEnchantments.transfer(input.getStackInSlot(1), result, lookup);
		}
		return result;
	}
}
