package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.RandomSpawns;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** A player joining with no saved data is new to the world: they start at a random spot (RandomSpawns). */
@Mixin(PlayerManager.class)
public abstract class RandomFirstSpawnMixin {
	@WrapOperation(method = "onPlayerConnect", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/PlayerManager;loadPlayerData(Lnet/minecraft/server/network/ServerPlayerEntity;)Ljava/util/Optional;"))
	private Optional<NbtCompound> eternalreturn$randomFirstSpawn(PlayerManager manager, ServerPlayerEntity player, Operation<Optional<NbtCompound>> original) {
		Optional<NbtCompound> data = original.call(manager, player);
		if (data.isEmpty()) {
			RandomSpawns.firstSpawn(player);
		}
		return data;
	}
}
