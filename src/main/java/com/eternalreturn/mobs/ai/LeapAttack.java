package com.eternalreturn.mobs.ai;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.climbing.SurfaceWalker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Vec3d;

/**
 * A melee fighter that isn't heavy (entity tag eternalreturn:heavy_mobs) leaps at a target standing about 2 blocks
 * above it and close by (on a pillar, a ledge), timed for its swing, and a hit in the leap knocks the target back hard,
 * to knock it off. Spiders and gimps climb up instead. Driven from MeleeAttackGoalMixin.
 */
public final class LeapAttack {
	public static final TagKey<EntityType<?>> HEAVY = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("heavy_mobs"));
	/** Ticks a leap counts as one (for the hit's knockback). */
	public static final int LEAP_TICKS = 12;
	/** Extra knockback of a hit in a leap. */
	public static final double KNOCKBACK = 0.8;

	private LeapAttack() {
	}

	/** Whether it should leap at its target now (its swing ready within cooldownLeft ticks). */
	public static boolean shouldLeap(MobEntity mob, LivingEntity target, int cooldownLeft) {
		if (!mob.isOnGround() || mob.isTouchingWater() || mob.hasVehicle() || mob.getType().isIn(HEAVY) || mob instanceof SurfaceWalker
				|| cooldownLeft > 2 || mob.isInAttackRange(target)) {
			return false;
		}
		double rise = target.getY() - mob.getY();
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double reach = mob.getWidth() / 2.0 + target.getWidth() / 2.0 + 1.0;
		return rise >= 1.4 && rise <= 2.6 && dx * dx + dz * dz <= reach * reach;
	}

	/** Up, and a little toward the target. */
	public static void leap(MobEntity mob, LivingEntity target) {
		Vec3d toward = new Vec3d(target.getX() - mob.getX(), 0.0, target.getZ() - mob.getZ());
		Vec3d push = toward.lengthSquared() > 1.0E-4 ? toward.normalize().multiply(0.15) : Vec3d.ZERO;
		mob.setVelocity(mob.getVelocity().x + push.x, 0.5, mob.getVelocity().z + push.z);
		mob.velocityDirty = true;
	}

	/** A hit in a leap: knocked back hard, away from the leaper. */
	public static void knockOff(MobEntity mob, LivingEntity target) {
		target.takeKnockback(KNOCKBACK, mob.getX() - target.getX(), mob.getZ() - target.getZ());
	}
}
