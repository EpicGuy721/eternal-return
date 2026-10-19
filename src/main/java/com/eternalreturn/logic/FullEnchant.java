package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Fully enchanted gear (the beholder's drop): the item's inherent enchantments, then random enchantments that fit it
 * and go together, each at its maximum level while the capacity left allows, until the capacity is used up (or nothing
 * more fits). Never a curse or one of the config's disabledEnchantments. Loot function eternalreturn:enchant_fully.
 */
public final class FullEnchant {
	public static final LootFunctionType<Function> TYPE = Registry.register(Registries.LOOT_FUNCTION_TYPE, EternalReturn.id("enchant_fully"),
			new LootFunctionType<>(Function.CODEC));

	private FullEnchant() {
	}

	public static void register() {
		// Loads the class, which registers TYPE.
	}

	private record Step(RegistryEntry<Enchantment> enchantment, int level) {
	}

	public static ItemStack enchant(ItemStack stack, RegistryWrapper.WrapperLookup lookup, Random random) {
		InherentEnchantments.apply(stack, lookup);
		List<RegistryEntry.Reference<Enchantment>> pool = lookup.getWrapperOrThrow(RegistryKeys.ENCHANTMENT).streamEntries()
				.filter(entry -> entry.value().isSupportedItem(stack) && !entry.isIn(EnchantmentTags.CURSE) && !EnchantLevels.isDisabled(entry))
				.toList();
		for (int guard = 0; guard < 64; guard++) {
			int remaining = EnchantCaps.remaining(stack);
			if (remaining <= 0) {
				break;
			}
			List<Step> full = new ArrayList<>();
			List<Step> partial = new ArrayList<>();
			for (RegistryEntry<Enchantment> entry : pool) {
				int current = EnchantmentHelper.getLevel(entry, stack);
				int max = entry.value().getMaxLevel();
				if (current >= max) {
					continue;
				}
				if (current == 0 && stack.getEnchantments().getEnchantmentEntries().stream().anyMatch(e -> !Enchantment.canBeCombined(e.getKey(), entry))) {
					continue;
				}
				int base = EnchantCaps.cost(stack, entry, current);
				if (EnchantCaps.cost(stack, entry, max) - base <= remaining) {
					full.add(new Step(entry, max));
					continue;
				}
				for (int level = max - 1; level > current; level--) {
					if (EnchantCaps.cost(stack, entry, level) - base <= remaining) {
						partial.add(new Step(entry, level));
						break;
					}
				}
			}
			List<Step> choices = full.isEmpty() ? partial : full;
			if (choices.isEmpty()) {
				break;
			}
			Step step = choices.get(random.nextInt(choices.size()));
			EnchantmentHelper.apply(stack, builder -> builder.set(step.enchantment(), step.level()));
		}
		return stack;
	}

	public static final class Function extends ConditionalLootFunction {
		static final MapCodec<Function> CODEC = RecordCodecBuilder.mapCodec(instance -> addConditionsField(instance).apply(instance, Function::new));

		Function(List<LootCondition> conditions) {
			super(conditions);
		}

		@Override
		public LootFunctionType<Function> getType() {
			return TYPE;
		}

		@Override
		protected ItemStack process(ItemStack stack, LootContext context) {
			return enchant(stack, context.getWorld().getRegistryManager(), context.getRandom());
		}
	}
}
