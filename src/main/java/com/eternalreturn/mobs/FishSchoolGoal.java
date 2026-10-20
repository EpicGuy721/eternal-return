package com.eternalreturn.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.SchoolingFishEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Schooling fish (cod, salmon, tropical fish) school by the boids rules (Boids) instead of following a leader
 * (mobs.fishSchools; SchoolingFishMixin). A fish schools only while it is under water with at least two others of its
 * kind in sight; alone, or out of the water, it swims (or flops) as vanilla's fish do. It keeps a block below the
 * surface and off the bottom, turns from walls, and drifts a little at random. Each kind has its own cruising speed.
 */
public class FishSchoolGoal extends Goal {
	static final Boids.Rules SCHOOL = new Boids.Rules(5.0, 6, 0.8, 0.05, 0.06, 0.004);
	private final SchoolingFishEntity fish;
	private List<SchoolingFishEntity> mates = List.of();
	private int sinceLook;

	public FishSchoolGoal(SchoolingFishEntity fish) {
		this.fish = fish;
		this.setControls(EnumSet.of(Goal.Control.MOVE));
	}

	/** Blocks a tick: salmon fastest, tropical fish slowest. */
	static double speed(SchoolingFishEntity fish) {
		EntityType<?> type = fish.getType();
		if (type == EntityType.SALMON) {
			return 0.13;
		}
		if (type == EntityType.TROPICAL_FISH) {
			return 0.08;
		}
		return 0.1;
	}

	private void look() {
		this.mates = Boids.neighbours(this.fish, SchoolingFishEntity.class, SCHOOL,
				other -> other.getType() == this.fish.getType() && other.isSubmergedIn(FluidTags.WATER));
	}

	private boolean schooling() {
		return EternalReturnConfig.get().mobs.fishSchools && this.fish.isSubmergedIn(FluidTags.WATER) && this.mates.size() >= 2;
	}

	@Override
	public boolean canStart() {
		if (this.fish.age % 10 != this.fish.getId() % 10) {
			return false;
		}
		this.look();
		return this.schooling();
	}

	@Override
	public boolean shouldContinue() {
		return this.schooling();
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.fish.getNavigation().stop();
		this.sinceLook = 0;
	}

	@Override
	public void tick() {
		if (this.sinceLook-- <= 0) {
			this.sinceLook = 5;
			this.look();
		}
		double max = speed(this.fish);
		Vec3d velocity = this.fish.getVelocity();
		Vec3d steer = Boids.steer(this.fish, this.mates, SCHOOL);
		steer = steer.add(this.fish.getRandom().nextGaussian() * 0.004, this.fish.getRandom().nextGaussian() * 0.002, this.fish.getRandom().nextGaussian() * 0.004);
		BlockPos at = this.fish.getBlockPos();
		if (!this.fish.getWorld().getFluidState(at.up()).isIn(FluidTags.WATER)) {
			steer = steer.add(0.0, -0.02, 0.0); // a block under the surface
		}
		if (!this.fish.getWorld().getFluidState(at.down()).isIn(FluidTags.WATER)) {
			steer = steer.add(0.0, 0.015, 0.0); // off the bottom
		}
		if (velocity.lengthSquared() > 1.0E-6) {
			Vec3d ahead = this.fish.getPos().add(velocity.normalize().multiply(1.5));
			if (!this.fish.getWorld().getFluidState(BlockPos.ofFloored(ahead)).isIn(FluidTags.WATER)) {
				steer = steer.subtract(velocity.multiply(0.4)).add(-velocity.z * 0.3, 0.0, velocity.x * 0.3); // a wall ahead: slow and turn
			}
		}
		Vec3d next = Boids.apply(velocity, steer, 0.03, 0.5 * max, max);
		this.fish.setVelocity(next);
		if (next.horizontalLengthSquared() > 1.0E-6) {
			float yaw = (float) (MathHelper.atan2(next.z, next.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
			this.fish.setYaw(yaw);
			this.fish.bodyYaw = yaw;
		}
	}
}
