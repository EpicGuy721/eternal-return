package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.ai.SpiderWebGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spiders and cave spiders fling webs (SpiderWebGoal). */
@Mixin(SpiderEntity.class)
public abstract class SpiderGoalsMixin extends HostileEntity {
	protected SpiderGoalsMixin(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void eternalreturn$webs(CallbackInfo ci) {
		this.goalSelector.add(3, new SpiderWebGoal((SpiderEntity) (Object) this));
	}
}
