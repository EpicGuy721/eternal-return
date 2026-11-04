package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

/**
 * Ice blocks (worldgen expansion phase 6, ice caves): the icicle and the ice crystal, which grow only in ice caves, and
 * three building blocks made from packed ice: polished ice, ice bricks, chiseled ice (crafted or stonecut, never
 * generated). Textures are the owner's (tools/ice/textures); models, blockstates, loot tables, recipes and tags are
 * written by tools/ice/build_ice.py. Wandering traders sell icicles (2 for an emerald) and ice crystals (one for 2), as in
 * Java 26.4.
 */
public final class IceBlocks {
	public static final Block ICICLE = block("icicle", new IcicleBlock(AbstractBlock.Settings.create().mapColor(MapColor.LIGHT_BLUE)
			.instrument(NoteBlockInstrument.CHIME).nonOpaque().sounds(BlockSoundGroup.GLASS).ticksRandomly().strength(0.5F)
			.offset(AbstractBlock.OffsetType.XZ).dynamicBounds().pistonBehavior(PistonBehavior.DESTROY).solidBlock((state, world, pos) -> false)));
	public static final Block ICE_CRYSTAL = block("ice_crystal", new IceCrystalBlock(AbstractBlock.Settings.create().mapColor(MapColor.PALE_PURPLE)
			.instrument(NoteBlockInstrument.CHIME).nonOpaque().solid().sounds(BlockSoundGroup.AMETHYST_CLUSTER).strength(1.5F).requiresTool()
			.luminance(state -> 4).pistonBehavior(PistonBehavior.DESTROY)));
	public static final Block POLISHED_ICE = block("polished_ice", new Block(AbstractBlock.Settings.copy(Blocks.PACKED_ICE)));
	public static final Block ICE_BRICKS = block("ice_bricks", new Block(AbstractBlock.Settings.copy(Blocks.PACKED_ICE)));
	public static final Block CHISELED_ICE = block("chiseled_ice", new Block(AbstractBlock.Settings.copy(Blocks.PACKED_ICE)));

	private IceBlocks() {
	}

	private static Block block(String name, Block block) {
		Registry.register(Registries.BLOCK, EternalReturn.id(name), block);
		Registry.register(Registries.ITEM, EternalReturn.id(name), new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries ->
				entries.addAfter(Items.BLUE_ICE, POLISHED_ICE, ICE_BRICKS, CHISELED_ICE));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.addAfter(Items.POINTED_DRIPSTONE, ICICLE, ICE_CRYSTAL));
		TradeOfferHelper.registerWanderingTraderOffers(1, factories -> {
			factories.add((entity, random) -> new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(ICICLE, 2), 5, 1, 0.05F));
			factories.add((entity, random) -> new TradeOffer(new TradedItem(Items.EMERALD, 2), new ItemStack(ICE_CRYSTAL, 1), 5, 1, 0.05F));
		});
	}
}
