package com.eternalreturn.mixin;

import com.eternalreturn.logic.EnchantGenerator;
import com.eternalreturn.logic.ShelfBias;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@Inject(method = "generateEnchantments", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$generate(Random random, ItemStack stack, int level,
			Stream<RegistryEntry<Enchantment>> possibleEnchantments,
			CallbackInfoReturnable<List<EnchantmentLevelEntry>> cir) {
		ShelfBias bias = ShelfBias.active();
		if (EnchantGenerator.shouldTakeOver(stack, bias)) {
			cir.setReturnValue(EnchantGenerator.generate(random, stack, level, possibleEnchantments, bias));
		}
	}
}
