package com.eternalreturn.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Snow on slabs, stairs, walls, fences and fence gates, as snow layers of its own (the rules; Snowlogging keeps and
 * sends the snow, SnowCoverBlock is the snow lying on part of a top):
 * <ul>
 * <li>Snowlogged snow shares the block's space: 1 to 8 layers of 2 pixels lying on the topmost thing in each pixel
 * column of the space the block leaves open, so on a bottom slab, on stairs' lower step, on fence rails and wall arms,
 * and on the ground around posts and beside a vertical slab (none under rails or a top slab: snow falls from above).
 * It's its own target: a player aiming at it outlines, mines and breaks only it, as a snow layer, and stands on it as
 * on one.</li>
 * <li>Snow lying on top: where the block and its snow reach the top of the space everywhere (a top or double slab,
 * upside-down stairs, a slab full of snow), ordinary snow layers; where the block reaches the top only in places
 * (posts, a vertical slab's edge, stairs' upper step), a snow cover over just those places.</li>
 * </ul>
 * Every such block gets a "snowy" property as it's made (SnowHostMixin, from KINDS), 0 by default: 1 when snow is logged
 * in it or lies on it, so that it's drawn with its snow and drips (client SnowModels). The shapes here are the blocks'
 * own (their culling shape, which follows a fence's rails; a gate's rails are added by hand): drawing follows their models.
 */
public final class SnowCover {
	public static final int MAX_LAYERS = SnowBlock.MAX_LAYERS;
	/** 1 when snow is logged in the block or lies on it. */
	public static final IntProperty SNOWY = IntProperty.of("snowy", 0, 1);
	/** The kinds of block that hold snow: a new shape needs a line here (and its own top here, if its culling shape doesn't follow it). */
	private static final List<Class<?>> KINDS = List.of(SlabBlock.class, StairsBlock.class, WallBlock.class, FenceBlock.class, FenceGateBlock.class,
			MixedSlabs.MixedSlabBlock.class);
	private static final Map<BlockState, Top> TOPS = new ConcurrentHashMap<>();
	private static final Map<String, Top> SHARED = new ConcurrentHashMap<>();

	/**
	 * A block's top: how high it reaches in each pixel column (index x + 16 z, -1 where it has nothing), the floor the
	 * snow lies on there, the most layers its open space takes, and its snow's outline and collision by layers.
	 */
	private static final class Top {
		final int[] heights;
		final int capacity;
		final VoxelShape[] outlines = new VoxelShape[MAX_LAYERS + 1];
		final VoxelShape[] collisions = new VoxelShape[MAX_LAYERS + 1];

		Top(int[] heights) {
			this.heights = heights;
			int most = 0;
			for (int height : heights) {
				if (height < 16) {
					most = Math.max(most, (16 - Math.max(0, height) + 1) / 2);
				}
			}
			this.capacity = Math.min(MAX_LAYERS, most);
		}

		VoxelShape snow(int pixels, VoxelShape[] cache, int index) {
			VoxelShape shape = cache[index];
			if (shape == null) {
				shape = snowShape(this.heights, pixels);
				cache[index] = shape;
			}
			return shape;
		}
	}

	private SnowCover() {
	}

	/** Gives a block being made its snowy property, if it's a kind that holds snow (not if it has a "snowy" of its own). */
	public static void addProperty(Block block, StateManager.Builder<Block, BlockState> builder) {
		for (Class<?> kind : KINDS) {
			if (kind.isInstance(block)) {
				try {
					builder.add(SNOWY);
				} catch (IllegalArgumentException taken) {
					// Some mod's block with a property of that name: it goes without.
				}
				return;
			}
		}
	}

	/** Whether a block holds snow (a slab, stairs, wall, fence or gate). */
	public static boolean isHost(BlockState state) {
		return state.contains(SNOWY);
	}

