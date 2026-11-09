package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/**
 * The cactus arm (CactusArmBlock): its block and item, after cactus in the natural blocks tab. The textures are the
 * owner's; the models and blockstate come from tools/cactus/build_cactus_arm.py.
 */
public final class CactusArms {
	public static final Block CACTUS_ARM = Registry.register(Registries.BLOCK, EternalReturn.id("cactus_arm"),
			new CactusArmBlock(AbstractBlock.Settings.create().mapColor(MapColor.DARK_GREEN).strength(0.4F).sounds(BlockSoundGroup.WOOL)
					.nonOpaque().notSolid().pistonBehavior(PistonBehavior.DESTROY)));
	public static final Item CACTUS_ARM_ITEM = Registry.register(Registries.ITEM, EternalReturn.id("cactus_arm"),
			new BlockItem(CACTUS_ARM, new Item.Settings()));

	private CactusArms() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> entries.addAfter(Items.CACTUS, CACTUS_ARM_ITEM));
	}

	public static boolean isArm(BlockState state) {
		return state.isOf(CACTUS_ARM);
	}
}
