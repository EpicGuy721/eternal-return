package com.eternalreturn.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractCauldronBlock;
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
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
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
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Stone dripstone (StoneDripstones): pointed dripstone made of another stone, behaving as vanilla's. It hangs under a
 * full face (a stalactite) or stands on one (a stalagmite), in segments base, middle, frustum, tip, and a merged tip where
 * a stalactite meets a stalagmite of the same stone; a stalactite whose support goes falls and its tip skewers what it
 * lands on; landing on a stalagmite's tip hurts double; a thrown trident breaks it. A stalactite drips the fluid standing
 * on the block above its root (water, or lava) and fills a cauldron up to 11 blocks under its tip (AbstractCauldronBlockMixin);
 * under its own stone (one of its hosts) with still water on top it grows as vanilla's grows under a dripstone block: one
 * block at the tip (to 7 long) or a stalagmite on the floor below (up to 10 down), at vanilla's rate.
 */
public class StoneDripstoneBlock extends Block implements LandingBlock, Waterloggable {
	public static final MapCodec<StoneDripstoneBlock> CODEC = createCodec(settings -> new StoneDripstoneBlock(settings, "stone"));
	public static final DirectionProperty VERTICAL_DIRECTION = PointedDripstoneBlock.VERTICAL_DIRECTION;
	public static final EnumProperty<Thickness> THICKNESS = PointedDripstoneBlock.THICKNESS;
	public static final BooleanProperty WATERLOGGED = PointedDripstoneBlock.WATERLOGGED;
	/** Vanilla's chances: growth per random tick, and a drip filling a cauldron (water, lava). */
	public static final float GROWTH_CHANCE = 0.011377778F;
	public static final float WATER_DRIP_CHANCE = 0.17578125F;
	public static final float LAVA_DRIP_CHANCE = 0.05859375F;
	public static final int MAX_STALACTITE = 7;
	public static final int DRIP_RANGE = 11;

	private static final VoxelShape TIP_MERGE = Block.createCuboidShape(5, 0, 5, 11, 16, 11);
	private static final VoxelShape UP_TIP = Block.createCuboidShape(5, 0, 5, 11, 11, 11);
	private static final VoxelShape DOWN_TIP = Block.createCuboidShape(5, 5, 5, 11, 16, 11);
	private static final VoxelShape FRUSTUM = Block.createCuboidShape(4, 0, 4, 12, 16, 12);
	private static final VoxelShape MIDDLE = Block.createCuboidShape(3, 0, 3, 13, 16, 13);
	private static final VoxelShape BASE = Block.createCuboidShape(2, 0, 2, 14, 16, 14);

	/** The table entry (stone_dripstones.json) this block is. */
	public final String kind;

	public StoneDripstoneBlock(AbstractBlock.Settings settings, String kind) {
		super(settings);
		this.kind = kind;
		this.setDefaultState(this.stateManager.getDefaultState().with(VERTICAL_DIRECTION, Direction.UP).with(THICKNESS, Thickness.TIP).with(WATERLOGGED, false));
	}

	@Override
	public MapCodec<? extends StoneDripstoneBlock> getCodec() {
		return CODEC;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(VERTICAL_DIRECTION, THICKNESS, WATERLOGGED);
	}

	// ------------------------------------------------------------------ shape of a column

	public boolean isPiece(BlockState state, Direction pointing) {
		return state.isOf(this) && state.get(VERTICAL_DIRECTION) == pointing;
	}

	private boolean canBeAt(WorldView world, BlockPos pos, Direction pointing) {
		BlockPos support = pos.offset(pointing.getOpposite());
		BlockState state = world.getBlockState(support);
		return state.isSideSolidFullSquare(world, support, pointing) || this.isPiece(state, pointing);
	}

	private Thickness thicknessAt(WorldView world, BlockPos pos, Direction pointing, boolean merge) {
		BlockState next = world.getBlockState(pos.offset(pointing));
		if (this.isPiece(next, pointing.getOpposite())) {
			return merge || next.get(THICKNESS) == Thickness.TIP_MERGE ? Thickness.TIP_MERGE : Thickness.TIP;
		}
		if (!this.isPiece(next, pointing)) {
			return Thickness.TIP;
		}
		Thickness nextThickness = next.get(THICKNESS);
		if (nextThickness == Thickness.TIP || nextThickness == Thickness.TIP_MERGE) {
			return Thickness.FRUSTUM;
		}
		return this.isPiece(world.getBlockState(pos.offset(pointing.getOpposite())), pointing) ? Thickness.MIDDLE : Thickness.BASE;
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

	/** A stalactite from here down falls, each segment a falling block; the lowest (the tip) hurts what it lands on. */
	public void fall(ServerWorld world, BlockPos from) {
		BlockPos.Mutable pos = from.mutableCopy();
		int length = 0;
		while (this.isPiece(world.getBlockState(pos), Direction.DOWN)) {
			length++;
			pos.move(Direction.DOWN);
		}
		pos.set(from);
		for (int i = 0; i < length; i++) {
			BlockState state = world.getBlockState(pos);
			FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, pos.toImmutable(), state.with(WATERLOGGED, false));
			falling.setDestroyedOnLanding();
			if (i == length - 1) {
				int most = Math.max(length, 6);
				falling.setHurtEntities(1.0F * most, most);
			}
			pos.move(Direction.DOWN);
		}
	}

