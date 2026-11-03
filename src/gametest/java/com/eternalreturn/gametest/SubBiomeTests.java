package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.trees.BiomeTrees;
import com.eternalreturn.worldgen.variants.BiomeVariants;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Sub-biomes (worldgen expansion phase 6, tools/worldgen/variants.json "subs"): each turns up in the biome layers of an
 * Eternal Return world, takes about its share of its parent, and once generated has its own decoration and grows the
 * trees of its own table. Logs a /tp for each, nearest the world's centre.
 */
public class SubBiomeTests implements FabricGameTest {
	public static final String BATCH = "sub_biomes";
	private static final int REACH = 6144;
	private static final int STEP = 32;
	/** One spot well inside each sub-biome (its sampled neighbours 32 blocks away the same), nearest 0, 0; shared by the tests. */
	private static Map<String, BlockPos> spots;
	private static Map<String, Integer> samples;

	private static Set<String> subBiomes() {
		return BiomeVariantTests.variantTable().keySet().stream().filter(id -> BiomeVariants.get(Identifier.of(id)).kind().equals("sub"))
				.collect(Collectors.toCollection(java.util.TreeSet::new));
	}

	private static synchronized void survey(ServerWorld world) {
		if (spots != null) {
			return;
		}
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		BiomeSource source = generator.getBiomeSource();
		int cells = REACH * 2 / STEP + 1;
		String[][] grid = new String[cells][cells];
		for (int i = 0; i < cells; i++) {
			for (int j = 0; j < cells; j++) {
				int x = -REACH + i * STEP;
				int z = -REACH + j * STEP;
				grid[i][j] = BiomeStoneTests.id(source.getBiome(x >> 2, 64 >> 2, z >> 2, noise.getMultiNoiseSampler()));
			}
		}
		Map<String, Integer> counts = new HashMap<>();
		Map<String, BlockPos> best = new HashMap<>();
		Map<String, Long> bestDistance = new HashMap<>();
		Map<String, BlockPos> any = new HashMap<>();
		for (int i = 1; i < cells - 1; i++) {
			for (int j = 1; j < cells - 1; j++) {
				String id = grid[i][j];
				counts.merge(id, 1, Integer::sum);
				int x = -REACH + i * STEP;
				int z = -REACH + j * STEP;
				long distance = (long) x * x + (long) z * z;
				any.merge(id, new BlockPos(x, 0, z), (a, b) -> (long) a.getX() * a.getX() + (long) a.getZ() * a.getZ() <= distance ? a : b);
				boolean inside = id.equals(grid[i - 1][j]) && id.equals(grid[i + 1][j]) && id.equals(grid[i][j - 1]) && id.equals(grid[i][j + 1]);
				if (inside && distance < bestDistance.getOrDefault(id, Long.MAX_VALUE)) {
					bestDistance.put(id, distance);
					best.put(id, new BlockPos(x, 0, z));
				}
			}
		}
		// Shore sub-biomes are thin strips: any spot will do.
		any.forEach(best::putIfAbsent);
		spots = best;
		samples = counts;
	}

	/**
	 * Every sub-biome is in the layers within 6,144 blocks of 0, 0; land sub-biomes take between half and twice their share
	 * of their parent (with its other sub-biomes). Logs each one's share and a /tp.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void subBiomesTurnUp(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		survey(world);
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Map<String, String> report = new TreeMap<>();
		for (String id : subBiomes()) {
			BlockPos spot = spots.get(id);
			ctx.assertTrue(spot != null, id + " isn't within " + REACH + " blocks of 0, 0");
			int y = generator.getHeight(spot.getX(), spot.getZ(), Heightmap.Type.WORLD_SURFACE_WG, world, noise);
			Identifier parent = BiomeVariants.get(Identifier.of(id)).base();
			int family = samples.getOrDefault(parent.toString(), 0) + subBiomes().stream()
					.filter(other -> BiomeVariants.get(Identifier.of(other)).base().equals(parent)).mapToInt(other -> samples.getOrDefault(other, 0)).sum();
			double share = samples.getOrDefault(id, 0) / (double) Math.max(1, family);
			report.put(id, String.format("/tp @s %d %d %d (%.1f%% of %s and its sub-biomes)", spot.getX(), y + 1, spot.getZ(), share * 100, parent));
			Integer expected = TABLE_SHARES.get(id);
			if (expected != null) {
				ctx.assertTrue(share >= 0.5 / expected && share <= 2.0 / expected,
						String.format("%s takes %.1f%% of its parent, expected about 1 in %d", id, share * 100, expected));
			}
		}
		report.forEach((id, line) -> EternalReturn.LOGGER.info("[sub-biomes] {}: {}", id, line));
		ctx.complete();
	}

	/** The "share" of each land sub-biome in tools/worldgen/variants.json. */
	private static final Map<String, Integer> TABLE_SHARES = Map.ofEntries(
			Map.entry("eternalreturn:pumpkin_plains", 4), Map.entry("eternalreturn:meadow", 4), Map.entry("eternalreturn:dark_meadow", 4),
			Map.entry("eternalreturn:heathland", 4), Map.entry("eternalreturn:webbed_forest", 8), Map.entry("eternalreturn:spooky_swamp", 8),
			Map.entry("eternalreturn:bald_cypress_swamp", 4), Map.entry("eternalreturn:redwood_forest", 4), Map.entry("eternalreturn:larch_forest", 4),
			Map.entry("eternalreturn:grand_fir_forest", 4), Map.entry("eternalreturn:poplar_grove", 4), Map.entry("eternalreturn:black_spruce_forest", 4));

