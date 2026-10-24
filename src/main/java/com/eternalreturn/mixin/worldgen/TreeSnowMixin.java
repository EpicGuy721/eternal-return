package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.SnowyTrees;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A tree grown in the world's generation (not from a sapling) snows like the trees around it (SnowyTrees). */
@Mixin(TreeFeature.class)
public abstract class TreeSnowMixin {
	@Inject(method = "generate(Lnet/minecraft/world/gen/feature/util/FeatureContext;)Z", at = @At("RETURN"))
	private void eternalreturn$snow(FeatureContext<TreeFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && context.getWorld() instanceof ChunkRegion && EternalReturnConfig.get().worldgen.snowOnBorderTrees) {
			SnowyTrees.afterTree(context.getWorld(), context.getOrigin());
		}
	}
}
