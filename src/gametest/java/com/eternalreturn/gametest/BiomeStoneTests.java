package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.compat.ModernerBetaHooks;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.stone.BiomeStoneSurface;
import com.eternalreturn.worldgen.stone.HardenedBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Biome stone, in an Eternal Return world with a twin dimension (same generator, same seed): squares of
 * chunks in every biome of the preset are generated to the carving step here with biome stone on and in
 * the twin with it off. Every block that differs must be plain stone in the twin and exactly its biome's
 * stone here (no blending, so topsoil, beaches, badlands bands, bedrock and caves are untouched); nearly
 * all of each mapped biome's stone must have changed; and caves must cut through every host stone.
 * The one allowed exception: a bare-rock patch at the surface (Moderner Beta strips the topsoil there)
 * takes the host stone it sits on, which on a biome border can belong to the neighbouring biome.
 * The biome is the one surface rules see (SurfaceBiomes). Diagnostic: -Deternalreturn.gametest.biomeStoneTwinOn=true
 * keeps biome stone on in the twin too, so any difference left is not biome stone's doing.
 */
public class BiomeStoneTests implements FabricGameTest {
	public static final String BIOME_STONE_BATCH = "biome_stone";
	private static final int AWAY_FROM_SPAWN = 384;

	/** The stone each biome should get (the default table in tools/worldgen/knobs.json); any other biome keeps stone. */
	public static Map<String, Block> expectedTable() {
		Map<String, Block> table = new LinkedHashMap<>();
		for (String biome : List.of("moderner_beta:late_beta_plains", "minecraft:ocean", "minecraft:river", "minecraft:beach",
				"minecraft:mushroom_fields", "moderner_beta:early_release_swampland", "minecraft:dark_forest")) {
			table.put(biome, Blocks.STONE);
		}
		table.put("minecraft:forest", Blocks.ANDESITE);
		table.put("moderner_beta:early_release_taiga", Blocks.ANDESITE);
		table.put("moderner_beta:early_release_extreme_hills", Blocks.ANDESITE);
		table.put("minecraft:birch_forest", Blocks.DIORITE);
		table.put("minecraft:jungle", Blocks.GRANITE);
		table.put("minecraft:savanna", Blocks.GRANITE);
		table.put("minecraft:desert", HardenedBlocks.get("hardened_sandstone"));
		table.put("minecraft:badlands", Blocks.RED_SANDSTONE);
		for (String biome : List.of("moderner_beta:early_release_ice_plains", "moderner_beta:late_beta_ice_plains", "minecraft:snowy_plains",
				"minecraft:frozen_ocean", "minecraft:frozen_river")) {
			table.put(biome, HardenedBlocks.get("hardened_packed_ice"));
		}
		return table;
	}

