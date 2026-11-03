package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.HiddenCoordinates;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A survival player joins with reduced debug info (HiddenCoordinates): the join packet's reducedDebugInfo flag
 * (its 7th argument, index 6, the first of its three booleans after the distances).
 */
@Mixin(PlayerManager.class)
public abstract class HiddenCoordinatesMixin {
	@ModifyArg(method = "onPlayerConnect", index = 6, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;<init>(IZLjava/util/Set;IIIZZZLnet/minecraft/network/packet/s2c/play/CommonPlayerSpawnInfo;Z)V"))
	private boolean eternalreturn$hideCoordinates(boolean reducedDebugInfo, @Local(argsOnly = true) ServerPlayerEntity player) {
		return reducedDebugInfo || HiddenCoordinates.hidden(player);
	}
}
