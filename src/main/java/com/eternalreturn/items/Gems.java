package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.logic.EnchantLevels;
import com.eternalreturn.logic.InherentEnchantments;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rubies and sapphires. Ruby ore is about as common as lapis (in the Nether too, in netherrack), sapphire ore as
 * many single blocks as diamond has veins, both only in Eternal Return worlds (OreBonuses). In an anvil:
 * <ul>
 * <li>a ruby rerolls an item's inherent enchantment: it becomes another one from the material table that fits the
 * item and doesn't clash with its other enchantments, at the same level (capped at that enchantment's maximum). The
 * result shows only "Inherent enchantment rerolled"; which one it is shows once taken. The new one is kept on the
 * item (InherentEnchantments.OVERRIDE), so the material's own doesn't come back;</li>
 * <li>a sapphire raises an item's enchant capacity by one, once per item (EnchantCaps).</li>
 * </ul>
 * Illagers drop rubies instead of emeralds (their loot tables).
 */
public final class Gems {
	public static final Item RUBY = item("ruby", new Item(new Item.Settings()));
	public static final Item SAPPHIRE = item("sapphire", new Item(new Item.Settings()));
	public static final Block RUBY_BLOCK = blockWithItem("ruby_block", new Block(AbstractBlock.Settings.copy(Blocks.EMERALD_BLOCK)));
	public static final Block SAPPHIRE_BLOCK = blockWithItem("sapphire_block", new Block(AbstractBlock.Settings.copy(Blocks.DIAMOND_BLOCK)));
	public static final Block RUBY_ORE = blockWithItem("ruby_ore",
			new ExperienceDroppingBlock(UniformIntProvider.create(3, 7), AbstractBlock.Settings.copy(Blocks.DIAMOND_ORE)));
	public static final Block SAPPHIRE_ORE = blockWithItem("sapphire_ore",
			new ExperienceDroppingBlock(UniformIntProvider.create(3, 7), AbstractBlock.Settings.copy(Blocks.DIAMOND_ORE)));
	public static final Block NETHER_RUBY_ORE = blockWithItem("nether_ruby_ore",
			new ExperienceDroppingBlock(UniformIntProvider.create(3, 7), AbstractBlock.Settings.copy(Blocks.NETHER_QUARTZ_ORE)));

	private static final String REROLL = "eternalreturn_reroll";
	public static final String SAPPHIRE_KEY = "eternalreturn_sapphire";

	private Gems() {
	}

	private static Item item(String id, Item item) {
		return Registry.register(Registries.ITEM, EternalReturn.id(id), item);
	}

