package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ocean biomes on dry land: over a wide square, the biome the world reports at the ground (what F3 shows,
 * and what structures and features go by) must not be an ocean where the ground stands above sea level.
 * Moderner Beta blends terrain heights across biome borders, so a coast can rise a block or two out of the
 * water inside an ocean biome (Release 1.6.4 did the same); a handful of such columns are allowed, all
 * less than 8 blocks above sea level. Ocean biomes whose height Moderner Beta didn't know once rose into whole hills
 * here (see the ocean heights in tools/worldgen/build_preset.py).
 */
public class CoastTests implements FabricGameTest {
	public static final String COAST_BATCH = "coast";
	private static final int HALF = 4000;
	private static final int STEP = 8;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = COAST_BATCH, tickLimit = 2_000_000)
	public void noOceanBiomesOnLand(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		int sea = generator.getSeaLevel();
		Map<String, Integer> oceanColumns = new TreeMap<>();
		Map<String, Integer> oceanOnLand = new TreeMap<>();
		Map<Integer, Integer> landHeights = new TreeMap<>();
		List<String> examples = new ArrayList<>();
		for (int x = -HALF; x < HALF; x += STEP) {
			for (int z = -HALF; z < HALF; z += STEP) {
				int ground = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
				String biome = BiomeStoneTests.id(generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(Math.max(ground, sea)),
						BiomeCoords.fromBlock(z), noise.getMultiNoiseSampler()));
				if (!biome.endsWith("_ocean") && !biome.equals("minecraft:ocean")) {
					continue;
				}
				oceanColumns.merge(biome, 1, Integer::sum);
				if (ground > sea) {
					oceanOnLand.merge(biome, 1, Integer::sum);
					landHeights.merge(Math.min((ground - sea) / 4 * 4, 40), 1, Integer::sum);
					if (examples.size() < 12 && (x + z) % 512 == 0) {
						examples.add(x + " " + ground + " " + z + " " + biome);
					}
				}
			}
		}
		int onLand = oceanOnLand.values().stream().mapToInt(Integer::intValue).sum();
		EternalReturn.LOGGER.info("[coast] ocean columns by biome {} | on land (ground above y {}) {} total {} | height above sea, in steps of 4: {} | e.g. {}",
				oceanColumns, sea, oceanOnLand, onLand, landHeights, examples);
		int oceans = oceanColumns.values().stream().mapToInt(Integer::intValue).sum();
		int high = landHeights.entrySet().stream().filter(entry -> entry.getKey() >= 8).mapToInt(Map.Entry::getValue).sum();
		ctx.assertTrue(onLand <= oceans / 20_000 && high == 0, onLand + " sampled columns of dry land report an ocean biome (" + high
				+ " of them 8 or more blocks above sea level): " + oceanOnLand + ", e.g. " + examples);
		ctx.complete();
	}
}
