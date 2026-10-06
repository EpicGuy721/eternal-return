package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.block.Block;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;

/**
 * Small helpers that look enchantments up by key, so no registry access is needed
 * when the enchantment is already sitting on the stack.
 */
public final class EnchantLevels {
	public static final RegistryKey<Enchantment> INVISIBILITY_CURSE =
			RegistryKey.of(RegistryKeys.ENCHANTMENT, EternalReturn.id("invisibility_curse"));

	public static final TagKey<Block> FORTUNE_CROPS = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("fortune_crops"));
	public static final TagKey<Item> FORTUNE_CROP_DROPS = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("fortune_crop_drops"));

	private EnchantLevels() {
	}

	public static int levelOf(ItemEnchantmentsComponent component, RegistryKey<Enchantment> key) {
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : component.getEnchantmentEntries()) {
			if (entry.getKey().matchesKey(key)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}

	public static int levelOf(ItemStack stack, RegistryKey<Enchantment> key) {
		if (stack.isEmpty()) {
			return 0;
		}
		return levelOf(stack.getEnchantments(), key);
	}

	/** Curse of Invisibility hides the enchantment list (and glint) of the item it is on. */
	public static boolean isConcealed(ItemStack stack) {
		return levelOf(stack, INVISIBILITY_CURSE) > 0;
	}

	public static boolean isDisabled(RegistryEntry<Enchantment> enchantment) {
		for (RegistryKey<Enchantment> key : EternalReturnConfig.rules().disabled()) {
			if (enchantment.matchesKey(key)) {
				return true;
			}
		}
		return false;
	}
}
