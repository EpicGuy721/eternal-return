package com.eternalreturn.mixin;

import com.eternalreturn.logic.ShelfBias;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
	@Shadow
	@Final
	private ScreenHandlerContext context;

	/** Scan the chiseled bookshelves around this table so the roller can bias toward their books. */
	@Inject(method = "generateEnchantments", at = @At("HEAD"))
	private void eternalreturn$beginShelfBias(DynamicRegistryManager registryManager, ItemStack stack, int slot, int level,
			CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
		ShelfBias.begin(this.context.get(ShelfBias::scan, ShelfBias.NONE));
	}

	@Inject(method = "generateEnchantments", at = @At("RETURN"))
	private void eternalreturn$endShelfBias(DynamicRegistryManager registryManager, ItemStack stack, int slot, int level,
			CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
		ShelfBias.end();
	}

	/** The table no longer checks the player's level. Lapis is still required. */
	@ModifyExpressionValue(method = "onButtonClick",
			at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;experienceLevel:I"))
	private int eternalreturn$ignoreLevelRequirement(int original) {
		return Integer.MAX_VALUE;
	}
}
