package com.eternalreturn.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.EmptyBlockView;
import org.jetbrains.annotations.Nullable;

/**
 * Vertical slabs, for every slab there is (any SlabBlock: vanilla's, this mod's, other mods'). Every slab has a
 * slab_axis property (SlabBlockMixin): y, the default, lies flat as in vanilla; x or z stands it on edge, its type then
 * saying which half it fills (bottom: west or north, top: east or south), and a double slab with it is a pair split that
 * way. A vertical slab is still its slab, so its hardness, tool, drops, burning and water are the slab's own, and it's
 * drawn with the slab's own model turned on edge (client SlabModels). Slabs place flat, as in vanilla; the wrench moves
 * a slab to the side clicked, and turns a pair a quarter turn (the half aimed at goes to the side clicked). Two different
 * slabs in one space make a mixed slab (MixedSlabs). Nothing here registers anything: every slab's constructor reads it.
 */
public final class Slabs {
	/** Which way a slab lies; y, flat, is the first value, so the default of every slab, even one setting its own default. */
	public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.of("slab_axis", Direction.Axis.class,
			Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z);
	/** A mixed slab's first half is on the positive side (up, south or east). */
	public static final BooleanProperty FLIPPED = BooleanProperty.of("flipped");
	private static final VoxelShape[] SHAPES = new VoxelShape[6];

