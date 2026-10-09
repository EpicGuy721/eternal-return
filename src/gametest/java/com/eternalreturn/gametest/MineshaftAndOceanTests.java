package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.structures.MineshaftWoods;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureSetKeys;
import net.minecraft.structure.StructureStart;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Eternal Return's mineshaft woods, ocean variants and cherry groves. Structure starts are found the way
 * the generator places them (each structure set's placement, then the structure's own start), over a
 * square of 500 x 500 chunks, without generating the area.
 */
public class MineshaftAndOceanTests implements FabricGameTest {
	private static final int RADIUS = 250;
	/** The signature wood of each biome with trees; every other biome is oak. */
	private static final Map<String, String> SIGNATURE = Map.of(
			"minecraft:birch_forest", "birch",
			"moderner_beta:early_release_taiga", "spruce",
			"minecraft:jungle", "jungle",
			"minecraft:savanna", "acacia",
			"minecraft:dark_forest", "dark_oak",
			"minecraft:cherry_grove", "cherry");
	/** Moderner Beta's biomes that vanilla's mineshaft tag misses; Eternal Return lets mineshafts start there. */
	private static final List<String> EXTRA_BIOMES = List.of("moderner_beta:early_release_extreme_hills", "moderner_beta:early_release_ice_plains",
			"moderner_beta:early_release_swampland", "moderner_beta:late_beta_plains");
	static final List<String> OCEANS = List.of("minecraft:ocean", "minecraft:deep_ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
			"minecraft:deep_lukewarm_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean");

