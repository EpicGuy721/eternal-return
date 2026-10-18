package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.ai.WitherSkullShotGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wither skeletons: four times the health (mobs.witherSkeletonHealth, 80), and wither skulls at far targets. */
@Mixin(WitherSkeletonEntity.class)
public abstract class WitherSkeletonMixin extends AbstractSkeletonEntity {
	protected WitherSkeletonMixin(EntityType<? extends AbstractSkeletonEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void eternalreturn$tougher(EntityType<? extends WitherSkeletonEntity> type, World world, CallbackInfo ci) {
		var health = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(EternalReturnConfig.get().mobs.witherSkeletonHealth);
			this.setHealth(this.getMaxHealth());
		}
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void eternalreturn$skulls(CallbackInfo ci) {
		this.goalSelector.add(3, new WitherSkullShotGoal(this));
	}
}
