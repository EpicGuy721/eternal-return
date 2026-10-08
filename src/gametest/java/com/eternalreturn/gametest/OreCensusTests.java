package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
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
 * (src/gametest/resources/ores/eternal_return.json; -PrecordFingerprints records a new one). Each ore's
 * total must stay within 1 percent (the emerald survey within 15), counting the host-stone variants as their ore; the counts per host
 * are logged. Also: no stone-textured ore sits inside host stone, and an emerald survey over a large
 * square of the highest extreme hills.
 */
public class OreCensusTests implements FabricGameTest {
	public static final String ORE_BATCH = "ores";
	/** Biomes to sample, each in a square of SQUARE x SQUARE chunks with the outer ring generated but not counted. */
	private static final String[] BIOMES = {"minecraft:badlands", "minecraft:desert", "moderner_beta:early_release_ice_plains",
			"minecraft:forest", "minecraft:jungle", "minecraft:birch_forest", "moderner_beta:late_beta_plains",
			"moderner_beta:early_release_extreme_hills", "minecraft:savanna", "moderner_beta:early_release_taiga"};
	private static final int SQUARE = 6;
	/** Each ore's total may move by this share of the baseline (or by SLACK blocks, whichever is larger). */
	private static final double TOLERANCE = 0.01;
	private static final int SLACK = 3;
	/**
	 * The emerald survey's few hundred emeralds sit near mountain surfaces, where features from
	 * neighbouring chunks (surface lava lakes above all) land before or after them depending on thread
	 * timing: identical code has given 259 and 285. So the survey gets a wider tolerance.
	 */
	private static final double EMERALD_TOLERANCE = 0.15;
	/** Emerald survey: a square of this many chunks a side around the highest extreme hills ground near spawn. */
	private static final int SURVEY = 24;
	static final String[] ORES = {"coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald"};
	static final List<String> HOSTS = List.of("andesite", "diorite", "granite", "red_sandstone", "hardened_sandstone", "hardened_packed_ice");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** "iron_ore" for minecraft:iron_ore, minecraft:deepslate_iron_ore and eternalreturn:granite_iron_ore; null for anything else. */
	static String oreType(Block block) {
		String path = Registries.BLOCK.getId(block).getPath();
		for (String ore : ORES) {
			if (path.equals(ore + "_ore") || path.endsWith("_" + ore + "_ore")) {
				return ore + "_ore";
			}
		}
		return null;
	}

	/** The host stone an ore block belongs to: its variant's host, "deepslate", or "stone" for a vanilla stone ore. */
	static String oreHost(Block block) {
		Identifier id = Registries.BLOCK.getId(block);
		String type = oreType(block);
		if (id.getNamespace().equals(EternalReturn.MOD_ID)) {
			return id.getPath().substring(0, id.getPath().length() - type.length() - 1);
		}
		return id.getPath().startsWith("deepslate_") ? "deepslate" : "stone";
	}

