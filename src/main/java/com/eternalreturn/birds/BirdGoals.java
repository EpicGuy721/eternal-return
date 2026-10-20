package com.eternalreturn.birds;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.Boids;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.ai.AboveGroundTargeting;
import net.minecraft.entity.ai.FuzzyTargeting;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * What birds do (BirdEntity): fly off from players, perch in trees (perching shapes) or wander low (the others), and,
 * for flocking shapes, take off and fly as a flock (FlockFlight, by the boids rules).
 */
final class BirdGoals {
	/** Flockmates are seen 8 blocks off, the nearest 7 count; keep about a block apart. */
	static final Boids.Rules FLOCK = new Boids.Rules(8.0, 7, 1.2, 0.12, 0.05, 0.004);
	/** Waders: the same, but held together less. */
	static final Boids.Rules LOOSE = new Boids.Rules(8.0, 7, 1.6, 0.12, 0.02, 0.0015);

	private BirdGoals() {
	}

	/** Flocking shapes flock; a pet doesn't. */
	static boolean flocks(BirdEntity bird) {
		return EternalReturnConfig.get().mobs.birdFlocks && bird.getShape().flocking() != BirdTable.Flocking.SOLO && !bird.isTamed();
	}

	static Boids.Rules rules(BirdEntity bird) {
		return bird.getShape().flocking() == BirdTable.Flocking.LOOSE ? LOOSE : FLOCK;
	}

	/** Flockmates: birds of the same shape. */
	static boolean mates(BirdEntity bird, BirdEntity other) {
		return BirdVariant.shape(other.getVariant()) == BirdVariant.shape(bird.getVariant());
	}

	/** The bird takes off toward target, and so do its flockmates nearby that are not flying yet (a startled flock bursts up together). */
	static void takeOff(BirdEntity bird, Vec3d target) {
		int ticks = 80 + bird.getRandom().nextInt(60);
		bird.startFlockFlight(target, ticks);
		for (BirdEntity mate : Boids.neighbours(bird, BirdEntity.class, new Boids.Rules(10.0, 16, 0, 0, 0, 0), other -> mates(bird, other) && !other.isFlocking() && !other.isTamed())) {
			Vec3d spread = new Vec3d(mate.getRandom().nextGaussian() * 2.0, 0.0, mate.getRandom().nextGaussian() * 2.0);
			mate.startFlockFlight(target.add(spread), ticks + mate.getRandom().nextInt(20));
		}
	}

	/**
	 * A player (not in creative or spectator) it can see within BirdTable.WARY_DISTANCE, or half that if they sneak: it
	 * flies off 8 to 16 blocks, away from them.
	 */
	static class FlyAway extends Goal {
		private final BirdEntity bird;
		private int ticks;

		FlyAway(BirdEntity bird) {
			this.bird = bird;
			this.setControls(EnumSet.of(Goal.Control.MOVE));
		}

		@Nullable
		private PlayerEntity threat() {
			PlayerEntity player = this.bird.getWorld().getClosestPlayer(this.bird.getX(), this.bird.getY(), this.bird.getZ(), BirdTable.WARY_DISTANCE,
					EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR);
			if (player == null || !this.bird.canSee(player)) {
				return null;
			}
			double reach = player.isSneaking() ? BirdTable.WARY_DISTANCE / 2.0 : BirdTable.WARY_DISTANCE;
			return this.bird.squaredDistanceTo(player) <= reach * reach ? player : null;
		}

		@Override
		public boolean canStart() {
			if (this.bird.isFlocking() || this.bird.isTamed()) {
				return false;
			}
			PlayerEntity player = this.threat();
			if (player == null) {
				return false;
			}
			Vec3d away = this.bird.getPos().subtract(player.getPos());
			Vec3d target = AboveGroundTargeting.find(this.bird, 16, 7, away.x, away.z, MathHelper.HALF_PI, 4, 2);
			if (target == null) {
				target = NoPenaltyTargeting.findFrom(this.bird, 16, 7, player.getPos());
			}
			if (target == null || target.squaredDistanceTo(player.getPos()) < this.bird.squaredDistanceTo(player)) {
				return false;
			}
			this.ticks = 0;
			if (flocks(this.bird)) {
				// A flocking bird bursts up with its flock and flies off together, further.
				Vec3d far = this.bird.getPos().add(away.normalize().multiply(20.0)).add(0.0, 6.0, 0.0);
				takeOff(this.bird, far);
				return false;
			}
			return this.bird.getNavigation().startMovingTo(target.x, target.y, target.z, 1.6);
		}

		@Override
		public boolean shouldContinue() {
			return !this.bird.getNavigation().isIdle() && ++this.ticks < 200;
		}
	}

	/** Perching shapes: now and then flies up to rest on leaves or a log nearby (as a parrot does), else flies about. */
	static class Perch extends FlyGoal {
		private final BirdEntity bird;

		Perch(BirdEntity bird) {
			super(bird, 1.0);
			this.bird = bird;
		}

		@Override
		public boolean canStart() {
			return this.bird.getShape().perches() && !this.bird.isTamed() && super.canStart();
		}

		@Nullable
		@Override
		protected Vec3d getWanderTarget() {
			Vec3d target = null;
			if (this.mob.isTouchingWater()) {
				target = FuzzyTargeting.find(this.mob, 15, 15);
			}
			if (this.mob.getRandom().nextFloat() >= this.probability) {
				target = this.perch();
			}
			return target == null ? super.getWanderTarget() : target;
		}

