package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.Snowlogging;
import net.minecraft.server.network.ChunkDataSender;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A chunk sent to a player takes the snow logged in it along (Snowlogging). */
@Mixin(ChunkDataSender.class)
public abstract class ChunkSendMixin {
	@Inject(method = "sendChunkData", at = @At("TAIL"))
	private static void eternalreturn$withSnow(ServerPlayNetworkHandler handler, ServerWorld world, WorldChunk chunk, CallbackInfo ci) {
		Snowlogging.chunkSent(handler, chunk);
	}
}
