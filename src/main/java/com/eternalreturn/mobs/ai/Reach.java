package com.eternalreturn.mobs.ai;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Whether a mob can get its hands (or paws) on a block, to break it, dig it or tear it open: the block close by
 * it (within ARM of its body, or up to `up` above its head), nothing between its eyes and the block, and its head
 * turned to look at it (within FACING degrees). Zombies (BreakLightsGoal, MineTowardTargetGoal) and bears (BearEntity).
 */
public final class Reach {
	/** How far past its body a mob reaches (blocks). */
	public static final double ARM = 1.0;
	/** How far off its line of sight it may be looking (degrees). */
	public static final double FACING = 60.0;

	private Reach() {
	}

	/** Within reach, in sight and looked at: see the class. */
	public static boolean canReach(MobEntity mob, BlockPos pos, double up) {
		return inReach(mob, pos, up) && inSight(mob, pos) && facing(mob, pos.toCenterPos());
	}

	/** The block right against the mob: within ARM of its body, and up to `up` more above its head. */
	public static boolean inReach(MobEntity mob, BlockPos pos, double up) {
		Box box = mob.getBoundingBox();
		Box reach = new Box(box.minX - ARM, box.minY - ARM, box.minZ - ARM, box.maxX + ARM, box.maxY + ARM + up, box.maxZ + ARM);
		return reach.intersects(new Box(pos));
	}

	/** Nothing between its eyes and the block. */
	public static boolean inSight(MobEntity mob, BlockPos pos) {
		BlockHitResult hit = mob.getWorld().raycast(new RaycastContext(mob.getEyePos(), pos.toCenterPos(), RaycastContext.ShapeType.OUTLINE,
				RaycastContext.FluidHandling.NONE, mob));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	/** Its head turned to look at the point (within FACING degrees). */
	public static boolean facing(MobEntity mob, Vec3d point) {
		Vec3d to = point.subtract(mob.getEyePos());
		if (to.lengthSquared() < 1.0E-4) {
			return true;
		}
		Vec3d look = Vec3d.fromPolar(mob.getPitch(), mob.getHeadYaw());
		return look.dotProduct(to.normalize()) >= MathHelper.cos((float) Math.toRadians(FACING));
	}
}
