package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.AzaleaTrees;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Some oaks grow as azalea trees in Eternal Return worlds (AzaleaTrees). */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureAzaleaMixin {
	@Inject(method = "generate(Lnet/minecraft/world/gen/feature/util/FeatureContext;)Z", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$azalea(FeatureContext<TreeFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		if (AzaleaTrees.replaces(context)) {
			cir.setReturnValue(AzaleaTrees.grow(context));
		}
	}
}
