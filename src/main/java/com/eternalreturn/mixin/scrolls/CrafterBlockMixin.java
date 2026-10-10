package com.eternalreturn.mixin.scrolls;

import com.eternalreturn.scrolls.Scrolls;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.CrafterBlock;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** A crafter has nobody who has read the scroll, so it never makes a book from a scroll recipe (Scrolls). */
@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
	@ModifyReturnValue(method = "getCraftingRecipe", at = @At("RETURN"))
	private static Optional<RecipeEntry<CraftingRecipe>> eternalreturn$noScrollRecipes(Optional<RecipeEntry<CraftingRecipe>> recipe) {
		return recipe.filter(entry -> !Scrolls.isScrollRecipe(entry));
	}
}
