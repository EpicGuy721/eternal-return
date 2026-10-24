package com.eternalreturn.mixin.client;

import com.eternalreturn.items.Spears;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Charging with a spear (Spears) neither slows the player down nor stops them sprinting, as using other items does. */
@Mixin(ClientPlayerEntity.class)
public abstract class SpearMovementMixin {
	@ModifyExpressionValue(method = {"tickMovement", "canStartSprinting"},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
	private boolean eternalreturn$chargeAtARun(boolean using) {
		return using && !(((ClientPlayerEntity) (Object) this).getActiveItem().getItem() instanceof Spears.Spear);
	}
}
