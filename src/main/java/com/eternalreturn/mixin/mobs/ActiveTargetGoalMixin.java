package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.LargeMobs;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PassiveEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A large zombie (LargeMobs) never picks villagers, animals or turtles as targets. */
@Mixin(ActiveTargetGoal.class)
public abstract class ActiveTargetGoalMixin<T extends LivingEntity> extends TrackTargetGoal {
	@Shadow
	@Final
	protected Class<T> targetClass;

	private ActiveTargetGoalMixin(MobEntity mob, boolean checkVisibility) {
		super(mob, checkVisibility);
	}

	@Inject(method = "canStart", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$largeZombiesLeavePassives(CallbackInfoReturnable<Boolean> cir) {
		if (this.mob instanceof ZombieEntity && PassiveEntity.class.isAssignableFrom(this.targetClass) && LargeMobs.isLarge(this.mob)) {
			cir.setReturnValue(false);
		}
	}
}
