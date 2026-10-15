package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Meals;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.CakeBlock;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A slice of cake heals (Meals) instead of feeding. */
@Mixin(CakeBlock.class)
public abstract class CakeBlockMixin {
	@WrapOperation(method = "tryEat", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;add(IF)V"))
	private static void eternalreturn$sliceHeals(HungerManager hunger, int food, float saturation, Operation<Void> original,
			@Local(argsOnly = true) PlayerEntity player) {
		if (Meals.enabled()) {
			Meals.add(player, Meals.cake());
		} else {
			original.call(hunger, food, saturation);
		}
	}
}
