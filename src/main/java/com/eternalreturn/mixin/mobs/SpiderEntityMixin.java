package com.eternalreturn.mixin.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.Jockeys;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpiderEntity.class)
public abstract class SpiderEntityMixin {
	/** Vanilla's "nextInt(100) == 0" always-skeleton jockey roll is switched off; the roll below replaces it. */
	@ModifyExpressionValue(method = "initialize", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I"))
	private int eternalreturn$disableVanillaJockey(int roll) {
		return 1;
	}

	@Inject(method = "initialize", at = @At("RETURN"))
	private void eternalreturn$jockey(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
			EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
		if (world.getRandom().nextFloat() < EternalReturnConfig.get().mobs.spiderJockeyChance) {
			Jockeys.addSpiderRider((MobEntity) (Object) this, world, difficulty, spawnReason);
		}
	}
}