	public static boolean marked(BlockState state) {
		return state.contains(SNOWY) && state.get(SNOWY) == 1;
	}

	public static BlockState mark(BlockState state, boolean snowy) {
		return state.contains(SNOWY) ? state.with(SNOWY, snowy ? 1 : 0) : state;
	}

	public static BlockState bare(BlockState state) {
		return mark(state, false);
	}

	/** A state with the snowy mark of another state of a block that holds snow. */
	public static BlockState withSnowOf(BlockState state, BlockState other) {
		return state.contains(SNOWY) && other.contains(SNOWY) ? state.with(SNOWY, other.get(SNOWY)) : state;
	}

	/** Snow, a snow block, powder snow or a snow cover. */
	public static boolean isSnow(BlockState state) {
		return state.isIn(BlockTags.SNOW);
	}

	public static boolean isCover(BlockState state) {
		return state.getBlock() instanceof SnowCoverBlock;
	}

	/** The layers of snow logged in a block (0 if it isn't marked snowy, or none is). */
	public static int layers(BlockView world, BlockPos pos, BlockState state) {
		return marked(state) ? Snowlogging.layers(world, pos) : 0;
	}

	private static Top top(BlockState state) {
		return TOPS.computeIfAbsent(bare(state), SnowCover::measure);
	}

	private static Top measure(BlockState bare) {
		int[] heights = topHeights(shapeOf(bare));
		return SHARED.computeIfAbsent(Arrays.toString(heights), key -> new Top(heights));
	}

