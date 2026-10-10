package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.dispenser.DispenserBehavior;
import net.minecraft.block.dispenser.ItemDispenserBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.AutomaticItemPlacementContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Redstone additions:
 * <ul>
 * <li>dispensers place blocks: a block item with no dispenser behaviour of its own (TNT, shulker boxes and
 * the like keep theirs) is placed in front of the dispenser, facing away from it; seeds and potatoes are
 * planted on farmland. If it can't be placed, it is dropped as before;</li>
 * <li>the obsidian pressure plate only reacts to players;</li>
 * <li>pistons move blocks that hold data (chests, furnaces, signs...), contents and all, except those in
 * the block tag eternalreturn:immovable_block_entities (spawners and the like). A block that can't stand
 * where it ends up breaks and drops its contents;</li>
 * <li>chains link blocks when pistons move them (ChainLinks).</li>
 * </ul>
 */
public final class Redstone {
	public static final TagKey<Block> IMMOVABLE_BLOCK_ENTITIES = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("immovable_block_entities"));

	public static final Block OBSIDIAN_PRESSURE_PLATE = register("obsidian_pressure_plate", new PlayerPressurePlate(BlockSetType.STONE,
			AbstractBlock.Settings.create().mapColor(Blocks2.OBSIDIAN_COLOR).requiresTool().noCollision().strength(50.0F, 1200.0F)
					.pistonBehavior(PistonBehavior.DESTROY).sounds(BlockSoundGroup.STONE)));

	private Redstone() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.addAfter(Items.STONE_PRESSURE_PLATE, OBSIDIAN_PRESSURE_PLATE));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.addAfter(Items.POLISHED_BLACKSTONE_PRESSURE_PLATE, OBSIDIAN_PRESSURE_PLATE));
	}

	private static Block register(String id, Block block) {
		Registry.register(Registries.BLOCK, EternalReturn.id(id), block);
		Registry.register(Registries.ITEM, EternalReturn.id(id), new BlockItem(block, new net.minecraft.item.Item.Settings()));
		return block;
	}

	/** A pressure plate only players (not spectators) press. */
	public static final class PlayerPressurePlate extends PressurePlateBlock {
		public PlayerPressurePlate(BlockSetType type, Settings settings) {
			super(type, settings);
		}

		@Override
		protected int getRedstoneOutput(World world, BlockPos pos) {
			Box box = BOX.offset(pos);
			return world.getEntitiesByClass(PlayerEntity.class, box, player -> !player.isSpectator() && !player.canAvoidTraps()).isEmpty() ? 0 : 15;
		}
	}

	// ---------------------------------------------------------------- dispensers

	/** The dispenser's behaviour for this item, with block placing for plain block items. */
	public static DispenserBehavior withBlockPlacing(DispenserBehavior behavior, ItemStack stack) {
		if (EternalReturnConfig.get().redstone.dispensersPlaceBlocks && behavior.getClass() == ItemDispenserBehavior.class && stack.getItem() instanceof BlockItem) {
			return PLACE_BLOCK;
		}
		return behavior;
	}

	private static final ItemDispenserBehavior PLACE_BLOCK = new ItemDispenserBehavior() {
		private final ItemDispenserBehavior drop = new ItemDispenserBehavior();

		@Override
		protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
			Direction facing = pointer.state().get(DispenserBlock.FACING);
			BlockPos target = pointer.pos().offset(facing);
			BlockItem item = (BlockItem) stack.getItem();
			if (item.place(new AutomaticItemPlacementContext(pointer.world(), target, facing, stack, facing)).isAccepted()) {
				return stack;
			}
			return this.drop.dispense(pointer, stack);
		}
	};

	// ---------------------------------------------------------------- pistons and block entities

	/** Whether a block's data stops a piston (vanilla: any data; here only the immovable tag). */
	public static boolean blocksPiston(BlockState state) {
		return !EternalReturnConfig.get().redstone.pistonsMoveBlockEntities || state.isIn(IMMOVABLE_BLOCK_ENTITIES);
	}

	/** Just before a moved block is lifted: its data, with its contents taken out so nothing spills. */
	@Nullable
	public static NbtCompound takeData(World world, BlockPos pos) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity == null) {
			return null;
		}
		NbtCompound data = blockEntity.createNbt(world.getRegistryManager());
		net.minecraft.util.Clearable.clear(blockEntity);
		return data;
	}

	/** When the moved block lands: its data back, or, if it couldn't be placed, it breaks and drops its contents. */
	public static void putData(World world, BlockPos pos, BlockState pushed, NbtCompound data) {
		BlockState now = world.getBlockState(pos);
		BlockEntity blockEntity = world.getBlockEntity(pos);
		if (blockEntity != null && now.isOf(pushed.getBlock())) {
			blockEntity.read(data, world.getRegistryManager());
			blockEntity.markDirty();
			world.updateListeners(pos, now, now, Block.NOTIFY_ALL);
			return;
		}
		if (pushed.getBlock() instanceof BlockEntityProvider provider) {
			BlockEntity temporary = provider.createBlockEntity(pos, pushed);
			if (temporary != null) {
				temporary.read(data, world.getRegistryManager());
				if (temporary instanceof Inventory inventory) {
					ItemScatterer.spawn(world, pos, inventory);
				}
			}
			Block.dropStacks(pushed, world, pos, temporary);
		}
	}

	/** Whether a block's data must be carried by a piston. */
	public static boolean carriesData(BlockState state) {
		return state.hasBlockEntity() && !blocksPiston(state);
	}

	static boolean isChain(BlockState state) {
		return state.getBlock() instanceof ChainBlock;
	}

	static boolean along(BlockState chain, Direction direction) {
		return chain.get(PillarBlock.AXIS) == direction.getAxis();
	}
}
