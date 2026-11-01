package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.trees.BiomeTrees;
import net.minecraft.block.BlockState;
import net.minecraft.block.SaplingGenerator;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In Eternal Return worlds a sapling grows a tree from its biome's tree table (BiomeTrees), by bone meal or on its own. */
@Mixin(SaplingGenerator.class)
public abstract class SaplingGeneratorMixin {
	@Inject(method = "generate", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$biomeTree(ServerWorld world, ChunkGenerator chunkGenerator, BlockPos pos, BlockState state, Random random,
			CallbackInfoReturnable<Boolean> cir) {
		Boolean grown = BiomeTrees.growSapling(world, chunkGenerator, pos, state, random);
		if (grown != null) {
			cir.setReturnValue(grown);
		}
	}
}
