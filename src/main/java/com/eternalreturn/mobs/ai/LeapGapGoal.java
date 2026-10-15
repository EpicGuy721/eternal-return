package com.eternalreturn.mobs.ai;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Mob parkour: a humanoid monster (entity tag eternalreturn:parkour_mobs) whose way to its target is blocked by a
 * gap jumps it. With a target, standing at an edge facing it, a drop of 2 or more blocks ahead and solid ground 2 to
 * 4 blocks on (level, a block up or a block down), it leaps across.
 */
public class LeapGapGoal extends Goal {
	private final MobEntity mob;
	private int nextCheck;
	private Vec3d leap;

	public LeapGapGoal(MobEntity mob) {
		this.mob = mob;
		this.setControls(EnumSet.of(Control.MOVE, Control.JUMP));
	}

	@Override
	public boolean canStart() {
		if (this.mob.age < this.nextCheck || !EternalReturnConfig.get().mobs.mobParkour) {
			return false;
		}
		this.nextCheck = this.mob.age + 5;
		LivingEntity target = this.mob.getTarget();
		if (target == null || !this.mob.isOnGround() || this.mob.isTouchingWater() || this.mob.hasVehicle()) {
			return false;
		}
		Vec3d toTarget = target.getPos().subtract(this.mob.getPos()).multiply(1.0, 0.0, 1.0);
		double flat = toTarget.length();
		if (flat < 2.5 || flat > 24.0) {
			return false;
		}
		Path path = this.mob.getNavigation().getCurrentPath();
		if (path != null && path.reachesTarget() && !this.mob.getNavigation().isIdle()) {
			return false;
		}
		this.leap = findLeap(this.mob.getWorld(), this.mob.getBlockPos(), toTarget.normalize());
		return this.leap != null;
	}

	/** The jump across a gap in that direction, or null. */
	static Vec3d findLeap(World world, BlockPos feet, Vec3d direction) {
		BlockPos ahead = BlockPos.ofFloored(feet.toCenterPos().add(direction));
		// A drop right ahead: no floor there, nor the block below it.
		if (!world.isAir(ahead) || !world.getBlockState(ahead.down()).getCollisionShape(world, ahead.down()).isEmpty()
				|| !world.getBlockState(ahead.down(2)).getCollisionShape(world, ahead.down(2)).isEmpty()) {
			return null;
		}
		for (int distance = 2; distance <= 4; distance++) {
			BlockPos over = BlockPos.ofFloored(feet.toCenterPos().add(direction.multiply(distance)));
			for (int rise = 1; rise >= -1; rise--) {
				BlockPos land = over.up(rise);
				if (world.getBlockState(land.down()).isSolidBlock(world, land.down()) && world.isAir(land) && world.isAir(land.up())
						&& world.isAir(feet.up(2))) {
					double speed = 0.12 + distance * 0.11;
					return new Vec3d(direction.x * speed, rise > 0 ? 0.52 : 0.42, direction.z * speed);
				}
			}
		}
		return null;
	}

	@Override
	public boolean shouldContinue() {
		return false;
	}

	@Override
	public void start() {
		LivingEntity target = this.mob.getTarget();
		if (target != null) {
			this.mob.getLookControl().lookAt(target, 30.0F, 30.0F);
			this.mob.setYaw((float) (MathHelper.atan2(this.leap.z, this.leap.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F);
		}
		this.mob.getNavigation().stop();
		this.mob.setVelocity(this.leap);
		this.mob.velocityDirty = true;
		this.nextCheck = this.mob.age + 30;
	}
}
