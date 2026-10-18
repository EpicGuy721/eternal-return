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
	/**
	 * Phantoms come on one night in mobs.phantomNightChance (13), whoever has slept: that night every player out
	 * under the sky draws them as if they hadn't slept for days; on other nights none come.
	 */
	@WrapOperation(method = "spawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/random/Random;nextInt(I)I", ordinal = 1))
	private int eternalreturn$phantomNights(net.minecraft.util.math.random.Random random, int bound, Operation<Integer> original,
			@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) net.minecraft.server.world.ServerWorld world) {
		int chance = com.eternalreturn.config.EternalReturnConfig.get().mobs.phantomNightChance;
		if (chance <= 0) {
			return original.call(random, bound);
		}
		return com.eternalreturn.mobs.SpawnTweaks.isPhantomNight(world) ? 72000 : 0;
	}

	@WrapOperation(method = "spawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;isSpectator()Z"))
	private boolean eternalreturn$beaconShelters(ServerPlayerEntity player, Operation<Boolean> original) {
		return original.call(player) || BeaconZones.blocksMonstersAt(player.getWorld(), player.getBlockPos());
	}
}
