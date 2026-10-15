package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Food;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.item.OminousBottleItem;
import net.minecraft.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drinks take a second (Food): potions, milk and ominous bottles (honey's time is in the food table, HoneyBottleMixin). */
@Mixin({PotionItem.class, MilkBucketItem.class, OminousBottleItem.class})
public abstract class DrinkTimeMixin {
	@Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$drinkTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(Food.DRINK_TICKS);
	}
}