	static {
		SHAPES[Direction.DOWN.getId()] = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
		SHAPES[Direction.UP.getId()] = Block.createCuboidShape(0.0, 8.0, 0.0, 16.0, 16.0, 16.0);
		SHAPES[Direction.NORTH.getId()] = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 8.0);
		SHAPES[Direction.SOUTH.getId()] = Block.createCuboidShape(0.0, 0.0, 8.0, 16.0, 16.0, 16.0);
		SHAPES[Direction.WEST.getId()] = Block.createCuboidShape(0.0, 0.0, 0.0, 8.0, 16.0, 16.0);
		SHAPES[Direction.EAST.getId()] = Block.createCuboidShape(8.0, 0.0, 0.0, 16.0, 16.0, 16.0);
	}

	private Slabs() {
	}

	public static boolean isSlab(BlockState state) {
		return state.getBlock() instanceof SlabBlock && state.contains(AXIS);
	}

	public static Direction.Axis axis(BlockState slab) {
		return slab.contains(AXIS) ? slab.get(AXIS) : Direction.Axis.Y;
	}

	private static Direction.AxisDirection sign(boolean positive) {
		return positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
	}

	/** The side of its block a single slab fills (down or up when flat). */
	public static Direction side(BlockState slab) {
		return Direction.from(axis(slab), sign(slab.get(SlabBlock.TYPE) == SlabType.TOP));
	}

	/** A single slab moved to fill a side, or a pair turned to split along that side's axis. */
	public static BlockState onSide(BlockState slab, Direction side) {
		BlockState turned = slab.with(AXIS, side.getAxis());
		return slab.get(SlabBlock.TYPE) == SlabType.DOUBLE ? turned
				: turned.with(SlabBlock.TYPE, side.getDirection() == Direction.AxisDirection.POSITIVE ? SlabType.TOP : SlabType.BOTTOM);
	}

	public static VoxelShape shape(BlockState slab) {
		return slab.get(SlabBlock.TYPE) == SlabType.DOUBLE ? VoxelShapes.fullCube() : SHAPES[side(slab).getId()];
	}

	/** A slab as a mixed slab keeps it: flat, bottom, dry and without snow (any other property, a mod's own, kept). */
	public static BlockState normalized(BlockState slab) {
		BlockState flat = SnowCover.bare(slab).with(SlabBlock.TYPE, SlabType.BOTTOM).with(SlabBlock.WATERLOGGED, false);
		return flat.contains(AXIS) ? flat.with(AXIS, Direction.Axis.Y) : flat;
	}

	/** A kept slab put back as one half of its block: the positive or negative half along an axis. */
	public static BlockState asHalf(BlockState slab, Direction.Axis axis, boolean positive) {
		return slab.with(SlabBlock.TYPE, positive ? SlabType.TOP : SlabType.BOTTOM).with(AXIS, axis);
	}

	/** Whether a slab can be half of a mixed slab: whole, it's a solid opaque block (not some mod's see-through slab). */
	public static boolean mixable(BlockState slab) {
		return isSlab(slab) && slab.with(SlabBlock.TYPE, SlabType.DOUBLE).isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
	}

	/** Whether a point hit on a block's side lies in the block's positive half along an axis. */
	public static boolean positiveAt(Vec3d hit, Direction side, BlockPos pos, Direction.Axis axis) {
		// A point on the side itself counts as just inside it.
		double inside = side.getAxis() == axis ? side.getDirection().offset() * 1.0E-3 : 0.0;
		return axis.choose(hit.x, hit.y, hit.z) - axis.choose(pos.getX(), pos.getY(), pos.getZ()) - inside > 0.5;
	}

	/** Whether a player aims at the positive half of a block split along an axis (not aiming at it: is on that side). */
	public static boolean aimsPositive(PlayerEntity player, BlockPos pos, Direction.Axis axis) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVec(1.0F).multiply(eye.distanceTo(Vec3d.ofCenter(pos)) + 2.0));
		BlockHitResult hit = VoxelShapes.fullCube().raycast(eye, end, pos);
		if (hit == null) {
			return axis.choose(eye.x, eye.y, eye.z) > axis.choose(pos.getX(), pos.getY(), pos.getZ()) + 0.5;
		}
		return positiveAt(hit.getPos(), hit.getSide(), pos, axis);
	}

	/** Whether a slab item fills the other half of a single slab: vanilla's rule for the same slab, turned with it. */
	public static boolean canFill(BlockState slab, ItemPlacementContext context) {
		if (slab.get(SlabBlock.TYPE) == SlabType.DOUBLE || !(context.getStack().getItem() instanceof BlockItem item)
				|| !(item.getBlock() instanceof SlabBlock)) {
			return false;
		}
		if (!slab.isOf(item.getBlock()) && !(mixable(slab) && mixable(item.getBlock().getDefaultState()))) {
			return false;
		}
		if (!context.canReplaceExisting()) {
			return true;
		}
		// Clicked on the slab itself: on its open face, or on a side face in the open half.
		Direction open = side(slab).getOpposite();
		Direction clicked = context.getSide();
		if (clicked.getAxis() == open.getAxis()) {
			return clicked == open;
		}
		return positiveAt(context.getHitPos(), clicked, context.getBlockPos(), open.getAxis()) == (open.getDirection() == Direction.AxisDirection.POSITIVE);
	}

	/**
	 * A slab, pair or mixed slab as the wrench leaves it, clicked on a side at a point, or null if it doesn't move. A
	 * single slab moves to the side clicked; a pair turns so the half aimed at is on the side clicked.
	 */
	@Nullable
	public static BlockState wrenched(BlockState state, BlockPos pos, Direction clicked, Vec3d hit) {
		if (state.contains(FLIPPED)) {
			Direction.Axis axis = state.get(AXIS);
			if (clicked.getAxis() == axis) {
				return null;
			}
			boolean aimedFirst = positiveAt(hit, clicked, pos, axis) == state.get(FLIPPED);
			return state.with(AXIS, clicked.getAxis()).with(FLIPPED, aimedFirst == (clicked.getDirection() == Direction.AxisDirection.POSITIVE));
		}
		if (!isSlab(state)) {
			return null;
		}
		if (state.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
			return clicked.getAxis() == state.get(AXIS) ? null : state.with(AXIS, clicked.getAxis());
		}
		return side(state) == clicked ? null : onSide(state, clicked);
	}

	/** Whether a fence, wall or pane joins a vertical slab's face: its narrow edges join (its back joins as any full face). */
	public static boolean joinsAtEdge(BlockState state, Direction face) {
		if (!isSlab(state) || state.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
			return false;
		}
		Direction.Axis axis = state.get(AXIS);
		return axis.isHorizontal() && face.getAxis().isHorizontal() && face.getAxis() != axis;
	}

	/** A slab turned with a build: a single slab's side or a pair's split turns with it. */
	public static BlockState rotate(BlockState slab, BlockRotation rotation) {
		return slab.contains(AXIS) && slab.get(AXIS).isHorizontal() ? onSide(slab, rotation.rotate(side(slab))) : slab;
	}

	public static BlockState mirror(BlockState slab, BlockMirror mirror) {
		return slab.contains(AXIS) && slab.get(AXIS).isHorizontal() ? onSide(slab, mirror.apply(side(slab))) : slab;
	}
}
