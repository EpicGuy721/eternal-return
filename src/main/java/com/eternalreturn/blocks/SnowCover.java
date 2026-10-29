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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.LightType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Snow on slabs, stairs, walls, fences and fence gates (and mixed slabs), kept on the block itself: every such block
 * gets a "snow" property as it's made (SnowHostMixin, from KINDS), 0 by default, so a block without snow is exactly what
 * it was. What a block holds goes by its shape:
 * <ul>
 * <li>a slab or stairs with open space in its top (a bottom slab, upright stairs) holds 1 to 4 layers of 2 pixels on its
 * lower surfaces, filling that space, and its surfaces at the top of the block carry one layer, drawn above the block
 * while the space above is empty. Once the open space is full, snow layers lie on it as on a full block;</li>
 * <li>one with no open space and no whole top (a vertical slab) holds one layer on its top edge;</li>
 * <li>one with a whole top (a top or double slab, upside-down stairs, a mixed slab) holds none itself: snow layers lie on
 * it as in vanilla, and the property only says whether some do (its sides then have the drips);</li>
 * <li>a wall, fence or gate holds one thin cap over its top.</li>
 * </ul>
 * Snow that sticks up into the space above (a cap) goes when anything takes that space, a gate's when it opens.
 * Waterlogged blocks hold none. The snow melts as snow layers do (block light above 11), settles in snowfall and in
 * snowy biomes' generation where a snow layer would settle on a full block (SnowfallMixin, FreezeTopLayerMixin), can be
 * put on the block's top by hand, collides as snow layers do (slabs and stairs only: caps never collide) and leaves what
 * neighbours hide and the light as they were (SnowShapeMixin). A tool that digs snow digs the snow first and leaves the
 * block (SnowBreakMixin); anything else breaks both, each dropping its own. The client draws the snow on the block's own
 * model, with drips down its sides (client SnowModels).
 */
public final class SnowCover {
	public static final int MAX_LAYERS = 4;
	/** Snow on a slab or stairs: layers in its open space, a layer on its top edge, or (a whole top) snow lying on it. */
	public static final IntProperty LAYERS = IntProperty.of("snow", 0, MAX_LAYERS);
	/** A cap of snow on a wall, fence or gate; on a mixed slab, snow lying on it. */
	public static final IntProperty CAP = IntProperty.of("snow", 0, 1);
	/** The kinds of block that hold snow (the first that fits a block): a new shape needs a line here, and nothing else unless its snow is to collide (SnowShapeMixin). */
	private static final List<Kind> KINDS = List.of(new Kind(SlabBlock.class, LAYERS), new Kind(StairsBlock.class, LAYERS),
			new Kind(WallBlock.class, CAP), new Kind(FenceBlock.class, CAP), new Kind(FenceGateBlock.class, CAP),
			new Kind(MixedSlabs.MixedSlabBlock.class, CAP));
	private static final Map<BlockState, Room> ROOMS = new ConcurrentHashMap<>();
	/** The same tops measured from many states, kept once each. */
	private static final Map<String, Room> SHARED = new ConcurrentHashMap<>();
	private static final Map<BlockState, VoxelShape> OUTLINES = new ConcurrentHashMap<>();
	private static final Map<BlockState, VoxelShape> COLLISIONS = new ConcurrentHashMap<>();

	private record Kind(Class<?> type, IntProperty property) {
	}

	/** A slab's or stairs' top, without snow: how high it reaches in each pixel column, whether that's all the top of the block, and the layers its open space holds. */
	private record Room(int[] heights, boolean wholeTop, int layers) {
	}

	private SnowCover() {
	}

	/** Gives a block being made its snow property, if it's a kind that holds snow (not if it has a "snow" of its own). */
	public static void addProperty(Block block, StateManager.Builder<Block, BlockState> builder) {
		for (Kind kind : KINDS) {
			if (kind.type().isInstance(block)) {
				try {
					builder.add(kind.property());
				} catch (IllegalArgumentException taken) {
					// Some mod's block with a property of that name: it goes without.
				}
				return;
			}
		}
	}

	@Nullable
	public static IntProperty property(BlockState state) {
		return state.contains(LAYERS) ? LAYERS : state.contains(CAP) ? CAP : null;
	}

	public static int raw(BlockState state) {
		IntProperty property = property(state);
		return property == null ? 0 : state.get(property);
	}

	public static BlockState with(BlockState state, int snow) {
		IntProperty property = property(state);
		return property == null ? state : state.with(property, Math.max(0, Math.min(property == LAYERS ? MAX_LAYERS : 1, snow)));
	}

	public static BlockState bare(BlockState state) {
		return raw(state) == 0 ? state : with(state, 0);
	}

	/** A state with the snow of another state of a block that holds snow. */
	public static BlockState withSnowOf(BlockState state, BlockState other) {
		return property(state) == null ? state : with(state, raw(other));
	}

	private static Room room(BlockState state) {
		return ROOMS.computeIfAbsent(bare(state), SnowCover::measure);
	}

