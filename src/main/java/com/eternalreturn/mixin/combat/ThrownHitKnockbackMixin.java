package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.OldCombat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A snowball or egg pushes a mob it hits less hard than vanilla's punch-strength knockback (OldCombat). */
@Mixin(LivingEntity.class)
public abstract class ThrownHitKnockbackMixin {
	@WrapOperation(method = "damage", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;takeKnockback(DDD)V"))
	private void eternalreturn$thrown(LivingEntity self, double strength, double x, double z, Operation<Void> original,
			@Local(argsOnly = true) DamageSource source) {
		original.call(self, OldCombat.knockbackStrength(source.getSource(), strength), x, z);
	}
}