	/** What each sub-biome must show in 5 x 5 generated chunks round its spot: a block (or block tag) and how many at least. */
	private record Sign(String what, int atLeast) {
	}

	private static final Map<String, List<Sign>> SIGNS = Map.ofEntries(
			Map.entry("eternalreturn:pumpkin_plains", List.of(new Sign("minecraft:pumpkin", 40))),
			Map.entry("eternalreturn:meadow", List.of(new Sign("#minecraft:small_flowers", 60))),
			Map.entry("eternalreturn:dark_meadow", List.of(new Sign("minecraft:lily_of_the_valley", 10), new Sign("minecraft:dark_oak_log", 10))),
			Map.entry("eternalreturn:heathland", List.of(new Sign("minecraft:podzol", 30), new Sign("minecraft:coarse_dirt", 30), new Sign("minecraft:allium", 15),
					new Sign("minecraft:oak_log", 10), new Sign("eternalreturn:azalea_log", 5))),
			Map.entry("eternalreturn:webbed_forest", List.of(new Sign("minecraft:cobweb", 150))),
			Map.entry("eternalreturn:spooky_swamp", List.of(new Sign("minecraft:mud", 30), new Sign("bare oak logs", 20))),
			Map.entry("eternalreturn:bald_cypress_swamp", List.of(new Sign("minecraft:oak_log", 60))),
			Map.entry("eternalreturn:redwood_forest", List.of(new Sign("minecraft:podzol", 200), new Sign("minecraft:spruce_log", 100))),
			Map.entry("eternalreturn:larch_forest", List.of(new Sign("minecraft:spruce_log", 60))),
			Map.entry("eternalreturn:grand_fir_forest", List.of(new Sign("minecraft:spruce_log", 60))),
			Map.entry("eternalreturn:poplar_grove", List.of(new Sign("minecraft:birch_log", 40), new Sign("eternalreturn:pale_oak_log", 10))),
			Map.entry("eternalreturn:black_spruce_forest", List.of(new Sign("minecraft:spruce_log", 30))),
			Map.entry("eternalreturn:palm_beach", List.of(new Sign("minecraft:jungle_log", 4))));

