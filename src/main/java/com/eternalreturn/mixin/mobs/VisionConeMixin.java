package com.eternalreturn.mixin.mobs;

import com.eternalreturn.mobs.ai.VisionCone;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Monsters see players only in their vision cone (VisionCone): where a check that respects sight (spotting a target,
 * or someone to look at) would pass, it fails for a player outside the cone. Checks that ignore sight (striking back
 * at an attacker) are left alone.
 */
@Mixin(TargetPredicate.class)
public abstract class VisionConeMixin {
	@Shadow
	private boolean respectsVisibility;

	@Inject(method = "test", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$visionCone(@Nullable LivingEntity base, LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.respectsVisibility && base != null && !VisionCone.sees(base, target)) {
			cir.setReturnValue(false);
		}
	}
}