	private static Room measure(BlockState bare) {
		int[] heights = topHeights(bare.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN));
		boolean whole = true;
		int lowest = 16;
		for (int height : heights) {
			whole &= height >= 16;
			if (height > 0 && height < lowest) {
				lowest = height;
			}
		}
		boolean wholeTop = whole;
		int layers = Math.min(MAX_LAYERS, (16 - lowest) / 2);
		return SHARED.computeIfAbsent(Arrays.toString(heights), key -> new Room(heights, wholeTop, layers));
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

	/** Whether snow on this block lies on top of it, as snow layers: its top is whole. */
	public static boolean wholeTop(BlockState state) {
		IntProperty property = property(state);
		return property == LAYERS ? room(state).wholeTop() : property == CAP && state.getBlock() instanceof MixedSlabs.MixedSlabBlock;
	}

	/** Whether its snow is only a cap on its top edge, in the space above: a wall, fence, gate or vertical slab. */
	public static boolean capOnly(BlockState state) {
		IntProperty property = property(state);
		return property == CAP ? !wholeTop(state) : property == LAYERS && !room(state).wholeTop() && room(state).layers() == 0;
	}

	/** The layers of snow this block can hold itself: in its open space, 1 for a cap, 0 if none (a whole top, water, an open gate). */
	public static int capacity(BlockState state) {
		IntProperty property = property(state);
		if (property == null || state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)
				|| state.contains(Properties.OPEN) && state.get(Properties.OPEN) || wholeTop(state)) {
			return 0;
		}
		return property == CAP ? 1 : Math.max(1, room(state).layers());
	}

	/** The layers of snow on this block, in its own space (0 if its snow is snow layers lying on it). */
	public static int held(BlockState state) {
		int raw = raw(state);
		return raw == 0 ? 0 : Math.min(raw, capacity(state));
	}

	/** The layers of snow its open space holds when full (0 if it has none: a cap, or a whole top). */
	public static int openSpace(BlockState state) {
		return property(state) == LAYERS && !room(state).wholeTop() ? room(state).layers() : 0;
	}

	/** Whether snow layers lie on its whole top (its sides then have the drips). */
	public static boolean snowOnTop(BlockState state) {
		return raw(state) > 0 && wholeTop(state);
	}

	/** Whether its open space is full of snow, so that snow layers can lie on it as on a full block. */
	public static boolean full(BlockState state) {
		int room = openSpace(state);
		return room > 0 && held(state) >= room;
	}

	/** Whether it ticks now and then to melt its snow (snow lying on a whole top is a snow layer, which melts itself). */
	public static boolean melts(BlockState state) {
		return raw(state) > 0 && !wholeTop(state);
	}

	public static boolean isSnow(BlockState state) {
		return state.isIn(BlockTags.SNOW);
	}

	/** Its snow as a snow layer of its own: what it drops, how it breaks and sounds underfoot. */
	public static BlockState asSnowLayer(BlockState state) {
		return Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, Math.max(1, Math.min(SnowBlock.MAX_LAYERS, held(state))));
	}

	/** A slab's or stairs' outline with the snow in its open space. */
	public static VoxelShape withOutline(BlockState state, VoxelShape shape) {
		// (Snow first: a bare block's shape is what its room is measured from.)
		int layers = held(state);
		return layers == 0 || openSpace(state) == 0 ? shape : OUTLINES.computeIfAbsent(state, s -> VoxelShapes.union(shape, snowShape(room(s), 2 * layers)));
	}

	/** Its collision with the snow a layer lower, as snow layers' (a single layer has none). */
	public static VoxelShape withCollision(BlockState state, VoxelShape shape) {
		int layers = held(state);
		return layers <= 1 || openSpace(state) == 0 ? shape : COLLISIONS.computeIfAbsent(state, s -> VoxelShapes.union(shape, snowShape(room(s), 2 * (layers - 1))));
	}

	/** Snow some pixels deep on every surface of a block's open space, up to the top of the block. */
	private static VoxelShape snowShape(Room room, int depth) {
		List<VoxelShape> rows = new ArrayList<>();
		int[] heights = room.heights();
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; ) {
				int height = heights[x + 16 * z];
				int end = x + 1;
				while (end < 16 && heights[end + 16 * z] == height) {
					end++;
				}
				if (height > 0 && height < 16) {
					rows.add(Block.createCuboidShape(x, height, z, end, Math.min(16, height + depth), z + 1));
				}
				x = end;
			}
		}
		return rows.stream().reduce(VoxelShapes.empty(), VoxelShapes::union).simplify();
	}

	/** Whether a player digs the snow off this block rather than the block: there's snow on it, and the tool in hand digs snow. */
	public static boolean digsSnow(PlayerEntity player, BlockState state) {
		return held(state) > 0 && player.getMainHandStack().isSuitableFor(Blocks.SNOW.getDefaultState());
	}

	/** Its drops with its snow's (a player breaking it with something that doesn't dig snow, a blast). */
	public static List<ItemStack> withSnowDrops(BlockState state, List<ItemStack> drops, LootContextParameterSet.Builder builder) {
		if (held(state) == 0) {
			return drops;
		}
		List<ItemStack> all = new ArrayList<>(drops);
		all.addAll(asSnowLayer(state).getDroppedStacks(builder));
		return all;
	}

	/** Whether the light melts snow here, as it melts snow layers: block light above 11, in its space or the space above. */
	public static boolean meltsHere(WorldView world, BlockPos pos) {
		return world.getLightLevel(LightType.BLOCK, pos) > 11 || world.getLightLevel(LightType.BLOCK, pos.up()) > 11;
	}

	/** A random tick of a block with snow: its snow melts if the light melts it (or it can't stay, as in water). Returns whether it went. */
	public static boolean melt(BlockState state, ServerWorld world, BlockPos pos) {
		if (held(state) > 0 && !meltsHere(world, pos)) {
			return false;
		}
		world.setBlockState(pos, bare(state), Block.NOTIFY_ALL);
		return true;
	}

	/**
	 * Snow settling where a snow layer would (an empty space, cold, dark enough and in the world), but can't, onto the
	 * block below it: a layer more on it, up to most (the snow-piling game rule; a cap is one). Returns whether it settled.
	 */
	public static boolean snowOnto(WorldAccess world, Biome biome, BlockPos space, int most, int flags) {
		if (biome.doesNotSnow(space) || space.getY() < world.getBottomY() || space.getY() >= world.getTopY()
				|| world.getLightLevel(LightType.BLOCK, space) >= 10 || !world.getBlockState(space).isAir()) {
			return false;
		}
		BlockPos pos = space.down();
		BlockState state = world.getBlockState(pos);
		int held = held(state);
		if (held >= Math.min(most, capacity(state))) {
			return false;
		}
		BlockState snowed = with(state, held + 1);
		if (world instanceof World real) {
			Block.pushEntitiesUpBeforeBlockChange(state, snowed, real, pos);
		}
		world.setBlockState(pos, snowed, flags);
		return true;
	}

	/** A snow layer about to settle in a space: the block under it, if its whole top takes snow, says snow lies on it. */
	public static void snowLiesOn(WorldAccess world, BlockPos space, int flags) {
		BlockPos pos = space.down();
		BlockState state = world.getBlockState(pos);
		if (wholeTop(state) && raw(state) == 0) {
			world.setBlockState(pos, with(state, 1), flags);
		}
	}

	/** A snow layer put on a block's top by hand: a layer more, if it has room (a cap only with the space above empty). Returns whether it took it. */
	public static boolean placeByHand(ItemStack stack, World world, BlockPos pos, BlockState state, @Nullable PlayerEntity player, Direction side) {
		if (!stack.isOf(Items.SNOW) || side != Direction.UP) {
			return false;
		}
		int held = held(state);
		if (held >= capacity(state) || capOnly(state) && !world.getBlockState(pos.up()).isAir()
				|| player != null && (!player.canModifyBlocks() || !world.canPlayerModifyAt(player, pos))) {
			return false;
		}
		BlockState snowed = with(state, held + 1);
		world.setBlockState(pos, snowed, Block.NOTIFY_ALL_AND_REDRAW);
		BlockSoundGroup sounds = BlockSoundGroup.SNOW;
		world.playSound(player, pos, sounds.getPlaceSound(), SoundCategory.BLOCKS, (sounds.getVolume() + 1.0F) / 2.0F, sounds.getPitch() * 0.8F);
		world.emitGameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Emitter.of(player, snowed));
		stack.decrementUnlessCreative(1, player);
		return true;
	}

	/** A block whose neighbour above changed: snow lies on its whole top as the space above says; a cap goes when anything takes that space. */
	public static BlockState neighborChanged(BlockState state, Direction direction, BlockState neighbor) {
		if (direction != Direction.UP || property(state) == null) {
			return state;
		}
		if (wholeTop(state)) {
			return with(state, isSnow(neighbor) ? 1 : 0);
		}
		return raw(state) > 0 && capOnly(state) && !neighbor.isAir() ? bare(state) : state;
	}

	/** A block put in its space or changed in it (a slab filled, turned with a tool): snow on a whole top as the space above says, otherwise the snow it can still hold. */
	public static BlockState settle(BlockState state, BlockView world, BlockPos pos) {
		if (property(state) == null) {
			return state;
		}
		return with(state, wholeTop(state) ? (isSnow(world.getBlockState(pos.up())) ? 1 : 0) : held(state));
	}

	/** A gate set open: its snow falls off in a puff of snow. */
	public static BlockState gateMoved(World world, BlockPos pos, BlockState state, @Nullable PlayerEntity player) {
		if (raw(state) == 0 || !state.contains(Properties.OPEN) || !state.get(Properties.OPEN)) {
			return state;
		}
		world.syncWorldEvent(player, WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(Blocks.SNOW.getDefaultState()));
		return bare(state);
	}
}
