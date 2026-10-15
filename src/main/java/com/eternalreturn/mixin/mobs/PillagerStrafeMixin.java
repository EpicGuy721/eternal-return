package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.CrossbowAttackGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PillagerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A pillager backs away, crossbow up, from a target that comes within 5 blocks. */
@Mixin(CrossbowAttackGoal.class)
public abstract class PillagerStrafeMixin {
	@Shadow
	@Final
	private HostileEntity actor;

	@Inject(method = "tick", at = @At("TAIL"))
	private void eternalreturn$backOff(CallbackInfo ci) {
		LivingEntity target = this.actor.getTarget();
		if (this.actor instanceof PillagerEntity && target != null && EternalReturnConfig.get().mobs.pillagersBackOff
				&& this.actor.squaredDistanceTo(target) < 25.0 && this.actor.getVisibilityCache().canSee(target)) {
			this.actor.getNavigation().stop();
			this.actor.getMoveControl().strafeTo(-0.6F, 0.0F);
			this.actor.lookAtEntity(target, 30.0F, 30.0F);
		}
	}
}
