package com.eternalreturn.fishing;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.type.FoodComponents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

public final class FishingItems {
	/** Raw fish of any kind: chum ingredient, and the only thing fishermen still buy. */
	public static final TagKey<Item> RAW_FISH = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("raw_fish"));

	public static final Item WORM = register("worm", new Item(new Item.Settings()));
	public static final Item CHUM = register("chum", new Item(new Item.Settings()));
	public static final Item GLOW_BAIT = register("glow_bait", new Item(new Item.Settings()));
	public static final Item INFERNAL_BAIT = register("infernal_bait", new Item(new Item.Settings().fireproof()));

	public static final Item PERCH = fish("perch", false);
	public static final Item PIKE = fish("pike", false);
	public static final Item CATFISH = fish("catfish", false);
	public static final Item TUNA = fish("tuna", false);
	public static final Item ICEFISH = fish("icefish", false);
	public static final Item ANGLERFISH = fish("anglerfish", false);
	public static final Item ELECTRIC_EEL = fish("electric_eel", false);
	public static final Item KOI = fish("koi", false);
	public static final Item MAGMA_EEL = fish("magma_eel", true);
	public static final Item ASHFIN = fish("ashfin", true);
	public static final Item SOULFIN = fish("soulfin", true);

	private FishingItems() {
	}

	/** All fish are eaten raw and fill the same as raw salmon. Nether fish don't burn. */
	private static Item fish(String name, boolean nether) {
		Item.Settings settings = new Item.Settings().food(FoodComponents.SALMON);
		return register(name, new Item(nether ? settings.fireproof() : settings));
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, EternalReturn.id(name), item);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> entries.addAfter(Items.PUFFERFISH,
				PERCH, PIKE, CATFISH, TUNA, ICEFISH, ANGLERFISH, ELECTRIC_EEL, KOI, MAGMA_EEL, ASHFIN, SOULFIN));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.addAfter(Items.FISHING_ROD,
				WORM, CHUM, GLOW_BAIT, INFERNAL_BAIT));
	}
}
