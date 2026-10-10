package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.NewEnchantments;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reeling (NewEnchantments): the arrow's hit pulls its target in. */
@Mixin(PersistentProjectileEntity.class)
public abstract class ArrowHitMixin {
	@Inject(method = "onEntityHit", at = @At("TAIL"))
	private void eternalreturn$reel(EntityHitResult hit, CallbackInfo ci) {
		PersistentProjectileEntity self = (PersistentProjectileEntity) (Object) this;
		if (!self.getWorld().isClient) {
			NewEnchantments.reel(self, hit.getEntity());
		}
	}
}
