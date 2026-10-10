package com.eternalreturn.mixin.scrolls;

import com.eternalreturn.scrolls.Scrolls;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Book recipes only craft for a player whose scroll taught them (Scrolls); this covers the crafting table and the inventory grid. */
@Mixin(CraftingScreenHandler.class)
public abstract class CraftingScreenHandlerMixin {
	@WrapOperation(method = "updateResult", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/inventory/CraftingResultInventory;shouldCraftRecipe(Lnet/minecraft/world/World;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/recipe/RecipeEntry;)Z"))
	private static boolean eternalreturn$learnedOnly(CraftingResultInventory result, World world, ServerPlayerEntity player, RecipeEntry<?> recipe,
			Operation<Boolean> original) {
		return Scrolls.mayCraft(player, recipe) && original.call(result, world, player, recipe);
	}
}
