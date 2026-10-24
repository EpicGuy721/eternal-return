package com.eternalreturn.mixin.tweaks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A punch with a feather in hand is a tickle: it looks and sounds like a hit (the flash, a light push) but does no damage. */
@Mixin(PlayerEntity.class)
public abstract class FeatherPunchMixin {
	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$tickle(Entity target, CallbackInfo ci) {
		PlayerEntity player = (PlayerEntity) (Object) this;
		if (!player.getMainHandStack().isOf(Items.FEATHER)) {
			return;
		}
		ci.cancel();
		if (!(player.getWorld() instanceof ServerWorld world) || !(target instanceof LivingEntity living) || !target.isAttackable()
				|| target.handleAttack(player) || living.isInvulnerable() || living.hurtTime > 0) {
			return;
		}
		world.sendEntityDamage(living, world.getDamageSources().playerAttack(player));
		float yaw = player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		living.takeKnockback(0.2, MathHelper.sin(yaw), -MathHelper.cos(yaw));
		living.velocityModified = true;
		living.hurtTime = living.maxHurtTime = 10;
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, player.getSoundCategory(), 1.0F, 1.2F);
		player.resetLastAttackedTicks();
	}
}
