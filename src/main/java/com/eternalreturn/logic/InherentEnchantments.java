package com.eternalreturn.logic;

import com.eternalreturn.config.EternalReturnConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Inherent enchantments are real enchantments stored on the stack, so every vanilla
 * system (damage, loot, tooltips, other mods) sees them. They are simply treated as
 * a free baseline: only levels above the inherent level cost enchant capacity.
 */
public final class InherentEnchantments {
	private InherentEnchantments() {
	}

	public static boolean hasRules(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		for (EternalReturnConfig.InherentRule rule : EternalReturnConfig.rules().inherent()) {
			if (stack.isIn(rule.tag())) {
				return true;
			}
		}
		return false;
	}

	/** The free level this stack gets for the given enchantment, or 0. */
	public static int inherentLevel(ItemStack stack, RegistryEntry<Enchantment> enchantment) {
		int best = 0;
		for (EternalReturnConfig.InherentRule rule : EternalReturnConfig.rules().inherent()) {
			if (!stack.isIn(rule.tag())) {
				continue;
			}
			for (Map.Entry<RegistryKey<Enchantment>, Integer> e : rule.levels().entrySet()) {
				if (enchantment.matchesKey(e.getKey())) {
					best = Math.max(best, e.getValue());
				}
			}
		}
		return best;
	}

	/**
	 * Adds any missing inherent enchantments. Server side only (needs the registry).
	 * Uses max-merge, so it never lowers an existing level.
	 */
	public static boolean apply(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
		if (stack.isEmpty()) {
			return false;
		}
		boolean changed = false;
		RegistryWrapper.Impl<Enchantment> enchantments = null;

		for (EternalReturnConfig.InherentRule rule : EternalReturnConfig.rules().inherent()) {
			if (!stack.isIn(rule.tag())) {
				continue;
			}
			for (Map.Entry<RegistryKey<Enchantment>, Integer> e : rule.levels().entrySet()) {
				if (EnchantLevels.levelOf(stack, e.getKey()) >= e.getValue()) {
					continue;
				}
				if (enchantments == null) {
					enchantments = lookup.getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
				}
				Optional<RegistryEntry.Reference<Enchantment>> entry = enchantments.getOptional(e.getKey());
				if (entry.isPresent()) {
					stack.addEnchantment(entry.get(), e.getValue());
					changed = true;
				}
			}
		}
		return changed;
	}

	/** True if the stack has enchantments and every one of them is fully covered by its inherent level. */
	public static boolean isOnlyInherent(ItemStack stack) {
		ItemEnchantmentsComponent enchantments = stack.getEnchantments();
		if (enchantments.isEmpty() || !hasRules(stack)) {
			return false;
		}
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : enchantments.getEnchantmentEntries()) {
			if (entry.getIntValue() > inherentLevel(stack, entry.getKey())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True if nothing on the stack was "bought": every enchantment is either inherent or a curse.
	 * Such items may still go into an enchanting table.
	 */
	public static boolean hasNoPurchasedEnchantments(ItemStack stack) {
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
			if (entry.getKey().isIn(EnchantmentTags.CURSE)) {
				continue;
			}
			if (entry.getIntValue() > inherentLevel(stack, entry.getKey())) {
				return false;
			}
		}
		return true;
	}

	/** The inherent part of each enchantment present on the stack (used by the grindstone and tooltip). */
	public static Map<RegistryEntry<Enchantment>, Integer> inherentPortion(ItemStack stack) {
		Map<RegistryEntry<Enchantment>, Integer> out = new LinkedHashMap<>();
		if (!hasRules(stack)) {
			return out;
		}
		for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
			int inherent = inherentLevel(stack, entry.getKey());
			if (inherent > 0) {
				out.put(entry.getKey(), Math.min(inherent, entry.getIntValue()));
			}
		}
		return out;
	}

	/**
	 * Smithing upgrade (diamond to netherite): strip the old material's free levels so they do not
	 * turn into "bought" levels on the new item, keep what the player actually paid for, then add
	 * the new material's free levels.
	 */
	public static void transfer(ItemStack from, ItemStack to, RegistryWrapper.WrapperLookup lookup) {
		if (hasRules(from)) {
			EnchantmentHelper.apply(to, builder -> {
				List<RegistryEntry<Enchantment>> present = new ArrayList<>(builder.getEnchantments());
				for (RegistryEntry<Enchantment> enchantment : present) {
					int oldFree = inherentLevel(from, enchantment);
					if (oldFree <= 0) {
						continue;
					}
					int kept = builder.getLevel(enchantment) - oldFree;
					if (kept > 0) {
						builder.set(enchantment, kept);
					} else {
						builder.remove(e -> e.equals(enchantment));
					}
				}
			});
		}
		apply(to, lookup);
	}
}
