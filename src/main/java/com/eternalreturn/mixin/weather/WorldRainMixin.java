package com.eternalreturn.mixin.weather;

import com.eternalreturn.weather.LocalWeather;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Whether it rains on a spot (mobs burning or not, crops, farmland, fishing, endermen, fire...) asks the storms there
 * (LocalWeather), on the server and in a player's game alike; the sky and biome checks stay vanilla's.
 */
@Mixin(World.class)
public abstract class WorldRainMixin {
	@WrapOperation(method = "hasRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isRaining()Z"))
	private boolean eternalreturn$stormHere(World world, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
		return LocalWeather.field(world) != null ? LocalWeather.rainingAt(world, pos) : original.call(world);
	}
}
