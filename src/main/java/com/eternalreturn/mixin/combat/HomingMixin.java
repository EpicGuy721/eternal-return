package com.eternalreturn.mixin.combat;

import com.eternalreturn.logic.Homing;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Curse of Homing pulls projectiles in (Homing): as each one ticks, before it moves (every projectile's tick starts
 * with ProjectileEntity's).
 */
@Mixin(ProjectileEntity.class)
public abstract class HomingMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void eternalreturn$home(CallbackInfo ci) {
		Homing.steer((ProjectileEntity) (Object) this);
	}
}
