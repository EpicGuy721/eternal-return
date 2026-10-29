package com.eternalreturn.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SnowBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

import java.util.List;

/**
 * A snow cover: snow layers lying on the parts of a block below that reach the top of its space, where its top isn't
 * whole (fence, wall and gate posts, a vertical slab's edge, stairs' upper step: SnowCover). Otherwise a snow layer: it
 * takes more snow up to 8 layers, melts, drops and breaks as one, and is shown as one in the player's hand. When the
 * block below comes to have a whole top (its snow filling it, a slab filling the other half of a vertical slab) it turns
 * into an ordinary snow layer; snow layers on a whole top turn into a snow cover when the top stops being whole
 * (SnowLayerMixin); with nothing reaching the top below it goes.
 */
public class SnowCoverBlock extends SnowBlock {
	public static final MapCodec<SnowCoverBlock> CODEC = createCodec(SnowCoverBlock::new);

	public SnowCoverBlock(Settings settings) {
		super(settings);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public MapCodec<SnowBlock> getCodec() {
		return (MapCodec) CODEC;
	}

	private static VoxelShape shape(BlockState state, BlockView world, BlockPos pos, int pixels) {
		return SnowCover.coverShape(world.getBlockState(pos.down()), pixels);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shape(state, world, pos, 2 * state.get(LAYERS));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shape(state, world, pos, 2 * (state.get(LAYERS) - 1));
	}

	@Override
	protected VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
		return shape(state, world, pos, 2 * state.get(LAYERS));
	}

	@Override
	protected VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return shape(state, world, pos, 2 * state.get(LAYERS));
	}

	/** It hides nothing of its neighbours, and light passes through it (asked off the server thread, where the block below can't be looked at). */
	@Override
	protected VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
		return VoxelShapes.empty();
	}

	@Override
	protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
		return true;
	}

	@Override
	protected int getOpacity(BlockState state, BlockView world, BlockPos pos) {
		return 0;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		BlockState below = world.getBlockState(pos.down());
		return !below.isIn(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON) && SnowCover.isHost(below) && SnowCover.reachesTop(below)
				&& !SnowCover.whole(below, SnowCover.layers(world, pos.down(), below));
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		BlockState layer = Blocks.SNOW.getDefaultState().with(LAYERS, state.get(LAYERS));
		if (layer.canPlaceAt(world, pos)) {
			return layer;
		}
		return state.canPlaceAt(world, pos) ? state : Blocks.AIR.getDefaultState();
	}

	/** It takes more snow as a snow layer does; with one layer, anything else placed into it replaces it. */
	@Override
	protected boolean canReplace(BlockState state, ItemPlacementContext context) {
		int layers = state.get(LAYERS);
		if (context.getStack().isOf(Items.SNOW) && layers < MAX_LAYERS) {
			return !context.canReplaceExisting() || context.getSide() == Direction.UP;
		}
		return layers == 1;
	}

	@Override
	protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
		return SnowCover.asSnowLayer(state.get(LAYERS)).getDroppedStacks(builder);
	}

	@Override
	public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
		return new ItemStack(Items.SNOW);
	}
}
