package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.SwordBlocking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player blocking with a sword can't attack (SwordBlocking). */
@Mixin(PlayerEntity.class)
public abstract class SwordBlockingMixin {
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noAttackWhileBlocking(Entity target, CallbackInfo ci) {
		if (SwordBlocking.cantAttack((PlayerEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
