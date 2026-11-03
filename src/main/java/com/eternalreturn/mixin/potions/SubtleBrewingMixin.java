package com.eternalreturn.mixin.potions;

import com.eternalreturn.items.SubtlePotions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.BrewingRecipeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An amethyst shard brews any potion with effects into its subtle form (SubtlePotions). A subtle potion keeps taking
 * gunpowder and dragon's breath: vanilla's item recipes are worked out on a stand-in potion and the result keeps the
 * subtle effects.
 */
@Mixin(BrewingRecipeRegistry.class)
public abstract class SubtleBrewingMixin {
	@Shadow
	public abstract ItemStack craft(ItemStack ingredient, ItemStack input);

	@Inject(method = "isValidIngredient", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$amethyst(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (SubtlePotions.isIngredient(stack)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "hasRecipe", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$subtleRecipe(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
		if (SubtlePotions.isIngredient(ingredient)) {
			cir.setReturnValue(SubtlePotions.canMakeSubtle(input));
		}
	}

	@Inject(method = "craft", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$brewSubtle(ItemStack ingredient, ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
		if (SubtlePotions.isIngredient(ingredient)) {
			cir.setReturnValue(SubtlePotions.canMakeSubtle(input) ? SubtlePotions.makeSubtle(input) : input);
		} else if (SubtlePotions.isSubtle(input)) {
			ItemStack standIn = input.copy();
			standIn.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.AWKWARD));
			ItemStack result = this.craft(ingredient, standIn);
			cir.setReturnValue(result.getItem() != input.getItem() ? input.copyComponentsToNewStack(result.getItem(), 1) : input);
		}
	}
}
