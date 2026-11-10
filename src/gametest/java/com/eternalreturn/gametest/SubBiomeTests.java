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
	/** Chunks these tests generated (they may be unloaded again, and then look new to the chunk manager). */
	private static final Set<Long> MADE = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
	 * of their parent (with its other sub-biomes; a third to three times for the 1-in-8 ones). Logs each one's share and a /tp.
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
			// The frozen and cold seas' sub-biomes also take part of the deep frozen and cold seas ("also" in the table).
			String deep = parent.getPath().endsWith("_ocean") && !parent.getPath().startsWith("deep_") && (parent.getPath().startsWith("frozen") || parent.getPath().startsWith("cold"))
					? parent.getNamespace() + ":deep_" + parent.getPath() : null;
			int family = samples.getOrDefault(parent.toString(), 0) + (deep != null ? samples.getOrDefault(deep, 0) : 0) + subBiomes().stream()
					.filter(other -> BiomeVariants.get(Identifier.of(other)).base().equals(parent)).mapToInt(other -> samples.getOrDefault(other, 0)).sum();
			double share = samples.getOrDefault(id, 0) / (double) Math.max(1, family);
			report.put(id, String.format("/tp @s %d %d %d (%.1f%% of %s and its sub-biomes)", spot.getX(), y + 1, spot.getZ(), share * 100, parent));
			Integer expected = TABLE_SHARES.get(id);
			if (expected != null) {
				// Half to twice the share; a third to three times for the 1-in-8 ones, whose parents (badlands, swampland,
				// forest) have few 256-block cells in the sample, so a few cells either way move the share a lot.
				double slack = expected >= 8 ? 3 : 2;
				ctx.assertTrue(share >= 1 / slack / expected && share <= slack / expected,
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
			Map.entry("eternalreturn:grand_fir_forest", 4), Map.entry("eternalreturn:poplar_grove", 4), Map.entry("eternalreturn:black_spruce_forest", 4),
			Map.entry("eternalreturn:desert_oasis", 8), Map.entry("eternalreturn:desert_baobabs", 4), Map.entry("eternalreturn:drylands", 4),
			Map.entry("eternalreturn:pinyon_woodland", 4), Map.entry("eternalreturn:monument_valley", 8), Map.entry("eternalreturn:mesa_verde", 8),
			Map.entry("eternalreturn:bryce_mesa", 8), Map.entry("eternalreturn:painted_canyon", 8), Map.entry("eternalreturn:white_mesa", 8),
			Map.entry("eternalreturn:tundra", 4), Map.entry("eternalreturn:arctic", 8), Map.entry("eternalreturn:snowy_mesa", 8),
			Map.entry("eternalreturn:alps", 8), Map.entry("eternalreturn:glacier_field", 8),
			// Batch D (the giant mushroom island isn't checked: the few mushroom islands in the sample make its share swing).
			Map.entry("eternalreturn:tropical_pillars", 8), Map.entry("eternalreturn:volcanic_jungle", 8), Map.entry("eternalreturn:roofed_fungi_forest", 4),
			Map.entry("eternalreturn:coral_ocean", 3), Map.entry("eternalreturn:archipelago", 3),
			// Expansion 2, phase 6, batch 1: the frozen and cold seas (shallow and deep).
			Map.entry("eternalreturn:pack_ice_ocean", 3), Map.entry("eternalreturn:iceberg_ocean", 3), Map.entry("eternalreturn:kelp_forest_ocean", 3),
			Map.entry("eternalreturn:sea_stacks_ocean", 3),
			// Batch 2: the plain and lukewarm seas (shallow only; the lagoon in a second round after the archipelago).
			Map.entry("eternalreturn:seagrass_meadow_ocean", 3), Map.entry("eternalreturn:shoals_ocean", 3));
	// (The mangrove lagoon is only a strip along the shores of its cells, the owner's call: its share of the sea isn't checked.)

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
			Map.entry("eternalreturn:palm_beach", List.of(new Sign("minecraft:jungle_log", 4))),
			Map.entry("eternalreturn:desert_oasis", List.of(new Sign("minecraft:grass_block", 200), new Sign("minecraft:water", 30), new Sign("minecraft:jungle_log", 8))),
			Map.entry("eternalreturn:desert_baobabs", List.of(new Sign("minecraft:acacia_log", 30), new Sign("minecraft:coarse_dirt", 50))),
			Map.entry("eternalreturn:drylands", List.of(new Sign("minecraft:coarse_dirt", 150), new Sign("minecraft:packed_mud", 50), new Sign("minecraft:dead_bush", 10))),
			Map.entry("eternalreturn:pinyon_woodland", List.of(new Sign("minecraft:spruce_log", 20))),
			Map.entry("eternalreturn:monument_valley", List.of(new Sign("minecraft:red_sand", 300), new Sign("minecraft:red_terracotta", 300))),
			Map.entry("eternalreturn:mesa_verde", List.of(new Sign("minecraft:grass_block", 200), new Sign("minecraft:brown_terracotta", 100), new Sign("minecraft:spruce_log", 10))),
			Map.entry("eternalreturn:bryce_mesa", List.of(new Sign("minecraft:pink_terracotta", 100))),
			Map.entry("eternalreturn:painted_canyon", List.of(new Sign("minecraft:purple_terracotta", 100), new Sign("minecraft:light_blue_terracotta", 30))),
			Map.entry("eternalreturn:white_mesa", List.of(new Sign("minecraft:calcite", 100), new Sign("minecraft:white_terracotta", 100))),
			Map.entry("eternalreturn:tundra", List.of(new Sign("minecraft:birch_log", 20), new Sign("minecraft:gravel", 30), new Sign("minecraft:moss_block", 20))),
			Map.entry("eternalreturn:arctic", List.of(new Sign("minecraft:snow_block", 300), new Sign("minecraft:packed_ice", 50))),
			Map.entry("eternalreturn:snowy_mesa", List.of(new Sign("minecraft:light_blue_terracotta", 100), new Sign("minecraft:snow", 100))),
			Map.entry("eternalreturn:alps", List.of(new Sign("minecraft:grass_block", 200), new Sign("minecraft:spruce_log", 10), new Sign("#minecraft:small_flowers", 20))),
			Map.entry("eternalreturn:glacier_field", List.of(new Sign("minecraft:gravel", 50), new Sign("minecraft:packed_ice", 30))),
			// Batch D. The test world places no structures, so the pillars, volcanoes, islands and reefs aren't there (LandmarkTests builds them).
			Map.entry("eternalreturn:tropical_pillars", List.of(new Sign("minecraft:jungle_log", 30))),
			Map.entry("eternalreturn:volcanic_jungle", List.of(new Sign("minecraft:blackstone", 100), new Sign("minecraft:coarse_dirt", 50), new Sign("minecraft:magma_block", 5))),
			Map.entry("eternalreturn:roofed_fungi_forest", List.of(new Sign("minecraft:mycelium", 100), new Sign("minecraft:mushroom_stem", 30), new Sign("minecraft:dark_oak_log", 10))),
			Map.entry("eternalreturn:giant_mushroom_island", List.of(new Sign("minecraft:mushroom_stem", 60), new Sign("minecraft:red_mushroom_block", 100))),
			Map.entry("eternalreturn:coral_ocean", List.of(new Sign("#minecraft:coral_blocks", 20))),
			Map.entry("eternalreturn:archipelago", List.of(new Sign("minecraft:seagrass", 30))),
			// The icebergs and sea stacks are landmarks (none in the test world): the sea itself.
			Map.entry("eternalreturn:pack_ice_ocean", List.of(new Sign("minecraft:ice", 1000))),
			Map.entry("eternalreturn:iceberg_ocean", List.of(new Sign("minecraft:ice", 1000))),
			Map.entry("eternalreturn:kelp_forest_ocean", List.of(new Sign("minecraft:kelp_plant", 3000))),
			Map.entry("eternalreturn:sea_stacks_ocean", List.of(new Sign("minecraft:water", 1000))),
			// The shoals' sandbars are landmarks; the lagoon's mangroves grow only in the shallows by a shore.
			Map.entry("eternalreturn:seagrass_meadow_ocean", List.of(new Sign("minecraft:tall_seagrass", 1500))),
			Map.entry("eternalreturn:shoals_ocean", List.of(new Sign("minecraft:water", 1000))),
			Map.entry("eternalreturn:mangrove_lagoon_ocean", List.of(new Sign("minecraft:water", 1000))));

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
					// Made by these tests, loaded now, or saved by another batch (unloaded since).
					madeBefore |= MADE.contains(net.minecraft.util.math.ChunkPos.toLong(cx, cz))
							|| world.getChunkManager().getChunk(cx, cz, ChunkStatus.FEATURES, false) != null
							|| world.getChunkManager().chunkLoadingManager.getNbt(new net.minecraft.util.math.ChunkPos(cx, cz)).join().isPresent();
				}
			}
			for (int cx = cx0 - 3; cx <= cx0 + 3; cx++) {
				for (int cz = cz0 - 3; cz <= cz0 + 3; cz++) {
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
					MADE.add(net.minecraft.util.math.ChunkPos.toLong(cx, cz));
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
					// The sea sub-biomes from their floor up.
					boolean sea = world.getBiome(pos).isIn(net.minecraft.registry.tag.BiomeTags.IS_OCEAN);
					for (int y = sea ? ground - 2 : Math.max(world.getBottomY(), world.getSeaLevel() - 8); y <= top + 2; y++) {
						BlockState state = world.getBlockState(pos.set(x, y, z));
						if (state.isAir()) {
							continue;
						}
						found.merge(Registries.BLOCK.getId(state.getBlock()).toString(), 1, Integer::sum);
						if (state.isIn(BlockTags.CORAL_BLOCKS)) {
							found.merge("#minecraft:coral_blocks", 1, Integer::sum);
						}
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

	/**
	 * Round a jungle and the swamps (5 x 5 chunks each, generated in full), no dirt rests on leaves: a tree that started on
	 * another tree's leaves put dirt there, and the owner saw trees growing out of that dirt.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void noTreesGrowOnLeaves(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		survey(world);
		Map<String, Integer> found = new TreeMap<>();
		for (String id : List.of("minecraft:jungle", "moderner_beta:early_release_swampland", "eternalreturn:spooky_swamp", "eternalreturn:bald_cypress_swamp")) {
			BlockPos spot = spots.get(id);
			ctx.assertTrue(spot != null, id + " not found");
			int cx0 = spot.getX() >> 4;
			int cz0 = spot.getZ() >> 4;
			for (int cx = cx0 - 3; cx <= cx0 + 3; cx++) {
				for (int cz = cz0 - 3; cz <= cz0 + 3; cz++) {
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
					MADE.add(net.minecraft.util.math.ChunkPos.toLong(cx, cz));
				}
			}
			int onLeaves = 0;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int x = (cx0 - 2) * 16; x < (cx0 + 3) * 16; x++) {
				for (int z = (cz0 - 2) * 16; z < (cz0 + 3) * 16; z++) {
					int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
					for (int y = world.getSeaLevel() - 4; y <= top; y++) {
						// A lone block of dirt (no ground beside it) on leaves: what a tree leaves when it starts on another
						// tree. Dirt on leaves at the edge of the land (a tree's leaves grown in under an overhang) isn't counted.
						if (world.getBlockState(pos.set(x, y, z)).isIn(BlockTags.DIRT) && world.getBlockState(pos.set(x, y - 1, z)).isIn(BlockTags.LEAVES)
								&& loneDirt(world, new BlockPos(x, y, z))) {
							onLeaves++;
							if (onLeaves <= 3) {
								EternalReturn.LOGGER.info("[sub-biomes] dirt on leaves at {} {} {} ({}), above it {}", x, y, z, id,
										Registries.BLOCK.getId(world.getBlockState(pos.set(x, y + 1, z)).getBlock()));
							}
						}
					}
				}
			}
			found.put(id, onLeaves);
		}
		EternalReturn.LOGGER.info("[sub-biomes] dirt on leaves: {}; trees kept from starting on leaves so far: {}", found, BiomeTrees.STOPPED_ON_LEAVES.get());
		ctx.assertTrue(found.values().stream().allMatch(n -> n == 0), "dirt on leaves: " + found);
		ctx.complete();
	}

	/**
	 * Sub-biomes whose own trees may not show in 5 x 5 chunks: bushes, which no table looks up (heathland, drylands), few
	 * trees (Bryce), or none (Monument Valley, Painted Canyon, White Mesa).
	 */
	private static final Set<String> BUSHES_ONLY = Set.of("eternalreturn:heathland", "eternalreturn:drylands", "eternalreturn:bryce_mesa",
			"eternalreturn:monument_valley", "eternalreturn:painted_canyon", "eternalreturn:white_mesa",
			// Batch C: dwarf birches are bushes; the arctic and icy mountains have no trees; the snowy mesa and glacier field few.
			"eternalreturn:tundra", "eternalreturn:arctic", "eternalreturn:snowy_mesa", "eternalreturn:glacier_field",
			// Lone trees, a few in 25 chunks (Moderner Beta's decoration varies a little from run to run).
			"eternalreturn:meadow", "eternalreturn:pumpkin_plains",
			// Batch D: the archipelago's trees grow on its islands, which are landmarks (none in the test world).
			"eternalreturn:archipelago",
			// The coral ocean and the giant mushroom island have no trees.
			"eternalreturn:coral_ocean", "eternalreturn:giant_mushroom_island",
			// The frozen and cold seas have no trees.
			"eternalreturn:pack_ice_ocean", "eternalreturn:iceberg_ocean", "eternalreturn:kelp_forest_ocean", "eternalreturn:sea_stacks_ocean",
			"eternalreturn:seagrass_meadow_ocean", "eternalreturn:shoals_ocean", "eternalreturn:mangrove_lagoon_ocean");

	private static boolean loneDirt(ServerWorld world, BlockPos dirt) {
		for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
			BlockState side = world.getBlockState(dirt.offset(d));
			if (side.isOpaqueFullCube(world, dirt.offset(d)) && !side.isIn(BlockTags.LEAVES) && !side.isIn(BlockTags.LOGS)) {
				return false;
			}
		}
		return true;
	}

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
