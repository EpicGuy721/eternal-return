package com.eternalreturn.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mixin.mobs.EntityNavigationAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.SchoolingFishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.List;

/**
 * Schooling fish (cod, salmon, tropical fish) school by the boids rules (Boids) instead of following a leader
 * (mobs.fishSchools; SchoolingFishMixin). A fish starts schooling under water with two others of its kind in sight, and
 * keeps on while one is; alone, or out of the water, it swims (or flops) as vanilla's fish do. The school steers away
 * together, faster, from players (not in creative or spectator), guardians and any other mob that isn't a fish within
 * THREAT_RANGE blocks; this goal runs ahead of vanilla's flee from players, so the two don't take turns steering. It keeps
 * a block below the surface and off the bottom, turns from walls, and wanders a little (a drift that changes slowly).
 * It turns smoothly: its course by at most a little each tick, its body toward its course by at most MAX_YAW_STEP
 * degrees. Each kind has its own cruising speed.
 */
public class FishSchoolGoal extends Goal {
	static final Boids.Rules SCHOOL = new Boids.Rules(5.0, 6, 0.8, 0.05, 0.05, 0.003);
	static final double THREAT_RANGE = 6.0;
	static final float MAX_YAW_STEP = 6.0F;
	private final SchoolingFishEntity fish;
	private List<SchoolingFishEntity> mates = List.of();
	private List<LivingEntity> threats = List.of();
	private Vec3d drift = Vec3d.ZERO;
	private int sinceLook;

	public FishSchoolGoal(SchoolingFishEntity fish) {
		this.fish = fish;
		this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
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

	/** What a school keeps clear of: players (not in creative or spectator), guardians, and any other mob but a fish. */
	static boolean threat(LivingEntity entity) {
		if (entity instanceof PlayerEntity player) {
			return !player.isCreative() && !player.isSpectator();
		}
		return !(entity instanceof FishEntity);
	}

	private void look() {
		this.mates = Boids.neighbours(this.fish, SchoolingFishEntity.class, SCHOOL,
				other -> other.getType() == this.fish.getType() && other.isSubmergedIn(FluidTags.WATER));
		this.threats = this.fish.getWorld().getEntitiesByClass(LivingEntity.class, this.fish.getBoundingBox().expand(THREAT_RANGE),
				other -> other != this.fish && other.isAlive() && threat(other));
	}

	private boolean schooling(int mates) {
		return EternalReturnConfig.get().mobs.fishSchools && this.fish.isSubmergedIn(FluidTags.WATER) && this.mates.size() >= mates;
	}

	@Override
	public boolean canStart() {
		if (this.fish.age % 10 != this.fish.getId() % 10) {
			return false;
		}
		this.look();
		return this.schooling(2);
	}

	@Override
	public boolean shouldContinue() {
		return this.schooling(1);
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.fish.getNavigation().stop();
		// For good: a path whose recalculation was put off (blocks changed near it) would otherwise come back some ticks
		// later and steer the fish away from its school, turning it sharply.
		((EntityNavigationAccessor) this.fish.getNavigation()).eternalreturn$setInRecalculationCooldown(false);
		this.sinceLook = 0;
	}

	@Override
	public void tick() {
		if (this.sinceLook-- <= 0) {
			this.sinceLook = 5;
			this.look();
		}
		double max = speed(this.fish);
		Vec3d position = this.fish.getPos();
		Vec3d velocity = this.fish.getVelocity();
		Vec3d steer = Boids.steer(this.fish, this.mates, SCHOOL);
		// Away from threats, harder the nearer they are, and faster while any is near.
		Vec3d flee = Vec3d.ZERO;
		for (LivingEntity threat : this.threats) {
			Vec3d away = position.subtract(threat.getPos());
			double distance = Math.max(0.5, away.length());
			if (distance < THREAT_RANGE) {
				flee = flee.add(away.multiply((THREAT_RANGE - distance) / (distance * THREAT_RANGE)));
			}
		}
		boolean fleeing = flee.lengthSquared() > 1.0E-6;
		if (fleeing) {
			steer = steer.add(flee.normalize().multiply(0.03));
			max *= 1.6;
		}
		// A slow wander.
		this.drift = this.drift.multiply(0.96).add(this.fish.getRandom().nextGaussian() * 6.0E-4, this.fish.getRandom().nextGaussian() * 2.0E-4,
				this.fish.getRandom().nextGaussian() * 6.0E-4);
		steer = steer.add(this.drift);
		BlockPos at = this.fish.getBlockPos();
		if (!this.fish.getWorld().getFluidState(at.up()).isIn(FluidTags.WATER)) {
			steer = steer.add(0.0, -0.01, 0.0); // a block under the surface
		}
		if (!this.fish.getWorld().getFluidState(at.down()).isIn(FluidTags.WATER)) {
			steer = steer.add(0.0, 0.008, 0.0); // off the bottom
		}
		if (velocity.lengthSquared() > 1.0E-6) {
			Vec3d ahead = position.add(velocity.normalize().multiply(1.5));
			if (!this.fish.getWorld().getFluidState(BlockPos.ofFloored(ahead)).isIn(FluidTags.WATER)) {
				steer = steer.subtract(velocity.multiply(0.2)).add(-velocity.z * 0.15, 0.0, velocity.x * 0.15); // a wall ahead: slow and turn
			}
		}
		Vec3d next = Boids.apply(velocity, steer, fleeing ? 0.02 : 0.01, 0.5 * max, max);
		this.fish.setVelocity(next);
		if (next.horizontalLengthSquared() > 1.0E-6) {
			// The body turns toward the course, a little at a time.
			float course = (float) (MathHelper.atan2(next.z, next.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
			float turn = MathHelper.clamp(MathHelper.wrapDegrees(course - this.fish.getYaw()), -MAX_YAW_STEP, MAX_YAW_STEP);
			float yaw = this.fish.getYaw() + turn;
			this.fish.setYaw(yaw);
			this.fish.bodyYaw = yaw;
			this.fish.headYaw = yaw;
		}
	}
}
