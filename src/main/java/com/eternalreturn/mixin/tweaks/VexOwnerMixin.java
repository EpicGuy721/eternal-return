package com.eternalreturn.mixin.tweaks;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vexes die with the evoker that summoned them. */
@Mixin(VexEntity.class)
public abstract class VexOwnerMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void eternalreturn$dieWithOwner(CallbackInfo ci) {
		VexEntity vex = (VexEntity) (Object) this;
		MobEntity owner = vex.getOwner();
		if (!vex.getWorld().isClient && vex.isAlive() && owner != null && !owner.isAlive()) {
			vex.kill();
		}
	}
}
