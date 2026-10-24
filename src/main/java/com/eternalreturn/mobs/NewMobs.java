package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.logic.FullEnchant;
import com.eternalreturn.mobs.entity.AtropalEntity;
import com.eternalreturn.mobs.entity.BeholderEntity;
import com.eternalreturn.mobs.entity.BoneSpiderEntity;
import com.eternalreturn.mobs.entity.FlowerCowEntity;
import com.eternalreturn.mobs.entity.FrostbiteEntity;
import com.eternalreturn.mobs.entity.GimpEntity;
import com.eternalreturn.mobs.entity.GrimlockEntity;
import com.eternalreturn.mobs.entity.ParchedEntity;
import com.eternalreturn.mobs.entity.SoldierEntity;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.SpawnSettings;

/**
 * Frostbites (cold biomes), the parched (deserts), bone spiders (the Nether), skeleton wolves (a wolf variant of the
 * Nether, data/eternalreturn/wolf_variant/skeleton.json), beholders (rare, the Nether), grimlocks (deep caves, below
 * y 0, in small groups), mooblooms (autumnal forests) and moolips (cherry groves). Soldiers, gimps and atropals are
 * kept (spawn eggs, commands) but no longer spawn naturally (a gimp from an egg still brings its atropal).
 * Frostbites and the parched take most of the zombies' and skeletons' place where they live, as strays do in the
 * snow: biome tags eternalreturn:frostbite_biomes and eternalreturn:parched_biomes.
 */
public final class NewMobs {
	public static final EntityType<FrostbiteEntity> FROSTBITE = type("frostbite",
			EntityType.Builder.<FrostbiteEntity>create(FrostbiteEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 1.95F).eyeHeight(1.74F).maxTrackingRange(8));
	public static final EntityType<ParchedEntity> PARCHED = type("parched",
			EntityType.Builder.<ParchedEntity>create(ParchedEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 1.99F).eyeHeight(1.74F).maxTrackingRange(8));
	public static final EntityType<BoneSpiderEntity> BONE_SPIDER = type("bone_spider",
			EntityType.Builder.<BoneSpiderEntity>create(BoneSpiderEntity::new, SpawnGroup.MONSTER).dimensions(1.4F, 0.9F).eyeHeight(0.65F).maxTrackingRange(8));
	/** As big as a ghast, and as fire-proof. */
	public static final EntityType<BeholderEntity> BEHOLDER = type("beholder",
			EntityType.Builder.<BeholderEntity>create(BeholderEntity::new, SpawnGroup.MONSTER).makeFireImmune().dimensions(4.0F, 4.0F).eyeHeight(2.25F)
					.maxTrackingRange(10));
	public static final EntityType<SoldierEntity> SOLDIER = type("soldier",
			EntityType.Builder.<SoldierEntity>create(SoldierEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 2.2F).eyeHeight(1.94F).maxTrackingRange(8));
	/** As wide as a spider (its legs reach further), as tall as a man. */
	public static final EntityType<GimpEntity> GIMP = type("gimp",
			EntityType.Builder.<GimpEntity>create(GimpEntity::new, SpawnGroup.MONSTER).dimensions(1.4F, 2.0F).eyeHeight(1.8F).maxTrackingRange(8));
	public static final EntityType<AtropalEntity> ATROPAL = type("atropal",
			EntityType.Builder.<AtropalEntity>create(AtropalEntity::new, SpawnGroup.MONSTER).dimensions(0.7F, 1.5F).eyeHeight(1.2F).maxTrackingRange(8));
	public static final EntityType<GrimlockEntity> GRIMLOCK = type("grimlock",
			EntityType.Builder.<GrimlockEntity>create(GrimlockEntity::new, SpawnGroup.MONSTER).dimensions(0.6F, 1.95F).eyeHeight(1.74F).maxTrackingRange(8));
	public static final EntityType<FlowerCowEntity> MOOBLOOM = type("moobloom",
			EntityType.Builder.<FlowerCowEntity>create(FlowerCowEntity::new, SpawnGroup.CREATURE).dimensions(0.9F, 1.4F).eyeHeight(1.3F).maxTrackingRange(10));
	public static final EntityType<FlowerCowEntity> MOOLIP = type("moolip",
			EntityType.Builder.<FlowerCowEntity>create(FlowerCowEntity::new, SpawnGroup.CREATURE).dimensions(0.9F, 1.4F).eyeHeight(1.3F).maxTrackingRange(10));

	public static final TagKey<net.minecraft.world.biome.Biome> FROSTBITE_BIOMES = TagKey.of(RegistryKeys.BIOME, EternalReturn.id("frostbite_biomes"));
	public static final TagKey<net.minecraft.world.biome.Biome> PARCHED_BIOMES = TagKey.of(RegistryKeys.BIOME, EternalReturn.id("parched_biomes"));

	private NewMobs() {
	}

	private static <T extends net.minecraft.entity.Entity> EntityType<T> type(String id, EntityType.Builder<T> builder) {
		return Registry.register(Registries.ENTITY_TYPE, EternalReturn.id(id), builder.build(EternalReturn.id(id).toString()));
	}

