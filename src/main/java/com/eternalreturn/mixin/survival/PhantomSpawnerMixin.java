package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.BeaconZones;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** No phantoms come for a player inside a working beacon's area (BeaconZones): they are passed over like spectators. */
@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerMixin {
	@WrapOperation(method = "spawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isSpectator()Z"))
	private boolean eternalreturn$beaconShelters(ServerPlayerEntity player, Operation<Boolean> original) {
		return original.call(player) || BeaconZones.blocksMonstersAt(player.getWorld(), player.getBlockPos());
	}
}
