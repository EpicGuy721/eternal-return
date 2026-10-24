package com.eternalreturn.mixin.tweaks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A punch with a feather in hand does nothing (a tickle): no damage, no knockback. */
@Mixin(PlayerEntity.class)
public abstract class FeatherPunchMixin {
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$tickle(Entity target, CallbackInfo ci) {
		if (((PlayerEntity) (Object) this).getMainHandStack().isOf(Items.FEATHER)) {
			ci.cancel();
		}
	}
}
