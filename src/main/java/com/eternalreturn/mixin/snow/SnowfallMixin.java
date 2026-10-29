package com.eternalreturn.mixin.snow;

import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.blocks.Snowlogging;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Snow on blocks (SnowCover, Snowlogging) in the world's ticking: snowfall that can't settle as a snow layer settles in
 * and on the block below instead, up to the snow-piling game rule; a snow layer settling on a whole top says so to the
 * block; and logged snow melts in the block's random ticks, before the block's own (which a locked block skips: the
 * snow melts all the same).
 */
@Mixin(ServerWorld.class)
public abstract class SnowfallMixin {
	@WrapOperation(method = "tickIceAndSnow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/Biome;canSetSnow(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;)Z"))
	private boolean eternalreturn$snowOnBlocks(Biome biome, WorldView view, BlockPos pos, Operation<Boolean> original) {
		ServerWorld world = (ServerWorld) (Object) this;
		if (original.call(biome, view, pos)) {
			Snowlogging.snowLiesOn(world, pos, Block.NOTIFY_ALL);
			return true;
		}
		Snowlogging.snowfall(world, biome, pos, world.getGameRules().getInt(GameRules.SNOW_ACCUMULATION_HEIGHT), Block.NOTIFY_ALL);
		return false;
	}

	@WrapOperation(method = "tickChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;randomTick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V"))
	private void eternalreturn$melt(BlockState state, ServerWorld world, BlockPos pos, Random random, Operation<Void> original) {
		if (SnowCover.marked(state)) {
			if (Snowlogging.melt(state, world, pos)) {
				state = world.getBlockState(pos);
			}
			if (!SnowCover.bare(state).hasRandomTicks()) {
				return;
			}
		}
		original.call(state, world, pos, random);
	}
}
