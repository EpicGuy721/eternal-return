package com.eternalreturn.mixin.client;

import com.eternalreturn.items.Spears;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player charging with a spear (Spears) holds it level before them, arms out as with a loaded crossbow. */
@Mixin(PlayerEntityRenderer.class)
public abstract class SpearArmPoseMixin {
	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private static void eternalreturn$levelledSpear(AbstractClientPlayerEntity player, Hand hand, CallbackInfoReturnable<BipedEntityModel.ArmPose> cir) {
		if (player.isUsingItem() && player.getActiveHand() == hand && player.getActiveItem().getItem() instanceof Spears.Spear) {
			cir.setReturnValue(BipedEntityModel.ArmPose.CROSSBOW_HOLD);
		}
	}
}
