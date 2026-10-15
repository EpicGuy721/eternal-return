package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.OldCombat;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Snowballs and eggs knock back what they hit (OldCombat). */
@Mixin({SnowballEntity.class, EggEntity.class})
public abstract class ThrownKnockbackMixin {
	@Inject(method = "onEntityHit", at = @At("TAIL"))
	private void eternalreturn$knockback(EntityHitResult hit, CallbackInfo ci) {
		OldCombat.knockback((ProjectileEntity) (Object) this, hit.getEntity());
	}
}
