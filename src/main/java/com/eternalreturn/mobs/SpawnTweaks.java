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
 * <li>witches spawn now and then in every Nether biome (weight mobs.netherWitchWeight);</li>
 * <li>phantoms spawn in the End (weight mobs.endPhantomWeight), at sizes from a vanilla phantom's up to six times
 * that (MobEntitySpawnMixin).</li>
 * </ul>
 */
public final class SpawnTweaks {
	private SpawnTweaks() {
	}

	/**
	 * Whether tonight is a phantom night (one in mobs.phantomNightChance), the same for the whole night: from the
	 * world's seed and the day's number.
	 */
	public static boolean isPhantomNight(net.minecraft.server.world.ServerWorld world) {
		int chance = EternalReturnConfig.get().mobs.phantomNightChance;
		long day = (world.getTimeOfDay() + 12000L) / 24000L;
		long mixed = world.getSeed() * 6364136223846793005L + day * 1442695040888963407L;
		mixed ^= mixed >>> 31;
		return chance > 0 && Math.floorMod(mixed, (long) chance) == 0;
	}

	public static void register() {
		for (String id : EternalReturnConfig.get().mobs.noNaturalSpawns) {
			net.minecraft.util.Identifier type = net.minecraft.util.Identifier.tryParse(id);
			if (type == null || !net.minecraft.registry.Registries.ENTITY_TYPE.containsId(type)) {
				continue;
			}
			net.minecraft.entity.EntityType<?> entity = net.minecraft.registry.Registries.ENTITY_TYPE.get(type);
			BiomeModifications.create(EternalReturn.id("no_" + type.getPath())).add(ModificationPhase.REMOVALS, BiomeSelectors.all(),
					context -> context.getSpawnSettings().removeSpawnsOfEntityType(entity));
		}
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
		BiomeModifications.create(EternalReturn.id("end_phantoms")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInTheEnd(), context -> {
			int weight = EternalReturnConfig.get().mobs.endPhantomWeight;
			if (weight > 0) {
				context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.PHANTOM, weight, 1, 2));
			}
		});
		BiomeModifications.create(EternalReturn.id("nether_witches")).add(ModificationPhase.ADDITIONS, BiomeSelectors.foundInTheNether(), context -> {
			int weight = EternalReturnConfig.get().mobs.netherWitchWeight;
			if (weight > 0) {
				context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.WITCH, weight, 1, 1));
			}
		});
	}
}
