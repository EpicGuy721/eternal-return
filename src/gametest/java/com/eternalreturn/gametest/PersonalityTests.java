package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.personality.Personality;
import com.eternalreturn.worldgen.personality.PersonalityFeatures;
import com.eternalreturn.worldgen.personality.PersonalitySync;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Biome personality rolls (worldgen expansion 2, phase 7: Personality): the rolls stay inside their limits (density
 * within a quarter of normal, one rare patch in 40, two to four of a biome's own flowers, the signature tree at least half,
 * a small tint that fades from patch to patch), the features they act on are told apart, nothing changes outside
 * Eternal Return worlds, and in an Eternal Return world every flower is one of its patch's palette.
 */
public class PersonalityTests implements FabricGameTest {
	public static final String BATCH = "personality";
	public static final String RULES_BATCH = "personality_rules";

	/** The rolls over 40,000 cells of three families keep to their limits. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = RULES_BATCH)
	public void rollsStayInsideTheirLimits(TestContext ctx) {
		long salt = Personality.salt(173164L);
		List<String> six = List.of("minecraft:allium", "minecraft:azure_bluet", "minecraft:cornflower", "minecraft:dandelion", "minecraft:oxeye_daisy", "minecraft:poppy");
		List<String> two = List.of("minecraft:dandelion", "minecraft:poppy");
		int rare = 0;
		int cells = 0;
		Set<List<String>> palettes = new HashSet<>();
		double lowest = 2;
		double highest = 0;
		for (String family : List.of("minecraft:forest", "minecraft:plains", "eternalreturn:redwood_forest")) {
			for (int cx = -50; cx < 50; cx++) {
				for (int cz = -67; cz < 67; cz++) {
					Personality.Roll roll = Personality.at(salt, Identifier.of(family), cx << Personality.CELL_SHIFT, cz << Personality.CELL_SHIFT);
					cells++;
					rare += roll.rare() ? 1 : 0;
					lowest = Math.min(lowest, roll.density());
					highest = Math.max(highest, roll.density());
					List<String> palette = roll.palette(six);
					ctx.assertTrue(six.containsAll(palette) && (roll.rare() ? palette.size() == 6 : palette.size() >= 2 && palette.size() <= 4),
							"a palette of two to four of the six: " + palette);
					palettes.add(palette);
					ctx.assertTrue(roll.palette(two).equals(two), "a biome with two flowers keeps both");
					double[] weights = roll.canopy(List.of("vanilla", "eternalreturn:tree/white_oak", "eternalreturn:tree/bur_oak"), new int[]{6, 2, 2});
					ctx.assertTrue(weights[0] / (weights[0] + weights[1] + weights[2]) >= 0.5 - 1e-9, "the signature tree stays at least half");
					ctx.assertTrue(weights[1] > 0 && weights[2] > 0, "no tree of the table drops out");
				}
			}
		}
		ctx.assertTrue(lowest >= 0.75 - 1e-9 && highest <= 1.25 + 1e-9 && lowest < 0.8 && highest > 1.2, "density from 0.75 to 1.25: " + lowest + " to " + highest);
		double share = rare / (double) cells;
		ctx.assertTrue(share > 1 / 60.0 && share < 1 / 28.0, "about one rare patch in 40: " + rare + " of " + cells);
		ctx.assertTrue(palettes.size() >= 10, "patches pick different palettes: " + palettes.size());
		ctx.assertTrue(Personality.family(EternalReturn.id("forest_peak")).equals(Identifier.of("minecraft:forest")), "a peak rolls as its base");
		ctx.complete();
	}

	/** The tint stays near the biome's colour and fades smoothly from patch to patch (no seams). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = RULES_BATCH)
	public void theTintIsSmallAndSmooth(TestContext ctx) {
		long salt = Personality.salt(173164L);
		Identifier plains = Identifier.of("minecraft:plains");
		int grass = 0x91BD59;
		int most = 0;
		int step = 0;
		Set<Integer> colours = new HashSet<>();
		for (int x = -2048; x < 2048; x += 3) {
			int z = x * 7 / 5;
			int here = Personality.tint(salt, plains, grass, x, z);
			int next = Personality.tint(salt, plains, grass, x + 1, z);
			colours.add(here);
			for (int shift = 0; shift <= 16; shift += 8) {
				most = Math.max(most, Math.abs((here >> shift & 255) - (grass >> shift & 255)));
				step = Math.max(step, Math.abs((here >> shift & 255) - (next >> shift & 255)));
			}
		}
		// What a section redraw pays: a million calls over a few patches (logged).
		long start = System.nanoTime();
		int sink = 0;
		for (int i = 0; i < 1_000_000; i++) {
			sink ^= Personality.tint(salt, plains, grass, (i % 1000) + 0.5, (i / 1000) + 0.5);
		}
		EternalReturn.LOGGER.info("Personality tint: {} ns a call ({})", (System.nanoTime() - start) / 1_000_000, sink);
		ctx.assertTrue(most <= 24, "the tint stays close to the biome's colour: up to " + most + " a channel");
		ctx.assertTrue(most >= 6, "but shifts it: up to " + most + " a channel");
		ctx.assertTrue(step <= 2, "neighbouring blocks differ by at most 2 a channel: " + step);
		ctx.assertTrue(colours.size() > 50, "many shades across patches: " + colours.size());
		ctx.complete();
	}

	/** Trees, grass patches and boulders are told apart from the rest (flowers among them); a biome's flowers are found. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = RULES_BATCH)
	public void theFeaturesAreToldApart(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		var placed = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE);
		Map<String, String> expected = Map.of("minecraft:trees_plains", "trees", "minecraft:trees_birch_and_oak", "trees", "minecraft:patch_grass_plain", "grass",
				"minecraft:patch_tall_grass", "grass", "minecraft:flower_default", "none", "minecraft:patch_sugar_cane", "none", "eternalreturn:boulder", "boulders");
		for (Map.Entry<String, String> entry : expected.entrySet()) {
			PlacedFeature feature = placed.get(RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(entry.getKey())));
			ctx.assertTrue(feature != null, entry.getKey() + " exists");
			ctx.assertTrue(PersonalityFeatures.kind(feature).equals(entry.getValue()), entry.getKey() + " is " + entry.getValue() + ": " + PersonalityFeatures.kind(feature));
		}
		Biome plains = world.getRegistryManager().get(RegistryKeys.BIOME).getOrThrow(BiomeKeys.PLAINS);
		List<String> small = PersonalityFeatures.flowersOfBiome(plains).get(0);
		ctx.assertTrue(small.containsAll(List.of("minecraft:dandelion", "minecraft:poppy", "minecraft:oxeye_daisy", "minecraft:cornflower")) && small.size() >= 6,
				"the plains' own flowers: " + small);
		Biome flowerForest = world.getRegistryManager().get(RegistryKeys.BIOME).getOrThrow(BiomeKeys.FLOWER_FOREST);
		ctx.assertTrue(PersonalityFeatures.flowersOfBiome(flowerForest).get(1).contains("minecraft:lilac"), "the flower forest's tall flowers: "
				+ PersonalityFeatures.flowersOfBiome(flowerForest).get(1));
		ctx.complete();
	}

	/** Outside Eternal Return worlds there are no rolls: no salt sent, no tint. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = RULES_BATCH)
	public void noRollsInOtherWorlds(TestContext ctx) {
		ctx.assertTrue(PersonalitySync.salt(ctx.getWorld().getServer()) == 0, "no salt in the flat world");
		ctx.complete();
	}

	/**
	 * In an Eternal Return world: the world has a salt, and every flower on the land around up to eight spots on the test
	 * seed is one of its patch's palette of its biome's own flowers.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void flowersFollowTheirPatch(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		long salt = PersonalitySync.salt(world.getServer());
		ctx.assertTrue(salt != 0, "an Eternal Return world has a salt");
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		List<ChunkPos> centres = new ArrayList<>();
		// Spots in biomes with three or more flowers of their own (where a palette leaves some out), at most two of a biome.
		Map<String, Integer> perBiome = new TreeMap<>();
		for (int gx = -10; gx <= 10 && centres.size() < 10; gx++) {
			for (int gz = -10; gz <= 10 && centres.size() < 10; gz++) {
				int x = gx * 320 + 8;
				int z = gz * 320 + 8;
				int top = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
				RegistryEntry<Biome> biome = generator.getBiomeSource().getBiome(x >> 2, top >> 2, z >> 2, noise.getMultiNoiseSampler());
				String key = biome.getKey().map(k -> k.getValue().toString()).orElse("?");
				if (top > generator.getSeaLevel() + 2 && PersonalityFeatures.flowersOfBiome(biome.value()).get(0).size() >= 3 && perBiome.getOrDefault(key, 0) < 2) {
					perBiome.merge(key, 1, Integer::sum);
					centres.add(new ChunkPos(x >> 4, z >> 4));
				}
			}
		}
		ctx.assertTrue(centres.size() >= 3, "only " + centres.size() + " spots in flowery biomes: " + perBiome);
		int flowers = 0;
		List<String> wrong = new ArrayList<>();
		Map<String, Set<List<String>>> palettesByFamily = new TreeMap<>();
		Map<String, Integer> seen = new TreeMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (ChunkPos centre : centres) {
			for (int cx = centre.x - 1; cx <= centre.x + 1; cx++) {
				for (int cz = centre.z - 1; cz <= centre.z + 1; cz++) {
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
				}
			}
			for (int x = centre.getStartX() - 8; x < centre.getStartX() + 24; x++) {
				for (int z = centre.getStartZ() - 8; z < centre.getStartZ() + 24; z++) {
					int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
					for (int y = top; y >= top - 2; y--) {
						BlockState state = world.getBlockState(pos.set(x, y, z));
						boolean small = state.isIn(BlockTags.SMALL_FLOWERS);
						if (!small && !state.isIn(BlockTags.TALL_FLOWERS) || state.contains(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF)
								&& state.get(net.minecraft.state.property.Properties.DOUBLE_BLOCK_HALF) == net.minecraft.block.enums.DoubleBlockHalf.UPPER) {
							continue;
						}
						RegistryEntry<Biome> biome = world.getBiome(pos);
						Identifier id = biome.getKey().orElseThrow().getValue();
						Personality.Roll roll = Personality.at(salt, id, x, z);
						List<String> own = PersonalityFeatures.flowersOfBiome(biome.value()).get(small ? 0 : 1);
						String flower = Registries.BLOCK.getId(state.getBlock()).toString();
						seen.merge(id + " " + flower + (own.contains(flower) ? "" : " (not own: " + own + ")"), 1, Integer::sum);
						if (!own.contains(flower)) {
							// Not its biome's own (from a neighbour's patch over the border, or planted by something else): no palette.
							continue;
						}
						flowers++;
						List<String> palette = roll.palette(own);
						palettesByFamily.computeIfAbsent(roll.family().toString(), key -> new HashSet<>()).add(palette);
						if (!palette.contains(flower)) {
							wrong.add(flower + " at " + pos.toShortString() + " in " + id + " (palette " + palette + ")");
						}
					}
				}
			}
		}
		EternalReturn.LOGGER.info("Personality: {} flowers checked around {} spots ({}); palettes by family {}; seen {}", flowers, centres.size(), perBiome, palettesByFamily, seen);
		ctx.assertTrue(flowers >= 20, "only " + flowers + " flowers found around " + centres.size() + " spots");
		ctx.assertTrue(wrong.isEmpty(), wrong.size() + " flowers outside their patch's palette, first " + wrong.subList(0, Math.min(5, wrong.size())));
		ctx.complete();
	}

	/** The accent table: every accent a known one, every biome a real one (Moderner Beta's where it's loaded), two to five accents each. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = RULES_BATCH)
	public void theAccentTableIsSound(TestContext ctx) {
		var biomes = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
		Map<String, List<String>> table = com.eternalreturn.worldgen.personality.AccentFeature.table();
		ctx.assertTrue(table.size() >= 70, "the table lists every land biome and sea: " + table.size());
		for (Map.Entry<String, List<String>> entry : table.entrySet()) {
			Identifier id = Identifier.of(entry.getKey());
			// (Without Moderner Beta, its biomes and the sub-biomes of them are missing.)
			ctx.assertTrue(biomes.containsId(id) || !GameTestWorld.isModernerBetaLoaded() || id.getPath().startsWith("deep_"), entry.getKey() + " is a biome");
			ctx.assertTrue(!entry.getValue().isEmpty() && entry.getValue().size() <= 5, entry.getKey() + " has one to five accents: " + entry.getValue());
			for (String accent : entry.getValue()) {
				String name = accent.contains(":") ? accent.substring(0, accent.indexOf(':')) : accent;
				ctx.assertTrue(com.eternalreturn.worldgen.personality.AccentFeature.NAMES.contains(name), entry.getKey() + ": unknown accent " + accent);
				if (accent.contains(":")) {
					ctx.assertTrue(Registries.BLOCK.containsId(Identifier.of(accent.substring(accent.indexOf(':') + 1))), entry.getKey() + ": no block in " + accent);
				}
			}
		}
		ctx.complete();
	}

	/**
	 * In an Eternal Return world: generating land and sea round the test seed, accents appear, several kinds of them, and
	 * every one is the accent its patch rolled from its biome's list.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void accentsFollowTheirPatch(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		long salt = PersonalitySync.salt(world.getServer());
		com.eternalreturn.worldgen.personality.AccentFeature.RECENT.clear();
		// Eight times as often as in a world, so a few hundred chunks show enough of them.
		com.eternalreturn.worldgen.personality.AccentFeature.boost = 8;
		try {
			for (int gx = -2; gx <= 2; gx++) {
				for (int gz = -2; gz <= 2; gz++) {
					ChunkPos centre = new ChunkPos(gx * 40 - 100, gz * 40);
					for (int cx = centre.x - 1; cx <= centre.x + 1; cx++) {
						for (int cz = centre.z - 1; cz <= centre.z + 1; cz++) {
							world.getChunk(cx, cz, ChunkStatus.FULL, true);
						}
					}
				}
			}
		} finally {
			com.eternalreturn.worldgen.personality.AccentFeature.boost = 1;
		}
		List<String> placed = new ArrayList<>(com.eternalreturn.worldgen.personality.AccentFeature.RECENT);
		Map<String, Integer> kinds = new TreeMap<>();
		List<String> wrong = new ArrayList<>();
		for (String line : placed) {
			String[] parts = line.split(" ");
			Identifier biome = Identifier.of(parts[0]);
			int x = Integer.parseInt(parts[2]);
			int z = Integer.parseInt(parts[3]);
			String rolled = com.eternalreturn.worldgen.personality.AccentFeature.accentOf(Personality.at(salt, biome, x, z), biome);
			if (!parts[1].equals(rolled)) {
				wrong.add(line + " (rolled " + rolled + ")");
			}
			String name = parts[1].contains(":") ? parts[1].substring(0, parts[1].indexOf(':')) : parts[1];
			kinds.merge(name, 1, Integer::sum);
		}
		EternalReturn.LOGGER.info("Personality accents: {} placed, by kind {}", placed.size(), kinds);
		EternalReturn.LOGGER.info("Personality accents placed: {}", placed);
		ctx.assertTrue(placed.size() >= 15, "only " + placed.size() + " accents round 25 spots: " + kinds);
		ctx.assertTrue(kinds.size() >= 6, "only " + kinds.size() + " kinds of accent: " + kinds);
		ctx.assertTrue(wrong.isEmpty(), wrong.size() + " accents not their patch's: " + wrong.subList(0, Math.min(5, wrong.size())));
		ctx.complete();
	}
}
