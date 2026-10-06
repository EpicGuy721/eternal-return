package com.eternalreturn.logic;

import com.eternalreturn.config.EternalReturnConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;

/**
 * Enchant capacity: the sum of enchantment levels an item can hold.
 * Inherent levels are free, curses refund points.
 */
public final class EnchantCaps {
	public static final int UNCAPPED = Integer.MAX_VALUE;

	private EnchantCaps() {
	}

	public static int capFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return UNCAPPED;
		}
		for (EternalReturnConfig.CapRule rule : EternalReturnConfig.rules().caps()) {
			if (stack.isIn(rule.tag())) {
				return rule.cap();
			}
		}
		return UNCAPPED;
	}

	public static int pointsUsed(ItemStack stack) {
		return pointsUsed(stack, stack.getEnchantments());
	}

	public static int pointsUsed(ItemStack stack, ItemEnchantmentsComponent enchantments) {
		int total = 0;
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
			total += cost(stack, entry.getKey(), entry.getIntValue());
		}
		return total;
	}

	/** Points this enchantment at this level takes up on this stack (negative for curses). */
	public static int cost(ItemStack stack, RegistryEntry<Enchantment> enchantment, int level) {
		if (enchantment.isIn(EnchantmentTags.CURSE)) {
			return -level * EternalReturnConfig.get().pointsRefundedPerCurseLevel;
		}
		return Math.max(0, level - InherentEnchantments.inherentLevel(stack, enchantment));
	}

	/** Remaining points, or UNCAPPED. */
	public static int remaining(ItemStack stack) {
		int cap = capFor(stack);
		return cap == UNCAPPED ? UNCAPPED : cap - pointsUsed(stack);
	}
}
