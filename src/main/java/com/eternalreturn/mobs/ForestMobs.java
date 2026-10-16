package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.entity.BearEntity;
import com.eternalreturn.mobs.entity.VampireEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.SpawnSettings;

/**
 * The forests' new mobs: brown, black and sun bears in every forest biome (biome tag minecraft:is_forest,
 * mobs.bearSpawnWeight each), and the autumnal forest's vampires (in that biome's own spawn list, at night), with
 * their spawn eggs.
 */
public final class ForestMobs {
	public static final Item VAMPIRE_SPAWN_EGG = egg("vampire_spawn_egg", ModEntities.VAMPIRE, 0x1A1A1F, 0x8E1B1B);
	public static final Item BROWN_BEAR_SPAWN_EGG = egg("brown_bear_spawn_egg", ModEntities.BROWN_BEAR, 0x6B4A2B, 0x3E2A18);
	public static final Item BLACK_BEAR_SPAWN_EGG = egg("black_bear_spawn_egg", ModEntities.BLACK_BEAR, 0x1E1B1A, 0x6A5848);
	public static final Item SUN_BEAR_SPAWN_EGG = egg("sun_bear_spawn_egg", ModEntities.SUN_BEAR, 0x2A2420, 0xD9A441);

	private ForestMobs() {
	}

	private static Item egg(String id, EntityType<? extends MobEntity> type, int primary, int secondary) {
		return Registry.register(Registries.ITEM, EternalReturn.id(id), new SpawnEggItem(type, primary, secondary, new Item.Settings()));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(ModEntities.VAMPIRE, VampireEntity.createVampireAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.VAMPIRE_BAT, BatEntity.createBatAttributes());
		SpawnRestriction.register(ModEntities.VAMPIRE, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, VampireEntity::canSpawn);
		int weight = EternalReturnConfig.get().mobs.bearSpawnWeight;
		for (EntityType<BearEntity> bear : new EntityType[]{ModEntities.BROWN_BEAR, ModEntities.BLACK_BEAR, ModEntities.SUN_BEAR}) {
			FabricDefaultAttributeRegistry.register(bear, PolarBearEntity.createPolarBearAttributes());
			SpawnRestriction.register(bear, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::isValidNaturalSpawn);
			if (weight > 0) {
				BiomeModifications.create(Registries.ENTITY_TYPE.getId(bear)).add(ModificationPhase.ADDITIONS, BiomeSelectors.tag(BiomeTags.IS_FOREST),
						context -> context.getSpawnSettings().addSpawn(SpawnGroup.CREATURE, new SpawnSettings.SpawnEntry(bear, weight, 1, 2)));
			}
		}
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
			entries.addAfter(Items.BLAZE_SPAWN_EGG, BLACK_BEAR_SPAWN_EGG, BROWN_BEAR_SPAWN_EGG);
			entries.addAfter(Items.STRIDER_SPAWN_EGG, SUN_BEAR_SPAWN_EGG);
			entries.addAfter(Items.VEX_SPAWN_EGG, VAMPIRE_SPAWN_EGG);
		});
	}
}
