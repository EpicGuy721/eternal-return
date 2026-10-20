package com.eternalreturn.birds;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Birds (BirdEntity): one mob, many looks (BirdTable, BirdVariant). Stage 1: the mob and its spawn egg, which hatches a
 * random bird (mostly presets). They don't spawn on their own yet.
 */
public final class Birds {
	public static final EntityType<BirdEntity> BIRD = Registry.register(Registries.ENTITY_TYPE, EternalReturn.id("bird"),
			EntityType.Builder.<BirdEntity>create(BirdEntity::new, SpawnGroup.CREATURE).dimensions(0.35F, 0.4F).eyeHeight(0.3F).maxTrackingRange(8)
					.build(EternalReturn.id("bird").toString()));
	public static final Item SPAWN_EGG = Registry.register(Registries.ITEM, EternalReturn.id("bird_spawn_egg"),
			new SpawnEggItem(BIRD, 0x8A6A4A, 0xD8C8A0, new Item.Settings()));

	private Birds() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(BIRD, BirdEntity.createBirdAttributes());
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.addAfter(Items.PARROT_SPAWN_EGG, SPAWN_EGG));
	}
}
