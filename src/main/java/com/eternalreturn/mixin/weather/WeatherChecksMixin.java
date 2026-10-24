package com.eternalreturn.mixin.weather;

import com.eternalreturn.weather.LocalWeather;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fire asks first whether it rains anywhere, then around itself: with storms (LocalWeather) the second question
 * decides, so rain puts out fires and stops them spreading only under a storm.
 */
@Mixin(FireBlock.class)
public abstract class WeatherChecksMixin {
	@WrapOperation(method = "scheduledTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;isRaining()Z"))
	private boolean eternalreturn$askAround(ServerWorld world, Operation<Boolean> original) {
		return LocalWeather.mayRain(world) || original.call(world);
	}
}
