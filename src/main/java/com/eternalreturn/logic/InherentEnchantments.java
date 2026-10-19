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

	/**
	 * An item's own inherent enchantments, in place of its material's (a ruby's reroll, com.eternalreturn.items.Gems):
	 * custom data eternalreturn_inherent, enchantment id to level.
	 */
	public static final String OVERRIDE = "eternalreturn_inherent";

	public static boolean hasRules(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return !rules(stack).isEmpty();
	}

	/** The free levels this stack gets: its own (OVERRIDE) if it has them, else its material's, by tag. */
	public static Map<RegistryKey<Enchantment>, Integer> rules(ItemStack stack) {
		Map<RegistryKey<Enchantment>, Integer> out = new java.util.LinkedHashMap<>();
		net.minecraft.component.type.NbtComponent data = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
		if (data != null && data.contains(OVERRIDE)) {
			net.minecraft.nbt.NbtCompound own = data.copyNbt().getCompound(OVERRIDE);
			for (String id : own.getKeys()) {
				net.minecraft.util.Identifier key = net.minecraft.util.Identifier.tryParse(id);
				if (key != null) {
					out.put(RegistryKey.of(RegistryKeys.ENCHANTMENT, key), own.getInt(id));
				}
			}
			return out;
		}
		for (EternalReturnConfig.InherentRule rule : EternalReturnConfig.rules().inherent()) {
			if (stack.isIn(rule.tag())) {
				rule.levels().forEach((key, level) -> out.merge(key, level, Math::max));
			}
		}
		return out;
	}

	public static void setOverride(ItemStack stack, Map<RegistryKey<Enchantment>, Integer> levels) {
		net.minecraft.component.type.NbtComponent.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
			net.minecraft.nbt.NbtCompound own = new net.minecraft.nbt.NbtCompound();
			levels.forEach((key, level) -> own.putInt(key.getValue().toString(), level));
			nbt.put(OVERRIDE, own);
		});
	}

	/** The free level this stack gets for the given enchantment, or 0. */
	public static int inherentLevel(ItemStack stack, RegistryEntry<Enchantment> enchantment) {
		int best = 0;
		for (Map.Entry<RegistryKey<Enchantment>, Integer> e : rules(stack).entrySet()) {
			if (enchantment.matchesKey(e.getKey())) {
				best = Math.max(best, e.getValue());
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

		for (Map.Entry<RegistryKey<Enchantment>, Integer> e : rules(stack).entrySet()) {
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
	 * the new material's free levels. A ruby's reroll (OVERRIDE) doesn't carry over: the new item gets its material's own.
	 */
	public static void transfer(ItemStack from, ItemStack to, RegistryWrapper.WrapperLookup lookup) {
		net.minecraft.component.type.NbtComponent data = to.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
		if (data != null && data.contains(OVERRIDE)) {
			net.minecraft.component.type.NbtComponent.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA, to, nbt -> nbt.remove(OVERRIDE));
			net.minecraft.component.type.NbtComponent left = to.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
			if (left != null && left.isEmpty()) {
				to.remove(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
			}
		}
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
