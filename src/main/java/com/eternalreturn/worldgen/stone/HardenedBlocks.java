package com.eternalreturn.worldgen.stone;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hardened host stones: blocks that look like a vanilla block but behave like stone underground
 * (stone's hardness and blast resistance, a pickaxe needed, stone sounds, fully opaque, not slippery,
 * never melting). Mining one with any pickaxe, Silk Touch included, drops the plain vanilla block, so the
 * hardened block itself only exists in generated worlds and the creative inventory.
 * <p>
 * Each one is a single entry in DEFINITIONS: its id, the block it looks like (its model and texture are
 * that block's own, served by the client's model loader) and what it drops. Its name is "Hardened" plus
 * the base block's name (one language line for all). A new one also goes in the block tag
 * eternalreturn:hardened_stones, which brings it into pickaxe mining, stone blobs, carving and ores.
 */
public final class HardenedBlocks {
	public record Definition(String name, Block base, Item drop, Identifier model) {
	}

	public static final List<Definition> DEFINITIONS = List.of(
			new Definition("hardened_sandstone", Blocks.SANDSTONE, Items.SANDSTONE, Identifier.ofVanilla("block/sandstone")),
			new Definition("hardened_packed_ice", Blocks.PACKED_ICE, Items.PACKED_ICE, Identifier.ofVanilla("block/packed_ice")));

	private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

	private HardenedBlocks() {
	}

	public static void register() {
		for (Definition definition : DEFINITIONS) {
			Block block = Registry.register(Registries.BLOCK, EternalReturn.id(definition.name()), new HardenedBlock(definition));
			Item item = Registry.register(Registries.ITEM, EternalReturn.id(definition.name()), new HardenedBlockItem(block));
			BLOCKS.put(definition.name(), block);
			ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.addAfter(definition.base(), item));
		}
	}

	/** The registered block for a definition name, such as "hardened_sandstone". */
	public static Block get(String name) {
		return BLOCKS.get(name);
	}

	public static Map<String, Block> all() {
		return BLOCKS;
	}

	public static class HardenedBlock extends Block {
		private final Definition definition;

		public HardenedBlock(Definition definition) {
			super(AbstractBlock.Settings.copy(Blocks.STONE).mapColor(definition.base().getDefaultMapColor()));
			this.definition = definition;
		}

		public Definition definition() {
			return this.definition;
		}

		@Override
		public MutableText getName() {
			return Text.translatable("block.eternalreturn.hardened", this.definition.base().getName());
		}

		/** Always the plain vanilla block, whatever the tool (the game only calls this when the block was mined with a suitable one). */
		@Override
		protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
			return List.of(new ItemStack(this.definition.drop()));
		}
	}

	public static class HardenedBlockItem extends BlockItem {
		public HardenedBlockItem(Block block) {
			super(block, new Item.Settings());
		}

		@Override
		public Text getName(ItemStack stack) {
			return this.getBlock().getName();
		}
	}
}