	static boolean isHostStone(BlockState state) {
		return HOSTS.contains(Registries.BLOCK.getId(state.getBlock()).getPath());
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = ORE_BATCH, tickLimit = 2_000_000)
	public void oreCountsHold(TestContext ctx) throws IOException {
		ServerWorld world = ctx.getWorld();
		Map<String, Map<String, Integer>> census = new LinkedHashMap<>();
		Map<String, Integer> total = new TreeMap<>();
		Map<String, Map<String, Integer>> byHost = new TreeMap<>();
		int[] stoneOresInHost = new int[1];
		Map<String, Integer> exposed = new TreeMap<>();
		SurfaceBiomes surfaceBiomes = new SurfaceBiomes(world);
		Map<String, Block> table = BiomeStoneTests.expectedTable();
		Map<String, List<String>> caveSpots = new TreeMap<>();
		List<String> examples = new ArrayList<>();
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
				for (Chunk chunk : chunks) {
					countOres(world, chunk, (pos, state, type) -> {
						counts.merge(type, 1, Integer::sum);
						total.merge(type, 1, Integer::sum);
						byHost.computeIfAbsent(oreHost(state.getBlock()), key -> new TreeMap<>()).merge(type, 1, Integer::sum);
						if (!oreHost(state.getBlock()).equals("stone") && touchesAir(world, pos)) {
							exposed.merge(oreHost(state.getBlock()), 1, Integer::sum);
							// For the in-game checklist: a few variants per host showing in a cave wall, with an air block next to them.
							List<String> spots = caveSpots.computeIfAbsent(oreHost(state.getBlock()), key -> new ArrayList<>());
							if (spots.size() < 6 && spots.stream().noneMatch(spot -> spot.startsWith(type))) {
								for (Direction direction : Direction.values()) {
									BlockPos air = pos.offset(direction);
									if (world.getBlockState(air).isAir() && world.getBlockState(air.down()).isSolidBlock(world, air.down())) {
										spots.add(type + " at " + pos.toShortString() + ", stand at " + air.toShortString());
										break;
									}
								}
							}
						}
						if (oreHost(state.getBlock()).equals("stone") && insideHost(world, pos)
								&& table.getOrDefault(BiomeStoneTests.id(surfaceBiomes.at(pos)), Blocks.STONE) != Blocks.STONE) {
							stoneOresInHost[0]++;
							if (examples.size() < 30) {
								examples.add(Registries.BLOCK.getId(state.getBlock()).getPath() + " at " + pos.toShortString() + " in " + BiomeStoneTests.id(surfaceBiomes.at(pos))
										+ ", around it " + surroundings(world, pos));
							}
						}
					});
				}
				counts.put("_square_from_chunk", x0 * 100000 + z0);
			}
			census.put(biome, counts);
		}
		census.put("all", total);
		census.put("emerald_survey", emeraldSurvey(world));
		Map<String, Integer> checks = new TreeMap<>();
		checks.put("stone_ores_inside_host_stone", stoneOresInHost[0]);
		census.put("checks", checks);
		census.put("variants_open_to_caves_by_host", exposed);
		Map<String, Integer> biomeStoneOff = biomeStoneOff(world);
		census.put("biome_stone_off", biomeStoneOff);
		EternalReturn.LOGGER.info("[ore-census] {}", census);
		EternalReturn.LOGGER.info("[ore-census] by host {}", byHost);
		EternalReturn.LOGGER.info("[ore-census] variants in cave walls (checklist spots): {}", caveSpots);
		EternalReturn.LOGGER.info("[ore-census] stone-textured ores inside host stone: {} e.g. {}", stoneOresInHost[0], examples);

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
		Map<String, Integer> allNow = new TreeMap<>(total);
		allNow.put("emerald_ore (survey)", census.get("emerald_survey").getOrDefault("emerald_ore", 0));
		Map<String, Integer> allBefore = new TreeMap<>(baseline.get("all"));
		allBefore.put("emerald_ore (survey)", baseline.get("emerald_survey").getOrDefault("emerald_ore", 0));
		List<String> off = new ArrayList<>();
		allBefore.forEach((ore, before) -> {
			int now = allNow.getOrDefault(ore, 0);
			double tolerance = ore.startsWith("emerald_ore (survey)") ? EMERALD_TOLERANCE : TOLERANCE;
			if (Math.abs(now - before) > Math.max(before * tolerance, SLACK)) {
				off.add(ore + " " + before + " -> " + now);
			}
		});
		EternalReturn.LOGGER.info("[ore-census] baseline {} | now {} | outside {}%: {}", allBefore, allNow, TOLERANCE * 100, off);
		ctx.assertTrue(off.isEmpty(), "ore counts moved: " + off);
		ctx.assertTrue(stoneOresInHost[0] == 0, stoneOresInHost[0] + " stone-textured ores inside host stone, e.g. " + examples);
		for (String host : HOSTS) {
			ctx.assertTrue(exposed.getOrDefault(host, 0) > 0, "no " + host + " ore variant open to a cave: " + exposed);
		}
		ctx.assertTrue(biomeStoneOff.get("variants") == 0 && biomeStoneOff.get("host_stone_blocks_besides_blobs") == 0 && biomeStoneOff.get("vanilla_ores") > 100,
				"with biome stone off: " + biomeStoneOff);
		ctx.complete();
	}

	interface OreVisitor {
		void visit(BlockPos pos, BlockState state, String type);
	}

	static void countOres(ServerWorld world, Chunk chunk, OreVisitor visitor) {
		ChunkPos chunkPos = chunk.getPos();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = world.getBottomY(); y < world.getTopY(); y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					BlockState state = chunk.getBlockState(pos.set(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z));
					if (state.isAir() || state.isOf(Blocks.STONE)) {
						continue;
					}
					String type = oreType(state.getBlock());
					if (type != null) {
						visitor.visit(pos.toImmutable(), state, type);
					}
				}
			}
		}
	}

	/**
	 * An ore sits inside host stone when every solid non-ore block next to it is a host stone, with no
	 * stone, tuff or fossil bone nearby (it may sit in plain stone or a tuff blob). The census flags a stone ore there
	 * only when its own biome calls for a host stone: at a biome border (Moderner Beta's biome changes a
	 * block or two up or down there) a strip of a river's plain stone can run between host stone, and
	 * ores placed into it are rightly stone ores.
	 */
	static boolean insideHost(ServerWorld world, BlockPos pos) {
		// Stone, tuff or bone (a fossil, which turns some of its bone into coal or diamond ore) within two
		// blocks: the ore may have gone into that block, whose neighbours the vein has since replaced.
		for (BlockPos near : BlockPos.iterate(pos.add(-2, -2, -2), pos.add(2, 2, 2))) {
			BlockState state = world.getBlockState(near);
			if (state.isOf(Blocks.STONE) || state.isOf(Blocks.TUFF) || state.isOf(Blocks.BONE_BLOCK)) {
				return false;
			}
		}
		boolean host = false;
		for (Direction direction : Direction.values()) {
			BlockState side = world.getBlockState(pos.offset(direction));
			if (side.isAir() || !side.getFluidState().isEmpty() || oreType(side.getBlock()) != null) {
				continue;
			}
			if (!isHostStone(side)) {
				return false;
			}
			host = true;
		}
		return host;
	}

	/** Block counts in the 5 x 5 x 5 cube around a position. */
	static String surroundings(ServerWorld world, BlockPos pos) {
		Map<String, Integer> counts = new TreeMap<>();
		for (BlockPos near : BlockPos.iterate(pos.add(-2, -2, -2), pos.add(2, 2, 2))) {
			counts.merge(Registries.BLOCK.getId(world.getBlockState(near).getBlock()).getPath(), 1, Integer::sum);
		}
		return counts.toString();
	}

	static String neighbours(ServerWorld world, BlockPos pos) {
		StringBuilder text = new StringBuilder("[");
		for (Direction direction : Direction.values()) {
			text.append(direction.getName().charAt(0)).append('=').append(Registries.BLOCK.getId(world.getBlockState(pos.offset(direction)).getBlock()).getPath()).append(' ');
		}
		return text.append(']').toString();
	}

	static boolean touchesAir(ServerWorld world, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (world.getBlockState(pos.offset(direction)).isAir()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Eternal Return with biome stone off: a fresh square in the desert, generated with the setting off,
	 * has no host stones but the usual granite, diorite and andesite blobs, and only vanilla ores.
	 */
	private static Map<String, Integer> biomeStoneOff(ServerWorld world) {
		EternalReturnConfig.WorldgenTweaks worldgen = EternalReturnConfig.get().worldgen;
		boolean was = worldgen.biomeStone;
		Map<String, Integer> result = new TreeMap<>();
		result.put("variants", 0);
		result.put("vanilla_ores", 0);
		result.put("host_stone_blocks_besides_blobs", 0);
		try {
			worldgen.biomeStone = false;
			ChunkPos centre = findChunkInside(world, "minecraft:desert", 30);
			List<Chunk> inner = new ArrayList<>();
			for (int cx = centre.x - 2; cx <= centre.x + 2; cx++) {
				for (int cz = centre.z - 2; cz <= centre.z + 2; cz++) {
					Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, true);
					if (Math.abs(cx - centre.x) <= 1 && Math.abs(cz - centre.z) <= 1) {
						inner.add(chunk);
					}
				}
			}
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (Chunk chunk : inner) {
				countOres(world, chunk, (orePos, state, type) -> result.merge(oreHost(state.getBlock()).equals("stone") ? "vanilla_ores" : "variants", 1, Integer::sum));
				for (int y = world.getBottomY(); y < world.getTopY(); y++) {
					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							BlockState state = chunk.getBlockState(pos.set(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z));
							String id = Registries.BLOCK.getId(state.getBlock()).getPath();
							if (id.startsWith("hardened_") || id.equals("red_sandstone")) {
								result.merge("host_stone_blocks_besides_blobs", 1, Integer::sum);
							}
						}
					}
				}
			}
			result.put("square_centre_x", centre.getStartX() + 8);
			result.put("square_centre_z", centre.getStartZ() + 8);
		} finally {
			worldgen.biomeStone = was;
		}
		return result;
	}

	/** Emeralds over a large square around the highest extreme hills ground within 3000 blocks of spawn. */
	private static Map<String, Integer> emeraldSurvey(ServerWorld world) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		int bestX = 0;
		int bestZ = 0;
		int bestY = Integer.MIN_VALUE;
		for (int x = -3000; x <= 3000; x += 64) {
			for (int z = -3000; z <= 3000; z += 64) {
				String biome = generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(64), BiomeCoords.fromBlock(z),
						noise.getMultiNoiseSampler()).getKey().map(key -> key.getValue().toString()).orElse("?");
				if (!biome.equals("moderner_beta:early_release_extreme_hills")) {
					continue;
				}
				int y = generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
				if (y > bestY) {
					bestY = y;
					bestX = x;
					bestZ = z;
				}
			}
		}
		int x0 = (bestX >> 4) - SURVEY / 2;
		int z0 = (bestZ >> 4) - SURVEY / 2;
		int[] emeralds = new int[1];
		int[] emeraldVariants = new int[1];
		int columnsAbove95 = 0;
		// The outer ring is generated in full but not counted, so every counted chunk has all its decoration.
		for (int cx = x0 - 1; cx <= x0 + SURVEY; cx++) {
			for (int cz = z0 - 1; cz <= z0 + SURVEY; cz++) {
				world.getChunk(cx, cz, ChunkStatus.FULL, true);
			}
		}
		for (int cx = x0; cx < x0 + SURVEY; cx++) {
			for (int cz = z0; cz < z0 + SURVEY; cz++) {
				Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, true);
				countOres(world, chunk, (pos, state, type) -> {
					if (type.equals("emerald_ore")) {
						emeralds[0]++;
						if (!oreHost(state.getBlock()).equals("stone")) {
							emeraldVariants[0]++;
						}
					}
				});
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						if (chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z) >= 95) {
							columnsAbove95++;
						}
					}
				}
			}
		}
		Map<String, Integer> survey = new TreeMap<>();
		survey.put("emerald_ore", emeralds[0]);
		survey.put("emerald_ore_in_host_stone", emeraldVariants[0]);
		survey.put("chunks", SURVEY * SURVEY);
		survey.put("columns_with_ground_at_or_above_y95", columnsAbove95);
		survey.put("centre_x", bestX);
		survey.put("centre_z", bestZ);
		survey.put("centre_ground_y", bestY);
		return survey;
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
