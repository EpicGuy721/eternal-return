package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * The worldgen benchmark (./gradlew runGametestBenchmark, see build.gradle): how many chunks a second the
 * server generates all the way to full chunks, in a square on the coast and one inland, with every worker
 * thread busy as when a player explores or a pre-generator runs. Logs "[bench]" lines; the numbers go in
 * docs/performance.md. The same squares are used in every world (Eternal Return, vanilla, Moderner Beta),
 * so the runs compare.
 * <p>
 * The er_fingerprint batch proves an optimization changed nothing: it hashes carved and fully decorated
 * chunks of the world (as ControlFingerprintTests does for the control worlds) and compares them with
 * fingerprints/&lt;name&gt;.json and &lt;name&gt;_features.json, recorded before the change.
 */
public class WorldgenBenchmarkTests implements FabricGameTest {
	public static final String BATCH = "worldgen_bench";
	public static final String FINGERPRINT_BATCH = "er_fingerprint";
	private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("eternalreturn_bench", Comparator.comparingLong(ChunkPos::toLong));
	/** Square side in chunks (-Deternalreturn.bench.size, default 24). */
	private static final int SIZE = Integer.getInteger("eternalreturn.bench.size", 24);

	/** Centre chunk of each timed square: a coast with islands, and deep inland (seed 173164). A small square first warms the JIT. */
	private static final Object[][] AREAS = {
			{"warm-up", 125, -125, 8},
			{"coast", -13, -19, SIZE},
			{"inland", -66, -66, SIZE},
	};

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 1_000_000)
	public void chunksPerSecond(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerChunkManager manager = world.getChunkManager();
		int threads = Runtime.getRuntime().availableProcessors();
		List<String> results = new ArrayList<>();
		for (Object[] area : AREAS) {
			String name = (String) area[0];
			int centreX = (int) area[1];
			int centreZ = (int) area[2];
			int size = (int) area[3];
			int min = -size / 2;
			List<ChunkPos> square = new ArrayList<>();
			for (int dx = min; dx < min + size; dx++) {
				for (int dz = min; dz < min + size; dz++) {
					square.add(new ChunkPos(centreX + dx, centreZ + dz));
				}
			}
			WorldgenProfile.reset();
			long start = System.nanoTime();
			// Every chunk wanted at once (level 33 = a full chunk), then wait for each: the workers generate them in parallel.
			for (ChunkPos pos : square) {
				manager.addTicket(TICKET, pos, 0, pos);
			}
			for (ChunkPos pos : square) {
				manager.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
			}
			double seconds = (System.nanoTime() - start) / 1e9;
			String biome = world.getBiome(new BlockPos(centreX * 16 + 8, 64, centreZ * 16 + 8)).getKey().map(k -> k.getValue().toString()).orElse("?");
			String line = String.format("%s: %d chunks in %.2f s = %.1f chunks/s (centre chunk %d,%d, %s)", name, square.size(), seconds, square.size() / seconds,
					centreX, centreZ, biome);
			EternalReturn.LOGGER.info("[bench] {}", line);
			results.add(line);
			WorldgenProfile.log(name, 60);
			for (ChunkPos pos : square) {
				manager.removeTicket(TICKET, pos, 0, pos);
			}
		}
		EternalReturn.LOGGER.info("[bench] world {} with {} processors: {}", System.getProperty("eternalreturn.gametest.preset"), threads, String.join(" | ", results));
		EternalReturn.LOGGER.info("[bench] biome cell memory: {} answered from memory, {} worked out ({})", com.eternalreturn.compat.ModernerBetaHooks.BIOME_CACHE_HITS.sum(),
				com.eternalreturn.compat.ModernerBetaHooks.BIOME_CACHE_MISSES.sum(), com.eternalreturn.compat.ModernerBetaHooks.BIOME_CACHES);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = FINGERPRINT_BATCH, tickLimit = 1_000_000)
	public void unchanged(TestContext ctx) throws IOException {
		String name = System.getProperty("eternalreturn.gametest.fingerprint", "eternal_return");
		ServerWorld world = ctx.getWorld();
		logFeatureOrder(world);
		Map<String, String> carved = ControlFingerprintTests.fingerprint(world, false);
		Map<String, String> decorated = ControlFingerprintTests.fingerprint(world, true);
		String dump = System.getProperty("eternalreturn.gametest.dumpBlocks");
		if (dump != null) {
			dumpDecorated(world, Path.of(dump));
		}
		ControlFingerprintTests.compareOnly(ctx, name, carved, "carved terrain");
		ControlFingerprintTests.compareOnly(ctx, name + "_features", decorated, "decorated terrain");
		ctx.complete();
	}

	/** The order of the decoration features per step (each feature's random comes from its place in this list): logs a hash and writes the list to feature_order.txt. */
	@SuppressWarnings("unchecked")
	private static void logFeatureOrder(ServerWorld world) {
		try {
			var generator = world.getChunkManager().getChunkGenerator();
			java.lang.reflect.Field field = net.minecraft.world.gen.chunk.ChunkGenerator.class.getDeclaredField("indexedFeaturesListSupplier");
			field.setAccessible(true);
			var steps = ((java.util.function.Supplier<List<net.minecraft.world.gen.feature.util.PlacedFeatureIndexer.IndexedFeatures>>) field.get(generator)).get();
			var registry = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.PLACED_FEATURE);
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < steps.size(); i++) {
				out.append("step ").append(i).append(System.lineSeparator());
				for (var feature : steps.get(i).features()) {
					out.append("  ").append(registry.getId(feature)).append(System.lineSeparator());
				}
			}
			Files.writeString(Path.of("feature_order.txt"), out);
			EternalReturn.LOGGER.info("[bench] feature order hash {}", out.toString().hashCode());
		} catch (ReflectiveOperationException | IOException e) {
			EternalReturn.LOGGER.warn("[bench] cannot read the feature order", e);
		}
	}

	/** Every block of the decorated fingerprint chunks, to compare two runs block by block (tools/bench/diff_dumps.py). */
	private static void dumpDecorated(ServerWorld world, Path file) throws IOException {
		Map<BlockState, Integer> palette = new HashMap<>();
		List<String> names = new ArrayList<>();
		List<int[]> chunks = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int[] centre : ControlFingerprintTests.decoratedCentres()) {
			for (int chunkX = centre[0] - 1; chunkX <= centre[0] + 1; chunkX++) {
				for (int chunkZ = centre[1] - 1; chunkZ <= centre[1] + 1; chunkZ++) {
					Chunk chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.FEATURES, true);
					int[] ids = new int[(world.getTopY() - world.getBottomY()) * 256];
					int i = 0;
					for (int y = world.getBottomY(); y < world.getTopY(); y++) {
						for (int x = 0; x < 16; x++) {
							for (int z = 0; z < 16; z++) {
								BlockState state = chunk.getBlockState(pos.set(chunkX * 16 + x, y, chunkZ * 16 + z));
								ids[i++] = palette.computeIfAbsent(state, s -> {
									names.add(ControlFingerprintTests.stateName(s));
									return names.size() - 1;
								});
							}
						}
					}
					chunks.add(ids);
					keys.add(chunkX + "," + chunkZ);
				}
			}
		}
		Files.createDirectories(file.toAbsolutePath().getParent());
		try (OutputStream raw = Files.newOutputStream(file); DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
			out.writeInt(world.getBottomY());
			out.writeInt(names.size());
			for (String n : names) {
				out.writeUTF(n);
			}
			out.writeInt(chunks.size());
			for (int c = 0; c < chunks.size(); c++) {
				out.writeUTF(keys.get(c));
				int[] ids = chunks.get(c);
				out.writeInt(ids.length);
				for (int id : ids) {
					out.writeShort(id);
				}
			}
		}
		EternalReturn.LOGGER.info("[bench] dumped {} decorated chunks to {}", chunks.size(), file);
	}
}
