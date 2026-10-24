package com.eternalreturn.mixin.weather;

import com.eternalreturn.weather.LocalWeather;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.loot.condition.WeatherCheckLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A loot or enchantment condition on the weather (fish caught in the rain, Channeling) asks the storms where it
 * happens (LocalWeather): no storm there, no rain or thunder.
 */
@Mixin(WeatherCheckLootCondition.class)
public abstract class WeatherConditionMixin {
	@WrapOperation(method = "test(Lnet/minecraft/loot/context/LootContext;)Z", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;isRaining()Z"))
	private boolean eternalreturn$rainThere(ServerWorld world, Operation<Boolean> original, @Local(argsOnly = true) LootContext context) {
		Vec3d origin = context.get(LootContextParameters.ORIGIN);
		return LocalWeather.field(world) == null ? original.call(world) : origin != null && LocalWeather.rainingAt(world, BlockPos.ofFloored(origin));
	}

	@WrapOperation(method = "test(Lnet/minecraft/loot/context/LootContext;)Z", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;isThundering()Z"))
	private boolean eternalreturn$thunderThere(ServerWorld world, Operation<Boolean> original, @Local(argsOnly = true) LootContext context) {
		Vec3d origin = context.get(LootContextParameters.ORIGIN);
		return LocalWeather.field(world) == null ? original.call(world) : origin != null && LocalWeather.thunderAt(world, BlockPos.ofFloored(origin));
	}
}
