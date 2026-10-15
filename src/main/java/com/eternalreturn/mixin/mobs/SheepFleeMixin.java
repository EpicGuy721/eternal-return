package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sheep run from wild wolves within 16 blocks (tamed ones are left alone, or a pet dog would scatter every flock). */
@Mixin(SheepEntity.class)
public abstract class SheepFleeMixin extends AnimalEntity {
	protected SheepFleeMixin(EntityType<? extends AnimalEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void eternalreturn$fleeWolves(CallbackInfo ci) {
		this.goalSelector.add(1, new FleeEntityGoal<>(this, WolfEntity.class, 16.0F, 1.1, 1.4,
				wolf -> EternalReturnConfig.get().mobs.sheepFleeWolves && !((WolfEntity) wolf).isTamed()));
	}
}
