package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;

/**
 * Snow has gravity (survival.snowFalls): a snow layer or snow block with air, water or another
 * replaceable block under it falls like sand, two ticks after it is placed or its support goes. A
 * falling snow layer keeps its layers. Snow already hanging in freshly generated land stays until
 * something next to it changes.
 */
public final class SnowFall {
	private static final int DELAY = 2;

	private SnowFall() {
	}

	public static boolean isSnow(BlockState state) {
		return state.isOf(Blocks.SNOW) || state.isOf(Blocks.SNOW_BLOCK);
	}

	/** Schedules the fall if this is snow with nothing under it; returns whether it did. */
	public static boolean scheduleIfUnsupported(BlockState state, WorldAccess world, BlockPos pos) {
		if (!isSnow(state) || !EternalReturnConfig.get().survival.snowFalls || world.isClient() || !unsupported(world, pos)) {
			return false;
		}
		world.scheduleBlockTick(pos, state.getBlock(), DELAY);
		return true;
	}

	public static void fall(BlockState state, ServerWorld world, BlockPos pos) {
		if (isSnow(state) && EternalReturnConfig.get().survival.snowFalls && unsupported(world, pos)) {
			FallingBlockEntity.spawnFromBlock(world, pos, state);
		}
	}

	private static boolean unsupported(WorldAccess world, BlockPos pos) {
		return pos.getY() > world.getBottomY() && FallingBlock.canFallThrough(world.getBlockState(pos.down()));
	}
}
