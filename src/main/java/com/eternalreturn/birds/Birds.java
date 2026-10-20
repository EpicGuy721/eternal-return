package com.eternalreturn.birds;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.WorldAccess;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Birds (BirdEntity): one mob, many looks (BirdTable, BirdVariant), and a spawn egg that hatches any bird. In the wild
 * they spawn among creatures in every habitat (biome tag eternalreturn:birds/any), on the blocks of
 * eternalreturn:birds_spawnable_on in daylight, as a bird of that place (BirdVariant.forBiome); flocking shapes come in
 * groups of 3 to 6 alike, loose ones 2 to 3 (spawnFlockmates).
 */
public final class Birds {
	public static final EntityType<BirdEntity> BIRD = Registry.register(Registries.ENTITY_TYPE, EternalReturn.id("bird"),
			EntityType.Builder.<BirdEntity>create(BirdEntity::new, SpawnGroup.CREATURE).dimensions(0.35F, 0.4F).eyeHeight(0.3F).maxTrackingRange(8)
					.build(EternalReturn.id("bird").toString()));
	public static final Item SPAWN_EGG = Registry.register(Registries.ITEM, EternalReturn.id("bird_spawn_egg"),
			new SpawnEggItem(BIRD, 0x8A6A4A, 0xD8C8A0, new Item.Settings()));

	private Birds() {
	}

	/** On the ground birds like, in daylight. */
	public static boolean canSpawn(EntityType<BirdEntity> type, WorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return world.getBlockState(pos.down()).isIn(SPAWNABLE_ON) && world.getBaseLightLevel(pos, 0) > 8;
	}

	/**
	 * A wild flocking bird brings its flock: 2 to 5 more alike (loose shapes 1 or 2), on the ground within 3 blocks and
	 * in its own chunk. Solo shapes come alone. Returns how many came.
	 */
	public static int spawnFlockmates(ServerWorldAccess world, BirdEntity leader) {
		BirdTable.Flocking flocking = leader.getShape().flocking();
		if (flocking == BirdTable.Flocking.SOLO) {
			return 0;
		}
		Random random = world.getRandom();
		int count = flocking == BirdTable.Flocking.FLOCK ? 2 + random.nextInt(4) : 1 + random.nextInt(2);
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			for (int attempt = 0; attempt < 4; attempt++) {
				int x = leader.getBlockX() + random.nextInt(7) - 3;
				int z = leader.getBlockZ() + random.nextInt(7) - 3;
				if (x >> 4 != leader.getBlockX() >> 4 || z >> 4 != leader.getBlockZ() >> 4) {
					continue; // in the leader's chunk: while a world generates, only that chunk can be reached
				}
				BlockPos pos = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
				if (Math.abs(pos.getY() - leader.getBlockY()) > 3 || !world.getBlockState(pos.down()).isIn(SPAWNABLE_ON) || !world.isAir(pos)) {
					continue;
				}
				BirdEntity mate = BIRD.create(world.toServerWorld());
				if (mate != null) {
					mate.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
					mate.setVariant(leader.getVariant());
					world.spawnEntity(mate);
					spawned++;
				}
				break;
			}
		}
		return spawned;
	}

	public static final TagKey<Block> SPAWNABLE_ON = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("birds_spawnable_on"));

	public static void register() {
		FabricDefaultAttributeRegistry.register(BIRD, BirdEntity.createBirdAttributes());
		SpawnRestriction.register(BIRD, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, Birds::canSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(BirdTable.ANY_HABITAT), SpawnGroup.CREATURE, BIRD, BirdTable.SPAWN_WEIGHT, 1, 1);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.addAfter(Items.PARROT_SPAWN_EGG, SPAWN_EGG));
	}
}
