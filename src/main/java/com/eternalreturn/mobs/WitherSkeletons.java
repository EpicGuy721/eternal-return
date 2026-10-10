package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.world.biome.SpawnSettings;

/**
 * Wither skeletons now and then spawn anywhere in the Nether, not only in fortresses (weight
 * mobs.netherWitherSkeletonWeight in every Nether biome's monster list, read when the world loads), and
 * always drop their skull (data/minecraft/loot_table/entities/wither_skeleton.json).
 */
public final class WitherSkeletons {
	private WitherSkeletons() {
	}

	public static void register() {
		BiomeModifications.create(EternalReturn.id("nether_wither_skeletons")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInTheNether(), context -> {
			int weight = EternalReturnConfig.get().mobs.netherWitherSkeletonWeight;
			if (weight > 0) {
				context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.WITHER_SKELETON, weight, 1, 2));
			}
		});
	}
}
