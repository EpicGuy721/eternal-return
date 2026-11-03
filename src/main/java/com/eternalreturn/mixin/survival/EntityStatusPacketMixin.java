package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.HiddenCoordinates;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Full debug info sent to a survival player (the reducedDebugInfo game rule turned off) stays reduced
 * (HiddenCoordinates). The constructor's only byte is the status.
 */
@Mixin(EntityStatusS2CPacket.class)
public abstract class EntityStatusPacketMixin {
	@ModifyVariable(method = "<init>(Lnet/minecraft/entity/Entity;B)V", at = @At("HEAD"), argsOnly = true)
	private static byte eternalreturn$keepReduced(byte status, Entity entity) {
		if (status == EntityStatuses.USE_FULL_DEBUG_INFO && entity instanceof ServerPlayerEntity player && HiddenCoordinates.hidden(player)) {
			return EntityStatuses.USE_REDUCED_DEBUG_INFO;
		}
		return status;
	}
}
