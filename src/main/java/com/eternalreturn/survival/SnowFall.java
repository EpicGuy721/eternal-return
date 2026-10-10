package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

/**
 * Snow has gravity (survival.snowFalls): a snow layer or snow block with air, water or another
 * replaceable block under it falls like sand, two ticks after it is placed or its support goes. A
 * falling snow layer keeps its layers, and snow that lands on a snow layer adds to it (FallingBlockEntityMixin):
 * the layers add up (a snow block counts as 8) to a full layer of 8, and any left over pile on top. Snow
 * already hanging in freshly generated land stays until something next to it changes.
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

	/** Layers of snow in a snow layer (1 to 8) or snow block (8); 0 for anything else. */
	public static int layers(BlockState state) {
		return state.isOf(Blocks.SNOW) ? state.get(SnowBlock.LAYERS) : state.isOf(Blocks.SNOW_BLOCK) ? SnowBlock.MAX_LAYERS : 0;
	}

	/**
	 * Falling snow landing on a snow layer at pos: piles the layers there and above. Returns whether it did
	 * (the falling block is then used up).
	 */
	public static boolean land(World world, BlockPos pos, BlockState falling, BlockState landingOn) {
		int adding = layers(falling);
		if (world.isClient || adding == 0 || !landingOn.isOf(Blocks.SNOW) || !EternalReturnConfig.get().survival.snowFalls) {
			return false;
		}
		int total = landingOn.get(SnowBlock.LAYERS) + adding;
		world.setBlockState(pos, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, Math.min(SnowBlock.MAX_LAYERS, total)), Block.NOTIFY_ALL);
		int rest = total - SnowBlock.MAX_LAYERS;
		BlockPos above = pos.up();
		if (rest > 0 && world.getBlockState(above).isReplaceable()) {
			world.setBlockState(above, Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, rest), Block.NOTIFY_ALL);
		}
		world.playSound(null, pos, SoundEvents.BLOCK_SNOW_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
		return true;
	}

	/** Nothing holds it up: air, water or a replaceable block below, except a full (8-layer) snow layer, which snow rests on. */
	private static boolean unsupported(WorldAccess world, BlockPos pos) {
		BlockState below = world.getBlockState(pos.down());
		return pos.getY() > world.getBottomY() && FallingBlock.canFallThrough(below) && !(below.isOf(Blocks.SNOW) && below.get(SnowBlock.LAYERS) == SnowBlock.MAX_LAYERS);
	}
}
