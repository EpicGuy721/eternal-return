package com.eternalreturn.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * A cactus arm (worldgen expansion 2, phase 2): a thinner cactus (10 x 10 pixels) that goes only on top of another arm or
 * on the side of a cactus. An arm joins the side of each cactus beside it (drawn as a short stub across), unless an arm
 * is under it: an arm's upright stands clear of the trunk beside it. It breaks and drops when it loses both (the cactus
 * it was joined to goes, or the arm under it), and hurts what touches it, as cactus does. Arms come only from wild
 * cacti, which grow them (CactusShapes); there is no recipe. Bone meal on an arm grows its cactus as on the trunk.
 */
public class CactusArmBlock extends Block implements net.minecraft.block.Fertilizable {
	public static final MapCodec<CactusArmBlock> CODEC = createCodec(CactusArmBlock::new);
	private static final VoxelShape COLUMN = Block.createCuboidShape(3, 0, 3, 13, 16, 13);
	private static final VoxelShape COLUMN_COLLISION = Block.createCuboidShape(3, 0, 3, 13, 15, 13);
	/** The stub across to a cactus, per side: north, east, south, west. */
	private static final VoxelShape[] STUBS = {
			Block.createCuboidShape(3, 3, 0, 13, 13, 3),
			Block.createCuboidShape(13, 3, 3, 16, 13, 13),
			Block.createCuboidShape(3, 3, 13, 13, 13, 16),
			Block.createCuboidShape(0, 3, 3, 3, 13, 13),
	};
	private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	public CactusArmBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getStateManager().getDefaultState().with(Properties.NORTH, false).with(Properties.EAST, false)
				.with(Properties.SOUTH, false).with(Properties.WEST, false));
	}

	@Override
	protected MapCodec<? extends Block> getCodec() {
		return CODEC;
	}

	public static BooleanProperty side(Direction direction) {
		return switch (direction) {
			case NORTH -> Properties.NORTH;
			case EAST -> Properties.EAST;
			case SOUTH -> Properties.SOUTH;
			case WEST -> Properties.WEST;
			default -> throw new IllegalArgumentException(direction.toString());
		};
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(Properties.NORTH, Properties.EAST, Properties.SOUTH, Properties.WEST);
	}

	/** This arm as it stands here: joined to each cactus beside it, unless an arm is under it. */
	public BlockState joined(BlockState state, BlockView world, BlockPos pos) {
		boolean onArm = world.getBlockState(pos.down()).isOf(this);
		for (Direction direction : SIDES) {
			state = state.with(side(direction), !onArm && world.getBlockState(pos.offset(direction)).isOf(Blocks.CACTUS));
		}
		return state;
	}

	/** An arm stays on top of another arm, or beside a cactus. */
	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		if (world.getBlockState(pos.down()).isOf(this)) {
			return true;
		}
		for (Direction direction : SIDES) {
			if (world.getBlockState(pos.offset(direction)).isOf(Blocks.CACTUS)) {
				return true;
			}
		}
		return false;
	}

	@Override
	@Nullable
	public BlockState getPlacementState(ItemPlacementContext context) {
		BlockState state = this.joined(this.getDefaultState(), context.getWorld(), context.getBlockPos());
		return state.canPlaceAt(context.getWorld(), context.getBlockPos()) ? state : null;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos) {
		if (!state.canPlaceAt(world, pos)) {
			// As cactus: break on the next tick, so a whole arm comes down piece by piece.
			world.scheduleBlockTick(pos, this, 1);
		}
		return this.joined(state, world, pos);
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (!state.canPlaceAt(world, pos)) {
			world.breakBlock(pos, true);
		}
	}

	private static VoxelShape withStubs(VoxelShape column, BlockState state) {
		VoxelShape shape = column;
		for (int i = 0; i < 4; i++) {
			if (state.get(side(SIDES[i]))) {
				shape = VoxelShapes.union(shape, STUBS[i]);
			}
		}
		return shape;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return withStubs(COLUMN, state);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return withStubs(COLUMN_COLLISION, state);
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		entity.damage(world.getDamageSources().cactus(), 1.0F);
	}

	@Override
	protected boolean canPathfindThrough(BlockState state, NavigationType type) {
		return false;
	}
	@Override
	public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
		com.eternalreturn.worldgen.cactus.CactusShapes.Entry entry = com.eternalreturn.worldgen.cactus.CactusShapes.entryFor(world.getBiome(pos));
		BlockPos root = entry == null ? null : com.eternalreturn.worldgen.cactus.CactusShapes.root(world, pos);
		return root != null && com.eternalreturn.worldgen.cactus.CactusShapes.next(world, root, entry.shape()) != null;
	}

	@Override
	public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
		return true;
	}

	@Override
	public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
		com.eternalreturn.worldgen.cactus.CactusShapes.Entry entry = com.eternalreturn.worldgen.cactus.CactusShapes.entryFor(world.getBiome(pos));
		BlockPos root = entry == null ? null : com.eternalreturn.worldgen.cactus.CactusShapes.root(world, pos);
		if (root != null) {
			com.eternalreturn.worldgen.cactus.CactusShapes.growNext(world, root, entry.shape());
		}
	}
}
