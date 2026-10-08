package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proves that a world's carved terrain is unchanged: hashes every block of a fixed set of chunks,
 * generated only up to the carving step (noise, surface, carvers; no features or structures), and
 * compares the hashes with a baseline recorded before a change (src/gametest/resources/fingerprints).
 * A second test does the same with three other groups decorated, so ores and other features are covered:
 * in vanilla worlds against a decorated baseline, in Moderner Beta worlds (whose decoration does not repeat
 * from run to run) by checking that none of this mod's blocks appear.
 * The run picks its baseline with eternalreturn.gametest.fingerprint; ./gradlew runAllGametests
 * -PrecordFingerprints writes new baselines instead of comparing.
 */
public class ControlFingerprintTests implements FabricGameTest {
	public static final String FINGERPRINT_BATCH = "fingerprint";
	/** Three 3x3 groups of chunks in different places, far from the test structures' area. */
	private static final int[][] GROUP_CENTRES = {{40, 40}, {-70, 25}, {15, -90}};
	/** The decorated groups sit elsewhere, so the carved groups are never generated past carving. */
	private static final int[][] DECORATED_CENTRES = {{40, 140}, {-170, 25}, {15, -190}};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FINGERPRINT_BATCH, tickLimit = 100_000)
	public void carvedTerrainMatchesBaseline(TestContext ctx) throws IOException {
		String name = System.getProperty("eternalreturn.gametest.fingerprint");
		ctx.assertTrue(name != null, "no eternalreturn.gametest.fingerprint set for this run");
		compare(ctx, name, fingerprint(ctx.getWorld(), false), "carved terrain");
	}

	/**
	 * Three other groups decorated (ores, plants, structures): each group's 5 x 5 chunks are decorated one
	 * by one in a fixed order and the inner 3 x 3 hashed, so every feature that can reach those chunks has
	 * run, always in the same order. Baselines are fingerprints/<name>_features.json.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FINGERPRINT_BATCH, tickLimit = 200_000)
	public void decoratedTerrainMatchesBaseline(TestContext ctx) throws IOException {
		String name = System.getProperty("eternalreturn.gametest.fingerprint");
		ctx.assertTrue(name != null, "no eternalreturn.gametest.fingerprint set for this run");
		ServerWorld world = ctx.getWorld();
		Map<String, String> hashes = fingerprint(world, true);
		// No block of this mod's (an ore variant or a hardened block) turns up in these worlds.
		Map<String, Integer> ours = new java.util.TreeMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int[] centre : DECORATED_CENTRES) {
			for (int chunkX = centre[0] - 1; chunkX <= centre[0] + 1; chunkX++) {
				for (int chunkZ = centre[1] - 1; chunkZ <= centre[1] + 1; chunkZ++) {
					Chunk chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FEATURES, true);
					for (int y = world.getBottomY(); y < world.getTopY(); y++) {
						for (int x = 0; x < 16; x++) {
							for (int z = 0; z < 16; z++) {
								net.minecraft.util.Identifier id = net.minecraft.registry.Registries.BLOCK.getId(chunk.getBlockState(pos.set(chunkX * 16 + x, y, chunkZ * 16 + z)).getBlock());
								if (id.getNamespace().equals(EternalReturn.MOD_ID)) {
									ours.merge(id.getPath(), 1, Integer::sum);
								}
							}
						}
					}
				}
			}
		}
		EternalReturn.LOGGER.info("[fingerprint] {}: blocks of this mod in the decorated chunks: {}", name, ours);
		ctx.assertTrue(ours.isEmpty(), name + ": this mod's blocks generated here: " + ours);
		// Moderner Beta's decoration does not repeat from run to run (underground blobs, clay and plants
		// differ between two runs of the same code even when chunks are decorated one by one in a fixed
		// order), so only vanilla worlds compare a decorated fingerprint.
		if (world.getChunkManager().getChunkGenerator().getClass().getName().contains("modernerbeta")) {
			ctx.complete();
			return;
		}
		compare(ctx, name + "_features", hashes, "decorated terrain");
	}

	private static void compare(TestContext ctx, String name, Map<String, String> hashes, String what) throws IOException {

		String recordDir = System.getProperty("eternalreturn.gametest.fingerprintRecord");
		if (recordDir != null) {
			Path file = Path.of(recordDir).resolve(name + ".json");
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(hashes, writer);
			}
			EternalReturn.LOGGER.info("[fingerprint] recorded {} chunks for {} in {}", hashes.size(), name, file);
			ctx.complete();
			return;
		}

		Map<String, String> baseline;
		try (InputStream in = ControlFingerprintTests.class.getResourceAsStream("/fingerprints/" + name + ".json")) {
			ctx.assertTrue(in != null, "no baseline fingerprints/" + name + ".json");
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				baseline = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, String>>() { }.getType());
			}
		}
		List<String> different = new ArrayList<>();
		baseline.forEach((chunk, hash) -> {
			if (!hash.equals(hashes.get(chunk))) {
				different.add(chunk);
			}
		});
		EternalReturn.LOGGER.info("[fingerprint] {}: {} of {} chunks match the baseline{}", name, baseline.size() - different.size(),
				baseline.size(), different.isEmpty() ? "" : ", different: " + different);
		ctx.assertTrue(different.isEmpty(), name + ": " + what + " changed in chunks " + different);
		ctx.complete();
	}

	/** Chunk "x,z" -> hash of every block state in it, after carving and before features (or fully decorated). */
	public static Map<String, String> fingerprint(ServerWorld world, boolean decorated) {
		Map<String, String> hashes = new LinkedHashMap<>();
		Map<BlockState, Long> stateCodes = new IdentityHashMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int[] centre : decorated ? DECORATED_CENTRES : GROUP_CENTRES) {
			if (decorated) {
				// One chunk at a time, in a fixed order: a chunk's features also write into its neighbours,
				// so the result depends on the order chunks are decorated in, which is otherwise up to the
				// worker threads.
				for (int chunkX = centre[0] - 2; chunkX <= centre[0] + 2; chunkX++) {
					for (int chunkZ = centre[1] - 2; chunkZ <= centre[1] + 2; chunkZ++) {
						world.getChunk(chunkX, chunkZ, ChunkStatus.FEATURES, true);
					}
				}
			}
			for (int chunkX = centre[0] - 1; chunkX <= centre[0] + 1; chunkX++) {
				for (int chunkZ = centre[1] - 1; chunkZ <= centre[1] + 1; chunkZ++) {
					Chunk chunk = world.getChunk(chunkX, chunkZ, decorated ? ChunkStatus.FEATURES : ChunkStatus.CARVERS, true);
					long hash = 1125899906842597L;
					for (int y = world.getBottomY(); y < world.getTopY(); y++) {
						for (int x = 0; x < 16; x++) {
							for (int z = 0; z < 16; z++) {
								BlockState state = chunk.getBlockState(pos.set(chunkX * 16 + x, y, chunkZ * 16 + z));
								long code = stateCodes.computeIfAbsent(state, s -> (long) s.toString().hashCode() * 0x9E3779B97F4A7C15L);
								hash = hash * 31 + code;
							}
						}
					}
					ChunkStatus expected = decorated ? ChunkStatus.FEATURES : ChunkStatus.CARVERS;
					hashes.put(chunkX + "," + chunkZ, Long.toHexString(hash) + (chunk.getStatus() == expected ? "" : " status " + chunk.getStatus()));
				}
			}
		}
		return hashes;
	}
}
