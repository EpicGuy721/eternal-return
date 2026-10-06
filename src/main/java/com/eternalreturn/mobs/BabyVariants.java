package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/**
 * Baby skeletons, strays and creepers. The flag itself lives in a tracked data entry added by
 * AbstractSkeletonEntityMixin and CreeperEntityMixin; vanilla's isBaby() hooks then halve the
 * hitbox, shadow and eye height and raise the voice pitch on their own.
 */
public final class BabyVariants {
	private static final Identifier SPEED_MODIFIER_ID = EternalReturn.id("baby_speed");

	private BabyVariants() {
	}

	public static boolean supportsBaby(Entity entity) {
		return entity instanceof AbstractSkeletonEntity || entity instanceof CreeperEntity;
	}

	public static void rollOnSpawn(MobEntity mob, Random random) {
		if (supportsBaby(mob) && !mob.isBaby() && mob.getType().isIn(MobTags.BABY_VARIANTS)
				&& random.nextFloat() < EternalReturnConfig.get().mobs.babyChance) {
			mob.setBaby(true);
		}
	}

	/** Same approach as ZombieEntity.setBaby: a temporary modifier, re-added whenever the flag is loaded. */
	public static void applySpeed(MobEntity mob, boolean baby) {
		if (mob.getWorld() == null || mob.getWorld().isClient) {
			return;
		}
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(SPEED_MODIFIER_ID);
		double bonus = EternalReturnConfig.get().mobs.babySpeedBonus;
		if (baby && bonus != 0) {
			speed.addTemporaryModifier(new EntityAttributeModifier(SPEED_MODIFIER_ID, bonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	public static int creeperFuse(int fuse) {
		// getClientFuseTime divides by (fuse - 2), so never go below 3.
		return Math.max(3, (int) Math.round(fuse * EternalReturnConfig.get().mobs.babyCreeperFuseMultiplier));
	}

	public static float creeperExplosionPower(float power) {
		return (float) (power * EternalReturnConfig.get().mobs.babyCreeperExplosionMultiplier);
	}
}
