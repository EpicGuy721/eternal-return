package com.eternalreturn.mixin.villager;

import com.eternalreturn.villager.WanderingTraders;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.WanderingTraderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wandering traders only come to a player near a village (WanderingTraders). With nobody near one, the try
 * fails like an unlucky roll, so the chance keeps building up as in vanilla.
 */
@Mixin(WanderingTraderManager.class)
public abstract class WanderingTraderManagerMixin {
	@Inject(method = "trySpawn", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$needsVillage(ServerWorld world, CallbackInfoReturnable<Boolean> cir) {
		if (WanderingTraders.playerNearVillage(world) == null) {
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "trySpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getRandomAlivePlayer()Lnet/minecraft/server/network/ServerPlayerEntity;"))
	private net.minecraft.server.network.ServerPlayerEntity eternalreturn$villagePlayer(ServerWorld world, Operation<net.minecraft.server.network.ServerPlayerEntity> original) {
		return WanderingTraders.playerNearVillage(world);
	}
}
