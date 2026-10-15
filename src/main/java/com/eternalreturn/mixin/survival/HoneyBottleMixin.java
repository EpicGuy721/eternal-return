package com.eternalreturn.mixin.survival;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.HoneyBottleItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Honey bottles take the drinking time the food table gives them (Meals), not a fixed 2 seconds. */
@Mixin(HoneyBottleItem.class)
public abstract class HoneyBottleMixin {
	@Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$drinkTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
		FoodComponent food = stack.get(DataComponentTypes.FOOD);
		if (food != null) {
			cir.setReturnValue(food.getEatTicks());
		}
	}
}