	private static Item egg(String id, EntityType<? extends MobEntity> type, int primary, int secondary) {
		return Registry.register(Registries.ITEM, EternalReturn.id(id + "_spawn_egg"), new SpawnEggItem(type, primary, secondary, new Item.Settings()));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(FROSTBITE, FrostbiteEntity.createFrostbiteAttributes());
		FabricDefaultAttributeRegistry.register(PARCHED, AbstractSkeletonEntity.createAbstractSkeletonAttributes());
		FabricDefaultAttributeRegistry.register(BONE_SPIDER, BoneSpiderEntity.createBoneSpiderAttributes());
		FabricDefaultAttributeRegistry.register(BEHOLDER, BeholderEntity.createBeholderAttributes());
		FabricDefaultAttributeRegistry.register(SOLDIER, SoldierEntity.createSoldierAttributes());
		FabricDefaultAttributeRegistry.register(GIMP, GimpEntity.createGimpAttributes());
		FabricDefaultAttributeRegistry.register(ATROPAL, AtropalEntity.createAtropalAttributes());
		FabricDefaultAttributeRegistry.register(GRIMLOCK, GrimlockEntity.createGrimlockAttributes());
		FabricDefaultAttributeRegistry.register(MOOBLOOM, CowEntity.createCowAttributes());
		FabricDefaultAttributeRegistry.register(MOOLIP, CowEntity.createCowAttributes());
		SpawnRestriction.register(FROSTBITE, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		SpawnRestriction.register(PARCHED, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		SpawnRestriction.register(BONE_SPIDER, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
		SpawnRestriction.register(BEHOLDER, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BeholderEntity::canSpawn);
		SpawnRestriction.register(SOLDIER, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, SoldierEntity::canSpawn);
		SpawnRestriction.register(GIMP, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, GimpEntity::canSpawn);
		SpawnRestriction.register(GRIMLOCK, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, GrimlockEntity::canSpawn);
		SpawnRestriction.register(MOOBLOOM, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::isValidNaturalSpawn);
		SpawnRestriction.register(MOOLIP, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::isValidNaturalSpawn);

		// As strays take four in five of skeletons' spawns in the snow: frostbites of zombies' in the cold, the parched of
		// skeletons' in deserts.
		BiomeModifications.create(EternalReturn.id("frostbites")).add(ModificationPhase.REPLACEMENTS, BiomeSelectors.tag(FROSTBITE_BIOMES), context -> {
			context.getSpawnSettings().removeSpawnsOfEntityType(EntityType.ZOMBIE);
			context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.ZOMBIE, 19, 4, 4));
			context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(FROSTBITE, 76, 4, 4));
		});
		BiomeModifications.create(EternalReturn.id("parched")).add(ModificationPhase.REPLACEMENTS, BiomeSelectors.tag(PARCHED_BIOMES), context -> {
			context.getSpawnSettings().removeSpawnsOfEntityType(EntityType.SKELETON);
			context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 20, 4, 4));
			context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(PARCHED, 80, 4, 4));
		});
		BiomeModifications.create(EternalReturn.id("nether_bone_spiders")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInTheNether(),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(BONE_SPIDER, 4, 1, 1)));
		// Rare: alone, and with a ghast's 1-in-20 spawn check on top of a small weight (a ghast's is 40 to 50).
		BiomeModifications.create(EternalReturn.id("nether_beholders")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInTheNether(),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(BEHOLDER, 3, 1, 1)));
		FullEnchant.register();
		// Deep caves only (GrimlockEntity.canSpawn: below y 0), in small groups.
		BiomeModifications.create(EternalReturn.id("grimlocks")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInOverworld(),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(GRIMLOCK, 30, 2, 3)));
		BiomeModifications.create(EternalReturn.id("nether_wolves")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInTheNether(),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.CREATURE, new SpawnSettings.SpawnEntry(EntityType.WOLF, 20, 1, 3)));
		BiomeModifications.create(EternalReturn.id("mooblooms")).add(ModificationPhase.ADDITIONS,
				BiomeSelectors.includeByKey(RegistryKey.of(RegistryKeys.BIOME, EternalReturn.id("autumnal_forest"))),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.CREATURE, new SpawnSettings.SpawnEntry(MOOBLOOM, 8, 2, 4)));
		BiomeModifications.create(EternalReturn.id("moolips")).add(ModificationPhase.ADDITIONS, BiomeSelectors.includeByKey(BiomeKeys.CHERRY_GROVE),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.CREATURE, new SpawnSettings.SpawnEntry(MOOLIP, 8, 2, 4)));

		Item frostbite = egg("frostbite", FROSTBITE, 0x6E8E9A, 0xD8F3FF);
		Item parched = egg("parched", PARCHED, 0xC8B78A, 0x5E5546);
		Item boneSpider = egg("bone_spider", BONE_SPIDER, 0x3A1A22, 0xE6E0D2);
		Item beholder = egg("beholder", BEHOLDER, 0x4A1426, 0xE8DCC0);
		Item soldier = egg("soldier", SOLDIER, 0x3B2A1E, 0x2A2A2E);
		Item gimp = egg("gimp", GIMP, 0x141218, 0x8C8A96);
		Item atropal = egg("atropal", ATROPAL, 0x8C8E9C, 0xA8687E);
		Item grimlock = egg("grimlock", GRIMLOCK, 0x6E7172, 0x6E6457);
		Item moobloom = egg("moobloom", MOOBLOOM, 0xF4D23A, 0xFFF6C8);
		Item moolip = egg("moolip", MOOLIP, 0xEFA2C6, 0xFFE3F0);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
			entries.addAfter(Items.FOX_SPAWN_EGG, frostbite);
			entries.addAfter(Items.PANDA_SPAWN_EGG, parched);
			entries.addAfter(Items.BLAZE_SPAWN_EGG, boneSpider);
			entries.addAfter(Items.GHAST_SPAWN_EGG, beholder);
			entries.addAfter(Items.SKELETON_SPAWN_EGG, soldier);
			entries.addAfter(Items.SPIDER_SPAWN_EGG, gimp, atropal);
			entries.addAfter(Items.ZOMBIE_SPAWN_EGG, grimlock);
			entries.addAfter(Items.MOOSHROOM_SPAWN_EGG, moobloom, moolip);
		});
	}
}
