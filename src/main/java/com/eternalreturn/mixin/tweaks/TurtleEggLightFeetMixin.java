package com.eternalreturn.mixin.tweaks;

import com.eternalreturn.survival.Growing;
import net.minecraft.block.TurtleEggBlock;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Feather Falling boots don't crack turtle eggs (Growing.treadsLightly). */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggLightFeetMixin {
	@Inject(method = "breaksEgg", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$lightFeet(World world, Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Growing.treadsLightly(entity)) {
			cir.setReturnValue(false);
		}
	}
}
