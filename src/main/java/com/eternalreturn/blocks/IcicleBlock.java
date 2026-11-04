package com.eternalreturn.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LandingBlock;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.enums.Thickness;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * The icicle (backported from Java 26.4's, as minecraft.wiki describes it): hangs under a block with a full bottom face
 * (a stalactite) or stands on one with a full top face (a stalagmite), in segments base, middle, frustum, tip, and a
 * merged tip where a stalactite meets a stalagmite, like pointed dripstone. Unlike dripstone it:
 * <ul>
 * <li>grows only hanging under packed ice (or hardened packed ice, the ice caves' own stone): a stalactite tip has a 64 in 5,625 chance each random tick to grow one block
 * down, to 5 long, not when waterlogged and not into a spot where it would melt; stalagmites never grow;</li>
 * <li>melts on a random tick where the block light is 5 or more, magma lies one or two blocks below, or in the Nether
 * (an ultrawarm dimension); the segment fixed to the block never melts. A melting stalactite falls with everything
 * hanging below it; a melting stalagmite breaks with everything on top of it, dropping them;</li>
 * <li>doesn't drip.</li>
 * </ul>
 * Its sounds are the owner's clips (IceBlocks): breaking, steps, placing, hitting, and landing when it falls (volume 2, as
 * the wiki gives). As dripstone: a stalactite whose support goes falls and its tip skewers what it lands on (falling stalactite damage);
 * landing on a stalagmite's tip doubles the fall damage; a thrown trident breaks one.
 */
public class IcicleBlock extends Block implements LandingBlock, Waterloggable {
	public static final MapCodec<IcicleBlock> CODEC = createCodec(IcicleBlock::new);
	public static final DirectionProperty VERTICAL_DIRECTION = PointedDripstoneBlock.VERTICAL_DIRECTION;
	public static final EnumProperty<Thickness> THICKNESS = PointedDripstoneBlock.THICKNESS;
	public static final BooleanProperty WATERLOGGED = PointedDripstoneBlock.WATERLOGGED;
	/** The wiki's growth chance per random tick of a stalactite's tip. */
	public static final float GROWTH_CHANCE = 64F / 5625F;
	public static final int MAX_LENGTH = 5;
	public static final int MELT_LIGHT = 5;

	private static final VoxelShape TIP_MERGE = Block.createCuboidShape(5, 0, 5, 11, 16, 11);
	private static final VoxelShape UP_TIP = Block.createCuboidShape(5, 0, 5, 11, 11, 11);
	private static final VoxelShape DOWN_TIP = Block.createCuboidShape(5, 5, 5, 11, 16, 11);
	private static final VoxelShape FRUSTUM = Block.createCuboidShape(4, 0, 4, 12, 16, 12);
	private static final VoxelShape MIDDLE = Block.createCuboidShape(3, 0, 3, 13, 16, 13);
	private static final VoxelShape BASE = Block.createCuboidShape(2, 0, 2, 14, 16, 14);

	public IcicleBlock(AbstractBlock.Settings settings) {
		super(settings);
		this.setDefaultState(this.stateManager.getDefaultState().with(VERTICAL_DIRECTION, Direction.UP).with(THICKNESS, Thickness.TIP).with(WATERLOGGED, false));
	}

	@Override
	public MapCodec<? extends IcicleBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(VERTICAL_DIRECTION, THICKNESS, WATERLOGGED);
	}

	// ------------------------------------------------------------------ shape of a column

	private boolean isIcicle(BlockState state, Direction pointing) {
		return state.isOf(this) && state.get(VERTICAL_DIRECTION) == pointing;
	}

	/** Whether an icicle pointing this way can be here: a full face to hang from (or stand on), or an icicle pointing the same way. */
	private boolean canBeAt(WorldView world, BlockPos pos, Direction pointing) {
		BlockPos support = pos.offset(pointing.getOpposite());
		BlockState state = world.getBlockState(support);
		return state.isSideSolidFullSquare(world, support, pointing) || this.isIcicle(state, pointing);
	}

	/** The segment here from its neighbours: tip at the end, frustum above a tip, middle between, base at the root. */
	private Thickness thicknessAt(WorldView world, BlockPos pos, Direction pointing, boolean merge) {
		BlockState next = world.getBlockState(pos.offset(pointing));
		if (this.isIcicle(next, pointing.getOpposite())) {
			return merge || next.get(THICKNESS) == Thickness.TIP_MERGE ? Thickness.TIP_MERGE : Thickness.TIP;
		}
		if (!this.isIcicle(next, pointing)) {
			return Thickness.TIP;
		}
		Thickness nextThickness = next.get(THICKNESS);
		if (nextThickness == Thickness.TIP || nextThickness == Thickness.TIP_MERGE) {
			return Thickness.FRUSTUM;
		}
		return this.isIcicle(world.getBlockState(pos.offset(pointing.getOpposite())), pointing) ? Thickness.MIDDLE : Thickness.BASE;
	}

	@Override
	protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return this.canBeAt(world, pos, state.get(VERTICAL_DIRECTION));
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext context) {
		WorldAccess world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		Direction wanted = context.getVerticalPlayerLookDirection().getOpposite();
		Direction pointing = this.canBeAt(world, pos, wanted) ? wanted : this.canBeAt(world, pos, wanted.getOpposite()) ? wanted.getOpposite() : null;
		if (pointing == null) {
			return null;
		}
		return this.getDefaultState().with(VERTICAL_DIRECTION, pointing)
				.with(THICKNESS, this.thicknessAt(world, pos, pointing, !context.shouldCancelInteraction()))
				.with(WATERLOGGED, world.getFluidState(pos).getFluid() == Fluids.WATER);
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos,
			BlockPos neighborPos) {
		if (state.get(WATERLOGGED)) {
			world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
		}
		if (direction != Direction.UP && direction != Direction.DOWN) {
			return state;
		}
		Direction pointing = state.get(VERTICAL_DIRECTION);
		if (direction == pointing.getOpposite() && !this.canPlaceAt(state, world, pos)) {
			// Lost its support: a stalactite falls, a stalagmite breaks (next tick, as dripstone does).
			world.scheduleBlockTick(pos, this, pointing == Direction.DOWN ? 2 : 1);
			return state;
		}
		return state.with(THICKNESS, this.thicknessAt(world, pos, pointing, state.get(THICKNESS) == Thickness.TIP_MERGE));
	}

	@Override
	protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (this.canPlaceAt(state, world, pos)) {
			return;
		}
		if (state.get(VERTICAL_DIRECTION) == Direction.DOWN) {
			this.fall(world, pos);
		} else {
			world.breakBlock(pos, true);
		}
	}

	// ------------------------------------------------------------------ falling, melting, growing

	/** A stalactite from here down falls, each segment a falling block; the lowest (the tip) hurts what it lands on. */
	public void fall(ServerWorld world, BlockPos from) {
		BlockPos.Mutable pos = from.mutableCopy();
		int length = 0;
		while (this.isIcicle(world.getBlockState(pos), Direction.DOWN)) {
			length++;
			pos.move(Direction.DOWN);
		}
		pos.set(from);
		for (int i = 0; i < length; i++) {
			BlockState state = world.getBlockState(pos);
			FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, pos.toImmutable(), state.with(WATERLOGGED, false));
			falling.setDestroyedOnLanding();
			if (i == length - 1) {
				// As pointed dripstone: damage grows with the drop, at most as much as the column was long (at least 6).
				int most = Math.max(length, 6);
				falling.setHurtEntities(1.0F * most, most);
			}
			pos.move(Direction.DOWN);
		}
	}

	/** Whether an icicle (not its root segment) melts here: block light 5 or more, magma one or two below, or the Nether. */
	public static boolean meltsAt(World world, BlockPos pos) {
		return world.getLightLevel(LightType.BLOCK, pos) >= MELT_LIGHT || world.getBlockState(pos.down()).isOf(Blocks.MAGMA_BLOCK)
				|| world.getBlockState(pos.down(2)).isOf(Blocks.MAGMA_BLOCK) || world.getDimension().ultrawarm();
	}

	@Override
	protected boolean hasRandomTicks(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		Direction pointing = state.get(VERTICAL_DIRECTION);
		boolean root = !this.isIcicle(world.getBlockState(pos.offset(pointing.getOpposite())), pointing);
		if (!root && meltsAt(world, pos)) {
			this.melt(world, pos, pointing);
			return;
		}
		if (pointing == Direction.DOWN && state.get(THICKNESS) == Thickness.TIP && !state.get(WATERLOGGED) && random.nextFloat() < GROWTH_CHANCE) {
			this.grow(world, pos);
		}
	}

	/** A stalactite falls from here; a stalagmite breaks from here up, dropping. */
	public void melt(ServerWorld world, BlockPos pos, Direction pointing) {
		if (pointing == Direction.DOWN) {
			this.fall(world, pos);
			return;
		}
		BlockPos.Mutable at = pos.mutableCopy();
		while (this.isIcicle(world.getBlockState(at), Direction.UP)) {
			world.breakBlock(at.toImmutable(), true);
			at.move(Direction.UP);
		}
	}

	/** A stalactite's tip grows one block down: only under packed ice (or hardened), to MAX_LENGTH, into open air where it wouldn't melt. */
	public boolean grow(ServerWorld world, BlockPos tip) {
		BlockPos.Mutable at = tip.mutableCopy();
		int length = 0;
		while (this.isIcicle(world.getBlockState(at), Direction.DOWN)) {
			length++;
			at.move(Direction.UP);
		}
		BlockState root = world.getBlockState(at);
		if (!(root.isOf(Blocks.PACKED_ICE) || root.isOf(com.eternalreturn.worldgen.stone.HardenedBlocks.get("hardened_packed_ice"))) || length >= MAX_LENGTH) {
			return false;
		}
		BlockPos below = tip.down();
		if (!world.getBlockState(below).isAir() || meltsAt(world, below)) {
			return false;
		}
		world.setBlockState(below, this.getDefaultState().with(VERTICAL_DIRECTION, Direction.DOWN).with(THICKNESS, Thickness.TIP), Block.NOTIFY_ALL);
		return true;
	}

	// ------------------------------------------------------------------ as dripstone

	@Override
	public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
		if (state.get(VERTICAL_DIRECTION) == Direction.UP && state.get(THICKNESS) == Thickness.TIP) {
			// The fall counts twice and two blocks less: ceil(fall x 2 - 2), the wiki's figure.
			entity.handleFallDamage(fallDistance + 2.0F, 2.0F, world.getDamageSources().stalagmite());
		} else {
			super.onLandedUpon(world, state, pos, entity, fallDistance);
		}
	}

	@Override
	protected void onProjectileHit(World world, BlockState state, BlockHitResult hit, ProjectileEntity projectile) {
		if (!world.isClient && projectile.canModifyAt(world, hit.getBlockPos()) && projectile instanceof TridentEntity
				&& projectile.getVelocity().length() > 0.6) {
			world.breakBlock(hit.getBlockPos(), true);
		}
	}

	@Override
	public DamageSource getDamageSource(Entity attacker) {
		return attacker.getDamageSources().fallingStalactite(attacker);
	}

	@Override
	public void onDestroyedOnLanding(World world, BlockPos pos, FallingBlockEntity fallingBlockEntity) {
		if (!fallingBlockEntity.isSilent()) {
			world.playSound(null, pos, IceBlocks.ICICLE_LAND, SoundCategory.BLOCKS, 2.0F, 0.9F + world.random.nextFloat() * 0.1F);
		}
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		VoxelShape shape = switch (state.get(THICKNESS)) {
			case TIP_MERGE -> TIP_MERGE;
			case TIP -> state.get(VERTICAL_DIRECTION) == Direction.DOWN ? DOWN_TIP : UP_TIP;
			case FRUSTUM -> FRUSTUM;
			case MIDDLE -> MIDDLE;
			case BASE -> BASE;
		};
		Vec3d offset = state.getModelOffset(world, pos);
		return shape.offset(offset.x, 0.0, offset.z);
	}

	@Override
	protected VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
		return VoxelShapes.empty();
	}

	@Override
	protected boolean isShapeFullCube(BlockState state, BlockView world, BlockPos pos) {
		return false;
	}

	@Override
	protected float getMaxHorizontalModelOffset() {
		return 0.125F;
	}

	@Override
	protected boolean canPathfindThrough(BlockState state, NavigationType type) {
		return false;
	}
}
