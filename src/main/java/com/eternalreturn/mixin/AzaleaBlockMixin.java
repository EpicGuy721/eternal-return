package com.eternalreturn.mixin;

import com.eternalreturn.worldgen.AzaleaTrees;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.AzaleaBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.SaplingGenerator;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Bone meal on an azalea bush grows an azalea tree with an azalea trunk (AzaleaTrees.BUSH), not an oak one. */
@Mixin(AzaleaBlock.class)
public abstract class AzaleaBlockMixin {
	@WrapOperation(method = "grow", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/block/SaplingGenerator;generate(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/random/Random;)Z"))
	private boolean eternalreturn$azaleaTrunk(SaplingGenerator generator, ServerWorld world, ChunkGenerator chunkGenerator, BlockPos pos, BlockState state,
			Random random, Operation<Boolean> original) {
		return original.call(AzaleaTrees.BUSH, world, chunkGenerator, pos, state, random);
	}
}
