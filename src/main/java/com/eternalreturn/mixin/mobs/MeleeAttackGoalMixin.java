package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.ai.AttackSpeed;
import com.eternalreturn.mobs.ai.LeapAttack;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A melee fighter stays in the fight while its target is alive and within STAY blocks (it gives up a target it can't
 * see for a while anyway: TrackTargetGoal). Vanilla's goal (for
 * zombies, skeletons with a sword, wither skeletons...) let go whenever the mob ran out of path: right beside a target
 * that stands still, or one it can't walk any closer to (a player under a roof too low for a wither skeleton). It then
 * waited a second to start again, so its attack pose flickered off and on and, out of walking reach, it never swung.
 * Kept going, it re-paths and swings whenever in reach. Also: its wait between swings follows Haste and Mining
 * Fatigue (AttackSpeed), and it leaps at a target 2 blocks up to knock it off (LeapAttack).
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

	@Shadow
	private int cooldown;

	/** Ticks since it leapt at its target (LeapAttack), counting down. */
	@Unique
	private int eternalreturn$leaping;

	/** Not heavy, its target about 2 blocks up and close by: it leaps, timed for its swing. */
	@Inject(method = "tick", at = @At("HEAD"))
	private void eternalreturn$leapAtTarget(CallbackInfo ci) {
		if (this.eternalreturn$leaping > 0) {
			this.eternalreturn$leaping--;
		}
		LivingEntity target = this.mob.getTarget();
		if (target != null && LeapAttack.shouldLeap(this.mob, target, this.cooldown)) {
			LeapAttack.leap(this.mob, target);
			this.eternalreturn$leaping = LeapAttack.LEAP_TICKS;
		}
	}

	/** A hit in a leap knocks the target back hard, to knock it off its perch. */
	@Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/mob/PathAwareEntity;tryAttack(Lnet/minecraft/entity/Entity;)Z", shift = At.Shift.AFTER))
	private void eternalreturn$knockOff(LivingEntity target, CallbackInfo ci) {
		if (this.eternalreturn$leaping > 0) {
			LeapAttack.knockOff(this.mob, target);
			this.eternalreturn$leaping = 0;
		}
	}

	/** Haste and Mining Fatigue: the wait between swings shortens or lengthens (AttackSpeed). */
	@Inject(method = "resetCooldown", at = @At("TAIL"))
	private void eternalreturn$attackSpeed(CallbackInfo ci) {
		this.cooldown = (int) AttackSpeed.scale(this.mob, this.cooldown);
	}

	@Inject(method = "shouldContinue", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$stayInTheFight(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || this.pauseWhenMobIdle) {
			return;
		}
		LivingEntity target = this.mob.getTarget();
		if (target != null && target.isAlive() && EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.test(target)
				&& this.mob.squaredDistanceTo(target) <= STAY * STAY) {
			cir.setReturnValue(true);
		}
	}
}
