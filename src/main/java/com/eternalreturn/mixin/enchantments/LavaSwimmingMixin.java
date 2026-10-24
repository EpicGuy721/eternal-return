package com.eternalreturn.mixin.enchantments;

import com.eternalreturn.logic.LavaSwimming;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A lava swimmer (LavaSwimming) isn't hurt or set alight by lava. (When they swim in it: SwimKeyMixin.) */
@Mixin(Entity.class)
public abstract class LavaSwimmingMixin {
	@Inject(method = "setOnFireFromLava", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$lavaProof(CallbackInfo ci) {
		if (LavaSwimming.swims((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
