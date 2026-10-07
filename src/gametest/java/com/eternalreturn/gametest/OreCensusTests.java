package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ore counts in an Eternal Return world, by ore and by biome, over fixed squares of fully generated
 * chunks (one square inside each of several biomes), compared with a baseline recorded before a change
 * (src/gametest/resources/ores/eternal_return.json; -PrecordFingerprints records a new one). Counts may
 * move a little between runs (ore blobs cross chunk borders), so each ore must stay within 15 percent.
 */
public class OreCensusTests implements FabricGameTest {
	public static final String ORE_BATCH = "ores";
	/** Biomes to sample, each in a square of SQUARE x SQUARE chunks with the outer ring generated but not counted. */
	private static final String[] BIOMES = {"minecraft:badlands", "minecraft:desert", "moderner_beta:early_release_ice_plains",
			"minecraft:forest", "minecraft:jungle", "minecraft:birch_forest", "moderner_beta:late_beta_plains"};
	private static final int SQUARE = 6;
	private static final double TOLERANCE = 0.15;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = ORE_BATCH, tickLimit = 2_000_000)
	public void oreCountsHold(TestContext ctx) throws IOException {
		ServerWorld world = ctx.getWorld();
		Map<String, Map<String, Integer>> census = new LinkedHashMap<>();
		Map<String, Integer> total = new TreeMap<>();
		for (String biome : BIOMES) {
			ChunkPos centre = findChunkInside(world, biome);
			Map<String, Integer> counts = new TreeMap<>();
			if (centre != null) {
				int x0 = centre.x - SQUARE / 2;
				int z0 = centre.z - SQUARE / 2;
				List<Chunk> chunks = new ArrayList<>();
				for (int cx = x0; cx < x0 + SQUARE; cx++) {
					for (int cz = z0; cz < z0 + SQUARE; cz++) {
						Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, true);
						if (cx > x0 && cx < x0 + SQUARE - 1 && cz > z0 && cz < z0 + SQUARE - 1) {
							chunks.add(chunk);
						}
					}
				}
				BlockPos.Mutable pos = new BlockPos.Mutable();
				for (Chunk chunk : chunks) {
					ChunkPos chunkPos = chunk.getPos();
					for (int y = world.getBottomY(); y < 200; y++) {
						for (int x = 0; x < 16; x++) {
							for (int z = 0; z < 16; z++) {
								BlockState state = chunk.getBlockState(pos.set(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z));
								String id = Registries.BLOCK.getId(state.getBlock()).getPath();
								if (id.endsWith("_ore")) {
									String ore = id.replace("deepslate_", "");
									counts.merge(ore, 1, Integer::sum);
									total.merge(ore, 1, Integer::sum);
								}
							}
						}
					}
				}
				counts.put("_square_from_chunk", x0 * 100000 + z0);
			}
			census.put(biome, counts);
		}
		census.put("all", total);
		EternalReturn.LOGGER.info("[ore-census] {}", census);

		String recordDir = System.getProperty("eternalreturn.gametest.fingerprintRecord");
		if (recordDir != null) {
			Path file = Path.of(recordDir).getParent().resolve("ores").resolve("eternal_return.json");
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(census, writer);
			}
			ctx.complete();
			return;
		}
		Map<String, Map<String, Integer>> baseline;
		try (InputStream in = OreCensusTests.class.getResourceAsStream("/ores/eternal_return.json")) {
			ctx.assertTrue(in != null, "no ore baseline ores/eternal_return.json");
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				baseline = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, TreeMap<String, Integer>>>() { }.getType());
			}
		}
		List<String> off = new ArrayList<>();
		baseline.get("all").forEach((ore, before) -> {
			int now = total.getOrDefault(ore, 0);
			if (before >= 40 && Math.abs(now - before) > before * TOLERANCE) {
				off.add(ore + " " + before + " -> " + now);
			}
		});
		EternalReturn.LOGGER.info("[ore-census] baseline {} | now {} | outside {}%: {}", baseline.get("all"), total, (int) (TOLERANCE * 100), off);
		ctx.assertTrue(off.isEmpty(), "ore counts moved: " + off);
		ctx.complete();
	}

	/** A chunk whose centre and corners all sit in the biome, searched outward from 0,0 on a 64-block grid (deterministic). */
	static ChunkPos findChunkInside(ServerWorld world, String biome) {
		return findChunkInside(world, biome, 0);
	}

	/** The same, skipping the first rings (each 64 blocks) around 0,0. */
	static ChunkPos findChunkInside(ServerWorld world, String biome, int firstRing) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		for (int ring = firstRing; ring < 80; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int x = dx * 64;
					int z = dz * 64;
					boolean inside = true;
					for (int[] offset : new int[][]{{0, 0}, {-40, -40}, {40, -40}, {-40, 40}, {40, 40}}) {
						String id = generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x + offset[0]), BiomeCoords.fromBlock(64), BiomeCoords.fromBlock(z + offset[1]),
								noise.getMultiNoiseSampler()).getKey().map(key -> key.getValue().toString()).orElse("?");
						if (!id.equals(biome)) {
							inside = false;
							break;
						}
					}
					if (inside) {
						return new ChunkPos(x >> 4, z >> 4);
					}
				}
			}
		}
		return null;
	}
}
