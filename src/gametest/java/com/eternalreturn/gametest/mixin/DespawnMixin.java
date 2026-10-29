package com.eternalreturn.gametest.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mobs don't despawn on the test server: with no player in the world (as in most tests) nothing despawns anyway, but a
 * scripted player in one test (or one a failed test left behind) made the mobs of tests 128 blocks off vanish at once,
 * and those 32 blocks off now and then. Whether a mob would despawn (isPersistent, canImmediatelyDespawn) is unchanged.
 */
@Mixin(MobEntity.class)
public abstract class DespawnMixin {
	@ModifyExpressionValue(method = "checkDespawn", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/World;getClosestPlayer(Lnet/minecraft/entity/Entity;D)Lnet/minecraft/entity/player/PlayerEntity;"))
	private PlayerEntity eternalreturn$noPlayerToDespawnBy(PlayerEntity closest) {
		return null;
	}
}
