package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.logic.BlockLocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.block.PistonExtensionBlock;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DebugStickStateComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The wrench and the hammer, for setting blocks by hand (250 uses each):
 * <ul>
 * <li>the wrench (4 iron ingots) turns a block: right-click turns it to the next way it can face, or the next axis it
 * can lie along (sneaking, the other way round), where it can stand;</li>
 * <li>the hammer (5 iron ingots, 2 sticks) is a survival debug stick: right-click sets the chosen property of a block
 * to its next value, sneak and right-click chooses the next property (both shown above the hotbar). Properties that
 * would make or destroy something (a crop's age, water, cake slices, books, the eyes of an end portal frame, ...)
 * can't be hammered, and it skips values that would leave nothing to see or touch (a wall with neither post nor
 * sides).</li>
 * </ul>
 * Neither works on two-block things (doors, beds, tall plants, double chests, extended pistons) or unbreakable blocks. A
 * block set with either keeps its state (BlockLocks).
 */
public final class BlockTools {
	public static final Item WRENCH = Registry.register(Registries.ITEM, EternalReturn.id("block_wrench"), new Wrench(new Item.Settings().maxDamage(250)));
	public static final Item HAMMER = Registry.register(Registries.ITEM, EternalReturn.id("hammer"), new Hammer(new Item.Settings().maxDamage(250)));

	/** What the wrench turns, the first a block has. */
	private static final List<Property<?>> TURNING = List.of(Properties.FACING, Properties.HORIZONTAL_FACING, Properties.HOPPER_FACING,
			Properties.VERTICAL_DIRECTION, Properties.AXIS, Properties.HORIZONTAL_AXIS, Properties.ROTATION, Properties.ORIENTATION);
	/** What the hammer won't change: these would make or destroy something. */
	private static final Set<String> MAKES_OR_DESTROYS = Set.of("age", "eggs", "hatch", "bites", "level", "honey_level", "charges", "candles",
			"pickles", "layers", "flower_amount", "segment_amount", "waterlogged", "has_book", "has_record", "has_bottle_0", "has_bottle_1",
			"has_bottle_2", "dusted", "berries", "moisture", "stage", "distance", "eye", "vault_state", "ominous", "trial_spawner_state",
			"slot_0_occupied", "slot_1_occupied", "slot_2_occupied", "slot_3_occupied", "slot_4_occupied", "slot_5_occupied");

