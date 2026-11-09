package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.cactus.CactusShapes;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.BlockColumnFeature;
import net.minecraft.world.gen.feature.BlockColumnFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where a biome grows its own cactus shape (CactusShapes, Eternal Return worlds only), vanilla's straight cactus columns
 * aren't placed: the shapes take their place (CactusFeature). Elsewhere, and in every other world, nothing changes.
 */
@Mixin(BlockColumnFeature.class)
public abstract class BlockColumnCactusMixin {
	@Inject(method = "generate", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$shapedInstead(FeatureContext<BlockColumnFeatureConfig> context, CallbackInfoReturnable<Boolean> cir) {
		// The biome first (null in every other world), and the column's block read without the feature's random numbers.
		if (CactusShapes.entryFor(context.getWorld().getBiome(context.getOrigin())) == null) {
			return;
		}
		for (BlockColumnFeatureConfig.Layer layer : context.getConfig().layers()) {
			if (layer.state() instanceof net.minecraft.world.gen.stateprovider.SimpleBlockStateProvider simple
					&& simple.get(net.minecraft.util.math.random.Random.create(0L), BlockPos.ORIGIN).isOf(Blocks.CACTUS)) {
				cir.setReturnValue(false);
				return;
			}
		}
	}
}
