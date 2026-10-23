package com.eternalreturn.mixin.mobs;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A melee fighter stays in the fight while its target is alive, in sight and within STAY blocks. Vanilla's goal (for
 * zombies, skeletons with a sword, wither skeletons...) let go whenever the mob ran out of path: right beside a target
 * that stands still, or one it can't walk any closer to. It then waited a second to start again, so its attack pose
 * flickered off and on and, out of walking reach, it never swung. Kept going, it re-paths and swings whenever in reach.
 */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {
	@Unique
	private static final double STAY = 16.0;

	@Shadow
	@Final
	protected PathAwareEntity mob;

	@Shadow
	@Final
	private boolean pauseWhenMobIdle;

	@Inject(method = "shouldContinue", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$stayInTheFight(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || this.pauseWhenMobIdle) {
			return;
		}
		LivingEntity target = this.mob.getTarget();
		if (target != null && target.isAlive() && EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.test(target)
				&& this.mob.squaredDistanceTo(target) <= STAY * STAY && this.mob.getVisibilityCache().canSee(target)) {
			cir.setReturnValue(true);
		}
	}
}
