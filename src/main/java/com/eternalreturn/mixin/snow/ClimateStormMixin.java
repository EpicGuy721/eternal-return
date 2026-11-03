package com.eternalreturn.mixin.snow;

import com.eternalreturn.weather.LocalWeather;
import com.eternalreturn.worldgen.climate.Climate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Storm snow in Eternal Return worlds (Climate): it piles up to the climate's depth for the spot rather than the
 * snow-piling game rule's (whichever is more; a rule of 0 still stops it, and leaves still take one layer), and some of
 * what falls on a tree sifts through its leaves.
 */
@Mixin(ServerWorld.class)
public abstract class ClimateStormMixin {
	@WrapOperation(method = "tickIceAndSnow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/GameRules;getInt(Lnet/minecraft/world/GameRules$Key;)I"))
	private int eternalreturn$climateDepth(GameRules rules, GameRules.Key<GameRules.IntRule> key, Operation<Integer> original,
			@Local(argsOnly = true) BlockPos pos) {
		int rule = original.call(rules, key);
		if (key != GameRules.SNOW_ACCUMULATION_HEIGHT) {
			return rule;
		}
		ServerWorld world = (ServerWorld) (Object) this;
		return Climate.stormLimit(world, world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos), rule);
	}

	@Inject(method = "tickIceAndSnow", at = @At("TAIL"))
	private void eternalreturn$sift(BlockPos pos, CallbackInfo ci) {
		ServerWorld world = (ServerWorld) (Object) this;
		if (!Climate.applies(world)) {
			return;
		}
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos);
		if (LocalWeather.rainingAt(world, top) && world.getGameRules().getInt(GameRules.SNOW_ACCUMULATION_HEIGHT) > 0) {
			Climate.stormSift(world, top);
		}
	}
}
