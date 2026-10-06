package com.eternalreturn.logic;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.random.Random;

import java.util.Collections;
import java.util.List;

public final class FortuneHelper {
	/** Fortune level of the shears currently shearing a sheep (set around the shear call). */
	public static final ThreadLocal<Integer> SHEAR_FORTUNE = new ThreadLocal<>();

	private FortuneHelper() {
	}

	/** Vanilla's ore_drops formula: multiply by max(1, rand(0..level+1)). */
	public static int oreMultiplier(Random random, int fortune) {
		return Math.max(1, random.nextInt(fortune + 2));
	}

	public static List<ItemStack> applyCropFortune(List<ItemStack> drops, BlockState state, Random random, ItemStack tool) {
		if (!EternalReturnConfig.get().fortune.hoesMultiplyCrops || tool == null || tool.isEmpty() || !tool.isIn(ItemTags.HOES)) {
			return drops;
		}
		if (!state.isIn(EnchantLevels.FORTUNE_CROPS) || !isFullyGrown(state)) {
			return drops;
		}
		int fortune = EnchantLevels.levelOf(tool, Enchantments.FORTUNE);
		if (fortune <= 0) {
			return drops;
		}
		for (ItemStack drop : drops) {
			if (drop.isIn(EnchantLevels.FORTUNE_CROP_DROPS)) {
				int multiplied = drop.getCount() * oreMultiplier(random, fortune);
				drop.setCount(Math.min(drop.getMaxCount(), multiplied));
			}
		}
		return drops;
	}

	/** A crop counts as grown when its "age" property is at its maximum (works for modded crops too). */
	private static boolean isFullyGrown(BlockState state) {
		for (Property<?> property : state.getProperties()) {
			if (property instanceof IntProperty age && property.getName().equals("age")) {
				return state.get(age) >= Collections.max(age.getValues());
			}
		}
		return false;
	}
}
