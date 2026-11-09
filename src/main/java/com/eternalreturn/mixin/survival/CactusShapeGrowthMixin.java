package com.eternalreturn.mixin.survival;

import com.eternalreturn.worldgen.cactus.CactusShapes;
import net.minecraft.block.BlockState;
import net.minecraft.block.CactusBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a biome with a cactus shape (CactusShapes, Eternal Return worlds), a cactus grows that shape instead of straight up. */
@Mixin(CactusBlock.class)
public abstract class CactusShapeGrowthMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$growShape(BlockState state, ServerWorld world, BlockPos pos, Random random, CallbackInfo ci) {
		if (CactusShapes.randomTick(state, world, pos)) {
			ci.cancel();
		}
	}
}
