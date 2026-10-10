package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.ZombieHabits;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Phantoms sometimes carry a skeleton (ZombieHabits.trySkeletonRider). */
@Mixin(PhantomEntity.class)
public abstract class PhantomEntityMixin {
	@Inject(method = "initialize", at = @At("RETURN"))
	private void eternalreturn$skeletonRider(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, EntityData data, CallbackInfoReturnable<EntityData> cir) {
		ZombieHabits.trySkeletonRider((PhantomEntity) (Object) this, world, difficulty, reason, EternalReturnConfig.get().mobs.phantomSkeletonChance);
	}
}
