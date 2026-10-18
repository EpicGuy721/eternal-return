package com.eternalreturn.mixin.compat;

import com.eternalreturn.compat.ModernerBetaHooks;
import com.eternalreturn.worldgen.terrain.TerrainShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Where Moderner Beta works out a terrain column's depth and scale from the biomes around it: deep
 * stepped oceans and sinkholes in Eternal Return worlds (TerrainShape). Elsewhere the value passes through.
 */
@Mixin(targets = "mod.bluestaggo.modernerbeta.api.level.chunk.ChunkProviderForcedHeight", remap = false)
public abstract class ModernerBetaHeightMixin {
	@Unique
	private TerrainShape.State eternalreturn$shape;
	@Unique
	private static volatile MethodHandle eternalreturn$depth;
	@Unique
	private static volatile MethodHandle eternalreturn$scale;
	@Unique
	private static volatile MethodHandle eternalreturn$make;

	@Inject(method = "getHeightConfigAt", at = @At("RETURN"), cancellable = true, remap = false)
	private void eternalreturn$reshape(int noiseX, int noiseZ, CallbackInfoReturnable<Object> cir) {
		TerrainShape.State shape = this.eternalreturn$shape;
		if (shape == null) {
			shape = TerrainShape.State.of(this);
			this.eternalreturn$shape = shape;
			ModernerBetaHooks.HEIGHT_PROVIDERS.incrementAndGet();
		}
		Object config = cir.getReturnValue();
		if (config == null) {
			return;
		}
		try {
			if (eternalreturn$make == null) {
				Class<?> type = config.getClass();
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				eternalreturn$depth = lookup.findVirtual(type, "depth", MethodType.methodType(float.class));
				eternalreturn$scale = lookup.findVirtual(type, "scale", MethodType.methodType(float.class));
				eternalreturn$make = lookup.findConstructor(type, MethodType.methodType(void.class, float.class, float.class));
			}
			float depth = (float) eternalreturn$depth.invoke(config);
			float scale = (float) eternalreturn$scale.invoke(config);
			float[] column = {depth, scale};
			TerrainShape.reshape(shape, noiseX, noiseZ, column);
			if (column[0] != depth || column[1] != scale) {
				cir.setReturnValue(eternalreturn$make.invoke(column[0], column[1]));
			}
		} catch (Throwable e) {
			// Leave Moderner Beta's own value.
		}
	}
}
