package com.eternalreturn.blocks;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Cacti that join sideways (worldgen expansion 2, phase 2; CactusArmsMixin): the cactus has a connection to each side
 * (north, east, south, west, as a fence), drawn as a joint between the two. A cactus placed against the side of another
 * joins it; a cactus beside another without joining (an arm's upright beside the trunk) is fine too. Only other blocks
 * beside it still break it. Every piece must trace back, down or through its joins, to a cactus standing on sand or
 * red sand: break the trunk and everything it held up or out breaks and drops. Every world (a block's own rule).
 */
public final class CactusArms {
	/** How many pieces the support search looks through before it takes a cactus as held (big hedges). */
	private static final int SEARCH_LIMIT = 256;

	private CactusArms() {
	}

	public static BooleanProperty side(Direction direction) {
		return switch (direction) {
			case NORTH -> Properties.NORTH;
			case SOUTH -> Properties.SOUTH;
			case EAST -> Properties.EAST;
			case WEST -> Properties.WEST;
			default -> throw new IllegalArgumentException(direction.toString());
		};
	}

	public static boolean joined(BlockState state, Direction direction) {
		return state.isOf(Blocks.CACTUS) && state.get(side(direction));
	}

	public static boolean anyJoin(BlockState state) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (joined(state, direction)) {
				return true;
			}
		}
		return false;
	}

	/** The cactus rule: nothing but cactus beside it and no lava, no liquid on top, and held from below or through its joins. */
	public static boolean canStay(BlockState state, WorldView world, BlockPos pos) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			BlockPos beside = pos.offset(direction);
			BlockState there = world.getBlockState(beside);
			if ((there.isSolid() && !there.isOf(Blocks.CACTUS)) || world.getFluidState(beside).isIn(FluidTags.LAVA)) {
				return false;
			}
		}
		if (world.getBlockState(pos.up()).isLiquid()) {
			return false;
		}
		return held(state, world, pos);
	}

	/** Whether a cactus here traces back, down through cactus or through joins, to a cactus standing on sand. */
	public static boolean held(BlockState state, WorldView world, BlockPos pos) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		queue.add(pos);
		seen.add(pos);
		while (!queue.isEmpty()) {
			if (seen.size() > SEARCH_LIMIT) {
				return true;
			}
			BlockPos at = queue.poll();
			BlockState here = at.equals(pos) ? state : world.getBlockState(at);
			BlockState below = world.getBlockState(at.down());
			if (below.isIn(BlockTags.SAND)) {
				return true;
			}
			if (below.isOf(Blocks.CACTUS) && seen.add(at.down())) {
				queue.add(at.down());
			}
			for (Direction direction : Direction.Type.HORIZONTAL) {
				BlockPos next = at.offset(direction);
				if (here.get(side(direction)) && world.getBlockState(next).isOf(Blocks.CACTUS) && seen.add(next)) {
					queue.add(next);
				}
			}
		}
		return false;
	}

	/** A cactus as placed here: joined to every cactus beside it. */
	public static BlockState placed(BlockState state, WorldView world, BlockPos pos) {
		for (Direction direction : Direction.Type.HORIZONTAL) {
			state = state.with(side(direction), world.getBlockState(pos.offset(direction)).isOf(Blocks.CACTUS));
		}
		return state;
	}

	/** A cactus whose neighbour changed: the join goes with the neighbour, and comes when the neighbour joined toward it. */
	public static BlockState neighbourChanged(BlockState state, Direction direction, BlockState neighbour) {
		if (!state.isOf(Blocks.CACTUS) || !direction.getAxis().isHorizontal()) {
			return state;
		}
		if (!neighbour.isOf(Blocks.CACTUS)) {
			return state.with(side(direction), false);
		}
		if (neighbour.get(side(direction.getOpposite()))) {
			return state.with(side(direction), true);
		}
		return state;
	}
}