	/**
	 * Round each sub-biome's spot, 5 x 5 chunks are generated in full. Each must show its signs (pumpkins, cobwebs, heath
	 * ground and so on), and every tree its features placed there must come from its own table, with at least one that
	 * isn't vanilla's (dead trees, which have no leaves, aren't looked up and count as bare oak logs).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 4_000_000)
	public void subBiomesAreDecorated(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		survey(world);
		List<String> failures = new java.util.ArrayList<>();
		for (String id : subBiomes()) {
			BlockPos spot = spots.get(id);
			if (spot == null) {
				failures.add(id + " not found");
				continue;
			}
			Map<String, Integer> before = snapshot(id);
			int cx0 = spot.getX() >> 4;
			int cz0 = spot.getZ() >> 4;
			// Trees are counted as they generate: where another batch already made these chunks, there is nothing to count.
			boolean madeBefore = false;
			for (int cx = cx0 - 2; cx <= cx0 + 2; cx++) {
				for (int cz = cz0 - 2; cz <= cz0 + 2; cz++) {
					madeBefore |= world.getChunkManager().getChunk(cx, cz, ChunkStatus.FEATURES, false) != null;
				}
			}
			for (int cx = cx0 - 3; cx <= cx0 + 3; cx++) {
				for (int cz = cz0 - 3; cz <= cz0 + 3; cz++) {
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
				}
			}
			Map<String, Integer> found = new TreeMap<>();
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int x = (cx0 - 2) * 16; x < (cx0 + 3) * 16; x++) {
				for (int z = (cz0 - 2) * 16; z < (cz0 + 3) * 16; z++) {
					// Only columns of the biome itself, from just under the sea up (no mineshafts, no neighbours' trees).
					int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
					int ground = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
					if (!BiomeStoneTests.id(world.getBiome(pos.set(x, Math.min(ground, top), z))).equals(id)) {
						continue;
					}
					for (int y = Math.max(world.getBottomY(), world.getSeaLevel() - 8); y <= top + 2; y++) {
						BlockState state = world.getBlockState(pos.set(x, y, z));
						if (state.isAir()) {
							continue;
						}
						found.merge(Registries.BLOCK.getId(state.getBlock()).toString(), 1, Integer::sum);
						if (state.isIn(BlockTags.SMALL_FLOWERS)) {
							found.merge("#minecraft:small_flowers", 1, Integer::sum);
						}
						if (state.isOf(Blocks.OAK_LOG) && noLeavesNear(world, pos)) {
							found.merge("bare oak logs", 1, Integer::sum);
						}
					}
				}
			}
			for (Sign sign : SIGNS.getOrDefault(id, List.of())) {
				int count = found.getOrDefault(sign.what(), 0);
				if (count < sign.atLeast()) {
					failures.add(id + ": " + count + " " + sign.what() + ", expected " + sign.atLeast() + " or more");
				}
			}
			Map<String, Integer> trees = new TreeMap<>();
			snapshot(id).forEach((name, n) -> {
				int grown = n - before.getOrDefault(name, 0);
				if (grown > 0) {
					trees.put(name, grown);
				}
			});
			Set<String> allowed = allowedTrees(world, id);
			for (String name : trees.keySet()) {
				if (!allowed.contains(name)) {
					failures.add(id + " grew " + name + ", not on its table " + allowed);
				}
			}
			// The heathland's shrubs are bushes, which no table looks up (its oak and azalea logs are its signs).
			if (!madeBefore && !allowed.isEmpty() && !BUSHES_ONLY.contains(id) && trees.keySet().stream().noneMatch(name -> !name.equals("vanilla"))) {
				failures.add(id + " grew none of its own trees: " + trees);
			}
			EternalReturn.LOGGER.info("[sub-biomes] {} at {} {}{}: trees {}; blocks {}", id, spot.getX(), spot.getZ(),
					madeBefore ? " (already generated: trees not counted)" : "", trees,
					found.entrySet().stream().filter(e -> e.getValue() >= 5 && !e.getKey().contains("stone") && !e.getKey().contains("dirt") || e.getKey().contains("coarse"))
							.map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
		}
		ctx.assertTrue(failures.isEmpty(), String.join("; ", failures));
		ctx.complete();
	}

	private static final Set<String> BUSHES_ONLY = Set.of("eternalreturn:heathland");

	private static boolean noLeavesNear(ServerWorld world, BlockPos log) {
		for (BlockPos p : BlockPos.iterate(log.add(-2, -2, -2), log.add(2, 2, 2))) {
			if (world.getBlockState(p).isIn(BlockTags.LEAVES)) {
				return false;
			}
		}
		return true;
	}

	/** The trees a biome's table can pick (by name, as BiomeTrees counts them), over every sapling. */
	private static Set<String> allowedTrees(ServerWorld world, String id) {
		RegistryEntry<Biome> biome = world.getRegistryManager().get(RegistryKeys.BIOME).getEntry(
				net.minecraft.registry.RegistryKey.of(RegistryKeys.BIOME, Identifier.of(id))).orElseThrow();
		Set<String> names = new java.util.TreeSet<>();
		for (Block sapling : Registries.BLOCK) {
			Identifier saplingId = Registries.BLOCK.getId(sapling);
			for (boolean mega : new boolean[]{false, true}) {
				List<BiomeTrees.Choice> list = BiomeTrees.listFor(biome, saplingId, mega);
				if (list != null) {
					list.forEach(choice -> names.add(BiomeTrees.name(choice)));
				}
			}
		}
		return names;
	}

	private static Map<String, Integer> snapshot(String id) {
		Map<String, Integer> out = new HashMap<>();
		BiomeTrees.GENERATED.forEach((key, n) -> {
			if (key.startsWith(id + " ")) {
				out.put(key.substring(id.length() + 1), n.get());
			}
		});
		return out;
	}
}
