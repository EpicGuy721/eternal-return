package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Launch (NewEnchantments) on a mob's hit. */
@Mixin(MobEntity.class)
public abstract class MobAttackMixin {
	@Inject(method = "tryAttack", at = @At("RETURN"))
	private void eternalreturn$launch(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			NewEnchantments.launch((MobEntity) (Object) this, target);
		}
	}
}
