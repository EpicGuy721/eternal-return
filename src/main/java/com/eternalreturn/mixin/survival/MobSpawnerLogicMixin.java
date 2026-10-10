package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.SpawnerMobs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks each mob a monster spawner lets out (SpawnerMobs), whatever spawn data the spawner holds. */
@Mixin(MobSpawnerLogic.class)
public abstract class MobSpawnerLogicMixin {
	@WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;spawnNewEntityAndPassengers(Lnet/minecraft/entity/Entity;)Z"))
	private boolean eternalreturn$markSpawnerMob(ServerWorld world, Entity entity, Operation<Boolean> original) {
		SpawnerMobs.mark(entity);
		return original.call(world, entity);
	}
}
