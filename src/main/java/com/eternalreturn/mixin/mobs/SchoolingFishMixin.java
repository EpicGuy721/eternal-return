package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.FishSchoolGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.FollowGroupLeaderGoal;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.SchoolingFishEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Schooling fish school by the boids rules (FishSchoolGoal) rather than following a leader. The boids goal is checked
 * ahead of random swimming; with mobs.fishSchools off it never starts, and the fish swim alone as vanilla's do.
 */
@Mixin(SchoolingFishEntity.class)
public abstract class SchoolingFishMixin extends FishEntity {
	protected SchoolingFishMixin(EntityType<? extends FishEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void eternalreturn$boids(CallbackInfo ci) {
		this.goalSelector.clear(goal -> goal instanceof FollowGroupLeaderGoal);
		this.goalSelector.add(3, new FishSchoolGoal((SchoolingFishEntity) (Object) this));
	}
}
