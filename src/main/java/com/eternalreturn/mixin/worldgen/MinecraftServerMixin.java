package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.EternalReturnServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Works out whether this server's world is an Eternal Return world before its dimensions are made (EternalReturnServer). */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Inject(method = "createWorlds", at = @At("HEAD"))
	private void eternalreturn$detectWorld(WorldGenerationProgressListener listener, CallbackInfo ci) {
		EternalReturnServer.detect((MinecraftServer) (Object) this);
	}
}
