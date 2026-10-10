package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.Blazes;
import com.eternalreturn.mobs.Slimes;
import com.eternalreturn.survival.BeaconZones;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.util.math.Box;
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
		// Beacons: no monsters inside their area, animals only there (BeaconZones).
		if (spawnReason == SpawnReason.NATURAL && !BeaconZones.allowsNaturalSpawn(world.toServerWorld(), type, pos)) {
			cir.setReturnValue(false);
			return;
		}
		// Slimes in the rain at night, any biome (Slimes).
		if (type == EntityType.SLIME && spawnReason == SpawnReason.NATURAL && Slimes.rainSpawn(world.toServerWorld(), pos)) {
			cir.setReturnValue(true);
			return;
		}
		// Blazes outside nether fortresses (Blazes).
		if (type == EntityType.BLAZE && spawnReason == SpawnReason.NATURAL && !Blazes.mayFreeSpawn(world.toServerWorld(), pos)) {
			cir.setReturnValue(false);
			return;
		}
		// Natural elder guardians (see OceanSpawns): rarer than their spawn weight alone, and never two close together.
		if (type == EntityType.ELDER_GUARDIAN && spawnReason == SpawnReason.NATURAL) {
			EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
			if (random.nextDouble() >= cfg.elderGuardianSpawnChance
					|| !world.getEntitiesByClass(ElderGuardianEntity.class, new Box(pos).expand(cfg.elderGuardianSpacing), elder -> true).isEmpty()) {
				cir.setReturnValue(false);
			}
		}
	}
}
