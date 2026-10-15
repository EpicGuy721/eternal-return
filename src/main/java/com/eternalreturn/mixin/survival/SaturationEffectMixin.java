package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.Meals;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** With no hunger (Meals), the Saturation effect heals instead: a point of health a tick per level. */
@Mixin(targets = "net.minecraft.entity.effect.SaturationStatusEffect")
public abstract class SaturationEffectMixin {
	@WrapOperation(method = "applyUpdateEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;add(IF)V"))
	private void eternalreturn$heals(HungerManager hunger, int food, float saturation, Operation<Void> original, @Local(argsOnly = true) LivingEntity entity) {
		if (Meals.enabled()) {
			entity.heal(food);
		} else {
			original.call(hunger, food, saturation);
		}
	}
}
