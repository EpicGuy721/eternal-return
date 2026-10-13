package com.eternalreturn.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mixin.mobs.CreeperEntityAccessor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

/**
 * Large zombies, skeletons and creepers (every kind of each): mobs.largeVariantChance (0.5%) of them spawn
 * twice the size (the scale attribute, so the hitbox doubles too, and it is saved with the mob). They never
 * hold anything (a large skeleton fights with its fists), but can wear armor. A large zombie only goes for
 * players and iron golems: no animals, villagers or turtles, no raw meat, no lights or beds. A large creeper's
 * fuse is twice as long and its blast twice as big. Only where there is room for the bigger body.
 */
public final class LargeMobs {
	public static final double SCALE = 2.0;

	private LargeMobs() {
	}

	public static boolean isLarge(LivingEntity mob) {
		EntityAttributeInstance scale = mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		return scale != null && scale.getBaseValue() >= SCALE - 0.01;
	}

	public static boolean canBeLarge(LivingEntity mob) {
		return mob instanceof ZombieEntity || mob instanceof AbstractSkeletonEntity || mob instanceof CreeperEntity;
	}

	/** As a zombie, skeleton or creeper spawns (after its gear): maybe large. */
	public static void roll(MobEntity mob, Random random) {
		if (!canBeLarge(mob) || mob.isBaby() || isLarge(mob) || random.nextDouble() >= EternalReturnConfig.get().mobs.largeVariantChance) {
			return;
		}
		makeLarge(mob);
	}

	public static boolean makeLarge(MobEntity mob) {
		Box box = mob.getBoundingBox();
		Box large = new Box(mob.getX() - box.getLengthX(), box.minY, mob.getZ() - box.getLengthZ(),
				mob.getX() + box.getLengthX(), box.minY + box.getLengthY() * SCALE, mob.getZ() + box.getLengthZ());
		if (!mob.getWorld().isSpaceEmpty(mob, large)) {
			return false;
		}
		EntityAttributeInstance scale = mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale == null) {
			return false;
		}
		scale.setBaseValue(SCALE);
		mob.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		if (mob instanceof CreeperEntity creeper) {
			CreeperEntityAccessor accessor = (CreeperEntityAccessor) creeper;
			accessor.eternalreturn$setFuseTime(accessor.eternalreturn$getFuseTime() * 2);
			accessor.eternalreturn$setExplosionRadius(accessor.eternalreturn$getExplosionRadius() * 2);
		}
		mob.calculateDimensions();
		return true;
	}
}
