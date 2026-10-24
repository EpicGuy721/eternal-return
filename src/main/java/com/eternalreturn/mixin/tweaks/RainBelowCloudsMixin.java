package com.eternalreturn.mixin.tweaks;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rain falls from the clouds, so not above them: in a world with a sky, nothing above cloud height (CLOUDS, the
 * overworld's) is rained on (fires stay lit, mobs dry). The client draws no rain there either (WeatherAboveCloudsMixin).
 */
@Mixin(World.class)
public abstract class RainBelowCloudsMixin {
	@Unique
	private static final int CLOUDS = 192;

	@Inject(method = "hasRain", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noRainAboveClouds(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		World world = (World) (Object) this;
		if (pos.getY() >= CLOUDS && world.getDimension().hasSkyLight() && !world.getDimension().hasCeiling()) {
			cir.setReturnValue(false);
		}
	}
}
