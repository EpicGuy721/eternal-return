package com.eternalreturn.gametest.mixin;

import com.eternalreturn.gametest.Heard;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records the blocks shown to the players watching an entity while it is still there (Heard.shown): a landing block's. */
@Mixin(ServerChunkManager.class)
public abstract class ShownMixin {
	@Inject(method = "sendToOtherNearbyPlayers", at = @At("HEAD"))
	private void eternalreturn$shown(Entity entity, Packet<?> packet, CallbackInfo ci) {
		if (packet instanceof BlockUpdateS2CPacket update && !entity.isRemoved()) {
			Heard.shown(update.getPos(), update.getState());
		}
	}
}
