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
 * Natural spawn lists, in every world, read when the world loads:
 * <ul>
 * <li>zombie villagers make up mobs.zombieVillagerShare (15%) of a biome's zombies and zombie villagers
 * (vanilla 5%): wherever a biome spawns zombie villagers, their weight is set from its zombies' weight;</li>
 * <li>witches spawn now and then in every Nether biome (weight mobs.netherWitchWeight).</li>
 * </ul>
 */
public final class SpawnTweaks {
	private SpawnTweaks() {
	}

	public static void register() {
		BiomeModifications.create(EternalReturn.id("zombie_villagers")).add(ModificationPhase.POST_PROCESSING,
				context -> context.getBiome().getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream()
						.anyMatch(entry -> entry.type == EntityType.ZOMBIE_VILLAGER),
				(selection, context) -> {
					double share = EternalReturnConfig.get().mobs.zombieVillagerShare;
					var entries = selection.getBiome().getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries();
					int zombies = entries.stream().filter(entry -> entry.type == EntityType.ZOMBIE).mapToInt(entry -> entry.getWeight().getValue()).sum();
					SpawnSettings.SpawnEntry old = entries.stream().filter(entry -> entry.type == EntityType.ZOMBIE_VILLAGER).findFirst().orElseThrow();
					if (zombies <= 0 || share <= 0.0 || share >= 1.0) {
						return;
					}
					int weight = Math.max(1, (int) Math.round(zombies * share / (1.0 - share)));
					context.getSpawnSettings().removeSpawnsOfEntityType(EntityType.ZOMBIE_VILLAGER);
					context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.ZOMBIE_VILLAGER, weight, old.minGroupSize, old.maxGroupSize));
				});
		BiomeModifications.create(EternalReturn.id("nether_witches")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInTheNether(), context -> {
			int weight = EternalReturnConfig.get().mobs.netherWitchWeight;
			if (weight > 0) {
				context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.WITCH, weight, 1, 1));
			}
		});
	}
}
