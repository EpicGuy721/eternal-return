package com.eternalreturn.mobs.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.EnumSet;

/**
 * A zombie holding a crossbow (zombie fletchers, ZombieVillagerJobs) loads it and shoots at its target from 3 to 16
 * blocks, while still walking at it like any zombie.
 */
public class ZombieCrossbowGoal extends Goal {
	private final ZombieEntity zombie;
	private int cooldown;

	public ZombieCrossbowGoal(ZombieEntity zombie) {
		this.zombie = zombie;
		this.setControls(EnumSet.noneOf(Control.class));
	}

	private boolean holdsCrossbow() {
		return this.zombie.getMainHandStack().getItem() instanceof CrossbowItem;
	}

	@Override
	public boolean canStart() {
		LivingEntity target = this.zombie.getTarget();
		return target != null && target.isAlive() && this.holdsCrossbow();
	}

	@Override
	public boolean shouldContinue() {
		return this.canStart();
	}

	@Override
	public void stop() {
		if (this.zombie.isUsingItem()) {
			this.zombie.clearActiveItem();
		}
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = this.zombie.getTarget();
		ItemStack crossbow = this.zombie.getMainHandStack();
		double distance = this.zombie.squaredDistanceTo(target);
		boolean inRange = distance >= 9.0 && distance <= 256.0 && this.zombie.getVisibilityCache().canSee(target);
		if (this.cooldown > 0) {
			this.cooldown--;
		}
		if (!CrossbowItem.isCharged(crossbow)) {
			if (!this.zombie.isUsingItem()) {
				if (inRange && this.cooldown == 0) {
					this.zombie.setCurrentHand(Hand.MAIN_HAND);
				}
			} else if (this.zombie.getItemUseTime() >= CrossbowItem.getPullTime(crossbow, this.zombie)) {
				this.zombie.stopUsingItem();
			}
		} else if (inRange) {
			this.zombie.getLookControl().lookAt(target, 30.0F, 30.0F);
			((CrossbowItem) crossbow.getItem()).shootAll(this.zombie.getWorld(), this.zombie, Hand.MAIN_HAND, crossbow, 1.6F,
					14 - this.zombie.getWorld().getDifficulty().getId() * 4, target);
			this.cooldown = 20;
		}
	}
}
