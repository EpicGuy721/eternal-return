package com.eternalreturn.mixin.colorlight;

import com.eternalreturn.client.ColorLightFollow;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * ColorLight builds its block colours from its settings and swaps the new table in: just before, related lights take
 * their source's colour (ColorLightFollow). Applied only when ColorLight is installed and still builds it this way
 * (ColorLightMixinPlugin).
 */
@Mixin(targets = "me.mrhikmen.colorlight.client.core.light.registry.ColorLightBlockRegistry", remap = false)
public abstract class ColorLightFollowMixin {
	@SuppressWarnings("rawtypes")
	@Shadow
	private static volatile Map byBlock;

	@SuppressWarnings("rawtypes")
	@Redirect(method = "load", at = @At(value = "FIELD", target = "Lme/mrhikmen/colorlight/client/core/light/registry/ColorLightBlockRegistry;byBlock:Ljava/util/Map;", opcode = Opcodes.PUTSTATIC), remap = false)
	private static void eternalreturn$follow(Map colours) {
		byBlock = ColorLightFollow.apply(colours);
	}
}
