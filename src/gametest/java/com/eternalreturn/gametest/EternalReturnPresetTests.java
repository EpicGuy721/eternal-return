package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Checks for a world made with the Eternal Return preset (the gametestEternalReturn run). */
public class EternalReturnPresetTests implements FabricGameTest {
	/** The Release 1.6.4 overworld biomes (as Moderner Beta names them) plus the four later additions. */
	private static final Set<String> EXPECTED_BIOMES = Set.of(
			"minecraft:ocean", "minecraft:frozen_ocean", "minecraft:river", "minecraft:frozen_river", "minecraft:beach",
			"minecraft:desert", "minecraft:forest", "minecraft:jungle", "minecraft:mushroom_fields",
			"moderner_beta:late_beta_plains", "moderner_beta:early_release_extreme_hills", "moderner_beta:early_release_taiga",
			"moderner_beta:early_release_swampland", "moderner_beta:early_release_ice_plains",
			"minecraft:birch_forest", "minecraft:savanna", "minecraft:badlands", "minecraft:dark_forest");
	private static final Set<String> CAVE_BIOMES = Set.of("minecraft:lush_caves", "minecraft:dripstone_caves", "minecraft:deep_dark");

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.ETERNAL_RETURN_BATCH, tickLimit = 200_000)
	public void presetBiomesHeightAndUnderground(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();

		ctx.assertTrue(world.getBottomY() == -64 && world.getTopY() == 320,
				"world should span -64 to 320, got " + world.getBottomY() + " to " + world.getTopY());

		// Biomes over a 32768-block square, at the surface and at three depths where cave biomes would sit.
		Set<String> found = new TreeSet<>();
		Map<String, BlockPos> firstSeen = new TreeMap<>();
		for (int x = -16384; x < 16384; x += 128) {
			for (int z = -16384; z < 16384; z += 128) {
				for (int y : new int[]{64, 30, 0, -40}) {
					String id = generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z),
							noise.getMultiNoiseSampler()).getKey().map(key -> key.getValue().toString()).orElse("?");
					found.add(id);
					firstSeen.putIfAbsent(id, new BlockPos(x, y, z));
				}
			}
		}
		EternalReturn.LOGGER.info("[preset-test] biomes found: {}", found);
		for (String cave : CAVE_BIOMES) {
			ctx.assertFalse(found.contains(cave), "modern cave biome " + cave + " generated at " + firstSeen.get(cave));
		}
		Set<String> missing = new TreeSet<>(EXPECTED_BIOMES);
		missing.removeAll(found);
		ctx.assertTrue(missing.isEmpty(), "biomes never generated: " + missing);
		Set<String> unexpected = new TreeSet<>(found);
		unexpected.removeAll(EXPECTED_BIOMES);
		ctx.assertTrue(unexpected.isEmpty(), "biomes outside the list: " + unexpected);

		// Underground: solid down to the -64 floor, with no deepslate anywhere. Ores that land in the
		// tuff blobs below y=0 would take their deepslate variant without the ore tag changes (tuff moved from
		// deepslate_ore_replaceables to stone_ore_replaceables); caves exposing tuff to air made that reachable.
		int deepslate = 0;
		Map<String, Integer> deepslateKinds = new TreeMap<>();
		int solidBelowZero = 0;
		int cellsBelowZero = 0;
		for (int[] c : new int[][]{{0, 0}, {40, -25}, {-63, 18}, {90, 77}, {-12, -140}, {150, 3}, {-200, -90}, {7, 210}}) {
			Chunk chunk = world.getChunk(c[0], c[1], ChunkStatus.FULL, true);
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					for (int y = -64; y < 40; y++) {
						var state = chunk.getBlockState(pos.set(c[0] * 16 + x, y, c[1] * 16 + z));
						String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
						if (blockId.contains("deepslate")) {
							deepslate++;
							deepslateKinds.merge(blockId + "@y" + (y >> 3 << 3), 1, Integer::sum);
						}
						if (y < 0) {
							cellsBelowZero++;
							if (!state.isAir() && state.getFluidState().isEmpty()) {
								solidBelowZero++;
							}
						}
					}
				}
			}
		}
		ctx.assertTrue(deepslate == 0, deepslate + " deepslate blocks generated: " + deepslateKinds);
		double solidShare = (double) solidBelowZero / cellsBelowZero;
		ctx.assertTrue(solidShare > 0.85, "below y=0 should be solid rock (caves aside), only " + solidShare);

		// Badlands get Moderner Beta's badlands surface: terracotta bands and red sand.
		BlockPos badlands = firstSeen.get("minecraft:badlands");
		int surfaceY = generator.getHeight(badlands.getX(), badlands.getZ(), Heightmap.Type.WORLD_SURFACE_WG, world, noise);
		Chunk chunk = world.getChunk(badlands.getX() >> 4, badlands.getZ() >> 4, ChunkStatus.FULL, true);
		Map<String, Integer> surface = new TreeMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int bx = (badlands.getX() & ~15) + x;
				int bz = (badlands.getZ() & ~15) + z;
				int top = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z);
				for (int y = top; y > top - 8; y--) {
					surface.merge(Registries.BLOCK.getId(chunk.getBlockState(pos.set(bx, y, bz)).getBlock()).getPath(), 1, Integer::sum);
				}
			}
		}
		EternalReturn.LOGGER.info("[preset-test] badlands chunk at {} (surface ~{}): {}", badlands, surfaceY, surface);
		int badlandsBlocks = surface.entrySet().stream().filter(e -> e.getKey().contains("terracotta") || e.getKey().equals("red_sand"))
				.mapToInt(Map.Entry::getValue).sum();
		ctx.assertTrue(badlandsBlocks > 0, "no terracotta or red sand in a badlands chunk: " + surface);

		// The in-game checklist's teleport spots (README), read from this seed's world.
		int[][] spots = {{-736, -1274}, {-1564, -844}, {972, 296}, {736, -400}, {-2000, 1176}, {-288, -352}, {-648, 456}, {24, 0}};
		for (int[] spot : spots) {
			int ground = generator.getHeight(spot[0], spot[1], Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
			int top = generator.getHeight(spot[0], spot[1], Heightmap.Type.WORLD_SURFACE_WG, world, noise);
			String id = generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(spot[0]), BiomeCoords.fromBlock(Math.max(ground, 63)),
					BiomeCoords.fromBlock(spot[1]), noise.getMultiNoiseSampler()).getKey().map(key -> key.getValue().toString()).orElse("?");
			EternalReturn.LOGGER.info("[preset-test] spot {} {}: {} ground y={} water above it={}", spot[0], spot[1], id, ground - 1, Math.max(0, top - ground));
		}
		ctx.complete();
	}
}
