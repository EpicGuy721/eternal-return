package com.eternalreturn.mixin.colorlight;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two lines of ColorLight's shader maths (read through Sodium's shader loader from the mod files, so a resource pack
 * can't replace it), rewritten as Sodium loads it:
 * <ul>
 * <li>By day ColorLight coloured light only as far as that light, not daylight, made a block bright, counted ten times
 * over: a torch in the open lit white, one a sky level into shade fully coloured beside it, in hard-edged patches.
 * Here light is coloured day and night alike, as it is at night.</li>
 * <li>Where ColorLight has no light of its own for a face, it drew the face pitch black, ignoring the game's light
 * there: the sliver of a block face above flowing lava, along every lava pool's edge. Here such a face takes the game's
 * own light (none in the dark, so dark places stay dark); faces ColorLight lights are unchanged.</li>
 * </ul>
 * Applied only with Sodium and ColorLight (ColorLightMixinPlugin); a line that is no longer there is left as it is with
 * a warning.
 */
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public abstract class ColorLightShaderMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("eternalreturn");
	private static final String SHADER = "include/colorlight_light.glsl";
	private static final String DAYLIGHT = "float blockShare = (fullPeak > 0.0001) ? clamp(1.0 - skyPeak / fullPeak, 0.0, 1.0) * 10.0 : 1.0;";
	private static final String ALWAYS = "float blockShare = 1.0;";
	private static final String NO_LIGHT_BLACK = "float blockLevel = max(lvl, fullBright);";
	private static final String NO_LIGHT_GAME = "float blockLevel = max(lvl, lvl > 0.0 ? fullBright : vanillaBlock);";

	@Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, remap = false)
	private static void eternalreturn$rewrite(Identifier name, CallbackInfoReturnable<String> cir) {
		if (!name.getNamespace().equals("colorlight") || !name.getPath().equals(SHADER) || cir.getReturnValue() == null) {
			return;
		}
		String source = cir.getReturnValue();
		if (source.contains(DAYLIGHT)) {
			source = source.replace(DAYLIGHT, ALWAYS);
		} else {
			LOGGER.warn("Eternal Return: ColorLight's shader has changed, so its colours by day are left as ColorLight draws them.");
		}
		if (source.contains(NO_LIGHT_BLACK)) {
			source = source.replace(NO_LIGHT_BLACK, NO_LIGHT_GAME);
		} else {
			LOGGER.warn("Eternal Return: ColorLight's shader has changed, so faces it has no light for are left as ColorLight draws them.");
		}
		cir.setReturnValue(source);
	}
}
