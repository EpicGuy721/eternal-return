package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/**
 * A trough (com.eternalreturn.survival.Troughs): a composter half as tall that holds water, hay, carrots or seeds, up to
 * MAX_LEVEL. A water bucket fills it with water (an empty bucket takes a full trough's water back), a hay bale fills
 * it with hay, and each carrot or seed (item tag eternalreturn:trough_seeds) adds a level; what's in it can only be
 * topped up with more of the same until it's empty. Animals eat and drink from it a level at a time. Empty, it
 * remembers what it held (level 0), for farmers to refill; a new one holds nothing (contents none).
 */
public class TroughBlock extends Block {
	public static final int MAX_LEVEL = 4;
	public static final EnumProperty<Contents> CONTENTS = EnumProperty.of("contents", Contents.class);
	public static final IntProperty LEVEL = IntProperty.of("level", 0, MAX_LEVEL);
	public static final TagKey<net.minecraft.item.Item> SEEDS = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("trough_seeds"));
	private static final VoxelShape SHAPE = VoxelShapes.combineAndSimplify(Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
			Block.createCuboidShape(2.0, 2.0, 2.0, 14.0, 8.0, 14.0), BooleanBiFunction.ONLY_FIRST);

	public enum Contents implements StringIdentifiable {
		NONE("none"), WATER("water"), HAY("hay"), CARROTS("carrots"), SEEDS("seeds");

		private final String name;

		Contents(String name) {
			this.name = name;
		}

		@Override
		public String asString() {
			return this.name;
		}
	}

	public TroughBlock(Settings settings) {
		super(settings);
		this.setDefaultState(this.getDefaultState().with(CONTENTS, Contents.NONE).with(LEVEL, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(CONTENTS, LEVEL);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canPathfindThrough(BlockState state, NavigationType type) {
		return false;
	}

	@Override
	protected boolean hasComparatorOutput(BlockState state) {
		return true;
	}

	@Override
	protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
		return state.get(LEVEL);
	}

	/** Holding some of this. */
	public static boolean holds(BlockState state, Contents contents) {
		return state.getBlock() instanceof TroughBlock && state.get(CONTENTS) == contents && state.get(LEVEL) > 0;
	}

	/** Room for this: empty, or holding this and not full. */
	public static boolean takes(BlockState state, Contents contents) {
		return state.getBlock() instanceof TroughBlock && state.get(LEVEL) < MAX_LEVEL && (state.get(LEVEL) == 0 || state.get(CONTENTS) == contents);
	}

	/** Adds up to this many levels of the contents (if there's room for them); how many went in. */
	public static int fill(World world, BlockPos pos, Contents contents, int levels) {
		BlockState state = world.getBlockState(pos);
		if (!takes(state, contents)) {
			return 0;
		}
		int added = Math.min(levels, MAX_LEVEL - state.get(LEVEL));
		world.setBlockState(pos, state.with(CONTENTS, contents).with(LEVEL, state.get(LEVEL) + added));
		return added;
	}

	/** One level eaten or drunk (it remembers what it held when empty). */
	public static void take(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof TroughBlock && state.get(LEVEL) > 0) {
			world.setBlockState(pos, state.with(LEVEL, state.get(LEVEL) - 1));
			world.emitGameEvent(null, GameEvent.BLOCK_CHANGE, pos);
		}
	}

	@Override
	protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (stack.isOf(Items.WATER_BUCKET)) {
			if (!takes(state, Contents.WATER)) {
				return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			if (!world.isClient) {
				player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.BUCKET)));
				fill(world, pos, Contents.WATER, MAX_LEVEL);
			}
			return this.done(world, pos, player, SoundEvents.ITEM_BUCKET_EMPTY);
		}
		if (stack.isOf(Items.BUCKET)) {
			if (!holds(state, Contents.WATER) || state.get(LEVEL) < MAX_LEVEL) {
				return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			if (!world.isClient) {
				player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.WATER_BUCKET)));
				world.setBlockState(pos, state.with(LEVEL, 0));
			}
			return this.done(world, pos, player, SoundEvents.ITEM_BUCKET_FILL);
		}
		Contents contents = stack.isOf(Items.HAY_BLOCK) ? Contents.HAY : stack.isOf(Items.CARROT) ? Contents.CARROTS : stack.isIn(SEEDS) ? Contents.SEEDS : null;
		if (contents == null || !takes(state, contents)) {
			return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		}
		if (!world.isClient) {
			fill(world, pos, contents, contents == Contents.HAY ? MAX_LEVEL : 1);
			stack.decrementUnlessCreative(1, player);
		}
		return this.done(world, pos, player, contents == Contents.HAY ? SoundEvents.BLOCK_GRASS_PLACE : SoundEvents.BLOCK_COMPOSTER_FILL);
	}

	private ItemActionResult done(World world, BlockPos pos, PlayerEntity player, SoundEvent sound) {
		world.playSound(player, pos, sound, SoundCategory.BLOCKS, 1.0F, 1.0F);
		world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		return ItemActionResult.success(world.isClient);
	}
}
