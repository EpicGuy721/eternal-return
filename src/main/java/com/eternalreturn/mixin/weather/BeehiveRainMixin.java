package com.eternalreturn.mixin.weather;

import com.eternalreturn.weather.LocalWeather;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bees stay in the hive while a storm is over it (LocalWeather), not whenever it rains anywhere. */
@Mixin(BeehiveBlockEntity.class)
public abstract class BeehiveRainMixin {
	@WrapOperation(method = "releaseBee", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isRaining()Z"))
	private static boolean eternalreturn$rainOverHive(World world, Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) BlockPos pos) {
		return LocalWeather.field(world) != null ? LocalWeather.rainingAt(world, pos) : original.call(world);
	}
}
