package com.eternalreturn.mobs.climbing;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Idle, a surface walker now and then picks a spot it can see within 8 blocks on a wall or ceiling (or, off the floor,
 * back on a floor) and goes there over the surfaces between (Climber). It hangs there until the next.
 */
public class SurfaceWanderGoal extends Goal {
	private static final int TIME_LIMIT = 240;
	private final MobEntity mob;
	private final SurfaceWalker walker;
	@Nullable
	private Vec3d spot;
	private int ticks;

	/** mob: a SurfaceWalker. */
	public SurfaceWanderGoal(MobEntity mob) {
		this.mob = mob;
		this.walker = (SurfaceWalker) mob;
		this.setControls(EnumSet.of(Goal.Control.MOVE));
	}

	@Override
	public boolean canStart() {
		if (!Climber.enabled() || this.mob.getTarget() != null || this.mob.hasVehicle()
				|| this.mob.getRandom().nextInt(this.walker.eternalreturn$climber().offFloor() ? 80 : 240) != 0) {
			return false;
		}
		this.spot = this.pick();
		return this.spot != null;
	}

	@Override
	public boolean shouldContinue() {
		return this.spot != null && this.mob.getTarget() == null && this.ticks < TIME_LIMIT
				&& this.mob.getBoundingBox().getCenter().squaredDistanceTo(this.spot) > 2.25;
	}

	@Override
	public void start() {
		this.ticks = 0;
		this.mob.getNavigation().stop();
	}

	@Override
	public void stop() {
		this.spot = null;
	}

	@Override
	public void tick() {
		this.ticks++;
		this.walker.eternalreturn$climber().setDestination(this.spot, 0.8);
	}

	/** An open block it can see with a wall or ceiling beside it (off the floor, a floor will do too). */
	@Nullable
	private Vec3d pick() {
		World world = this.mob.getWorld();
		Random random = this.mob.getRandom();
		BlockPos at = this.mob.getBlockPos();
		Vec3d eyes = this.mob.getEyePos();
		for (int i = 0; i < 16; i++) {
			BlockPos pos = at.add(random.nextInt(17) - 8, random.nextInt(10) - 3, random.nextInt(17) - 8);
			if (!world.isAir(pos) || !world.isAir(pos.up())) {
				continue;
			}
			boolean surface = false;
			for (Direction side : Direction.values()) {
				if (side == Direction.DOWN && !this.walker.eternalreturn$climber().offFloor()) {
					continue;
				}
				if (world.getBlockState(pos.offset(side)).isSideSolidFullSquare(world, pos.offset(side), side.getOpposite())) {
					surface = true;
					break;
				}
			}
			if (!surface) {
				continue;
			}
			Vec3d spot = Vec3d.ofCenter(pos);
			if (world.raycast(new RaycastContext(eyes, spot, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mob)).getType()
					== HitResult.Type.MISS) {
				return spot;
			}
		}
		return null;
	}
}
