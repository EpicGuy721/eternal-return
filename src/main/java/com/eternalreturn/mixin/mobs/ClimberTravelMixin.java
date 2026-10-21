package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.climbing.SurfaceWalker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A surface walker on a wall or ceiling moves along it (Climber.travel) instead of walking and falling. */
@Mixin(LivingEntity.class)
public abstract class ClimberTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$climb(Vec3d movementInput, CallbackInfo ci) {
		if ((Object) this instanceof SurfaceWalker walker && walker.eternalreturn$climber().travel()) {
			ci.cancel();
		}
	}
}
