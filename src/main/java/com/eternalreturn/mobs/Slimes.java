package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.biome.SpawnSettings;

/**
 * Slimes: besides vanilla's sizes 1, 2 and 4, a newly spawned slime is sometimes size 8
 * (mobs.slimeSize8Chance) or 16 (mobs.slimeSize16Chance), which split down through the sizes as usual.
 * And while it rains at night, slimes spawn on the surface in any overworld biome (mobs.slimesInNightRain),
 * on top of vanilla's swamps and slime chunks: every overworld biome without slimes in its spawn list
 * gets them (weight 100, as vanilla gives most biomes), and the spawn rule lets them through where the
 * sky is open while it rains at night.
 */
public final class Slimes {
	private Slimes() {
	}

	public static void register() {
		BiomeModifications.create(EternalReturn.id("rain_slimes")).add(ModificationPhase.ADDITIONS,
				BiomeSelectors.foundInOverworld().and(context -> EternalReturnConfig.get().mobs.slimesInNightRain && context.getBiome().getSpawnSettings()
						.getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream().noneMatch(entry -> entry.type == EntityType.SLIME)),
				context -> context.getSpawnSettings().addSpawn(SpawnGroup.MONSTER, new SpawnSettings.SpawnEntry(EntityType.SLIME, 100, 4, 4)));
	}

	/** From a slime's initialize: sometimes bigger than vanilla allows. */
	public static void maybeGrow(SlimeEntity slime, Random random) {
		if (slime.getType() != EntityType.SLIME) {
			return;
		}
		EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
		double roll = random.nextDouble();
		if (roll < cfg.slimeSize16Chance) {
			slime.setSize(16, true);
		} else if (roll < cfg.slimeSize16Chance + cfg.slimeSize8Chance) {
			slime.setSize(8, true);
		}
	}

	/** A natural slime spawn on the surface while it rains there at night (any biome; with storms, under one). */
	public static boolean rainSpawn(ServerWorld world, BlockPos pos) {
		if (!EternalReturnConfig.get().mobs.slimesInNightRain || world.getDifficulty() == Difficulty.PEACEFUL
				|| !com.eternalreturn.weather.LocalWeather.rainingAt(world, pos)) {
			return false;
		}
		long time = Math.floorMod(world.getTimeOfDay(), 24000L);
		return time >= 13000L && time < 23000L && world.isSkyVisible(pos);
	}
}