	private static final class Tally {
		long twinStone;
		long changed;
		long columns;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BIOME_STONE_BATCH, tickLimit = 2_000_000)
	public void biomeStoneReplacesStoneOnly(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerWorld twin = world.getServer().getWorld(CaveTests.TWIN);
		ctx.assertTrue(twin != null, "twin dimension missing (run with preset eternalreturn_test:eternal_return_twin)");
		Map<String, Block> table = expectedTable();
		SurfaceBiomes biomes = new SurfaceBiomes(world);

		Map<String, Tally> tallies = new TreeMap<>();
		Map<String, Long> caveContact = new TreeMap<>();
		Map<String, String> squares = new LinkedHashMap<>();
		List<String> wrong = new ArrayList<>();
		long wrongCount = 0;
		// Moderner Beta's bare-rock patches take the host stone they sit on, which on a biome border can be the neighbour's.
		long bareRockOnBorder = 0;
		List<String> preexisting = new ArrayList<>();
		Map<String, Integer> wrongByChunk = new TreeMap<>();
		long bedrockHere = 0;
		long bedrockTwin = 0;
		EternalReturnConfig.WorldgenTweaks worldgen = EternalReturnConfig.get().worldgen;
		for (String biome : table.keySet()) {
			// Away from spawn, whose chunks are already fully generated (ores, plants) before the tests start.
			ChunkPos centre = OreCensusTests.findChunkInside(world, biome, AWAY_FROM_SPAWN / 64);
			int half = 2;
			if (centre == null) {
				centre = findChunkWith(world, biome, AWAY_FROM_SPAWN / 32);
				half = 1;
			}
			if (centre == null) {
				squares.put(biome, "not found within 4000 blocks");
				continue;
			}
			squares.put(biome, (centre.getStartX() + 8) + ", " + (centre.getStartZ() + 8));
			List<Chunk[]> pairs = new ArrayList<>();
			boolean was = worldgen.biomeStone;
			try {
				worldgen.biomeStone = true;
				List<Chunk> here = new ArrayList<>();
				for (int cx = centre.x - half; cx <= centre.x + half; cx++) {
					for (int cz = centre.z - half; cz <= centre.z + half; cz++) {
						Chunk before = world.getChunkManager().getChunk(cx, cz, ChunkStatus.EMPTY, false);
						Chunk twinBefore = twin.getChunkManager().getChunk(cx, cz, ChunkStatus.EMPTY, false);
						if (before != null || twinBefore != null) {
							preexisting.add(cx + "," + cz + " here " + (before == null ? "-" : before.getStatus()) + " twin " + (twinBefore == null ? "-" : twinBefore.getStatus()));
						}
						here.add(world.getChunk(cx, cz, ChunkStatus.CARVERS, true));
					}
				}
				worldgen.biomeStone = Boolean.getBoolean("eternalreturn.gametest.biomeStoneTwinOn");
				for (Chunk chunk : here) {
					pairs.add(new Chunk[]{chunk, twin.getChunk(chunk.getPos().x, chunk.getPos().z, ChunkStatus.CARVERS, true)});
				}
			} finally {
				worldgen.biomeStone = was;
			}
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (Chunk[] pair : pairs) {
				Chunk chunk = pair[0];
				Chunk twinChunk = pair[1];
				ChunkPos chunkPos = chunk.getPos();
				for (int x = chunkPos.getStartX(); x < chunkPos.getStartX() + 16; x++) {
					for (int z = chunkPos.getStartZ(); z < chunkPos.getStartZ() + 16; z++) {
						String columnBiome = id(biomes.at(pos.set(x, 64, z)));
						tallies.computeIfAbsent(columnBiome, key -> new Tally()).columns++;
						for (int y = world.getBottomY(); y < world.getTopY(); y++) {
							pos.set(x, y, z);
							BlockState state = chunk.getBlockState(pos);
							BlockState twinState = twinChunk.getBlockState(pos);
							if (state.isOf(Blocks.BEDROCK)) {
								bedrockHere++;
							}
							if (twinState.isOf(Blocks.BEDROCK)) {
								bedrockTwin++;
							}
							boolean twinStone = twinState.isOf(Blocks.STONE);
							if (!twinStone && state == twinState) {
								continue;
							}
							Block expected = twinStone ? table.getOrDefault(id(biomes.at(pos)), Blocks.STONE) : null;
							if (twinStone) {
								Tally tally = tallies.computeIfAbsent(id(biomes.at(pos)), key -> new Tally());
								tally.twinStone++;
								if (state.isOf(expected) && expected != Blocks.STONE) {
									tally.changed++;
								}
							}
							boolean bareRock = twinStone && state.isIn(BiomeStoneSurface.HOST_STONES) && chunk.getBlockState(pos.down()).isOf(state.getBlock())
									&& y >= chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x & 15, z & 15) - 8;
							if (state != twinState && (expected == null || !state.isOf(expected)) && bareRock) {
								bareRockOnBorder++;
							} else if (state != twinState && (expected == null || !state.isOf(expected))) {
								wrongCount++;
								wrongByChunk.merge((x >> 4) + "," + (z >> 4), 1, Integer::sum);
								if (wrong.size() < 12) {
									wrong.add(x + " " + y + " " + z + " " + id(biomes.at(pos)) + ": twin " + name(twinState.getBlock()) + ", here " + name(state.getBlock()));
								}
							}
							if (state.isOf(expected) && expected != Blocks.STONE && y < 40) {
								for (Direction direction : Direction.values()) {
									BlockPos side = pos.offset(direction);
									if (side.getX() >> 4 == chunkPos.x && side.getZ() >> 4 == chunkPos.z && chunk.getBlockState(side).isAir()) {
										caveContact.merge(name(expected), 1L, Long::sum);
										break;
									}
								}
							}
						}
					}
				}
			}
		}

		Map<String, String> shares = new LinkedHashMap<>();
		List<String> low = new ArrayList<>();
		tallies.forEach((biome, tally) -> {
			Block expected = table.getOrDefault(biome, Blocks.STONE);
			if (expected == Blocks.STONE || tally.twinStone == 0) {
				shares.put(biome, name(expected) + (tally.changed > 0 ? " (" + tally.changed + " changed!)" : "") + " | " + tally.columns + " columns");
				return;
			}
			double share = tally.changed / (double) tally.twinStone;
			shares.put(biome, name(expected) + " " + String.format("%.3f%%", share * 100) + " of " + tally.twinStone + " stone | " + tally.columns + " columns");
			if (tally.twinStone > 2000 && share < 0.98) {
				low.add(biome + " " + String.format("%.3f", share));
			}
		});
		EternalReturn.LOGGER.info("[biome-stone] squares {} (per-block biomes from Moderner Beta: {})", squares, biomes.usesModernerBeta());
		EternalReturn.LOGGER.info("[biome-stone] per biome (share of the twin's stone that changed): {}", shares);
		EternalReturn.LOGGER.info("[biome-stone] chunks that existed before the test: {} | wrong blocks by chunk {}", preexisting, wrongByChunk);
		EternalReturn.LOGGER.info("[biome-stone] bare-rock blocks on a biome border taking the stone below: {}", bareRockOnBorder);
		EternalReturn.LOGGER.info("[biome-stone] wrong blocks {} e.g. {} | bedrock {} vs twin {} | cave air touching each host stone below y=40: {}",
				wrongCount, wrong, bedrockHere, bedrockTwin, caveContact);

