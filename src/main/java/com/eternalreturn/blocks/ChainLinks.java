package com.eternalreturn.blocks;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Chains link blocks when pistons move them (redstone.chainsLinkBlocks). A chain is attached to the two
 * blocks at its ends, along its axis: when a piston moves either of them, or the chain, the chain and
 * both ends move together, whether or not the piston pushes the chain itself. A chain only sticks to slime
 * or honey (and they to it) where it visibly joins them, along its axis; side by side, they don't stick.
 * The usual 12-block limit still holds. Applied in PistonHandlerMixin.
 */
public final class ChainLinks {
	private ChainLinks() {
	}

	static boolean enabled() {
		return EternalReturnConfig.get().redstone.chainsLinkBlocks;
	}

	/**
	 * Whether the block at to moves along with the moving block from, which it touches in direction
	 * (from to to). Pairs without a chain keep vanilla's answer.
	 */
	public static boolean stuck(BlockState from, BlockState to, Direction direction, boolean vanilla) {
		if (!enabled() || !Redstone.isChain(from) && !Redstone.isChain(to)) {
			return vanilla;
		}
		boolean fromLinks = Redstone.isChain(from) && Redstone.along(from, direction) && !to.isAir();
		boolean toLinks = Redstone.isChain(to) && Redstone.along(to, direction);
		return fromLinks || toLinks;
	}

	/** Whether a moving block must look at its neighbours: it is sticky, a chain, or a chain is attached to it. */
	public static boolean pulls(World world, BlockPos pos, BlockState state, boolean vanilla) {
		if (vanilla || !enabled()) {
			return vanilla;
		}
		if (Redstone.isChain(state)) {
			return true;
		}
		for (Direction direction : Direction.values()) {
			BlockState neighbour = world.getBlockState(pos.offset(direction));
			if (Redstone.isChain(neighbour) && Redstone.along(neighbour, direction)) {
				return true;
			}
		}
		return false;
	}

	/** In a line of blocks pulled behind a moving block: whether the line goes on past pos (along motion). */
	public static boolean pullsBehind(World world, BlockPos pos, BlockState state, Direction behind, boolean vanilla) {
		if (vanilla || !enabled()) {
			return vanilla;
		}
		if (Redstone.isChain(state) && Redstone.along(state, behind)) {
			return true;
		}
		BlockState next = world.getBlockState(pos.offset(behind));
		return Redstone.isChain(next) && Redstone.along(next, behind);
	}
}
