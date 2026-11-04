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
import net.minecraft.sound.SoundEvent;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;

/**
 * Ice blocks (worldgen expansion phase 6, ice caves): the icicle and the ice crystal, which grow only in ice caves, and
 * building blocks made from packed ice: polished ice, ice bricks with their slab, stairs and wall, chiseled ice (crafted or stonecut, never
 * generated, but for the ice brick dungeons of cold biomes, worldgen.Dungeons). Textures and sounds are the owner's (tools/ice/textures, tools/ice/sounds); models, blockstates, loot tables, recipes and tags are
 * written by tools/ice/build_ice.py. Wandering traders sell icicles (2 for an emerald) and ice crystals (one for 2), as in
 * Java 26.4.
 */
public final class IceBlocks {
	// Sounds (the owner's clips, assets/eternalreturn/sounds.json from tools/ice/build_ice.py), registered before the blocks.
	public static final SoundEvent ICICLE_BREAK = sound("block.icicle.break");
	public static final SoundEvent ICICLE_STEP = sound("block.icicle.step");
	public static final SoundEvent ICICLE_PLACE = sound("block.icicle.place");
	public static final SoundEvent ICICLE_HIT = sound("block.icicle.hit");
	public static final SoundEvent ICICLE_FALL = sound("block.icicle.fall");
	public static final SoundEvent ICICLE_LAND = sound("block.icicle.land");
	public static final SoundEvent ICE_CRYSTAL_BREAK = sound("block.ice_crystal.break");
	public static final SoundEvent ICE_CRYSTAL_STEP = sound("block.ice_crystal.step");
	public static final SoundEvent ICE_CRYSTAL_PLACE = sound("block.ice_crystal.place");
	public static final SoundEvent ICE_CRYSTAL_HIT = sound("block.ice_crystal.hit");
	public static final SoundEvent ICE_CRYSTAL_FALL = sound("block.ice_crystal.fall");
	public static final SoundEvent ICE_CRYSTAL_AMBIENT = sound("block.ice_crystal.ambient");
	public static final BlockSoundGroup ICICLE_SOUNDS = new BlockSoundGroup(1.0F, 1.0F, ICICLE_BREAK, ICICLE_STEP, ICICLE_PLACE, ICICLE_HIT, ICICLE_FALL);
	public static final BlockSoundGroup ICE_CRYSTAL_SOUNDS = new BlockSoundGroup(1.0F, 1.0F, ICE_CRYSTAL_BREAK, ICE_CRYSTAL_STEP, ICE_CRYSTAL_PLACE,
			ICE_CRYSTAL_HIT, ICE_CRYSTAL_FALL);

	public static final Block ICICLE = block("icicle", new IcicleBlock(AbstractBlock.Settings.create().mapColor(MapColor.LIGHT_BLUE)
			.instrument(NoteBlockInstrument.CHIME).nonOpaque().sounds(ICICLE_SOUNDS).ticksRandomly().strength(0.5F)
			.offset(AbstractBlock.OffsetType.XZ).dynamicBounds().pistonBehavior(PistonBehavior.DESTROY).solidBlock((state, world, pos) -> false)));
	public static final Block ICE_CRYSTAL = block("ice_crystal", new IceCrystalBlock(AbstractBlock.Settings.create().mapColor(MapColor.PALE_PURPLE)
			.instrument(NoteBlockInstrument.CHIME).nonOpaque().solid().sounds(ICE_CRYSTAL_SOUNDS).strength(1.5F).requiresTool()
			.luminance(state -> 4).pistonBehavior(PistonBehavior.DESTROY)));
	public static final Block POLISHED_ICE = block("polished_ice", new Block(AbstractBlock.Settings.copy(Blocks.PACKED_ICE)));
	public static final Block ICE_BRICKS = block("ice_bricks", new Block(AbstractBlock.Settings.copy(Blocks.PACKED_ICE)));
	public static final Block CHISELED_ICE = block("chiseled_ice", new Block(AbstractBlock.Settings.copy(Blocks.PACKED_ICE)));
	public static final Block ICE_BRICK_SLAB = block("ice_brick_slab", new net.minecraft.block.SlabBlock(AbstractBlock.Settings.copy(ICE_BRICKS)));
	public static final Block ICE_BRICK_STAIRS = block("ice_brick_stairs", new net.minecraft.block.StairsBlock(ICE_BRICKS.getDefaultState(), AbstractBlock.Settings.copy(ICE_BRICKS)));
	public static final Block ICE_BRICK_WALL = block("ice_brick_wall", new net.minecraft.block.WallBlock(AbstractBlock.Settings.copy(ICE_BRICKS).solid()));

	private IceBlocks() {
	}

	private static SoundEvent sound(String name) {
		return Registry.register(Registries.SOUND_EVENT, EternalReturn.id(name), SoundEvent.of(EternalReturn.id(name)));
	}

	private static Block block(String name, Block block) {
		Registry.register(Registries.BLOCK, EternalReturn.id(name), block);
		Registry.register(Registries.ITEM, EternalReturn.id(name), new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries ->
				entries.addAfter(Items.BLUE_ICE, POLISHED_ICE, ICE_BRICKS, ICE_BRICK_STAIRS, ICE_BRICK_SLAB, ICE_BRICK_WALL, CHISELED_ICE));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.addAfter(Items.POINTED_DRIPSTONE, ICICLE, ICE_CRYSTAL));
		TradeOfferHelper.registerWanderingTraderOffers(1, factories -> {
			factories.add((entity, random) -> new TradeOffer(new TradedItem(Items.EMERALD, 1), new ItemStack(ICICLE, 2), 5, 1, 0.05F));
			factories.add((entity, random) -> new TradeOffer(new TradedItem(Items.EMERALD, 2), new ItemStack(ICE_CRYSTAL, 1), 5, 1, 0.05F));
		});
	}
}
