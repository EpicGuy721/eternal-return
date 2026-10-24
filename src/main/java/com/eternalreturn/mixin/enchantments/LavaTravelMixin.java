package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.logic.LavaSwimming;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A lava swimmer (LavaSwimming) moves through lava as through water: vanilla's water movement, not its lava crawl. */
@Mixin(LivingEntity.class)
public abstract class LavaTravelMixin {
	@ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isTouchingWater()Z"))
	private boolean eternalreturn$lavaAsWater(boolean water) {
		LivingEntity self = (LivingEntity) (Object) this;
		return water || (self.isInLava() && LavaSwimming.swims(self));
	}
}
