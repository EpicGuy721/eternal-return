package com.eternalreturn.mobs.ai;

import com.eternalreturn.mobs.Ballistics;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * A mob holding an ender pearl throws it at a distant target. Vanilla pearls already teleport
 * non-player owners, so this only has to aim and throw.
 */
public class ThrowPearlGoal extends Goal {
	private static final double MIN_DISTANCE = 12.0;
	private static final double MAX_DISTANCE = 40.0;
	private static final double PEARL_SPEED = 1.5;
	private static final double PEARL_GRAVITY = 0.03;

	private final MobEntity mob;
	private int nextTryAge;

	public ThrowPearlGoal(MobEntity mob) {
		this.mob = mob;
		this.setControls(EnumSet.noneOf(Goal.Control.class));
	}

	@Override
	public boolean canStart() {
		if (this.mob.age < this.nextTryAge || this.pearlHand() == null || !this.mob.isOnGround()) {
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
				&& this.mob.getRandom().nextInt(10) == 0;
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}

	@Override
	public void start() {
		LivingEntity target = this.mob.getTarget();
		Hand hand = this.pearlHand();
		if (target == null || hand == null) {
			return;
		}
		EnderPearlEntity pearl = new EnderPearlEntity(this.mob.getWorld(), this.mob);
		Vec3d velocity = Ballistics.solve(pearl.getPos(), target.getPos(), PEARL_SPEED, PEARL_GRAVITY);
		if (velocity == null) {
			this.nextTryAge = this.mob.age + 40;
			return;
		}
		ItemStack stack = this.mob.getStackInHand(hand);
		pearl.setItem(stack.copyWithCount(1));
		pearl.setVelocity(velocity);
		this.mob.getWorld().spawnEntity(pearl);
		this.mob.swingHand(hand);
		this.mob.getWorld().playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(), SoundEvents.ENTITY_ENDER_PEARL_THROW,
				SoundCategory.HOSTILE, 0.5F, 0.4F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F));
		stack.decrement(1);
		this.mob.setStackInHand(hand, stack.isEmpty() ? ItemStack.EMPTY : stack);
		this.nextTryAge = this.mob.age + 40;
	}

	private Hand pearlHand() {
		if (this.mob.getOffHandStack().isOf(Items.ENDER_PEARL)) {
			return Hand.OFF_HAND;
		}
		return this.mob.getMainHandStack().isOf(Items.ENDER_PEARL) ? Hand.MAIN_HAND : null;
	}
}
