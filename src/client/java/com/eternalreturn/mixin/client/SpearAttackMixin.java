package com.eternalreturn.mixin.client;

import com.eternalreturn.items.Spears;
import com.eternalreturn.logic.SpearCombat;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With a spear, the attack key jabs (SpearCombat), at a full attack charge only, whatever is under the crosshair;
 * it never breaks blocks.
 */
@Mixin(MinecraftClient.class)
public abstract class SpearAttackMixin {
	@Shadow
	@Nullable
	public ClientPlayerEntity player;

	@Shadow
	@Nullable
	public ClientPlayerInteractionManager interactionManager;

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$jab(CallbackInfoReturnable<Boolean> cir) {
		if (this.player == null || this.player.isSpectator() || !(this.player.getMainHandStack().getItem() instanceof Spears.Spear)) {
			return;
		}
		if (this.player.getAttackCooldownProgress(0.0F) >= 1.0F) {
			ClientPlayNetworking.send(new SpearCombat.JabPayload());
			this.player.resetLastAttackedTicks();
			this.player.swingHand(Hand.MAIN_HAND);
		}
		cir.setReturnValue(true);
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noBreaking(boolean breaking, CallbackInfo ci) {
		if (this.player != null && this.player.getMainHandStack().getItem() instanceof Spears.Spear) {
			if (this.interactionManager != null) {
				this.interactionManager.cancelBlockBreaking();
			}
			ci.cancel();
		}
	}
}
