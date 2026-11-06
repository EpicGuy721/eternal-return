package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.MushroomBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;

import java.util.Set;

/**
 * The mushroom caves' mushrooms (owner's request, 2026-11-05; textures the owner's, resources from
 * tools/mushrooms/build_mushrooms.py): purple (toxic: its gas poisons), blue (its gas gives night vision) and green (glows,
 * light 8), each a small mushroom (GasMushroomBlock; purple and blue puffed or unpuffed) and a huge one: the purple's as
 * the huge brown mushroom, the green's as the huge red one, the blue's a tall pointed cone (HugeCaveMushroomFeature), all
 * on vanilla's mushroom stem. The purple and blue cap blocks come puffed or unpuffed too (GasMushroomCapBlock; huge mushrooms
 * grow puffed), and each puffed or unpuffed block is named so. The green caps glow at light 8 too.
 */
public final class CaveMushrooms {
	public static final Feature<HugeCaveMushroomFeature.Config> HUGE_FEATURE = Registry.register(Registries.FEATURE,
			EternalReturn.id("huge_cave_mushroom"), new HugeCaveMushroomFeature());

	public static final Block PURPLE_MUSHROOM_BLOCK = block("purple_mushroom_block", cap(MapColor.PURPLE, MushroomGas.Kind.POISON, false));
	public static final Block PUFFED_PURPLE_MUSHROOM_BLOCK = block("puffed_purple_mushroom_block", cap(MapColor.PURPLE, MushroomGas.Kind.POISON, true));
	public static final Block BLUE_MUSHROOM_BLOCK = block("blue_mushroom_block", cap(MapColor.BLUE, MushroomGas.Kind.NIGHT_VISION, false));
	public static final Block PUFFED_BLUE_MUSHROOM_BLOCK = block("puffed_blue_mushroom_block", cap(MapColor.BLUE, MushroomGas.Kind.NIGHT_VISION, true));
	public static final Block GREEN_MUSHROOM_BLOCK = block("green_mushroom_block",
			new MushroomBlock(AbstractBlock.Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK).mapColor(MapColor.LIME).luminance(state -> 8)));

	public static final Block PURPLE_MUSHROOM = block("purple_mushroom", mushroom("purple", MapColor.PURPLE, 0, MushroomGas.Kind.POISON, false));
	public static final Block PUFFED_PURPLE_MUSHROOM = block("puffed_purple_mushroom", mushroom("purple", MapColor.PURPLE, 0, MushroomGas.Kind.POISON, true));
	public static final Block BLUE_MUSHROOM = block("blue_mushroom", mushroom("blue", MapColor.BLUE, 0, MushroomGas.Kind.NIGHT_VISION, false));
	public static final Block PUFFED_BLUE_MUSHROOM = block("puffed_blue_mushroom", mushroom("blue", MapColor.BLUE, 0, MushroomGas.Kind.NIGHT_VISION, true));
	public static final Block GREEN_MUSHROOM = block("green_mushroom", mushroom("green", MapColor.LIME, 8, null, false));

	private CaveMushrooms() {
	}

	private static Block cap(MapColor color, MushroomGas.Kind gas, boolean puffed) {
		return new GasMushroomCapBlock(AbstractBlock.Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK).mapColor(color), gas, puffed);
	}

	private static RegistryKey<ConfiguredFeature<?, ?>> huge(String colour) {
		return RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, EternalReturn.id("huge_" + colour + "_mushroom"));
	}

	private static GasMushroomBlock mushroom(String colour, MapColor color, int light, MushroomGas.Kind gas, boolean puffed) {
		AbstractBlock.Settings settings = AbstractBlock.Settings.copy(Blocks.RED_MUSHROOM).mapColor(color).luminance(state -> light);
		java.util.function.Supplier<Block> natural = switch (colour) {
			case "purple" -> () -> PUFFED_PURPLE_MUSHROOM;
			case "blue" -> () -> PUFFED_BLUE_MUSHROOM;
			default -> () -> GREEN_MUSHROOM;
		};
		java.util.function.Supplier<Set<Block>> family = switch (colour) {
			case "purple" -> () -> Set.of(PURPLE_MUSHROOM, PUFFED_PURPLE_MUSHROOM);
			case "blue" -> () -> Set.of(BLUE_MUSHROOM, PUFFED_BLUE_MUSHROOM);
			default -> () -> Set.of(GREEN_MUSHROOM);
		};
		return new GasMushroomBlock(huge(colour), settings, gas, puffed, natural, family);
	}

	private static Block block(String name, Block block) {
		Registry.register(Registries.BLOCK, EternalReturn.id(name), block);
		Registry.register(Registries.ITEM, EternalReturn.id(name), new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static void register() {
		MushroomGas.register();
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			entries.addAfter(Items.RED_MUSHROOM, PURPLE_MUSHROOM, PUFFED_PURPLE_MUSHROOM, BLUE_MUSHROOM, PUFFED_BLUE_MUSHROOM, GREEN_MUSHROOM);
			entries.addAfter(Items.MUSHROOM_STEM, PURPLE_MUSHROOM_BLOCK, PUFFED_PURPLE_MUSHROOM_BLOCK, BLUE_MUSHROOM_BLOCK, PUFFED_BLUE_MUSHROOM_BLOCK,
					GREEN_MUSHROOM_BLOCK);
		});
	}
}