		/** An open spot on top of leaves or a log within 3 blocks across and 6 up or down. */
		@Nullable
		private Vec3d perch() {
			BlockPos at = this.mob.getBlockPos();
			BlockPos.Mutable below = new BlockPos.Mutable();
			BlockPos.Mutable above = new BlockPos.Mutable();
			for (BlockPos pos : BlockPos.iterate(at.add(-3, -6, -3), at.add(3, 6, 3))) {
				if (pos.equals(at)) {
					continue;
				}
				BlockState state = this.mob.getWorld().getBlockState(below.set(pos, Direction.DOWN));
				if ((state.getBlock() instanceof LeavesBlock || state.isIn(BlockTags.LOGS)) && this.mob.getWorld().isAir(pos)
						&& this.mob.getWorld().isAir(above.set(pos, Direction.UP))) {
					return Vec3d.ofBottomCenter(pos);
				}
			}
			return null;
		}
	}

	/** The others (ducks, waders, gulls) wander along the ground and water. */
	static class WanderLow extends WanderAroundFarGoal {
		private final BirdEntity bird;

		WanderLow(BirdEntity bird) {
			super(bird, 1.0);
			this.bird = bird;
		}

		@Override
		public boolean canStart() {
			return !this.bird.getShape().perches() && !this.bird.isTamed() && super.canStart();
		}
	}

	/**
	 * Flying with the flock: separation, alignment and cohesion with its flockmates (Boids), a pull toward the flock's
	 * target, a turn away from blocks ahead, and a height kept between 4 and 14 blocks above the ground. Speed between
	 * 60 percent and all of the shape's flight speed. Ends when the time is up or the target is reached; the bird then
	 * flutters down, and perches or lands.
	 */
	static class FlockFlight extends Goal {
		private final BirdEntity bird;
		private List<BirdEntity> mates = List.of();
		private int sinceLook;

		FlockFlight(BirdEntity bird) {
			this.bird = bird;
			this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK, Goal.Control.JUMP));
		}

		@Override
		public boolean canStart() {
			return this.bird.isFlocking();
		}

		@Override
		public boolean shouldContinue() {
			return this.bird.isFlocking() && !this.bird.isTouchingWater();
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public void start() {
			this.bird.getNavigation().stop();
			this.sinceLook = 0;
		}

		@Override
		public void stop() {
			this.bird.endFlockFlight();
		}

		@Override
		public void tick() {
			World world = this.bird.getWorld();
			if (this.sinceLook-- <= 0) {
				this.sinceLook = 5;
				this.mates = Boids.neighbours(this.bird, BirdEntity.class, rules(this.bird), other -> mates(this.bird, other) && other.isFlocking());
			}
			double max = this.bird.getShape().flightSpeed();
			Vec3d position = this.bird.getPos();
			Vec3d velocity = this.bird.getVelocity();
			Vec3d steer = Boids.steer(this.bird, this.mates, rules(this.bird));
			Vec3d toTarget = this.bird.getFlockTarget().subtract(position);
			Vec3d level = new Vec3d(toTarget.x, 0.0, toTarget.z);
			if (level.lengthSquared() < 9.0) {
				this.bird.endFlockFlight();
				return;
			}
			steer = steer.add(level.normalize().multiply(0.03));
			int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(position.x), MathHelper.floor(position.z));
			double height = position.y - ground;
			if (height < 4.0) {
				steer = steer.add(0.0, 0.04, 0.0);
			} else if (height > 14.0) {
				steer = steer.add(0.0, -0.03, 0.0);
			}
			if (velocity.lengthSquared() > 1.0E-4) {
				Vec3d ahead = velocity.normalize().multiply(2.5);
				if (!world.isSpaceEmpty(this.bird, this.bird.getBoundingBox().offset(ahead))) {
					steer = steer.add(0.0, 0.08, 0.0).subtract(velocity.multiply(0.15));
				}
			}
			Vec3d next = Boids.apply(velocity, steer, 0.08, 0.6 * max, max);
			this.bird.setVelocity(next);
			float yaw = (float) (MathHelper.atan2(next.z, next.x) * MathHelper.DEGREES_PER_RADIAN) - 90.0F;
			this.bird.setYaw(yaw);
			this.bird.bodyYaw = yaw;
			this.bird.headYaw = yaw;
			this.bird.tickFlockFlight();
		}
	}

	/** Flocking shapes, now and then (about once every two minutes), take off with their flock for a short flight 20 to 36 blocks off. */
	static class FlockWander extends Goal {
		private final BirdEntity bird;

		FlockWander(BirdEntity bird) {
			this.bird = bird;
		}

		@Override
		public boolean canStart() {
			if (!flocks(this.bird) || this.bird.isFlocking() || !this.bird.isOnGround() || this.bird.getRandom().nextInt(2400) != 0) {
				return false;
			}
			double angle = this.bird.getRandom().nextDouble() * Math.PI * 2.0;
			double distance = 20.0 + this.bird.getRandom().nextDouble() * 16.0;
			takeOff(this.bird, this.bird.getPos().add(Math.cos(angle) * distance, 6.0, Math.sin(angle) * distance));
			return false;
		}
	}
}
