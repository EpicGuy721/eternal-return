package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnRestriction.class)
public abstract class SpawnRestrictionMixin {
	/**
	 * Natural creeper spawns only where (almost) no sky light reaches: caves and other enclosed
	 * spaces, never the surface at night. Spawn eggs, spawners and commands are unaffected.
	 */
	@Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true)
	private static <T extends Entity> void eternalreturn$creepersUnderground(EntityType<T> type, ServerWorldAccess world, SpawnReason spawnReason,
			BlockPos pos, Random random, CallbackInfoReturnable<Boolean> cir) {
		if (type == EntityType.CREEPER && spawnReason == SpawnReason.NATURAL) {
			EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
			if (cfg.creepersOnlySpawnUnderground && world.getLightLevel(LightType.SKY, pos) > cfg.creeperMaxSkyLight) {
				cir.setReturnValue(false);
			}
		}
	}
}
