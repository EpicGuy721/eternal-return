package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.trees.BiomeTrees;
import com.eternalreturn.worldgen.variants.BiomeVariants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Biome variants (com.eternalreturn.worldgen.variants, tools/worldgen/variants.json): peaks, edges and rivers turn up in
 * generated land, peaks only on high ground, river variants only on water; every variant has its base's terrain height
 * and joins its base's tags, stone and trees.
 */
public class BiomeVariantTests implements FabricGameTest {
	public static final String BATCH = "biome_variants";

	private static RegistryEntry<Biome> biome(ServerWorld world, String id) {
		Registry<Biome> biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		return biomes.getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier.of(id))).orElseThrow();
	}

	static JsonObject variantTable() {
		try (var in = BiomeVariantTests.class.getResourceAsStream("/eternalreturn/biome_variants.json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Around up to twelve land spots (a grid every 512 blocks out to 1,536), 5 x 5 chunks each are generated in full and
	 * the biome on the ground read every 4 blocks. Peaks, edges and rivers must each turn up; a peak only at y 84 or
	 * higher (every peak's height is 90 or more, less a 4-block biome cell and the 8 blocks the rule allows); a river
	 * variant only on water.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void variantsTurnUp(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Map<String, Integer> found = new TreeMap<>();
		int spots = 0;
		int lowPeaks = 0;
		int dryRivers = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int gx = -3; gx <= 3 && spots < 12; gx++) {
			for (int gz = -3; gz <= 3 && spots < 12; gz++) {
				int x0 = gx * 512 + 136;
				int z0 = gz * 512 + 136;
				int top = generator.getHeight(x0, z0, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
				if (top <= generator.getSeaLevel() + 2) {
					continue;
				}
				spots++;
				ChunkPos centre = new ChunkPos(x0 >> 4, z0 >> 4);
				for (int cx = centre.x - 2; cx <= centre.x + 2; cx++) {
					for (int cz = centre.z - 2; cz <= centre.z + 2; cz++) {
						world.getChunk(cx, cz, ChunkStatus.FULL, true);
						for (int x = cx * 16; x < cx * 16 + 16; x += 4) {
							for (int z = cz * 16; z < cz * 16 + 16; z += 4) {
								int y = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
								RegistryEntry<Biome> here = world.getBiome(pos.set(x, y, z));
								BiomeVariants.Variant variant = BiomeVariants.get(here);
								if (variant == null) {
									continue;
								}
								found.merge(BiomeStoneTests.id(here), 1, Integer::sum);
								if (variant.kind().equals("peak") && y < 84) {
									lowPeaks++;
								}
								if (variant.kind().equals("river") && world.getFluidState(pos.set(x, world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1, z)).isEmpty()
										&& world.getFluidState(pos.set(x, y, z)).isEmpty()) {
									dryRivers++;
								}
							}
						}
					}
				}
			}
		}
		EternalReturn.LOGGER.info("[variants] {} land spots; variants on the ground (columns): {}; peaks below y 84: {}; river variants off water: {}",
				spots, found, lowPeaks, dryRivers);
		long peaks = found.keySet().stream().filter(id -> BiomeVariants.get(Identifier.of(id)).kind().equals("peak")).count();
		long edges = found.keySet().stream().filter(id -> BiomeVariants.get(Identifier.of(id)).kind().equals("edge")).count();
		long rivers = found.keySet().stream().filter(id -> BiomeVariants.get(Identifier.of(id)).kind().equals("river")).count();
		ctx.assertTrue(peaks >= 1 && edges >= 2 && rivers >= 2, peaks + " peak, " + edges + " edge and " + rivers + " river variants found: " + found);
		ctx.assertTrue(lowPeaks == 0, lowPeaks + " peak columns below y 84");
		int riverColumns = found.entrySet().stream().filter(e -> BiomeVariants.get(Identifier.of(e.getKey())).kind().equals("river")).mapToInt(Map.Entry::getValue).sum();
		ctx.assertTrue(dryRivers * 4 <= riverColumns, dryRivers + " of " + riverColumns + " river variant columns are off water");
		ctx.complete();
	}

	/**
	 * Every variant in the table exists as a biome, has its base's terrain height in the preset (so the land doesn't
	 * change), shares its base's tags (a forest peak is a forest, a sandy river a river) and stone, and grows its base's
	 * trees unless a table names it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void variantsFollowTheirBase(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		JsonObject heights;
		try (var in = world.getServer().getResourceManager().getResource(EternalReturn.id("moderner_beta/settings_preset/eternal_return.json")).orElseThrow().getReader()) {
			heights = JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("chunkSettings")
					.getAsJsonObject("moderner_beta:forced_biome_height").getAsJsonObject("heightOverrides");
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		for (String id : variantTable().keySet()) {
			BiomeVariants.Variant variant = BiomeVariants.get(Identifier.of(id));
			biome(world, id);
			if (!variant.kind().equals("peak")) {
				String base = variant.base().toString();
				// (Neither listed, as the beach and the palm beach: both take their height from Moderner Beta's height tags, which a variant joins.)
				ctx.assertTrue(java.util.Objects.equals(heights.get(id), heights.get(base)),
						id + " has height " + heights.get(id) + ", its base " + base + " " + heights.get(base));
			}
		}
		ctx.assertTrue(biome(world, "eternalreturn:forest_peak").isIn(BiomeTags.IS_FOREST), "the forest peak isn't a forest");
		ctx.assertTrue(biome(world, "eternalreturn:sandy_river").isIn(BiomeTags.IS_RIVER), "the sandy river isn't a river");
		ctx.assertTrue(biome(world, "eternalreturn:forest_dry_edge").isIn(TagKey.of(RegistryKeys.BIOME, EternalReturn.id("biome_stone/andesite"))),
				"the dry forest edge doesn't have the forest's andesite");
		List<BiomeTrees.Choice> junglePeak = BiomeTrees.listFor(biome(world, "eternalreturn:jungle_peak"), Identifier.ofVanilla("jungle_sapling"), false);
		List<BiomeTrees.Choice> jungle = BiomeTrees.listFor(biome(world, "minecraft:jungle"), Identifier.ofVanilla("jungle_sapling"), false);
		ctx.assertTrue(junglePeak != null && junglePeak.equals(jungle), "the jungle peak doesn't grow the jungle's trees");
		List<BiomeTrees.Choice> dryEdge = BiomeTrees.listFor(biome(world, "eternalreturn:forest_dry_edge"), Identifier.ofVanilla("oak_sapling"), false);
		ctx.assertTrue(dryEdge != null && dryEdge.stream().anyMatch(c -> BiomeTrees.name(c).equals("eternalreturn:tree/scrub_oak")),
				"the dry forest edge doesn't use its own table: " + dryEdge);
		ctx.complete();
	}
}
