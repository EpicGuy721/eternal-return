package com.eternalreturn.mixin.colorlight;

import com.eternalreturn.client.ColorLightDaylight;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ColorLight sends its shader a header of 16 numbers each frame, of which it uses the first 10. The 11th (index 10)
 * carries how far daylight takes the colour out of its light (ColorLightDaylight), written before ColorLight checks
 * whether the header changed, so a new value is sent. 0, the slot's value without this mixin, leaves ColorLight's
 * colours as they are. Applied only with ColorLight and Sodium (ColorLightMixinPlugin).
 */
@Mixin(targets = "me.mrhikmen.colorlight.client.core.render.gpu.GpuLightVolume", remap = false)
public abstract class ColorLightHeaderMixin {
	@Shadow(remap = false)
	@Final
	private int[] header;

	@Inject(method = "writeHeader", at = @At(value = "FIELD", target = "Lme/mrhikmen/colorlight/client/core/render/gpu/GpuLightVolume;headerWritten:Z",
			opcode = Opcodes.GETFIELD, ordinal = 0), remap = false)
	private void eternalreturn$daylight(CallbackInfo ci) {
		this.header[10] = Float.floatToRawIntBits(ColorLightDaylight.value());
	}
}
