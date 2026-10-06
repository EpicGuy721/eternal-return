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
 * The run picks its baseline with eternalreturn.gametest.fingerprint; ./gradlew runAllGametests
 * -PrecordFingerprints writes new baselines instead of comparing.
 */
public class ControlFingerprintTests implements FabricGameTest {
	public static final String FINGERPRINT_BATCH = "fingerprint";
	/** Three 3x3 groups of chunks in different places, far from the test structures' area. */
	private static final int[][] GROUP_CENTRES = {{40, 40}, {-70, 25}, {15, -90}};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FINGERPRINT_BATCH, tickLimit = 100_000)
	public void carvedTerrainMatchesBaseline(TestContext ctx) throws IOException {
		String name = System.getProperty("eternalreturn.gametest.fingerprint");
		ctx.assertTrue(name != null, "no eternalreturn.gametest.fingerprint set for this run");
		Map<String, String> hashes = fingerprint(ctx.getWorld());

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
		ctx.assertTrue(different.isEmpty(), name + ": carved terrain changed in chunks " + different);
		ctx.complete();
	}

	/** Chunk "x,z" -> hash of every block state in it, after carving and before features. */
	public static Map<String, String> fingerprint(ServerWorld world) {
		Map<String, String> hashes = new LinkedHashMap<>();
		Map<BlockState, Long> stateCodes = new IdentityHashMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int[] centre : GROUP_CENTRES) {
			for (int chunkX = centre[0] - 1; chunkX <= centre[0] + 1; chunkX++) {
				for (int chunkZ = centre[1] - 1; chunkZ <= centre[1] + 1; chunkZ++) {
					Chunk chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.CARVERS, true);
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
					hashes.put(chunkX + "," + chunkZ, Long.toHexString(hash) + (chunk.getStatus() == ChunkStatus.CARVERS ? "" : " status " + chunk.getStatus()));
				}
			}
		}
		return hashes;
	}
}
