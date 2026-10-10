package com.eternalreturn.scrolls;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.entry.LootTableEntry;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;

import java.util.List;
import java.util.Map;

/**
 * Scrolls as rare chest loot: stronghold and end city chests sometimes hold a scroll of a random
 * enchantment (loot table eternalreturn:chests/scroll, added as a pool of its own with the chances
 * below). The loot function eternalreturn:random_scroll picks the enchantment from a tag
 * (#minecraft:on_random_loot by default), never a disabled one.
 */
public final class ScrollLoot {
	public static final RegistryKey<LootTable> SCROLL_TABLE = RegistryKey.of(RegistryKeys.LOOT_TABLE, EternalReturn.id("chests/scroll"));

	/** Chest loot table -> chance that a chest holds a scroll. */
	private static final Map<RegistryKey<LootTable>, Float> CHANCES = Map.of(
			LootTables.STRONGHOLD_LIBRARY_CHEST, 0.20F,
			LootTables.STRONGHOLD_CORRIDOR_CHEST, 0.05F,
			LootTables.STRONGHOLD_CROSSING_CHEST, 0.05F,
			LootTables.END_CITY_TREASURE_CHEST, 0.10F);

	public static final LootFunctionType<RandomScroll> RANDOM_SCROLL = Registry.register(Registries.LOOT_FUNCTION_TYPE, EternalReturn.id("random_scroll"),
			new LootFunctionType<>(RandomScroll.CODEC));

	private ScrollLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			Float chance = CHANCES.get(key);
			if (chance != null && source.isBuiltin()) {
				table.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1.0F))
						.conditionally(RandomChanceLootCondition.builder(chance))
						.with(LootTableEntry.builder(SCROLL_TABLE)));
			}
		});
	}

	public static float chance(RegistryKey<LootTable> table) {
		return CHANCES.getOrDefault(table, 0.0F);
	}

	/** Makes the scroll a scroll of a random enchantment from the tag (never a disabled one). */
	public static final class RandomScroll extends ConditionalLootFunction {
		static final MapCodec<RandomScroll> CODEC = RecordCodecBuilder.mapCodec(instance -> addConditionsField(instance)
				.and(TagKey.codec(RegistryKeys.ENCHANTMENT).optionalFieldOf("options", EnchantmentTags.ON_RANDOM_LOOT).forGetter(RandomScroll::options))
				.apply(instance, RandomScroll::new));

		private final TagKey<Enchantment> options;

		RandomScroll(List<LootCondition> conditions, TagKey<Enchantment> options) {
			super(conditions);
			this.options = options;
		}

		TagKey<Enchantment> options() {
			return this.options;
		}

		@Override
		public LootFunctionType<RandomScroll> getType() {
			return RANDOM_SCROLL;
		}

		@Override
		protected ItemStack process(ItemStack stack, LootContext context) {
			List<String> disabled = EternalReturnConfig.get().disabledEnchantments;
			List<RegistryEntry<Enchantment>> choices = context.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntryList(this.options)
					.map(list -> list.stream().filter(entry -> entry.getKey().map(k -> !disabled.contains(k.getValue().toString())).orElse(false)).toList())
					.orElse(List.of());
			if (choices.isEmpty()) {
				return stack;
			}
			return Scrolls.of(choices.get(context.getRandom().nextInt(choices.size()))).copyWithCount(stack.getCount());
		}
	}
}
