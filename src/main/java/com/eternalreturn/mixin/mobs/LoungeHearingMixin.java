package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.ai.Lounging;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Noises (game events) wake lounging monsters nearby (Lounging.hear). */
@Mixin(ServerWorld.class)
public abstract class LoungeHearingMixin {
	@Inject(method = "emitGameEvent", at = @At("HEAD"))
	private void eternalreturn$wakeLoungers(RegistryEntry<GameEvent> event, Vec3d emitterPos, GameEvent.Emitter emitter, CallbackInfo ci) {
		Lounging.hear((ServerWorld) (Object) this, event, emitterPos, emitter);
	}
}
