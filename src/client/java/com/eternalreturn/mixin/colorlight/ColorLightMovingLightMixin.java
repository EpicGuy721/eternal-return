package com.eternalreturn.mixin.colorlight;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * ColorLight (an optional client mod) lights things that LambDynamicLights moves (a held or dropped torch, a lantern in
 * a frame) at half their light level: a held torch (14) and a held lantern (15) both light as a level 7 block, far
 * weaker than the same block placed. Here they get their whole level, so a held torch lights as a placed one does.
 * Applied only when ColorLight is installed and the halving is still where it was (ColorLightMixinPlugin).
 */
@Mixin(targets = "me.mrhikmen.colorlight.client.compat.lambdynlights.ColorLightEntityLightTicker", remap = false)
public abstract class ColorLightMovingLightMixin {
	@ModifyConstant(method = "apply", constant = @Constant(intValue = 2), remap = false)
	private static int eternalreturn$wholeLevel(int halves) {
		return 1;
	}
}
