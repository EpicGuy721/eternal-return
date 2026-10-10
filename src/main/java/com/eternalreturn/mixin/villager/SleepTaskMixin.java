package com.eternalreturn.mixin.villager;

import com.eternalreturn.villager.VillagerNeeds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.task.SleepTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A villager won't lie down in its bed while it can see more than one other adult villager (VillagerNeeds). */
@Mixin(SleepTask.class)
public abstract class SleepTaskMixin {
	@Inject(method = "shouldRun", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$needsPrivacy(ServerWorld world, LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && entity instanceof VillagerEntity villager && !VillagerNeeds.hasPrivacy(villager)) {
			cir.setReturnValue(false);
		}
	}
}
