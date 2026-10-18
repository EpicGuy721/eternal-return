package com.eternalreturn.mobs.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The parched, a skeleton of the desert (com.eternalreturn.mobs.NewMobs): it doesn't burn in daylight, and its arrows
 * give Weakness (15 seconds). It spawns in deserts in place of most skeletons.
 */
public class ParchedEntity extends SkeletonEntity {
	public ParchedEntity(EntityType<? extends SkeletonEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected boolean isAffectedByDaylight() {
		return false;
	}

	@Override
	protected PersistentProjectileEntity createArrowProjectile(ItemStack arrow, float damageModifier, @Nullable ItemStack shotFrom) {
		PersistentProjectileEntity projectile = super.createArrowProjectile(arrow, damageModifier, shotFrom);
		if (projectile instanceof ArrowEntity tipped) {
			tipped.addEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 300));
		}
		return projectile;
	}

	/** Powder snow doesn't make a stray of it. */
	@Override
	protected void convertToStray() {
	}
}
