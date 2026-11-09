package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.landmarks.HotSprings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla's snow and ice (as the world is made and in its weather) stay off the ground round hot springs (HotSprings). */
@Mixin(Biome.class)
public abstract class BiomeHotSpringMixin {
	@Inject(method = "canSetIce(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;Z)Z", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noIceAtSprings(WorldView world, BlockPos pos, boolean doWaterCheck, CallbackInfoReturnable<Boolean> cir) {
		if (HotSprings.keepsClear(world, pos)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canSetSnow", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$noSnowAtSprings(WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (HotSprings.keepsClear(world, pos)) {
			cir.setReturnValue(false);
		}
	}
}
