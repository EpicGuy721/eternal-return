package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Food;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Snacks are quick to eat, meals slow (Food). */
@Mixin(Item.class)
public abstract class ItemEatTimeMixin {
	@Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$eatTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
		int ticks = Food.eatTicks(stack);
		if (ticks > 0) {
			cir.setReturnValue(ticks);
		}
	}
}
