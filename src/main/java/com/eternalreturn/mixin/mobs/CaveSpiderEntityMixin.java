package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.Jockeys;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's CaveSpiderEntity.initialize returns immediately without calling super, which is the only reason cave spider jockeys never appear. */
@Mixin(CaveSpiderEntity.class)
public abstract class CaveSpiderEntityMixin {
	@Inject(method = "initialize", at = @At("RETURN"))
	private void eternalreturn$jockey(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
		if (world.getRandom().nextFloat() < EternalReturnConfig.get().mobs.caveSpiderJockeyChance) {
			Jockeys.addSpiderRider((MobEntity) (Object) this, world, difficulty, spawnReason);
		}
	}
}
