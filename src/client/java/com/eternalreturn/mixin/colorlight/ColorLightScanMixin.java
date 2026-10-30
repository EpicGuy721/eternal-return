package com.eternalreturn.mixin.colorlight;

import com.eternalreturn.client.ColorLightBrighter;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * ColorLight scans each chunk for light sources, reading each block's light level: here soul lights and candles read
 * brighter (ColorLightBrighter). Applied only when ColorLight is installed and still scans this way (ColorLightMixinPlugin).
 */
@Mixin(targets = "me.mrhikmen.colorlight.client.core.light.scan.ColorLightChunkScanner", remap = false)
public abstract class ColorLightScanMixin {
	@Redirect(method = "lambda$scanChunk$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getLuminance()I", remap = true), remap = false)
	private static int eternalreturn$brighter(BlockState state) {
		return ColorLightBrighter.level(state);
	}
}
