package com.eternalreturn.mixin;

import com.eternalreturn.logic.EnchantLevels;
import com.eternalreturn.logic.FortuneHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.dispenser.ShearsDispenserBehavior;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ShearsDispenserBehavior.class)
public abstract class ShearsDispenserBehaviorMixin {
	/** Fortune shears in a dispenser work on sheep too. */
	@WrapMethod(method = "dispenseSilently")
	private ItemStack eternalreturn$rememberShears(BlockPointer pointer, ItemStack stack, Operation<ItemStack> original) {
		FortuneHelper.SHEAR_FORTUNE.set(EnchantLevels.levelOf(stack, Enchantments.FORTUNE));
		try {
			return original.call(pointer, stack);
		} finally {
			FortuneHelper.SHEAR_FORTUNE.remove();
		}
	}
}
