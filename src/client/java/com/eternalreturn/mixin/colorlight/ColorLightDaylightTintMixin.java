package com.eternalreturn.mixin.colorlight;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ColorLight's shader (read through Sodium's shader loader from the mod files, so a resource pack can't replace it)
 * colours light as far as its light, not daylight, makes a block bright, with that share counted ten times over. Here
 * the share is also scaled down by how far daylight takes the colour out for the whole view (header slot 10, written by
 * ColorLightHeaderMixin): none outdoors at noon, ColorLight's own colours at night, in caves and without sky, and
 * between as the sun sets or the camera goes underground. Applied only with Sodium and ColorLight
 * (ColorLightMixinPlugin); if ColorLight's shader no longer has that line, it is left as it is with a warning.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class ColorLightDaylightTintMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("eternalreturn");
	private static final String SHADER = "include/colorlight_light.glsl";
	private static final String STEEP = "float blockShare = (fullPeak > 0.0001) ? clamp(1.0 - skyPeak / fullPeak, 0.0, 1.0) * 10.0 : 1.0;";
	private static final String EASED = "float blockShare = (fullPeak > 0.0001) ? clamp(1.0 - skyPeak / fullPeak, 0.0, 1.0) * 10.0 * (1.0 - cl_headerFloat(10)) : 1.0;";

	@Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
	private static void eternalreturn$easedDaylightTint(Identifier name, CallbackInfoReturnable<String> cir) {
		if (!name.getNamespace().equals("colorlight") || !name.getPath().equals(SHADER)) {
			return;
		}
		String source = cir.getReturnValue();
		if (source == null || !source.contains(STEEP)) {
			LOGGER.warn("Eternal Return: ColorLight's shader has changed, so its colours by day are left as ColorLight draws them.");
			return;
		}
		cir.setReturnValue(source.replace(STEEP, EASED));
	}
}
