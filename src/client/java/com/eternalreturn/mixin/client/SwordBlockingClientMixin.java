package com.eternalreturn.mixin.client;

import com.eternalreturn.logic.SwordBlocking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The attack key while using an item:
 * <ul>
 * <li>blocking with a sword, it does nothing (SwordBlocking): no swing, no hit sent, whatever Nostalgic Tweaks'
 * "Attack While Sword Blocking" lets through;</li>
 * <li>eating or drinking, holding it on a block swings the arm and knocks the block's hit particles off it, as in
 * 1.7.10, without cracking or breaking anything; the swing goes to the server, so others see it.</li>
 * </ul>
 */
@Mixin(MinecraftClient.class)
public abstract class SwordBlockingClientMixin {
	@Shadow
	@Nullable
	public ClientPlayerEntity player;

	@Shadow
	@Nullable
	public ClientWorld world;

	@Shadow
	@Nullable
	public HitResult crosshairTarget;

	@Shadow
	@Final
	public ParticleManager particleManager;

	@Shadow
	public int attackCooldown;

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noAttackWhileBlocking(CallbackInfoReturnable<Boolean> cir) {
		if (this.player != null && SwordBlocking.cantAttack(this.player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"))
	private void eternalreturn$eatingSwing(boolean breaking, CallbackInfo ci) {
		if (!breaking || this.player == null || this.world == null || this.attackCooldown > 0 || !this.player.isUsingItem()
				|| !(this.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		UseAction action = this.player.getActiveItem().getUseAction();
		if (action != UseAction.EAT && action != UseAction.DRINK) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		if (!this.world.getBlockState(pos).isAir()) {
			this.particleManager.addBlockBreakingParticles(pos, hit.getSide());
			this.player.swingHand(Hand.MAIN_HAND);
		}
	}
}
