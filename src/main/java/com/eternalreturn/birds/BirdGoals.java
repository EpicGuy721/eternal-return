package com.eternalreturn.birds;

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
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** What birds do (BirdEntity): fly off from players, perch in trees (perching shapes) or wander low (the others). */
final class BirdGoals {
	private BirdGoals() {
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
			return this.bird.getShape().perches() && super.canStart();
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
			return !this.bird.getShape().perches() && super.canStart();
		}
	}
}
