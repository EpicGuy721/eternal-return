package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mixin.slabs.FireBlockAccessor;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mixed slabs: two different slabs in one space (any two whose double is a solid block, on opposite sides) make a mixed
 * slab, which keeps both (MixedSlabEntity) and acts as the half in question: mining aims at a half, at its speed and with
 * its tool, and breaks and drops only it (MixedSlabBreakMixin), steps sound of the half underfoot (SlabStepMixin), fire
 * takes a flammable half and leaves the other (SlabFireMixin). Whole, it's a solid block: it blocks light, carries
 * redstone, resists blasts as its stronger half and drops both halves when something breaks it whole. Its block state
 * holds which way the pair is split (slab_axis) and which side its first half is on (flipped), so the wrench and builds
 * turned with structure blocks turn it without touching the halves. Two of the same slab make vanilla's double slab.
 */
public final class MixedSlabs {
	public static final Block MIXED_SLAB = Registry.register(Registries.BLOCK, EternalReturn.id("mixed_slab"),
			new MixedSlabBlock(AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).instrument(NoteBlockInstrument.BASEDRUM).strength(2.0F, 6.0F)
					.sounds(BlockSoundGroup.STONE)));
	public static final BlockEntityType<MixedSlabEntity> MIXED_SLAB_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, EternalReturn.id("mixed_slab"),
			BlockEntityType.Builder.create(MixedSlabEntity::new, MIXED_SLAB).build());
	/** The player whose game this is, on a client (middle-click picks the half it aims at); set by the client. */
	public static Supplier<PlayerEntity> clientPlayer = () -> null;

	private MixedSlabs() {
	}

	public static void register() {
	}

	/** A mixed slab's two slabs, as kept (Slabs.normalized): the first, and the second on the other side. */
	public record Halves(BlockState first, BlockState second) {
	}

	@Nullable
	public static Halves halves(BlockView world, BlockPos pos) {
		return world.getBlockEntity(pos) instanceof MixedSlabEntity mixed ? mixed.halves() : null;
	}

	/** The half of a mixed slab on its positive or negative side, as a slab on its own; null without its data. */
	@Nullable
	public static BlockState half(BlockState mixed, @Nullable Halves halves, boolean positive) {
		if (halves == null) {
			return null;
		}
		return Slabs.asHalf(positive != mixed.get(Slabs.FLIPPED) ? halves.second() : halves.first(), mixed.get(Slabs.AXIS), positive);
	}

	/** The half a player aims at; the mixed slab itself without its data. */
	public static BlockState aimedHalf(PlayerEntity player, BlockView world, BlockPos pos, BlockState mixed) {
		BlockState half = half(mixed, halves(world, pos), Slabs.aimsPositive(player, pos, mixed.get(Slabs.AXIS)));
		return half == null ? mixed : half;
	}

	/**
	 * What an entity at pos stands on: for a mixed slab, its top half, or the half under its middle when on edge; for a
	 * block with snow on it (SnowCover), the snow.
	 */
	public static BlockState underfoot(Entity entity, BlockPos pos, BlockState state) {
		if (SnowCover.held(state) > 0) {
			return SnowCover.asSnowLayer(state);
		}
		if (!state.isOf(MIXED_SLAB)) {
			return state;
		}
		Direction.Axis axis = state.get(Slabs.AXIS);
		double along = axis.choose(entity.getX(), entity.getY(), entity.getZ()) - axis.choose(pos.getX(), pos.getY(), pos.getZ());
		BlockState half = half(state, halves(entity.getWorld(), pos), along >= 0.5);
		return half == null ? state : half;
	}

	/** The half left when the other goes, or null without the mixed slab's data. */
	@Nullable
	public static BlockState otherHalf(BlockView world, BlockPos pos, BlockState mixed, BlockState gone) {
		Halves halves = halves(world, pos);
		BlockState positive = half(mixed, halves, true);
		return positive == null ? null : positive.equals(gone) ? half(mixed, halves, false) : positive;
	}

	/** Breaks one half of a mixed slab, leaving the other as a slab on its own. */
	public static boolean removeHalf(World world, BlockPos pos, BlockState mixed, BlockState gone) {
		BlockState left = otherHalf(world, pos, mixed, gone);
		return left != null && world.setBlockState(pos, left, Block.NOTIFY_ALL);
	}

	/** What fire sees of a block: a mixed slab's most flammable half, if it has a flammable one. */
	public static BlockState fireView(BlockView world, BlockPos pos, BlockState state) {
		if (!state.isOf(MIXED_SLAB)) {
			return state;
		}
		Halves halves = halves(world, pos);
		BlockState negative = half(state, halves, false);
		BlockState positive = half(state, halves, true);
		if (negative == null) {
			return state;
		}
		FireBlockAccessor fire = (FireBlockAccessor) Blocks.FIRE;
		int negativeChance = fire.eternalreturn$burnChance(negative);
		int positiveChance = fire.eternalreturn$burnChance(positive);
		return negativeChance == 0 && positiveChance == 0 ? state : positiveChance > negativeChance ? positive : negative;
	}

	/** Whether lava can set something alight: for a mixed slab, either half. */
	public static boolean burnable(BlockView world, BlockPos pos, BlockState state) {
		if (!state.isOf(MIXED_SLAB)) {
			return state.isBurnable();
		}
		Halves halves = halves(world, pos);
		return halves != null && (halves.first().isBurnable() || halves.second().isBurnable());
	}

	/** A slab placed into a different single slab: the mixed slab they make; null otherwise (the same slab: vanilla's double). */
	@Nullable
	public static BlockState placementState(ItemPlacementContext context, Block placed) {
		BlockState there = context.getWorld().getBlockState(context.getBlockPos());
		if (there.isOf(placed) || !Slabs.mixable(there) || there.get(SlabBlock.TYPE) == SlabType.DOUBLE || !Slabs.mixable(placed.getDefaultState())) {
			return null;
		}
		Direction side = Slabs.side(there);
		return MIXED_SLAB.getDefaultState().with(Slabs.AXIS, side.getAxis()).with(Slabs.FLIPPED, side.getDirection() == Direction.AxisDirection.POSITIVE);
	}

	/** Places a mixed slab (SlabPlacementMixin): its first half the slab there, its second the one placed. */
	public static boolean place(ItemPlacementContext context, BlockState mixed) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		BlockState there = world.getBlockState(pos);
		if (!Slabs.isSlab(there) || !(context.getStack().getItem() instanceof BlockItem item) || !world.setBlockState(pos, mixed, Block.NOTIFY_ALL_AND_REDRAW)) {
			return false;
		}
		if (world.getBlockEntity(pos) instanceof MixedSlabEntity entity) {
			entity.setHalves(Slabs.normalized(there), Slabs.normalized(item.getBlock().getDefaultState()));
		}
		return true;
	}

	public static class MixedSlabBlock extends Block implements BlockEntityProvider {
		public static final MapCodec<MixedSlabBlock> CODEC = createCodec(MixedSlabBlock::new);

		public MixedSlabBlock(Settings settings) {
			super(settings);
			this.setDefaultState(this.getDefaultState().with(Slabs.AXIS, Direction.Axis.Y).with(Slabs.FLIPPED, false));
		}

		@Override
		public MapCodec<MixedSlabBlock> getCodec() {
			return CODEC;
		}

		@Override
		protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
			builder.add(Slabs.AXIS, Slabs.FLIPPED);
		}

		@Override
		public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
			return new MixedSlabEntity(pos, state);
		}

		@Override
		protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
			BlockState half = aimedHalf(player, world, pos, state);
			return half == state ? super.calcBlockBreakingDelta(state, player, world, pos) : half.calcBlockBreakingDelta(player, world, pos);
		}

		/** Breaks the half aimed at, with its particles and sound; the mining that follows goes on with that half. */
		@Override
		public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
			return super.onBreak(world, pos, aimedHalf(player, world, pos, state), player);
		}

		/** The half broken counts as mined and drops as itself. */
		@Override
		public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
			if (state.isOf(this)) {
				super.afterBreak(world, player, pos, state, blockEntity, tool);
			} else {
				state.getBlock().afterBreak(world, player, pos, state, null, tool);
			}
		}

		/** Broken whole (a blast, a mob): both halves drop, each by its own loot table. */
		@Override
		protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
			Halves halves = builder.getOptional(LootContextParameters.BLOCK_ENTITY) instanceof MixedSlabEntity mixed ? mixed.halves() : null;
			if (halves == null) {
				return List.of();
			}
			List<ItemStack> drops = new ArrayList<>(half(state, halves, false).getDroppedStacks(builder));
			drops.addAll(half(state, halves, true).getDroppedStacks(builder));
			return drops;
		}

		@Override
		public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
			PlayerEntity player = clientPlayer.get();
			BlockState half = player == null ? state : aimedHalf(player, world, pos, state);
			return half == state ? ItemStack.EMPTY : half.getBlock().getPickStack(world, pos, half);
		}

		/** The side the first half is on. */
		private static Direction firstSide(BlockState state) {
			return Direction.from(state.get(Slabs.AXIS), state.get(Slabs.FLIPPED) ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
		}

		private static BlockState withFirstOn(BlockState state, Direction side) {
			return state.with(Slabs.AXIS, side.getAxis()).with(Slabs.FLIPPED, side.getDirection() == Direction.AxisDirection.POSITIVE);
		}

		@Override
		protected BlockState rotate(BlockState state, BlockRotation rotation) {
			return withFirstOn(state, rotation.rotate(firstSide(state)));
		}

		@Override
		protected BlockState mirror(BlockState state, BlockMirror mirror) {
			return withFirstOn(state, mirror.apply(firstSide(state)));
		}
	}

	/** A mixed slab's two slabs, known to the players' games (drawn from their models: client SlabModels). */
	public static class MixedSlabEntity extends BlockEntity implements RenderDataBlockEntity {
		@Nullable
		private Halves halves;

		public MixedSlabEntity(BlockPos pos, BlockState state) {
			super(MIXED_SLAB_ENTITY, pos, state);
		}

		@Nullable
		public Halves halves() {
			return this.halves;
		}

		public void setHalves(BlockState first, BlockState second) {
			this.halves = new Halves(first, second);
			this.markDirty();
			if (this.world != null) {
				this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
			}
		}

		/** The halves kept in a mixed slab's data (also a piston's, moving one); null if either isn't a slab (a mod gone). */
		@Nullable
		public static Halves readHalves(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			if (!nbt.contains("First") || !nbt.contains("Second")) {
				return null;
			}
			RegistryWrapper<Block> blocks = registries.getWrapperOrThrow(RegistryKeys.BLOCK);
			BlockState first = NbtHelper.toBlockState(blocks, nbt.getCompound("First"));
			BlockState second = NbtHelper.toBlockState(blocks, nbt.getCompound("Second"));
			return Slabs.isSlab(first) && Slabs.isSlab(second) ? new Halves(first, second) : null;
		}

		@Override
		protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			super.writeNbt(nbt, registries);
			if (this.halves != null) {
				nbt.put("First", NbtHelper.fromBlockState(this.halves.first()));
				nbt.put("Second", NbtHelper.fromBlockState(this.halves.second()));
			}
		}

		@Override
		protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			super.readNbt(nbt, registries);
			this.halves = readHalves(nbt, registries);
			// (on the client, drawn again with them)
			if (this.world != null && this.world.isClient) {
				this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
			}
		}

		@Nullable
		@Override
		public Packet<ClientPlayPacketListener> toUpdatePacket() {
			return BlockEntityUpdateS2CPacket.create(this);
		}

		@Override
		public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
			return this.createNbt(registries);
		}

		@Nullable
		@Override
		public Object getRenderData() {
			return this.halves;
		}
	}
}
