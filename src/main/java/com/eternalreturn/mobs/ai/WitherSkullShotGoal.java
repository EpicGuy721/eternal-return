package com.eternalreturn.mobs.ai;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

/**
 * A wither skeleton whose target is far off (8 to 32 blocks) and in sight shoots a wither skull at it, as the
 * Wither does, every 3 to 5 seconds. Up close it fights with its sword as before.
 */
public class WitherSkullShotGoal extends Goal {
	private final MobEntity mob;
	private int cooldown = 40;

	public WitherSkullShotGoal(MobEntity mob) {
		this.mob = mob;
		this.setControls(EnumSet.noneOf(Control.class));
	}

	private boolean inRange(LivingEntity target) {
		double distance = this.mob.squaredDistanceTo(target);
		return distance > 64.0 && distance < 1024.0 && this.mob.getVisibilityCache().canSee(target);
	}

	@Override
	public boolean canStart() {
		LivingEntity target = this.mob.getTarget();
		return EternalReturnConfig.get().mobs.witherSkeletonSkulls && target != null && target.isAlive() && this.inRange(target);
	}

	@Override
	public boolean shouldContinue() {
		return this.canStart();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (--this.cooldown > 0) {
			return;
		}
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}
		this.cooldown = 60 + this.mob.getRandom().nextInt(40);
		Vec3d from = this.mob.getEyePos();
		Vec3d to = target.getPos().add(0.0, target.getHeight() * 0.5, 0.0);
		WitherSkullEntity skull = new WitherSkullEntity(this.mob.getWorld(), this.mob, to.subtract(from).normalize());
		skull.setOwner(this.mob);
		skull.setPosition(from.x, from.y, from.z);
		this.mob.getWorld().spawnEntity(skull);
		this.mob.playSound(SoundEvents.ENTITY_WITHER_SHOOT, 0.6F, 1.2F);
		this.mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
	}
}
