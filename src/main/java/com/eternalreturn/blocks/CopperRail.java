package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Copper rails: an ordinary rail (it curves, carts run on it as on any rail) made from copper (6 ingots and a stick: 16). */
public final class CopperRail extends RailBlock {
	public static final Block BLOCK = Registry.register(Registries.BLOCK, EternalReturn.id("copper_rail"),
			new CopperRail(AbstractBlock.Settings.copy(Blocks.RAIL)));
	public static final Item ITEM = Registry.register(Registries.ITEM, EternalReturn.id("copper_rail"), new BlockItem(BLOCK, new Item.Settings()));

	private CopperRail(Settings settings) {
		super(settings);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.addAfter(Items.RAIL, ITEM));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.addAfter(Items.RAIL, ITEM));
	}
}
