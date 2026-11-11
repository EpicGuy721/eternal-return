package com.eternalreturn.mixin.personality;

import com.eternalreturn.worldgen.personality.PersonalityFeatures;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.world.gen.feature.SimpleBlockFeature;
import net.minecraft.world.gen.feature.SimpleBlockFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A flower planted as the world is made: one of its patch's palette (PersonalityFeatures). */
@Mixin(SimpleBlockFeature.class)
public abstract class SimpleBlockFlowerMixin {
	@ModifyExpressionValue(method = "generate", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/gen/stateprovider/BlockStateProvider;get(Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$palette(BlockState state, @Local(argsOnly = true) FeatureContext<SimpleBlockFeatureConfig> context) {
		return PersonalityFeatures.flower(state, context.getWorld(), context.getGenerator(), context.getOrigin());
	}
}