	private static Block blockWithItem(String id, Block block) {
		Registry.register(Registries.BLOCK, EternalReturn.id(id), block);
		item(id, new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.addAfter(Items.EMERALD, RUBY, SAPPHIRE));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.addAfter(Items.EMERALD_BLOCK, RUBY_BLOCK, SAPPHIRE_BLOCK));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			entries.addAfter(Items.DEEPSLATE_EMERALD_ORE, RUBY_ORE, SAPPHIRE_ORE);
			entries.addAfter(Items.NETHER_QUARTZ_ORE, NETHER_RUBY_ORE);
		});
	}

	// ---------------------------------------------------------------- sapphires

	public static boolean sapphired(ItemStack stack) {
		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
		return data != null && data.copyNbt().getBoolean(SAPPHIRE_KEY);
	}

	/** The anvil's result for an item and a sapphire: one more point of capacity, or empty if it has had one or has no cap. */
	public static ItemStack withSapphire(ItemStack item) {
		if (item.isEmpty() || sapphired(item) || com.eternalreturn.logic.EnchantCaps.capFor(item) == com.eternalreturn.logic.EnchantCaps.UNCAPPED) {
			return ItemStack.EMPTY;
		}
		ItemStack out = item.copy();
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, out, nbt -> nbt.putBoolean(SAPPHIRE_KEY, true));
		return out;
	}

	// ---------------------------------------------------------------- rubies

	/** Items whose ruby rerolls always come out at the new enchantment's maximum level (netherite). */
	public static final TagKey<Item> REROLLS_AT_MAX_LEVEL = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("ruby_rerolls_at_max_level"));

	/**
	 * The anvil's result for an item and a ruby: the item, marked to have its inherent enchantment rerolled once taken
	 * (or, if it has none, as a bow or a fishing rod, to be given one).
	 */
	public static ItemStack withRuby(ItemStack item, RegistryWrapper.WrapperLookup lookup) {
		if (item.isEmpty() || rerollChoices(item, lookup).isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = item.copy();
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, out, nbt -> nbt.putBoolean(REROLL, true));
		String line = InherentEnchantments.hasRules(item) ? "tooltip.eternalreturn.ruby_reroll" : "tooltip.eternalreturn.ruby_grant";
		out.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.translatable(line).formatted(Formatting.RED))));
		return out;
	}

	public static boolean pendingReroll(ItemStack stack) {
		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
		return data != null && data.copyNbt().getBoolean(REROLL);
	}

	/** Taken from the anvil: the reroll happens now, then the mark and its line go. */
	public static void finishReroll(ItemStack stack, RegistryWrapper.WrapperLookup lookup, Random random) {
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.remove(REROLL));
		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (data != null && data.isEmpty()) {
			stack.remove(DataComponentTypes.CUSTOM_DATA);
		}
		stack.remove(DataComponentTypes.LORE);
		reroll(stack, lookup, random);
	}

	/**
	 * One possible reroll: the inherent enchantment that goes (null when the item had none and gains one) and the one
	 * that comes, with its level.
	 */
	public record Choice(RegistryEntry<Enchantment> old, RegistryEntry<Enchantment> replacement, int level) {
	}

	/**
	 * Every way the item's inherent enchantment could be rerolled: each inherent enchantment it has, into any
	 * enchantment that can go on the item (not a curse, nor one of the config's disabledEnchantments), isn't already
	 * on it, and goes with its other enchantments, at the old level (capped at the new one's maximum; always the
	 * maximum on REROLLS_AT_MAX_LEVEL items). An item with no inherent enchantment (no rule gives it one) can gain any
	 * of those at level 1.
	 */
	public static List<Choice> rerollChoices(ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
		List<Choice> choices = new ArrayList<>();
		RegistryWrapper.Impl<Enchantment> registry = lookup.getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
		List<RegistryEntry.Reference<Enchantment>> pool = registry.streamEntries()
				.filter(entry -> entry.value().isSupportedItem(stack) && !entry.isIn(EnchantmentTags.CURSE) && !EnchantLevels.isDisabled(entry))
				.toList();
		boolean atMax = stack.isIn(REROLLS_AT_MAX_LEVEL);
		if (!InherentEnchantments.hasRules(stack)) {
			for (RegistryEntry<Enchantment> candidate : pool) {
				boolean clashes = stack.getEnchantments().getEnchantmentEntries().stream()
						.anyMatch(entry -> !Enchantment.canBeCombined(entry.getKey(), candidate));
				if (!clashes) {
					choices.add(new Choice(null, candidate, atMax ? candidate.value().getMaxLevel() : 1));
				}
			}
			return choices;
		}
		Map<RegistryEntry<Enchantment>, Integer> inherent = InherentEnchantments.inherentPortion(stack);
		for (Map.Entry<RegistryEntry<Enchantment>, Integer> old : inherent.entrySet()) {
			for (RegistryEntry<Enchantment> candidate : pool) {
				if (inherent.keySet().stream().anyMatch(entry -> entry.matchesKey(candidate.getKey().orElseThrow()))) {
					continue;
				}
				boolean clashes = stack.getEnchantments().getEnchantmentEntries().stream()
						.anyMatch(entry -> !entry.getKey().equals(old.getKey()) && !Enchantment.canBeCombined(entry.getKey(), candidate));
				if (!clashes) {
					int max = candidate.value().getMaxLevel();
					choices.add(new Choice(old.getKey(), candidate, atMax ? max : Math.min(old.getValue(), max)));
				}
			}
		}
		return choices;
	}

	/** Rerolls one inherent enchantment of the item at random (rerollChoices); false if there's nothing to reroll into. */
	public static boolean reroll(ItemStack stack, RegistryWrapper.WrapperLookup lookup, Random random) {
		List<Choice> choices = rerollChoices(stack, lookup);
		if (choices.isEmpty()) {
			return false;
		}
		Choice choice = choices.get(random.nextInt(choices.size()));
		Map<RegistryEntry<Enchantment>, Integer> inherent = InherentEnchantments.inherentPortion(stack);
		// The old one loses its free levels (any bought on top stay); the new one becomes this item's own inherent.
		if (choice.old() != null) {
			int had = net.minecraft.enchantment.EnchantmentHelper.getLevel(choice.old(), stack);
			int keep = had - inherent.get(choice.old());
			net.minecraft.enchantment.EnchantmentHelper.apply(stack, builder -> builder.set(choice.old(), Math.max(0, keep)));
		}
		Map<RegistryKey<Enchantment>, Integer> own = new LinkedHashMap<>();
		inherent.forEach((entry, level) -> {
			if (!entry.equals(choice.old())) {
				entry.getKey().ifPresent(key -> own.put(key, level));
			}
		});
		choice.replacement().getKey().ifPresent(key -> own.put(key, choice.level()));
		InherentEnchantments.setOverride(stack, own);
		InherentEnchantments.apply(stack, lookup);
		return true;
	}
}
