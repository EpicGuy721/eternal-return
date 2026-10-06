package com.eternalreturn.mobs.ai;

import com.eternalreturn.mobs.Ballistics;
import com.eternalreturn.mobs.entity.ZombieHookEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** A mob holding a fishing rod casts at its target from mid range and reels it in. Runs alongside movement and melee. */
public class ReelInTargetGoal extends Goal {
	private static final double MIN_DISTANCE = 4.0;
	private static final double MAX_DISTANCE = 16.0;
	private static final double HOOK_SPEED = 1.4;
	private static final double HOOK_GRAVITY = 0.03;

	private final MobEntity mob;
	@Nullable
	private ZombieHookEntity hook;
	private int nextCastAge;

	public ReelInTargetGoal(MobEntity mob) {
		this.mob = mob;
		this.setControls(EnumSet.noneOf(Goal.Control.class));
	}

	@Override
	public boolean canStart() {
		if (this.mob.age < this.nextCastAge || (this.hook != null && !this.hook.isRemoved())) {
			return false;
		}
		if (!this.mob.getMainHandStack().isOf(Items.FISHING_ROD)) {
			return false;
		}
		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}
		double distanceSq = this.mob.squaredDistanceTo(target);
		return distanceSq >= MIN_DISTANCE * MIN_DISTANCE
				&& distanceSq <= MAX_DISTANCE * MAX_DISTANCE
				&& this.mob.getVisibilityCache().canSee(target)
				&& this.mob.getRandom().nextInt(4) == 0;
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}

	@Override
	public void start() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}
		ZombieHookEntity cast = new ZombieHookEntity(this.mob.getWorld(), this.mob);
		Vec3d velocity = Ballistics.solve(cast.getPos(), new Vec3d(target.getX(), target.getBodyY(0.5), target.getZ()), HOOK_SPEED, HOOK_GRAVITY);
		if (velocity == null) {
			this.nextCastAge = this.mob.age + 20;
			return;
		}
		cast.setVelocity(velocity);
		this.mob.getWorld().spawnEntity(cast);
		this.mob.swingHand(Hand.MAIN_HAND);
		this.mob.getWorld().playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_THROW,
				SoundCategory.HOSTILE, 0.5F, 0.4F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F));
		this.hook = cast;
		this.nextCastAge = this.mob.age + 60 + this.mob.getRandom().nextInt(40);
	}
}
