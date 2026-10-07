package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.stone.BiomeStoneOres;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.structure.rule.RuleTest;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.OreFeature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ores and stone blobs treat red sandstone as stone in Eternal Return worlds (see BiomeStoneOres). */
@Mixin(OreFeature.class)
public abstract class OreFeatureMixin {
	@Inject(method = "generate", at = @At("HEAD"))
	private void eternalreturn$enterOre(FeatureContext<OreFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		BiomeStoneOres.enter(context);
	}

	@Inject(method = "generate", at = @At("RETURN"))
	private void eternalreturn$exitOre(FeatureContext<OreFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		BiomeStoneOres.exit();
	}

	@WrapOperation(method = "shouldPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/structure/rule/RuleTest;test(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/random/Random;)Z"))
	private static boolean eternalreturn$hostStoneAsStone(RuleTest rule, BlockState state, Random random, Operation<Boolean> original) {
		return original.call(rule, BiomeStoneOres.asStone(state), random);
	}
}
