package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.HiddenCoordinates;
import com.eternalreturn.survival.RandomSpawns;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A death with no bed respawns the player at a random spot (RandomSpawns); a game mode change tells the client whether
 * its coordinates are now hidden (HiddenCoordinates).
 */
@Mixin(ServerPlayerEntity.class)
public abstract class RandomSpawnMixin {
	@ModifyReturnValue(method = "getRespawnTarget", at = @At("RETURN"))
	private TeleportTarget eternalreturn$randomRespawn(TeleportTarget target, @Local(argsOnly = true) boolean alive) {
		return RandomSpawns.respawn((ServerPlayerEntity) (Object) this, alive, target);
	}

	@Inject(method = "changeGameMode", at = @At("RETURN"))
	private void eternalreturn$coordinatesForMode(GameMode gameMode, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			HiddenCoordinates.send((ServerPlayerEntity) (Object) this);
		}
	}
}
