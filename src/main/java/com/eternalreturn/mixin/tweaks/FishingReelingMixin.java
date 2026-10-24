package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reeling on a fishing rod: a hooked mob (or item) is pulled in harder, 40 percent more per level on top of vanilla's tug. */
@Mixin(FishingBobberEntity.class)
public abstract class FishingReelingMixin {
	@Inject(method = "pullHookedEntity", at = @At("TAIL"))
	private void eternalreturn$reeling(Entity entity, CallbackInfo ci) {
		FishingBobberEntity hook = (FishingBobberEntity) (Object) this;
		if (!(hook.getOwner() instanceof PlayerEntity player)) {
			return;
		}
		int level = Math.max(NewEnchantments.level(player.getStackInHand(Hand.MAIN_HAND), NewEnchantments.REELING),
				NewEnchantments.level(player.getStackInHand(Hand.OFF_HAND), NewEnchantments.REELING));
		if (level > 0) {
			Vec3d tug = new Vec3d(player.getX() - hook.getX(), player.getY() - hook.getY(), player.getZ() - hook.getZ()).multiply(0.1 * 0.4 * level);
			entity.setVelocity(entity.getVelocity().add(tug));
			entity.velocityModified = true;
		}
	}
}