	private BlockTools() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.addAfter(Items.BRUSH, WRENCH, HAMMER));
	}

	/** Whether the tools work on this block at all. */
	private static boolean settable(BlockState state, World world, BlockPos pos) {
		return state.getHardness(world, pos) >= 0.0F && !state.contains(Properties.DOUBLE_BLOCK_HALF) && !state.contains(Properties.BED_PART)
				&& !(state.contains(Properties.CHEST_TYPE) && state.get(Properties.CHEST_TYPE) != ChestType.SINGLE)
				&& !(state.contains(Properties.EXTENDED) && state.get(Properties.EXTENDED))
				&& !(state.getBlock() instanceof PistonHeadBlock) && !(state.getBlock() instanceof PistonExtensionBlock);
	}

	public static boolean hammerable(BlockState state, Property<?> property) {
		return !MAKES_OR_DESTROYS.contains(property.getName()) && property != Properties.SLAB_TYPE && property != Properties.CHEST_TYPE
				&& !(state.getBlock() instanceof MultifaceGrowthBlock && property instanceof BooleanProperty);
	}

	private static <T extends Comparable<T>> BlockState cycle(BlockState state, Property<T> property, boolean back) {
		T value = state.get(property);
		return state.with(property, back ? Util.previous(property.getValues(), value) : Util.next(property.getValues(), value));
	}

	/** The block turned to the next way it can stand, or null. */
	@Nullable
	public static BlockState turned(BlockState state, World world, BlockPos pos, boolean back) {
		if (!settable(state, world, pos)) {
			return null;
		}
		for (Property<?> property : TURNING) {
			if (state.contains(property)) {
				BlockState next = state;
				for (int i = 1; i < property.getValues().size(); i++) {
					next = cycle(next, property, back);
					if (next.canPlaceAt(world, pos)) {
						return next;
					}
				}
				return null;
			}
		}
		return null;
	}

	private static boolean mayEdit(PlayerEntity player, World world, BlockPos pos) {
		return player.canModifyBlocks() && world.canPlayerModifyAt(player, pos);
	}

	private static void used(ItemUsageContext context, World world, BlockPos pos) {
		world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.6F, 1.2F + world.random.nextFloat() * 0.2F);
		context.getStack().damage(1, context.getPlayer(), LivingEntity.getSlotForHand(context.getHand()));
	}

	public static class Wrench extends Item {
		public Wrench(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			PlayerEntity player = context.getPlayer();
			World world = context.getWorld();
			BlockPos pos = context.getBlockPos();
			if (player == null || !mayEdit(player, world, pos)) {
				return ActionResult.PASS;
			}
			BlockState turned = turned(world.getBlockState(pos), world, pos, player.isSneaking());
			if (turned == null) {
				return ActionResult.FAIL;
			}
			if (!world.isClient) {
				BlockLocks.lock(world, pos);
				world.setBlockState(pos, turned, Block.NOTIFY_ALL);
				used(context, world, pos);
			}
			return ActionResult.success(world.isClient);
		}
	}

	public static class Hammer extends Item {
		public Hammer(Settings settings) {
			super(settings);
		}

		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			PlayerEntity player = context.getPlayer();
			World world = context.getWorld();
			BlockPos pos = context.getBlockPos();
			if (player == null || !mayEdit(player, world, pos)) {
				return ActionResult.PASS;
			}
			BlockState state = world.getBlockState(pos);
			List<Property<?>> properties = state.getProperties().stream().filter(property -> hammerable(state, property)).toList();
			String name = Registries.BLOCK.getId(state.getBlock()).toString();
			if (!settable(state, world, pos) || properties.isEmpty()) {
				if (!world.isClient) {
					player.sendMessage(Text.translatable("item.minecraft.debug_stick.empty", name), true);
				}
				return ActionResult.FAIL;
			}
			if (world.isClient) {
				return ActionResult.SUCCESS;
			}
			ItemStack stack = context.getStack();
			RegistryEntry<Block> block = state.getRegistryEntry();
			DebugStickStateComponent chosen = stack.getOrDefault(DataComponentTypes.DEBUG_STICK_STATE, DebugStickStateComponent.DEFAULT);
			Property<?> property = chosen.properties().get(block);
			if (property == null || !properties.contains(property)) {
				property = properties.get(0);
			}
			if (player.isSneaking()) {
				property = Util.next(properties, property);
				stack.set(DataComponentTypes.DEBUG_STICK_STATE, chosen.with(block, property));
				player.sendMessage(Text.translatable("item.minecraft.debug_stick.select", property.getName(), value(state, property)), true);
				return ActionResult.SUCCESS;
			}
			// The next value that leaves the block something to see and touch (a wall with neither post nor sides is invisible).
			BlockState next = state;
			for (int i = 1; i < property.getValues().size(); i++) {
				next = cycle(next, property, false);
				if (!next.getOutlineShape(world, pos).isEmpty() || state.getOutlineShape(world, pos).isEmpty()) {
					break;
				}
			}
			if (next == state || (next.getOutlineShape(world, pos).isEmpty() && !state.getOutlineShape(world, pos).isEmpty())) {
				return ActionResult.FAIL;
			}
			BlockLocks.lock(world, pos);
			world.setBlockState(pos, next, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			player.sendMessage(Text.translatable("item.minecraft.debug_stick.update", property.getName(), value(next, property)), true);
			used(context, world, pos);
			return ActionResult.SUCCESS;
		}

		private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
			return property.name(state.get(property));
		}
	}
}
