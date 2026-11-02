package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.trees.BiomeTrees;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.registry.Registry;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Biome tree tables (com.eternalreturn.worldgen.trees): every species grows, saplings follow their biome's table (and
 * vanilla where there is none), a template tree's leaves decay once it is cut, generated trees come from the tables,
 * and other worlds keep vanilla's saplings.
 */
public class BiomeTreeTests implements FabricGameTest {
	public static final String BATCH = "biome_trees";
	private static final Set<String> MEGA = Set.of("eternalreturn:tree/redwood", "eternalreturn:tree/kapok", "eternalreturn:tree/ancient_dark_oak");

	/** A 6 x 6 grass pad with dirt under it, its north-west corner at pos's column, top at pos's height minus one. */
	private static void pad(ServerWorld world, BlockPos pos) {
		for (int x = 0; x < 6; x++) {
			for (int z = 0; z < 6; z++) {
				world.setBlockState(pos.add(x - 2, -2, z - 2), Blocks.DIRT.getDefaultState());
				world.setBlockState(pos.add(x - 2, -1, z - 2), Blocks.GRASS_BLOCK.getDefaultState());
			}
		}
	}

	private static int[] count(ServerWorld world, BlockPos pos) {
		int logs = 0;
		int leaves = 0;
		for (BlockPos p : BlockPos.iterate(pos.add(-14, 0, -14), pos.add(14, 50, 14))) {
			BlockState state = world.getBlockState(p);
			if (state.isIn(BlockTags.LOGS)) {
				logs++;
			} else if (state.getBlock() instanceof LeavesBlock) {
				leaves++;
			}
		}
		return new int[]{logs, leaves};
	}