	/** A block's shape for its snow: its culling shape (a fence's follows its rails), a closed gate's rails added; its outline if it has none. */
	private static VoxelShape shapeOf(BlockState bare) {
		VoxelShape culling = bare.getCullingShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
		VoxelShape shape = culling.isEmpty() ? bare.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN) : culling;
		if (bare.getBlock() instanceof FenceGateBlock && !bare.get(FenceGateBlock.OPEN) && !culling.isEmpty()) {
			// The rails between the posts, a pixel below their tops.
			Box posts = culling.getBoundingBox();
			shape = VoxelShapes.union(shape, VoxelShapes.cuboid(posts.minX, posts.maxY - 4.0 / 16.0, posts.minZ, posts.maxX, posts.maxY - 1.0 / 16.0, posts.maxZ));
		}
		return shape;
	}

	/** How high a shape reaches in each of a block's 16 by 16 pixel columns (index x + 16 z), -1 where it has nothing. */
	public static int[] topHeights(VoxelShape shape) {
		int[] heights = new int[256];
		Arrays.fill(heights, -1);
		shape.forEachBox((x1, y1, z1, x2, y2, z2) -> {
			int top = (int) Math.round(Math.min(1.0, y2) * 16.0);
			for (int x = pixel(x1); x < pixel(x2); x++) {
				for (int z = pixel(z1); z < pixel(z2); z++) {
					heights[x + 16 * z] = Math.max(heights[x + 16 * z], top);
				}
			}
		});
		return heights;
	}

	private static int pixel(double coordinate) {
		return Math.max(0, Math.min(16, (int) Math.round(coordinate * 16.0)));
	}

	/** The most layers of snow a block takes in its space: 8 with ground in it, 4 on a bottom slab; none in water. */
	public static int capacity(BlockState state) {
		if (!isHost(state) || state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) {
			return 0;
		}
		return top(state).capacity;
	}

	/** Whether a block and the snow logged in it reach the top of its space everywhere, so that snow layers lie on it. */
	public static boolean whole(BlockState state, int layers) {
		for (int height : top(state).heights) {
			if (height < 16 && Math.max(0, height) + 2 * layers < 16) {
				return false;
			}
		}
		return true;
	}

	/** Whether a block reaches the top of its space somewhere, where a snow cover can lie on it. */
	public static boolean reachesTop(BlockState state) {
		for (int height : top(state).heights) {
			if (height >= 16) {
				return true;
			}
		}
		return false;
	}

	/** The columns where a block reaches the top of its space (where a snow cover lies on it). */
	public static boolean[] coverFootprint(BlockState state) {
		int[] heights = top(state).heights;
		boolean[] footprint = new boolean[256];
		for (int i = 0; i < 256; i++) {
			footprint[i] = heights[i] >= 16;
		}
		return footprint;
	}

	/** The outline of the snow logged in a block, some layers deep. */
	public static VoxelShape snowOutline(BlockState state, int layers) {
		int clamped = Math.max(0, Math.min(MAX_LAYERS, layers));
		return clamped == 0 ? VoxelShapes.empty() : top(state).snow(2 * clamped, top(state).outlines, clamped);
	}

	/** The snow's collision: a layer lower, as snow layers' (a single layer has none). */
	public static VoxelShape snowCollision(BlockState state, int layers) {
		int clamped = Math.max(0, Math.min(MAX_LAYERS, layers));
		return clamped <= 1 ? VoxelShapes.empty() : top(state).snow(2 * (clamped - 1), top(state).collisions, clamped);
	}

	/** Snow some pixels deep on the topmost thing in every column the block leaves open, up to the top of the space. */
	private static VoxelShape snowShape(int[] heights, int depth) {
		List<VoxelShape> rows = new ArrayList<>();
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; ) {
				int height = heights[x + 16 * z];
				int end = x + 1;
				while (end < 16 && heights[end + 16 * z] == height) {
					end++;
				}
				int floor = Math.max(0, height);
				if (floor < 16) {
					rows.add(Block.createCuboidShape(x, floor, z, end, Math.min(16, floor + depth), z + 1));
				}
				x = end;
			}
		}
		return rows.stream().reduce(VoxelShapes.empty(), VoxelShapes::union).simplify();
	}

	/** A snow cover's shape over a block below: its parts reaching the top, some pixels deep. */
	public static VoxelShape coverShape(BlockState below, int pixels) {
		if (pixels <= 0 || !isHost(below)) {
			return VoxelShapes.empty();
		}
		boolean[] footprint = coverFootprint(below);
		List<VoxelShape> rows = new ArrayList<>();
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; ) {
				boolean in = footprint[x + 16 * z];
				int end = x + 1;
				while (end < 16 && footprint[end + 16 * z] == in) {
					end++;
				}
				if (in) {
					rows.add(Block.createCuboidShape(x, 0, z, end, Math.min(16, pixels), z + 1));
				}
				x = end;
			}
		}
		return rows.stream().reduce(VoxelShapes.empty(), VoxelShapes::union).simplify();
	}

	/** The snow logged in a block as a snow layer of its own: what it drops, how it breaks and sounds. */
	public static BlockState asSnowLayer(int layers) {
		return Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, Math.max(1, Math.min(MAX_LAYERS, layers)));
	}

	/** Whether a player aims at the snow logged in a block rather than the block: the snow is nearer along the look. */
	public static boolean aimsSnow(@Nullable PlayerEntity player, BlockView world, BlockPos pos, BlockState state) {
		int layers = layers(world, pos, state);
		if (player == null || layers == 0) {
			return false;
		}
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(eye.distanceTo(Vec3d.ofCenter(pos)) + 2.0));
		BlockHitResult snow = snowOutline(state, layers).raycast(eye, end, pos);
		if (snow == null) {
			return false;
		}
		BlockHitResult block = bare(state).getOutlineShape(EmptyBlockView.INSTANCE, pos).raycast(eye, end, pos);
		return block == null || snow.getPos().squaredDistanceTo(eye) <= block.getPos().squaredDistanceTo(eye);
	}

	/** A block's snowy mark as its snow says: snow logged in it, or snow lying on it. */
	public static BlockState markedFor(BlockState state, int layers, BlockState above) {
		return mark(state, layers > 0 || isSnow(above));
	}
}
