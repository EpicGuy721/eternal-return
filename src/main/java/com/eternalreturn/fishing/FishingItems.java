package com.eternalreturn.fishing;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.type.FoodComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;

public final class FishingItems {
	/** Raw fish of any kind: chum ingredient, and the only thing fishermen still buy. */
	public static final TagKey<Item> RAW_FISH = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("raw_fish"));

	public static final Item CHUM = register("chum", new Item(new Item.Settings()));
	/** The fish of the fish table (FishTable), in its order. */
	public static final List<Item> FISH = new ArrayList<>();

	static {
		for (FishTable.Fish fish : FishTable.fish()) {
			FISH.add(register(fish.id(), new FishItem(fish)));
		}
	}

	private FishingItems() {
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, EternalReturn.id(name), item);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries ->
				entries.addAfter(Items.PUFFERFISH, FISH.stream().map(ItemStack::new).toList(), net.minecraft.item.ItemGroup.StackVisibility.PARENT_AND_SEARCH_TABS));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.addAfter(Items.FISHING_ROD, CHUM));
	}

	/**
	 * A fish: eaten raw, filling as much as raw salmon, and never cooked. Nether fish don't burn. The name is
	 * coloured by rarity, and the tooltip tells its rarity, where and when it bites and which fishermen buy it
	 * (language lines written by tools/fish/build_fish.py).
	 */
	public static final class FishItem extends Item {
		private final FishTable.Fish fish;

		FishItem(FishTable.Fish fish) {
			super(settings(fish));
			this.fish = fish;
		}

		private static Item.Settings settings(FishTable.Fish fish) {
			Item.Settings settings = new Item.Settings().food(FoodComponents.SALMON).rarity(switch (fish.rarity().name()) {
				case "uncommon" -> Rarity.UNCOMMON;
				case "rare" -> Rarity.RARE;
				case "epic", "legendary" -> Rarity.EPIC;
				default -> Rarity.COMMON;
			});
			return fish.nether() ? settings.fireproof() : settings;
		}

		public FishTable.Fish fish() {
			return this.fish;
		}

		@Override
		public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
			tooltip.add(Text.translatable("fish.eternalreturn.rarity." + this.fish.rarity().name()).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("fish.eternalreturn." + this.fish.id() + ".habitat").formatted(Formatting.DARK_AQUA));
			tooltip.add(Text.translatable("fish.eternalreturn." + this.fish.id() + ".buyers").formatted(Formatting.DARK_GREEN));
		}
	}
}
