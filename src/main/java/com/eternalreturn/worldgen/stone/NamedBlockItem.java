package com.eternalreturn.worldgen.stone;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** A block item named exactly like its block, for blocks whose name is built from other blocks' names. */
public class NamedBlockItem extends BlockItem {
	public NamedBlockItem(Block block) {
		super(block, new Item.Settings());
	}

	@Override
	public Text getName(ItemStack stack) {
		return this.getBlock().getName();
	}
}
