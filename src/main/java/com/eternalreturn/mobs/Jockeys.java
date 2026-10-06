package com.eternalreturn.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.duck.JockeyMount;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.SpawnSettings;

import java.util.ArrayList;
import java.util.List;

public final class Jockeys {
	private Jockeys() {
	}

	/**
	 * Puts a rider on a freshly initialized spider or cave spider, the way SpiderEntity.initialize
	 * does for its 1% skeleton. The rider is spawned along with the spider by spawnEntityAndPassengers.
	 */
	public static void addSpiderRider(MobEntity spider, ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason) {
		EntityType<?> type = pickRider(spider, world);
		if (!(type.create(spider.getWorld()) instanceof MobEntity rider)) {
			return;
		}
		rider.refreshPositionAndAngles(spider.getX(), spider.getY(), spider.getZ(), spider.getYaw(), 0.0F);
		// Mount first so the rider's own initialize knows it already has a ride (no polar bear on top of a spider).
		if (!rider.startRiding(spider)) {
			return;
		}
		Random random = world.getRandom();
		// Riders that are zombies must not go looking for a chicken of their own.
		EntityData data = rider instanceof ZombieEntity ? new ZombieEntity.ZombieData(ZombieEntity.shouldBeBaby(random), false) : null;
		rider.initialize(world, difficulty, reason, data);
	}

	/** Called from a stray's initialize. The bear goes into the world now; the stray follows when its spawn finishes. */
	public static void tryPolarBearMount(MobEntity stray, ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason) {
		if (stray.hasVehicle() || world.getRandom().nextFloat() >= EternalReturnConfig.get().mobs.strayPolarBearJockeyChance) {
			return;
		}
		PolarBearEntity bear = EntityType.POLAR_BEAR.create(stray.getWorld());
		if (bear == null) {
			return;
		}
		bear.refreshPositionAndAngles(stray.getX(), stray.getY(), stray.getZ(), stray.getYaw(), 0.0F);
		// Room for the bear plus the stray sitting on it.
		if (!world.isSpaceEmpty(bear, bear.getBoundingBox().stretch(0.0, stray.getHeight(), 0.0))) {
			return;
		}
		bear.initialize(world, difficulty, reason, null);
		((JockeyMount) bear).eternalreturn$setJockeyMount(true);
		if (stray.startRiding(bear)) {
			world.spawnEntity(bear);
		}
	}

	/** Skeleton, unless the biome also spawns other riders (strays in the cold, husks in the desert). */
	private static EntityType<?> pickRider(MobEntity spider, ServerWorldAccess world) {
		if (!EternalReturnConfig.get().mobs.biomeMatchedJockeyRiders) {
			return EntityType.SKELETON;
		}
		List<EntityType<?>> types = new ArrayList<>();
		List<Integer> weights = new ArrayList<>();
		int total = 0;
		RegistryEntry<Biome> biome = world.getBiome(spider.getBlockPos());
		for (SpawnSettings.SpawnEntry entry : biome.value().getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries()) {
			if (entry.type.isIn(MobTags.JOCKEY_RIDERS)) {
				int weight = entry.getWeight().getValue();
				if (weight > 0) {
					types.add(entry.type);
					weights.add(weight);
					total += weight;
				}
			}
		}
		if (total <= 0) {
			return EntityType.SKELETON;
		}
		int roll = world.getRandom().nextInt(total);
		for (int i = 0; i < types.size(); i++) {
			roll -= weights.get(i);
			if (roll < 0) {
				return types.get(i);
			}
		}
		return EntityType.SKELETON;
	}
}