		ctx.assertTrue(wrongCount == 0, wrongCount + " blocks changed other than stone into its biome's stone, e.g. " + wrong);
		ctx.assertTrue(bedrockHere == bedrockTwin && bedrockHere > 0, "bedrock changed: " + bedrockHere + " vs " + bedrockTwin);
		ctx.assertTrue(low.isEmpty(), "biomes whose stone is mostly unchanged: " + low);
		for (Block hostStone : List.of(Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.RED_SANDSTONE,
				HardenedBlocks.get("hardened_sandstone"), HardenedBlocks.get("hardened_packed_ice"))) {
			ctx.assertTrue(caveContact.getOrDefault(name(hostStone), 0L) > 0, "no cave cuts through " + name(hostStone));
		}
		for (String biome : List.of("minecraft:forest", "minecraft:birch_forest", "minecraft:jungle", "minecraft:desert", "minecraft:badlands",
				"moderner_beta:early_release_ice_plains")) {
			ctx.assertTrue(tallies.containsKey(biome) && tallies.get(biome).twinStone > 2000, "too little of " + biome + " sampled");
		}
		ctx.complete();
	}

	/**
	 * The hooks into Moderner Beta's surface pass (com.eternalreturn.mixin.compat) are applied whenever
	 * Moderner Beta is installed, and run when an Eternal Return chunk's surface is built. If Moderner Beta
	 * changes and a hook's target goes missing, the game still starts (the hook is skipped with a warning),
	 * and this test fails loudly.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.ETERNAL_RETURN_BATCH, tickLimit = 100_000)
	public void modernerBetaHooksApplied(TestContext ctx) {
		if (!GameTestWorld.isModernerBetaLoaded()) {
			ctx.complete();
			return;
		}
		ctx.assertTrue(Boolean.TRUE.equals(ModernerBetaHooks.APPLIED.get("ModernerBetaSurfaceMixin"))
						&& Boolean.TRUE.equals(ModernerBetaHooks.APPLIED.get("ModernerBetaSurfaceExtraMixin")),
				"a hook into Moderner Beta's surface pass was not applied (its target changed?): " + ModernerBetaHooks.APPLIED);
		long passes = ModernerBetaHooks.SURFACE_PASSES.get();
		long checks = ModernerBetaHooks.SUITABLE_CHECKS.get();
		for (int cx = 0; cx < 3; cx++) {
			ctx.getWorld().getChunk(7000 + cx, 7000, ChunkStatus.SURFACE, true);
		}
		EternalReturn.LOGGER.info("[biome-stone] Moderner Beta hooks applied {}; surface passes {} -> {}, surface checks {} -> {}",
				ModernerBetaHooks.APPLIED, passes, ModernerBetaHooks.SURFACE_PASSES.get(), checks, ModernerBetaHooks.SUITABLE_CHECKS.get());
		ctx.assertTrue(ModernerBetaHooks.SURFACE_PASSES.get() >= passes + 3, "the surface-pass hook did not run for new chunks");
		ctx.assertTrue(ModernerBetaHooks.SUITABLE_CHECKS.get() > checks, "the surface-check hook did not run for new chunks");
		ctx.complete();
	}

	/** The chunk of the first column in the biome, searched outward from 0,0 on a 32-block grid (for narrow biomes such as rivers). */
	static ChunkPos findChunkWith(ServerWorld world, String biome, int firstRing) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		for (int ring = firstRing; ring < 125; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					int x = dx * 32;
					int z = dz * 32;
					if (id(generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(64), BiomeCoords.fromBlock(z),
							noise.getMultiNoiseSampler())).equals(biome)) {
						return new ChunkPos(x >> 4, z >> 4);
					}
				}
			}
		}
		return null;
	}

	static String id(RegistryEntry<Biome> biome) {
		return biome.getKey().map(key -> key.getValue().toString()).orElse("?");
	}

	static String name(Block block) {
		return Registries.BLOCK.getId(block).getPath();
	}
}