	// ------------------------------------------------------------------ dripping and growing

	/** The root of the stalactite this piece belongs to (the segment fixed to the ceiling), or null for a stalagmite. */
	@Nullable
	public BlockPos rootOf(BlockView world, BlockPos pos, BlockState state) {
		if (!state.isOf(this) || state.get(VERTICAL_DIRECTION) != Direction.DOWN) {
			return null;
		}
		BlockPos.Mutable at = pos.mutableCopy();
		for (int i = 0; i < DRIP_RANGE + 1 && this.isPiece(world.getBlockState(at.up()), Direction.DOWN); i++) {
			at.move(Direction.UP);
		}
		return at.toImmutable();
	}

	/** The tip of the stalactite this piece belongs to, or null. */
	@Nullable
	public BlockPos tipOf(BlockView world, BlockPos pos) {
		BlockPos.Mutable at = pos.mutableCopy();
		for (int i = 0; i < DRIP_RANGE + 1; i++) {
			BlockState state = world.getBlockState(at);
			if (!this.isPiece(state, Direction.DOWN)) {
				return null;
			}
			if (state.get(THICKNESS) == Thickness.TIP) {
				return at.toImmutable();
			}
			at.move(Direction.DOWN);
		}
		return null;
	}

	/** The fluid on the block above a stalactite's root (what it drips), or empty. */
	public Fluid dripFluid(World world, BlockPos pos, BlockState state) {
		BlockPos root = this.rootOf(world, pos, state);
		if (root == null) {
			return Fluids.EMPTY;
		}
		BlockPos above = root.up(2);
		if (world.getBlockState(above).isOf(Blocks.MUD) && !world.getDimension().ultrawarm()) {
			return Fluids.WATER;
		}
		return world.getFluidState(above).getFluid() instanceof net.minecraft.fluid.FlowableFluid flowable ? flowable.getStill() : Fluids.EMPTY;
	}

	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (!this.isPiece(state, Direction.DOWN) || state.get(THICKNESS) != Thickness.TIP) {
			return;
		}
		float roll = random.nextFloat();
		if (roll > 0.12F) {
			return;
		}
		Fluid fluid = this.dripFluid(world, pos, state);
		if (fluid == Fluids.EMPTY && roll >= 0.02F) {
			return;
		}
		if (fluid == Fluids.EMPTY) {
			fluid = world.getDimension().ultrawarm() ? Fluids.LAVA : Fluids.WATER;
		}
		Vec3d offset = state.getModelOffset(world, pos);
		world.addParticle(fluid.isIn(FluidTags.LAVA) ? ParticleTypes.DRIPPING_DRIPSTONE_LAVA : ParticleTypes.DRIPPING_DRIPSTONE_WATER,
				pos.getX() + 0.5 + offset.x, pos.getY() + 1 - 0.6875 - 0.0625, pos.getZ() + 0.5 + offset.z, 0, 0, 0);
	}

	@Override
	protected boolean hasRandomTicks(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (!this.isPiece(state, Direction.DOWN)) {
			return;
		}
		this.dripTick(state, world, pos, random.nextFloat());
		if (random.nextFloat() < GROWTH_CHANCE) {
			this.tryGrow(state, world, pos, random);
		}
	}

	/** As vanilla's: a drip (by chance) lands in the first cauldron up to 11 below the tip and fills it a step later. */
	public void dripTick(BlockState state, ServerWorld world, BlockPos pos, float chance) {
		Fluid fluid = this.dripFluid(world, pos, state);
		float needed = fluid == Fluids.WATER ? WATER_DRIP_CHANCE : fluid == Fluids.LAVA ? LAVA_DRIP_CHANCE : -1;
		if (chance >= needed) {
			return;
		}
		BlockPos tip = this.tipOf(world, pos);
		if (tip == null) {
			return;
		}
		BlockPos cauldron = cauldronBelow(world, tip, fluid);
		if (cauldron != null) {
			world.syncWorldEvent(WorldEvents.POINTED_DRIPSTONE_DRIPS, tip, 0);
			world.scheduleBlockTick(cauldron, world.getBlockState(cauldron).getBlock(), 50 + tip.getY() - cauldron.getY());
		}
	}

	@Nullable
	private static BlockPos cauldronBelow(World world, BlockPos tip, Fluid fluid) {
		BlockPos.Mutable at = tip.mutableCopy();
		for (int i = 0; i < DRIP_RANGE; i++) {
			at.move(Direction.DOWN);
			BlockState state = world.getBlockState(at);
			if (state.getBlock() instanceof AbstractCauldronBlock cauldron && ((com.eternalreturn.mixin.CauldronDripInvoker) cauldron).eternalreturn$canFill(fluid)) {
				return at.toImmutable();
			}
			if (!canDripThrough(world, at, state)) {
				return null;
			}
		}
		return null;
	}

	private static boolean canDripThrough(BlockView world, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (state.isOpaqueFullCube(world, pos)) {
			return false;
		}
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		VoxelShape shape = state.getCollisionShape(world, pos);
		return !VoxelShapes.matchesAnywhere(Block.createCuboidShape(6, 0, 6, 10, 16, 10), shape, net.minecraft.util.function.BooleanBiFunction.AND);
	}

	/**
	 * For a cauldron (AbstractCauldronBlockMixin): the first stalactite tip of any stone dripstone up to 11 above it and the
	 * fluid it drips, or null.
	 */
	@Nullable
	public static Fluid dripInto(World world, BlockPos cauldron) {
		BlockPos.Mutable at = cauldron.mutableCopy();
		for (int i = 0; i < DRIP_RANGE; i++) {
			at.move(Direction.UP);
			BlockState state = world.getBlockState(at);
			if (state.getBlock() instanceof StoneDripstoneBlock dripstone && dripstone.isPiece(state, Direction.DOWN)) {
				Fluid fluid = dripstone.dripFluid(world, at, state);
				return fluid == Fluids.EMPTY ? null : fluid;
			}
			if (!canDripThrough(world, at, state)) {
				return null;
			}
		}
		return null;
	}

	/** Grows as vanilla's: only under one of its own host stones with still water on top. */
	public void tryGrow(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		BlockPos root = this.rootOf(world, pos, state);
		if (root == null) {
			return;
		}
		BlockState host = world.getBlockState(root.up());
		BlockState water = world.getBlockState(root.up(2));
		if (!StoneDripstones.isHostOf(host.getBlock(), this) || !water.isOf(Blocks.WATER) || !water.getFluidState().isStill()) {
			return;
		}
		BlockPos tip = this.tipOf(world, root);
		if (tip == null || root.getY() - tip.getY() + 1 >= MAX_STALACTITE) {
			// Long enough: only the stalagmite under it may still grow.
			if (tip != null) {
				this.growStalagmite(world, tip);
			}
			return;
		}
		if (random.nextBoolean()) {
			this.growAt(world, tip.down(), Direction.DOWN);
		} else {
			this.growStalagmite(world, tip);
		}
	}

	private void growStalagmite(ServerWorld world, BlockPos tip) {
		BlockPos.Mutable at = tip.mutableCopy();
		for (int i = 0; i < 10; i++) {
			at.move(Direction.DOWN);
			BlockState state = world.getBlockState(at);
			if (!state.getFluidState().isEmpty()) {
				return;
			}
			if (this.isPiece(state, Direction.UP) && state.get(THICKNESS) == Thickness.TIP) {
				this.growAt(world, at.up(), Direction.UP);
				return;
			}
			if (this.canBeAt(world, at, Direction.UP) && !world.isWater(at.down())) {
				this.growAt(world, at.toImmutable(), Direction.UP);
				return;
			}
			if (!canDripThrough(world, at, state)) {
				return;
			}
		}
	}

	private void growAt(ServerWorld world, BlockPos pos, Direction pointing) {
		BlockState there = world.getBlockState(pos);
		if (this.isPiece(there, pointing.getOpposite()) && there.get(THICKNESS) == Thickness.TIP) {
			// Meets the other half: both tips merge.
			world.setBlockState(pos, there.with(THICKNESS, Thickness.TIP_MERGE), Block.NOTIFY_ALL);
			return;
		}
		if (there.isAir() || there.isOf(Blocks.WATER)) {
			world.setBlockState(pos, this.getDefaultState().with(VERTICAL_DIRECTION, pointing).with(THICKNESS, Thickness.TIP)
					.with(WATERLOGGED, world.getFluidState(pos).getFluid() == Fluids.WATER), Block.NOTIFY_ALL);
		}
	}

	// ------------------------------------------------------------------ as dripstone

	@Override
	public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
		if (state.get(VERTICAL_DIRECTION) == Direction.UP && state.get(THICKNESS) == Thickness.TIP) {
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
			world.syncWorldEvent(WorldEvents.POINTED_DRIPSTONE_LANDS, pos, 0);
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