	private record Found(StructureStart start, String biome, ChunkPos chunk) {
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.ETERNAL_RETURN_BATCH, tickLimit = 2_000_000)
	public void mineshaftsUseTheirBiomesWood(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<Found> mineshafts = new ArrayList<>();
		mineshafts.addAll(starts(world, StructureSetKeys.MINESHAFTS, StructureKeys.MINESHAFT));
		mineshafts.addAll(starts(world, StructureSetKeys.MINESHAFTS, StructureKeys.MINESHAFT_MESA));
		Map<String, Integer> byWood = new TreeMap<>();
		Map<String, Integer> byBiome = new TreeMap<>();
		List<String> wrong = new ArrayList<>();
		Map<String, Found> nearest = new LinkedHashMap<>();
		for (Found found : mineshafts) {
			String expected = SIGNATURE.getOrDefault(found.biome(), "oak");
			byBiome.merge(found.biome(), 1, Integer::sum);
			for (StructurePiece piece : found.start().getChildren()) {
				MineshaftWoods.Wood wood = ((MineshaftWoods.Holder) piece).eternalreturn$wood();
				if (wood == null || !wood.name().equals(expected)) {
					wrong.add(found.chunk() + " in " + found.biome() + ": " + (wood == null ? "vanilla" : wood.name()) + ", expected " + expected);
					break;
				}
			}
			byWood.merge(expected, 1, Integer::sum);
			// For the build check, mineshafts under land: pieces that would touch water are skipped, so one under the sea may build nothing.
			boolean underLand = !found.biome().contains("ocean") && !found.biome().contains("river") && !found.biome().equals("minecraft:beach");
			Found best = nearest.get(expected);
			if (underLand && distance(found.chunk()) > 40 * 40 && (best == null || distance(found.chunk()) < distance(best.chunk()))) {
				nearest.put(expected, found);
			}
		}
		EternalReturn.LOGGER.info("[mineshafts] {} starts within {} chunks | by wood {} | by biome {} | wrong {}", mineshafts.size(), RADIUS, byWood, byBiome,
				wrong.subList(0, Math.min(wrong.size(), 10)));
		ctx.assertTrue(wrong.isEmpty(), wrong.size() + " mineshafts with the wrong wood, e.g. " + wrong.subList(0, Math.min(wrong.size(), 5)));
		for (MineshaftWoods.Wood wood : MineshaftWoods.WOODS) {
			ctx.assertTrue(byWood.getOrDefault(wood.name(), 0) > 0, "no " + wood.name() + " mineshaft within " + RADIUS + " chunks: " + byWood);
		}
		for (String biome : EXTRA_BIOMES) {
			ctx.assertTrue(byBiome.getOrDefault(biome, 0) > 0, "no mineshaft starts in " + biome + ": " + byBiome);
		}

		// The nearest mineshaft of each wood, built for real: built from its wood, and the wood saved with its pieces. Test
		// worlds are made with structures switched off, so the start is placed here into fully generated terrain, chunk by
		// chunk, with the game's own piece code, as the generator would.
		Map<String, String> built = new LinkedHashMap<>();
		for (Map.Entry<String, Found> entry : nearest.entrySet()) {
			MineshaftWoods.Wood wood = MineshaftWoods.byName(entry.getKey());
			Found found = entry.getValue();
			BlockBox box = found.start().getBoundingBox();
			for (int cx = (box.getMinX() >> 4) - 1; cx <= (box.getMaxX() >> 4) + 1; cx++) {
				for (int cz = (box.getMinZ() >> 4) - 1; cz <= (box.getMaxZ() >> 4) + 1; cz++) {
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
				}
			}
			Random random = Random.create(world.getSeed());
			for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
				for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
					BlockBox chunkBox = new BlockBox(cx * 16, world.getBottomY(), cz * 16, cx * 16 + 15, world.getTopY() - 1, cz * 16 + 15);
					found.start().place(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(), random, chunkBox, new ChunkPos(cx, cz));
				}
			}
			Map<String, Integer> blocks = new TreeMap<>();
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (StructurePiece piece : found.start().getChildren()) {
				BlockBox pieceBox = piece.getBoundingBox();
				for (int x = pieceBox.getMinX(); x <= pieceBox.getMaxX(); x++) {
					for (int y = pieceBox.getMinY(); y <= pieceBox.getMaxY(); y++) {
						for (int z = pieceBox.getMinZ(); z <= pieceBox.getMaxZ(); z++) {
							BlockState state = world.getBlockState(pos.set(x, y, z));
							String id = Registries.BLOCK.getId(state.getBlock()).getPath();
							if (id.endsWith("_planks") || id.endsWith("_fence") || id.endsWith("_log")) {
								blocks.merge(id, 1, Integer::sum);
							}
						}
					}
				}
			}
			int ours = count(blocks, wood.planks()) + count(blocks, wood.fence()) + count(blocks, wood.log());
			int other = 0;
			for (MineshaftWoods.Wood any : MineshaftWoods.WOODS) {
				if (any != wood) {
					other += count(blocks, any.planks()) + count(blocks, any.fence());
				}
			}
			StructurePiece first = found.start().getChildren().get(0);
			NbtCompound saved = first.toNbt(StructureContext.from(world));
			built.put(entry.getKey(), found.biome() + " at " + found.start().getBoundingBox().getCenter().toShortString() + ": " + blocks);
			ctx.assertTrue(ours > 20, wood.name() + " mineshaft at " + box.getCenter().toShortString() + " in " + found.biome() + " has only " + ours
					+ " blocks of its wood: " + blocks);
			ctx.assertTrue(other <= ours / 10, wood.name() + " mineshaft at " + box.getCenter().toShortString() + " has " + other + " planks or fences of other woods: " + blocks);
			ctx.assertTrue(wood.name().equals(saved.getString("eternalreturn_wood")), wood.name() + " mineshaft piece saves " + saved.getString("eternalreturn_wood"));
		}
		EternalReturn.LOGGER.info("[mineshafts] built, nearest of each wood: {}", built);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.ETERNAL_RETURN_BATCH, tickLimit = 2_000_000)
	public void oceanVariantsCherryGrovesAndMonuments(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Map<String, Integer> biomes = new TreeMap<>();
		int samples = 0;
		int[] cherry = null;
		for (int x = -RADIUS * 16; x < RADIUS * 16; x += 32) {
			for (int z = -RADIUS * 16; z < RADIUS * 16; z += 32) {
				String biome = BiomeStoneTests.id(generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(64), BiomeCoords.fromBlock(z),
						noise.getMultiNoiseSampler()));
				biomes.merge(biome, 1, Integer::sum);
				if (biome.equals("minecraft:cherry_grove") && (cherry == null || (long) x * x + (long) z * z < (long) cherry[0] * cherry[0] + (long) cherry[1] * cherry[1])) {
					cherry = new int[]{x, z};
				}
				samples++;
			}
		}
		Map<String, String> share = new LinkedHashMap<>();
		int total = samples;
		biomes.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
				.forEach(entry -> share.put(entry.getKey(), String.format("%.2f%%", entry.getValue() * 100.0 / total)));
		List<Found> monuments = starts(world, StructureSetKeys.OCEAN_MONUMENTS, StructureKeys.MONUMENT);
		monuments.sort((a, b) -> Long.compare(distance(a.chunk()), distance(b.chunk())));
		List<String> monumentSpots = monuments.stream().limit(6).map(found -> found.start().getBoundingBox().getCenter().toShortString() + " in " + found.biome()).toList();
		EternalReturn.LOGGER.info("[oceans] biomes over {} x {} blocks: {} | ocean monuments {} e.g. {} | nearest cherry grove {}", RADIUS * 32, RADIUS * 32, share,
				monuments.size(), monumentSpots, cherry == null ? "none" : cherry[0] + ", " + cherry[1]);
		// For the in-game checklist: the highest ground within 3000 blocks, and the nearest stretch of some biomes.
		int[] top = {0, 0, Integer.MIN_VALUE};
		String topBiome = "?";
		for (int x = -3000; x <= 3000; x += 32) {
			for (int z = -3000; z <= 3000; z += 32) {
				int y = generator.getHeight(x, z, net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, world, noise);
				if (y > top[2]) {
					top = new int[]{x, z, y};
					topBiome = BiomeStoneTests.id(generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(64), BiomeCoords.fromBlock(z),
							noise.getMultiNoiseSampler()));
				}
			}
		}
		Map<String, String> spots = new LinkedHashMap<>();
		spots.put("highest ground", top[0] + " " + top[2] + " " + top[1] + " (" + topBiome + ")");
		for (String biome : List.of("minecraft:cherry_grove", "minecraft:mushroom_fields", "minecraft:badlands", "moderner_beta:early_release_ice_plains",
				"minecraft:dark_forest", "moderner_beta:early_release_extreme_hills", "minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:cold_ocean",
				"minecraft:frozen_ocean", "minecraft:deep_ocean", "minecraft:deep_frozen_ocean")) {
			ChunkPos inside = OreCensusTests.findChunkInside(world, biome);
			if (inside != null) {
				int x = inside.getStartX() + 8;
				int z = inside.getStartZ() + 8;
				spots.put(biome, x + " " + generator.getHeight(x, z, net.minecraft.world.Heightmap.Type.WORLD_SURFACE_WG, world, noise) + " " + z);
			}
		}
		EternalReturn.LOGGER.info("[oceans] checklist spots {}", spots);
		for (String ocean : OCEANS) {
			ctx.assertTrue(biomes.getOrDefault(ocean, 0) > 0, "no " + ocean + " within " + RADIUS * 16 + " blocks: " + share);
		}
		ctx.assertTrue(biomes.getOrDefault("minecraft:cherry_grove", 0) > 0, "no cherry grove: " + share);
		ctx.assertTrue(!monuments.isEmpty(), "no ocean monument within " + RADIUS + " chunks");
		ctx.complete();
	}

	/** Every start of a structure within RADIUS chunks of 0,0, found the way the generator places them (without generating). */
	private static List<Found> starts(ServerWorld world, RegistryKey<StructureSet> setKey, RegistryKey<Structure> structureKey) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		StructurePlacementCalculator calculator = world.getChunkManager().getStructurePlacementCalculator();
		StructureSet set = world.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).get(setKey);
		Registry<Structure> structures = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
		Structure structure = structures.get(structureKey);
		List<Found> found = new ArrayList<>();
		for (int cx = -RADIUS; cx < RADIUS; cx++) {
			for (int cz = -RADIUS; cz < RADIUS; cz++) {
				if (!set.placement().shouldGenerate(calculator, cx, cz)) {
					continue;
				}
				ChunkPos chunk = new ChunkPos(cx, cz);
				StructureStart start = structure.createStructureStart(world.getRegistryManager(), generator, generator.getBiomeSource(), noise,
						world.getStructureTemplateManager(), world.getSeed(), chunk, 0, world, biome -> structure.getValidBiomes().contains(biome));
				if (start.hasChildren()) {
					RegistryEntry<Biome> biome = generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(chunk.getOffsetX(2)), BiomeCoords.fromBlock(64),
							BiomeCoords.fromBlock(chunk.getOffsetZ(2)), noise.getMultiNoiseSampler());
					found.add(new Found(start, BiomeStoneTests.id(biome), chunk));
				}
			}
		}
		return found;
	}

	private static int count(Map<String, Integer> blocks, Block block) {
		return blocks.getOrDefault(Registries.BLOCK.getId(block).getPath(), 0);
	}

	private static long distance(ChunkPos chunk) {
		return (long) chunk.x * chunk.x + (long) chunk.z * chunk.z;
	}

	static Identifier id(String id) {
		return Identifier.of(id);
	}
}
