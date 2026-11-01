package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.trees.BiomeTrees;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Trees generated with Eternal Return worlds come from their biome's tree table too (BiomeTrees). */
@Mixin(TreeFeature.class)
public abstract class TreeFeatureBiomeMixin {
	@Inject(method = "generate(Lnet/minecraft/world/gen/feature/util/FeatureContext;)Z", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$biomeTree(FeatureContext<TreeFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		Boolean grown = BiomeTrees.replaceGenerated(context);
		if (grown != null) {
			cir.setReturnValue(grown);
		}
	}
}