	/** Each species and template (palms, larches, willow, baobab), grown on a grass pad high in the sky, makes logs and leaves. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void everySpeciesGrows(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).withY(200);
		List<BiomeTrees.Choice> species = new ArrayList<>();
		for (Identifier id : new TreeSet<>(world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).getIds())) {
			if (id.getNamespace().equals(EternalReturn.MOD_ID) && id.getPath().startsWith("tree/")) {
				species.add(new BiomeTrees.Choice(1, Optional.of(id), Optional.empty()));
			}
		}
		List<Identifier> templates = world.getServer().getStructureTemplateManager().streamTemplates()
				.filter(id -> id.getNamespace().equals(EternalReturn.MOD_ID) && id.getPath().startsWith("tree/")).sorted().toList();
		for (Identifier template : templates) {
			species.add(new BiomeTrees.Choice(1, Optional.empty(), Optional.of(template)));
		}
		ctx.assertTrue(species.size() >= 44 && templates.size() >= 10, species.size() + " species, " + templates.size() + " templates");
		Map<String, String> grown = new TreeMap<>();
		for (int i = 0; i < species.size(); i++) {
			BiomeTrees.Choice choice = species.get(i);
			BlockPos pos = base.add((i % 6) * 40, 0, (i / 6) * 40);
			world.getChunk(pos);
			pad(world, pos);
			String name = BiomeTrees.name(choice);
			boolean ok = false;
			for (int attempt = 0; attempt < 5 && !ok; attempt++) {
				ok = BiomeTrees.grow(choice, world, generator, Random.create(i * 31L + attempt), pos);
			}
			int[] blocks = count(world, pos);
			grown.put(name, blocks[0] + " logs, " + blocks[1] + " leaves" + (MEGA.contains(name) ? " (mega)" : ""));
			ctx.assertTrue(ok && blocks[0] > 0 && blocks[1] > 0, name + " didn't grow: " + blocks[0] + " logs, " + blocks[1] + " leaves");
		}
		EternalReturn.LOGGER.info("[trees] grown: {}", grown);
		ctx.complete();
	}

	/**
	 * No new tree drops leaves on its own: each species and template, grown 6 times (different random numbers), may have
	 * no leaf at the decay distance (LeafDistance.MAX, not persistent), as such a leaf decays at the next random tick. Vanilla's own
	 * trees are counted too, for comparison (logged, not checked).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "tree_leaves", tickLimit = 2_000)
	public void newTreesKeepTheirLeaves(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(0, 0, 1200).withY(200);
		List<BiomeTrees.Choice> species = new ArrayList<>();
		for (Identifier id : new TreeSet<>(world.getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).getIds())) {
			if (id.getNamespace().equals(EternalReturn.MOD_ID) && id.getPath().startsWith("tree/")) {
				species.add(new BiomeTrees.Choice(1, Optional.of(id), Optional.empty()));
			}
		}
		world.getServer().getStructureTemplateManager().streamTemplates()
				.filter(id -> id.getNamespace().equals(EternalReturn.MOD_ID) && id.getPath().startsWith("tree/")).sorted()
				.forEach(id -> species.add(new BiomeTrees.Choice(1, Optional.empty(), Optional.of(id))));
		for (String vanilla : List.of("oak", "fancy_oak", "spruce", "birch", "jungle_tree", "acacia", "cherry", "mangrove", "azalea_tree", "dark_oak", "mega_jungle_tree")) {
			species.add(new BiomeTrees.Choice(1, Optional.of(Identifier.ofVanilla(vanilla)), Optional.empty()));
		}
		Map<String, String> decaying = new TreeMap<>();
		Map<String, String> vanillaDecaying = new TreeMap<>();
		int i = 0;
		for (BiomeTrees.Choice choice : species) {
			int loose = 0;
			int total = 0;
			int cut = 0;
			int far = 0;
			for (int seed = 0; seed < 6; seed++, i++) {
				BlockPos pos = base.add((i % 12) * 40, 0, (i / 12) * 40);
				world.getChunk(pos);
				pad(world, pos);
				BiomeTrees.grow(choice, world, generator, Random.create(seed * 977L + i), pos);
				// The true distance of every leaf from a log, through leaves, with no limit (to see how far the loose ones are).
				Map<BlockPos, Integer> dist = new java.util.HashMap<>();
				java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
				for (BlockPos p : BlockPos.iterate(pos.add(-14, 0, -14), pos.add(14, 50, 14))) {
					if (world.getBlockState(p).isIn(BlockTags.LOGS)) {
						dist.put(p.toImmutable(), 0);
						queue.add(p.toImmutable());
					}
				}
				while (!queue.isEmpty()) {
					BlockPos p = queue.poll();
					for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
						BlockPos next = p.offset(d);
						if (!dist.containsKey(next) && world.getBlockState(next).getBlock() instanceof LeavesBlock) {
							dist.put(next, dist.get(p) + 1);
							queue.add(next);
						}
					}
				}
				for (BlockPos p : BlockPos.iterate(pos.add(-14, 0, -14), pos.add(14, 50, 14))) {
					BlockState state = world.getBlockState(p);
					if (state.getBlock() instanceof LeavesBlock) {
						total++;
						if (state.get(LeavesBlock.DISTANCE) >= com.eternalreturn.blocks.LeafDistance.MAX && !state.get(LeavesBlock.PERSISTENT)) {
							loose++;
							Integer d = dist.get(p.toImmutable());
							if (d == null) {
								cut++;
							} else {
								far = Math.max(far, d);
							}
						}
					}
				}
			}
			String name = BiomeTrees.name(choice);
			if (loose > 0) {
				(name.startsWith("minecraft:") ? vanillaDecaying : decaying).put(name, loose + " of " + total + " (farthest " + far + ", cut off " + cut + ")");
			}
		}
		EternalReturn.LOGGER.info("[leaves] decaying leaves over 6 trees each: new {}; vanilla {}", decaying, vanillaDecaying);
		ctx.assertTrue(decaying.isEmpty(), "new trees with leaves that decay: " + decaying);
		ctx.complete();
	}

	/** The scrub oak's crooked trunk never pokes out of its crown: over 20 trees, the top log always has leaves on it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void scrubOakTrunkIsCovered(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BiomeTrees.Choice scrub = new BiomeTrees.Choice(1, Optional.of(EternalReturn.id("tree/scrub_oak")), Optional.empty());
		for (int i = 0; i < 20; i++) {
			BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add((i % 5) * 16, 0, 600 + (i / 5) * 16).withY(200);
			world.getChunk(pos);
			pad(world, pos);
			ctx.assertTrue(BiomeTrees.grow(scrub, world, world.getChunkManager().getChunkGenerator(), Random.create(i), pos), "scrub oak " + i + " didn't grow");
			BlockPos top = null;
			for (BlockPos p : BlockPos.iterate(pos.add(-6, 0, -6), pos.add(6, 12, 6))) {
				if (world.getBlockState(p).isOf(Blocks.OAK_LOG) && (top == null || p.getY() > top.getY())) {
					top = p.toImmutable();
				}
			}
			ctx.assertTrue(top != null && world.getBlockState(top.up()).getBlock() instanceof LeavesBlock,
					"scrub oak " + i + ": the top log at " + top + " has " + (top == null ? "nothing" : world.getBlockState(top.up())) + " on it");
		}
		ctx.complete();
	}

	private static List<String> growOaks(ServerWorld world, BlockPos base, String biomeId, Block sapling, int count) {
		Registry<Biome> biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		RegistryEntry<Biome> biome = biomes.getEntry(net.minecraft.registry.RegistryKey.of(RegistryKeys.BIOME, Identifier.of(biomeId))).orElseThrow();
		BiomeTrees.setGrowthBiome((w, p) -> biome);
		List<String> names = new ArrayList<>();
		try {
			for (int i = 0; i < count; i++) {
				BlockPos pos = base.add((i % 6) * 24, 0, (i / 6) * 24);
				world.getChunk(pos);
				pad(world, pos);
				BlockState state = sapling.getDefaultState().with(SaplingBlock.STAGE, 1);
				world.setBlockState(pos, state);
				((SaplingBlock) sapling).generate(world, pos, state, Random.create(i * 7919L + biomeId.hashCode()));
				BiomeTrees.Choice choice = BiomeTrees.LAST_SAPLING_CHOICE.get();
				names.add(choice == null ? "none" : BiomeTrees.name(choice));
			}
		} finally {
			BiomeTrees.setGrowthBiome(ServerWorld::getBiome);
		}
		return names;
	}

	/**
	 * The same oak sapling grows from the swampland's table (vanilla, bald cypress, willow) in the swampland and from the
	 * savanna's (vanilla, scrub oak, shrub oak) in the savanna, each at least once something not vanilla; a spruce
	 * sapling in the plains, whose table has no spruce, grows as vanilla.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void saplingsFollowTheBiome(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos base = ctx.getAbsolutePos(BlockPos.ORIGIN).add(0, 0, 400).withY(200);
		List<String> swamp = growOaks(world, base, "moderner_beta:early_release_swampland", Blocks.OAK_SAPLING, 18);
		List<String> savanna = growOaks(world, base.add(200, 0, 0), "minecraft:savanna", Blocks.OAK_SAPLING, 18);
		List<String> plains = growOaks(world, base.add(400, 0, 0), "moderner_beta:late_beta_plains", Blocks.SPRUCE_SAPLING, 3);
		EternalReturn.LOGGER.info("[trees] oak saplings: swampland {}, savanna {}; spruce saplings in the plains {}", swamp, savanna, plains);
		Set<String> swampAllowed = Set.of("vanilla", "eternalreturn:tree/bald_cypress", "eternalreturn:tree/willow");
		Set<String> savannaAllowed = Set.of("vanilla", "eternalreturn:tree/scrub_oak", "eternalreturn:tree/shrub_oak");
		ctx.assertTrue(swampAllowed.containsAll(swamp) && swamp.stream().anyMatch(name -> !name.equals("vanilla")), "swampland oaks: " + swamp);
		ctx.assertTrue(savannaAllowed.containsAll(savanna) && savanna.stream().anyMatch(name -> !name.equals("vanilla")), "savanna oaks: " + savanna);
		ctx.assertTrue(plains.stream().allMatch(name -> name.equals("none")), "spruce in the plains: " + plains);
		BlockPos spruce = base.add(400, 0, 0);
		ctx.assertTrue(world.getBlockState(spruce).isOf(Blocks.SPRUCE_LOG), "the spruce sapling in the plains didn't grow a vanilla spruce");
		ctx.complete();
	}

	/**
	 * Template trees (the willow, the baobab, a curved palm, a larch) grow with every leaf not persistent and set to its true distance from
	 * a log, counted through leaves as the game counts it, so a cut tree's leaves decay as a vanilla tree's do. (Watching
	 * them decay doesn't work here: the test world doesn't run block ticks up in the sky.)
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void templateTreeLeavesDecay(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		int i = 0;
		for (String template : List.of("willow", "baobab", "palm_curved", "larch")) {
			BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).add(i++ * 40, 0, -200).withY(200);
			world.getChunk(pos);
			pad(world, pos);
			BiomeTrees.Choice choice = new BiomeTrees.Choice(1, Optional.empty(), Optional.of(EternalReturn.id("tree/" + template)));
			ctx.assertTrue(BiomeTrees.grow(choice, world, world.getChunkManager().getChunkGenerator(), Random.create(5), pos), template + " didn't grow");
			Map<BlockPos, Integer> distance = new java.util.HashMap<>();
			java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
			for (BlockPos p : BlockPos.iterate(pos.add(-12, 0, -12), pos.add(12, 20, 12))) {
				if (world.getBlockState(p).isIn(BlockTags.LOGS)) {
					distance.put(p.toImmutable(), 0);
					queue.add(p.toImmutable());
				}
			}
			while (!queue.isEmpty()) {
				BlockPos p = queue.poll();
				for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
					BlockPos next = p.offset(d);
					if (!distance.containsKey(next) && world.getBlockState(next).getBlock() instanceof LeavesBlock) {
						distance.put(next, distance.get(p) + 1);
						queue.add(next);
					}
				}
			}
			int leaves = 0;
			for (BlockPos p : BlockPos.iterate(pos.add(-12, 0, -12), pos.add(12, 20, 12))) {
				BlockState state = world.getBlockState(p);
				if (state.getBlock() instanceof LeavesBlock) {
					leaves++;
					int expected = Math.min(distance.getOrDefault(p.toImmutable(), com.eternalreturn.blocks.LeafDistance.MAX), com.eternalreturn.blocks.LeafDistance.MAX);
					ctx.assertTrue(!state.get(LeavesBlock.PERSISTENT) && state.get(LeavesBlock.DISTANCE) == expected && expected < com.eternalreturn.blocks.LeafDistance.MAX,
							template + ": leaf at " + p.toShortString() + " is " + state + ", " + expected + " from a log");
				}
			}
			ctx.assertTrue(leaves > 40, template + ": only " + leaves + " leaves");
		}
		ctx.complete();
	}

	/**
	 * Around up to eight land spots on the test seed, 5 x 5 chunks each are generated in full. Every tree the tables
	 * picked as the world generated must be on that biome's lists, and at least four species must have turned up.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void generatedTreesComeFromTheTables(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		BiomeTrees.GENERATED.clear();
		int spots = 0;
		for (int gx = -3; gx <= 3 && spots < 8; gx++) {
			for (int gz = -3; gz <= 3 && spots < 8; gz++) {
				int x = gx * 512 + 264;
				int z = gz * 512 + 264;
				int top = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
				var biome = generator.getBiomeSource().getBiome(x >> 2, top >> 2, z >> 2, noise.getMultiNoiseSampler());
				if (top <= generator.getSeaLevel() + 2 || biome.isIn(BiomeTags.IS_OCEAN) || biome.isIn(BiomeTags.IS_RIVER) || biome.isIn(BiomeTags.IS_BEACH)) {
					continue;
				}
				spots++;
				ChunkPos centre = new ChunkPos(x >> 4, z >> 4);
				for (int cx = centre.x - 2; cx <= centre.x + 2; cx++) {
					for (int cz = centre.z - 2; cz <= centre.z + 2; cz++) {
						world.getChunk(cx, cz, ChunkStatus.FULL, true);
					}
				}
			}
		}
		Map<String, Integer> picked = new TreeMap<>();
		BiomeTrees.GENERATED.forEach((key, count) -> picked.put(key, count.get()));
		EternalReturn.LOGGER.info("[trees] {} land spots; generated trees by biome: {}", spots, picked);
		Registry<Biome> biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		List<Identifier> saplings = new ArrayList<>();
		for (String sapling : List.of("minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:birch_sapling", "minecraft:jungle_sapling",
				"minecraft:acacia_sapling", "minecraft:dark_oak_sapling", "minecraft:cherry_sapling", "eternalreturn:maple_sapling",
				"eternalreturn:pale_oak_sapling", "minecraft:azalea")) {
			saplings.add(Identifier.of(sapling));
		}
		Set<String> species = new TreeSet<>();
		for (String key : picked.keySet()) {
			String biomeId = key.substring(0, key.indexOf(' '));
			String tree = key.substring(key.indexOf(' ') + 1);
			if (tree.equals("vanilla")) {
				continue;
			}
			species.add(tree);
			RegistryEntry<Biome> biome = biomes.getEntry(net.minecraft.registry.RegistryKey.of(RegistryKeys.BIOME, Identifier.of(biomeId))).orElseThrow();
			boolean listed = false;
			for (Identifier sapling : saplings) {
				for (boolean mega : new boolean[]{false, true}) {
					List<BiomeTrees.Choice> list = BiomeTrees.listFor(biome, sapling, mega);
					listed |= list != null && list.stream().anyMatch(choice -> BiomeTrees.name(choice).equals(tree));
				}
			}
			ctx.assertTrue(listed, tree + " generated in " + biomeId + ", which doesn't list it");
		}
		ctx.assertTrue(spots >= 4 && species.size() >= 4, spots + " land spots, species " + species);
		ctx.complete();
	}

	/**
	 * Leaves reach 10 from a log (LeafDistance): beside an oak log, a row of 11 oak leaves counts 1 to 10, so the first 9
	 * stay and only the 10th and 11th would decay; once the log goes, every leaf counts 10 and decays. Every world (flat runs).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "leaf_distance", tickLimit = 200)
	public void leavesReachTenFromALog(TestContext ctx) {
		BlockPos log = new BlockPos(0, 2, 0);
		ctx.setBlockState(log, Blocks.OAK_LOG);
		for (int x = 1; x <= 11; x++) {
			ctx.setBlockState(log.east(x), Blocks.OAK_LEAVES.getDefaultState());
		}
		ctx.waitAndRun(40, () -> {
			for (int x = 1; x <= 11; x++) {
				int expected = Math.min(x, com.eternalreturn.blocks.LeafDistance.MAX);
				BlockState state = ctx.getBlockState(log.east(x));
				ctx.assertTrue(state.get(LeavesBlock.DISTANCE) == expected, "leaf " + x + " from the log counts " + state.get(LeavesBlock.DISTANCE) + ", not " + expected);
			}
			ctx.setBlockState(log, Blocks.AIR);
			ctx.waitAndRun(40, () -> {
				for (int x = 1; x <= 11; x++) {
					BlockState state = ctx.getBlockState(log.east(x));
					ctx.assertTrue(!(state.getBlock() instanceof LeavesBlock) || state.get(LeavesBlock.DISTANCE) == com.eternalreturn.blocks.LeafDistance.MAX,
							"leaf " + x + " still counts " + state.get(LeavesBlock.DISTANCE) + " with the log gone");
				}
				ctx.complete();
			});
		});
	}

	/** In a world that isn't Eternal Return an oak sapling grows as vanilla: no table is asked. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "biome_trees_other_worlds", tickLimit = 100)
	public void otherWorldsGrowVanillaSaplings(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos pos = ctx.getAbsolutePos(BlockPos.ORIGIN).up(60);
		pad(world, pos);
		BlockState state = Blocks.OAK_SAPLING.getDefaultState().with(SaplingBlock.STAGE, 1);
		world.setBlockState(pos, state);
		((SaplingBlock) Blocks.OAK_SAPLING).generate(world, pos, state, Random.create(3));
		ctx.assertTrue(BiomeTrees.LAST_SAPLING_CHOICE.get() == null, "a tree table was used outside Eternal Return");
		ctx.assertTrue(world.getBlockState(pos).isOf(Blocks.OAK_LOG), "the oak didn't grow");
		for (BlockPos p : BlockPos.iterate(pos.add(-6, -2, -6), pos.add(6, 20, 6))) {
			world.setBlockState(p, Blocks.AIR.getDefaultState());
		}
		ctx.complete();
	}
}
