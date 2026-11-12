package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.climate.Climate;
import com.eternalreturn.worldgen.climate.FogTints;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
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
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Climate rules (com.eternalreturn.worldgen.climate), phase 5 of the worldgen expansion: in Eternal Return worlds snow
 * deepens with the cold, falls through leaves, cold water freezes thicker but keeps water at the bottom, cold lava lakes
 * get an obsidian crust, storms pile snow to the climate's depth and biomes have fog tints; other worlds get none of it.
 */
public class ClimateTests implements FabricGameTest {
	public static final String BATCH = "climate";
	private static final RegistryKey<PlacedFeature> CLIMATE = RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id("climate"));
	/** Cool: the taiga (0.05 in Moderner Beta's Release 1.6.4 biomes). Cold: 0 or below (ice plains, their peaks, the taiga's peaks and edges). */
	private static final float COOL_ABOVE = 0.03F;

	private static Climate.Config config(ServerWorld world) {
		Climate.Config config = Climate.config(world.getRegistryManager());
		if (config == null) {
			throw new AssertionError("no eternalreturn:climate configured feature");
		}
		return config;
	}

	/** Land spots (chunk positions) on a grid out to reach blocks whose surface biome passes the test, nearest first. */
	private static List<ChunkPos> landSpots(ServerWorld world, Predicate<RegistryEntry<Biome>> wanted, int reach, int step, int max) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		List<ChunkPos> spots = new ArrayList<>();
		for (int ring = 0; ring * step <= reach && spots.size() < max; ring++) {
			for (int gx = -ring; gx <= ring && spots.size() < max; gx++) {
				for (int gz = -ring; gz <= ring && spots.size() < max; gz++) {
					if (Math.max(Math.abs(gx), Math.abs(gz)) != ring) {
						continue;
					}
					int x = gx * step + 8;
					int z = gz * step + 8;
					// The biome first: the height near coasts is slow.
					if (!wanted.test(generator.getBiomeSource().getBiome(x >> 2, 16, z >> 2, noise.getMultiNoiseSampler()))
							&& !wanted.test(generator.getBiomeSource().getBiome(x >> 2, 24, z >> 2, noise.getMultiNoiseSampler()))) {
						continue;
					}
					int top = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
					if (top > generator.getSeaLevel() + 1 && wanted.test(generator.getBiomeSource().getBiome(x >> 2, top >> 2, z >> 2, noise.getMultiNoiseSampler()))) {
						spots.add(new ChunkPos(x >> 4, z >> 4));
					}
				}
			}
		}
		return spots;
	}

	private static void generate(ServerWorld world, ChunkPos centre, int radius) {
		for (int cx = centre.x - radius; cx <= centre.x + radius; cx++) {
			for (int cz = centre.z - radius; cz <= centre.z + radius; cz++) {
				world.getChunk(cx, cz, ChunkStatus.FULL, true);
			}
		}
	}

	private static boolean isOverworldBiome(RegistryEntry<Biome> biome, String... ids) {
		for (String id : ids) {
			if (biome.matchesId(Identifier.of(id))) {
				return true;
			}
		}
		return false;
	}

	/** Per-column snow found in generated chunks, for one group of spots. */
	private static final class SnowCount {
		int open;
		int openLayers;
		int deepOpen;
		int canopyGround;
		int canopyGroundLayers;
		int gaps;
		int maxOpen;
		int overDeep;
		BlockPos deepest;
		BlockPos underCrown;
		final Map<Integer, Integer> byLayers = new TreeMap<>();

		double average() {
			return this.open == 0 ? 0 : (double) this.openLayers / this.open;
		}

		@Override
		public String toString() {
			return String.format("deepest at %s, snow under a crown at %s; %d open columns, average %.2f layers, %d of 4+, max %d, by layers %s; %d under crowns (%.2f layers), %d on leaves inside crowns",
					this.deepest == null ? "-" : this.deepest.toShortString(), this.underCrown == null ? "-" : this.underCrown.toShortString(),
					this.open, this.average(), this.deepOpen, this.maxOpen, this.byLayers, this.canopyGround,
					this.canopyGround == 0 ? 0 : (double) this.canopyGroundLayers / this.canopyGround, this.gaps);
		}
	}

	private static void countSnow(ServerWorld world, ChunkPos centre, int radius, SnowCount count, Climate.Config config) {
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = (centre.x - radius) * 16; x < (centre.x + radius + 1) * 16; x++) {
			for (int z = (centre.z - radius) * 16; z < (centre.z + radius + 1) * 16; z++) {
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				BlockState state = world.getBlockState(pos.set(x, top, z));
				if (!state.isOf(Blocks.SNOW)) {
					continue;
				}
				BlockState under = world.getBlockState(pos.set(x, top - 1, z));
				if (!under.isIn(BlockTags.LEAVES)) {
					int layers = state.get(SnowBlock.LAYERS);
					count.open++;
					count.openLayers += layers;
					if (layers > count.maxOpen) {
						count.deepest = new BlockPos(x, top, z);
					}
					count.maxOpen = Math.max(count.maxOpen, layers);
					count.byLayers.merge(layers, 1, Integer::sum);
					if (layers >= 4) {
						count.deepOpen++;
					}
					continue;
				}
				// Down through the crown.
				for (int y = top - 2; y > top - 40; y--) {
					BlockState at = world.getBlockState(pos.set(x, y, z));
					if (at.isOf(Blocks.SNOW)) {
						BlockState below = world.getBlockState(pos.set(x, y - 1, z));
						if (below.isIn(BlockTags.LEAVES)) {
							count.gaps++;
							continue;
						}
						int layers = at.get(SnowBlock.LAYERS);
						if (count.underCrown == null && layers >= 2) {
							count.underCrown = new BlockPos(x, y, z);
						}
						count.canopyGround++;
						count.canopyGroundLayers += layers;
						int open = Climate.snowDepth(world, world.getSeed(), config, pos.set(x, y, z));
						if (layers > Math.max(1, open)) {
							count.overDeep++;
						}
						break;
					}
					if (!at.isAir() && !at.isIn(BlockTags.LEAVES)) {
						break;
					}
				}
			}
		}
	}

	/**
	 * Deep snow and snow through leaves, on the test seed: up to three land spots in the taiga (cool, 0.05) and three in
	 * colder land (the ice plains, 0, and the peaks and edges below it), 5 x 5 chunks each generated in full. The snow on
	 * open ground must average more layers in the cold spots than in the cool ones, the cold spots must have snow of 4
	 * layers or more somewhere, and no snow passes 8 layers. Under tree crowns there must be snow on the ground in both,
	 * never deeper than the open ground's there, and some snow on leaves inside crowns.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void coldBiomesHaveDeeperSnow(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Climate.Config config = config(world);
		List<ChunkPos> cool = landSpots(world, biome -> isOverworldBiome(biome, "moderner_beta:early_release_taiga"), 4096, 320, 3);
		List<ChunkPos> cold = landSpots(world, biome -> biome.value().getTemperature() <= 0.0F && !biome.matchesId(Identifier.of("minecraft:frozen_river"))
				&& !biome.matchesId(Identifier.of("minecraft:frozen_ocean")) && !biome.matchesId(Identifier.of("minecraft:deep_frozen_ocean")), 4096, 320, 3);
		ctx.assertTrue(!cool.isEmpty() && !cold.isEmpty(), "found " + cool.size() + " taiga spots and " + cold.size() + " cold spots");
		SnowCount coolSnow = new SnowCount();
		SnowCount coldSnow = new SnowCount();
		for (ChunkPos spot : cool) {
			generate(world, spot, 2);
			countSnow(world, spot, 2, coolSnow, config);
		}
		for (ChunkPos spot : cold) {
			generate(world, spot, 2);
			countSnow(world, spot, 2, coldSnow, config);
		}
		EternalReturn.LOGGER.info("[climate] cool spots {}: {}", cool, coolSnow);
		EternalReturn.LOGGER.info("[climate] cold spots {}: {}", cold, coldSnow);
		ctx.assertTrue(coolSnow.open > 100 && coldSnow.open > 100, "too little snow: cool " + coolSnow + "; cold " + coldSnow);
		ctx.assertTrue(coldSnow.average() > coolSnow.average() + 0.5, "the cold spots' snow is no deeper: cool " + coolSnow + "; cold " + coldSnow);
		ctx.assertTrue(coldSnow.deepOpen > 0, "no snow of 4 layers or more in the cold spots: " + coldSnow);
		ctx.assertTrue(coldSnow.maxOpen <= SnowBlock.MAX_LAYERS && coolSnow.maxOpen <= SnowBlock.MAX_LAYERS, "snow past 8 layers");
		ctx.assertTrue(coolSnow.canopyGround + coldSnow.canopyGround > 20, "hardly any snow under tree crowns: cool " + coolSnow + "; cold " + coldSnow);
		ctx.assertTrue(coolSnow.gaps + coldSnow.gaps > 0, "no snow on leaves inside crowns");
		ctx.assertTrue(coolSnow.overDeep + coldSnow.overDeep == 0, (coolSnow.overDeep + coldSnow.overDeep) + " columns under crowns have more snow than open ground");
		ctx.complete();
	}

	/**
	 * Frozen water keeps open water below the ice: in the chunks around up to four cold spots (ice plains and the frozen
	 * rivers and seas near them), every column of cold water under ice two or more blocks thick has at least water_kept
	 * blocks of water under the ice, and more than 20 such columns turn up. (Ice one block thick is Moderner Beta's own,
	 * over any depth of water.)
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void frozenLakesKeepWaterBelow(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Climate.Config config = config(world);
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		List<ChunkPos> cold = landSpots(world, biome -> biome.value().getTemperature() <= 0.0F, 4096, 256, 4);
		ctx.assertTrue(!cold.isEmpty(), "no cold spots found");
		int iced = 0;
		int thick = 0;
		BlockPos thickest = null;
		int thickestIce = 0;
		Map<String, Integer> byThickness = new TreeMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (ChunkPos spot : cold) {
			generate(world, spot, 3);
			for (int x = (spot.x - 3) * 16; x < (spot.x + 4) * 16; x++) {
				for (int z = (spot.z - 3) * 16; z < (spot.z + 4) * 16; z++) {
					int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
					if (!world.getBlockState(pos.set(x, top, z)).isOf(Blocks.ICE)) {
						continue;
					}
					int ice = 0;
					while (ice < 16 && world.getBlockState(pos.set(x, top - ice, z)).isOf(Blocks.ICE)) {
						ice++;
					}
					int water = 0;
					while (water < 64 && world.getBlockState(pos.set(x, top - ice - water, z)).isOf(Blocks.WATER)) {
						water++;
					}
					if (water == 0 && ice == 1) {
						continue;
					}
					iced++;
					byThickness.merge(ice + " ice over " + Math.min(water, config.waterKept() + 1) + (water > config.waterKept() ? "+" : "") + " water", 1, Integer::sum);
					if (ice >= 2) {
						if (ice > thickestIce || thickest == null) {
							thickest = new BlockPos(x, top, z);
							thickestIce = ice;
						}
						thick++;
						ctx.assertTrue(water >= config.waterKept(), "ice " + ice + " thick at " + x + ", " + (top - ice + 1) + ", " + z
								+ " leaves only " + water + " water below");
					}
				}
			}
		}
		EternalReturn.LOGGER.info("[climate] cold spots {} (sea level {}): {} iced columns, {} with ice 2 or more thick (thickest {} at {}); {}", cold,
				generator.getSeaLevel(), iced, thick, thickestIce, thickest == null ? "-" : thickest.toShortString(), byThickness);
		ctx.assertTrue(thick > 20, "only " + thick + " columns of thicker ice in " + iced + " iced columns");
		ctx.complete();
	}

	/** Builds a lava pit (5 x 5, three deep, open above) at the surface here and crusts it as a lake there would be. */
	private static int[] crustPit(ServerWorld world, Climate.Config config, BlockPos ground) {
		BlockPos corner = ground.add(-2, -3, -2);
		for (BlockPos at : BlockPos.iterate(corner.add(-1, -1, -1), corner.add(5, 60, 5))) {
			boolean inside = at.getX() >= corner.getX() && at.getX() < corner.getX() + 5 && at.getZ() >= corner.getZ() && at.getZ() < corner.getZ() + 5;
			int dy = at.getY() - corner.getY();
			BlockState state = !inside ? (dy < 3 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState())
					: dy < 0 ? Blocks.STONE.getDefaultState() : dy < 3 ? Blocks.LAVA.getDefaultState() : Blocks.AIR.getDefaultState();
			world.setBlockState(at, state, 2 | 16);
		}
		// The lake box's corner sits a layer below the pit's floor: lava in layers 1 to 3.
		Climate.crustLava(world, config, corner.down());
		int obsidian = 0;
		int lavaUnder = 0;
		for (BlockPos at : BlockPos.iterate(corner, corner.add(4, 2, 4))) {
			if (world.getBlockState(at).isOf(Blocks.OBSIDIAN)) {
				obsidian++;
			} else if (world.getBlockState(at).isOf(Blocks.LAVA)) {
				lavaUnder++;
			}
		}
		return new int[]{obsidian, lavaUnder};
	}

	/**
	 * Cold lava lakes crust over: a lava pit built at the surface of a cold spot (ice plains) gets an obsidian top with
	 * lava under it, all 25 columns; the same pit in a warm spot (desert or savanna) stays lava.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void coldLavaLakesCrustOver(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Climate.Config config = config(world);
		List<ChunkPos> cold = landSpots(world, biome -> isOverworldBiome(biome, "moderner_beta:early_release_ice_plains"), 4096, 320, 1);
		List<ChunkPos> warm = landSpots(world, biome -> biome.value().getTemperature() >= 1.0F, 4096, 320, 1);
		ctx.assertTrue(!cold.isEmpty() && !warm.isEmpty(), "found " + cold.size() + " ice plains and " + warm.size() + " warm spots");
		generate(world, cold.getFirst(), 1);
		generate(world, warm.getFirst(), 1);
		BlockPos coldGround = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cold.getFirst().getCenterAtY(0));
		BlockPos warmGround = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, warm.getFirst().getCenterAtY(0));
		int[] coldPit = crustPit(world, config, coldGround);
		int[] warmPit = crustPit(world, config, warmGround);
		EternalReturn.LOGGER.info("[climate] lava pit at {} (cold): {} obsidian, {} lava; at {} (warm): {} obsidian, {} lava",
				coldGround.toShortString(), coldPit[0], coldPit[1], warmGround.toShortString(), warmPit[0], warmPit[1]);
		ctx.assertTrue(coldPit[0] >= 25 && coldPit[1] >= 25, "the cold pit has " + coldPit[0] + " obsidian over " + coldPit[1] + " lava");
		ctx.assertTrue(warmPit[0] == 0, "the warm pit crusted over: " + warmPit[0] + " obsidian");
		ctx.complete();
	}

	/**
	 * Storms pile snow to the climate's depth: at a cold spot the storm limit for open ground is the climate's depth there
	 * (more than the game rule's 1), on leaves it stays the rule, and with the rule at 0 nothing piles. Storm snow on a
	 * tree sifts down to the ground under it, a layer at a time, up to the crown's share.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void stormsPileSnowToTheClimateDepth(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Climate.Config config = config(world);
		List<ChunkPos> cold = landSpots(world, biome -> biome.value().getTemperature() <= -0.2F, 4096, 256, 1);
		ctx.assertTrue(!cold.isEmpty(), "no spot of -0.2 or colder found");
		ChunkPos spot = cold.getFirst();
		generate(world, spot, 1);
		BlockPos ground = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spot.getCenterAtY(0));
		if (world.getBlockState(ground).isOf(Blocks.SNOW)) {
			ground = ground.up();
		}
		// A clear column of air on stone, for the open-ground limit.
		BlockPos open = ground.up(3);
		world.setBlockState(open.down(), Blocks.STONE.getDefaultState());
		int depth = Climate.snowDepth(world, world.getSeed(), config, open);
		ctx.assertTrue(depth >= 2, "the climate's depth at " + open.toShortString() + " is only " + depth);
		ctx.assertTrue(Climate.stormLimit(world, open, 1) == depth, "storm limit " + Climate.stormLimit(world, open, 1) + ", climate depth " + depth);
		ctx.assertTrue(Climate.stormLimit(world, open, 0) == 0, "storms pile snow with the game rule at 0");
		world.setBlockState(open.down(), Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
		ctx.assertTrue(Climate.stormLimit(world, open, 1) == 1, "storms pile more than the rule on leaves");
		// A crown: leaves 4 above a stone floor, the snow sifting from on top of it.
		BlockPos floor = open.up(2);
		world.setBlockState(floor, Blocks.STONE.getDefaultState());
		for (int i = 1; i <= 3; i++) {
			world.setBlockState(floor.up(i), Blocks.AIR.getDefaultState());
		}
		world.setBlockState(floor.up(4), Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
		world.setBlockState(floor.up(5), Blocks.SNOW.getDefaultState());
		int share = Climate.canopyDepth(config, Climate.snowDepth(world, world.getSeed(), config, floor.up()));
		for (int i = 0; i < 20; i++) {
			Climate.sift(world, world.getSeed(), config, floor.up(5), Random.create(i), true);
		}
		BlockState under = world.getBlockState(floor.up());
		ctx.assertTrue(under.isOf(Blocks.SNOW) && under.get(SnowBlock.LAYERS) == share, "under the crown " + under + ", expected " + share + " layers");
		ctx.complete();
	}

	/**
	 * Fog tints: the server's list gives the ice plains the icy tint, a variant (the taiga peak) its base's, and leaves
	 * the plains (not in the table) out.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void biomesHaveFogTints(TestContext ctx) {
		List<FogTints.Tint> tints = FogTints.tints(ctx.getWorld().getServer());
		Map<Identifier, FogTints.Tint> byBiome = new java.util.HashMap<>();
		tints.forEach(tint -> byBiome.put(tint.biome(), tint));
		FogTints.Tint ice = byBiome.get(Identifier.of("moderner_beta", "early_release_ice_plains"));
		FogTints.Tint taiga = byBiome.get(Identifier.of("moderner_beta", "early_release_taiga"));
		FogTints.Tint peak = byBiome.get(EternalReturn.id("taiga_peak"));
		ctx.assertTrue(ice != null && ice.day() == 0xDDE9F7 && ice.night() == 0x0D1426, "ice plains tint " + ice);
		ctx.assertTrue(taiga != null && peak != null && peak.day() == taiga.day() && peak.night() == taiga.night(), "taiga " + taiga + ", taiga peak " + peak);
		ctx.assertTrue(!byBiome.containsKey(Identifier.of("minecraft", "plains")), "the plains have a tint");
		ctx.complete();
	}

	/** Outside Eternal Return worlds the climate feature places nothing, storms keep the game rule and no fog tints are sent (flat runs). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "climate_other_worlds")
	public void noClimateInOtherWorlds(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		PlacedFeature climate = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE).getOrThrow(CLIMATE);
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
		BlockPos chunkStart = new ChunkPos(origin).getStartPos().withY(origin.getY());
		ctx.assertFalse(climate.generate(world, world.getChunkManager().getChunkGenerator(), Random.create(1), chunkStart),
				"the climate feature placed something in a world that isn't Eternal Return");
		ctx.assertTrue(Climate.stormLimit(world, origin.up(2), 1) == 1, "storms pile past the game rule in a world that isn't Eternal Return");
		ctx.assertTrue(FogTints.tints(world.getServer()).isEmpty(), "fog tints in a world that isn't Eternal Return");
		ctx.complete();
	}

	/**
	 * Snow on treetops follows the ground (ModernerBetaSnowCheckMixin): in chunks of the extreme hills on the test seed
	 * whose ground lies a little below the icy mountains (y 105, and up to 48 above the ground), the tops of tall trees
	 * reaching into the icy mountains carry snow only where the ground under them is cold enough for snow (the owner's
	 * report, 2026-11-11).
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void treetopSnowFollowsTheGround(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Identifier icy = EternalReturn.id("icy_mountains");
		int crowns = 0;
		int snowy = 0;
		int loaded = 0;
		List<String> wrong = new ArrayList<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int gx = -40; gx <= 40 && crowns < 200 && loaded < 160; gx++) {
			for (int gz = -40; gz <= 40 && crowns < 200 && loaded < 160; gz++) {
				int cx = gx * 64 + 8;
				int cz = gz * 64 + 8;
				int estimate = generator.getHeight(cx, cz, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
				if (estimate < 84 || estimate > 104) {
					continue;
				}
				RegistryEntry<Biome> source = generator.getBiomeSource().getBiome(cx >> 2, estimate >> 2, cz >> 2, noise.getMultiNoiseSampler());
				if (!com.eternalreturn.worldgen.variants.BiomeVariants.baseOf(source.getKey().orElseThrow().getValue()).toString().equals("moderner_beta:early_release_extreme_hills")) {
					continue;
				}
				// (The icy mountains go in at the surface step: the chunk made in full shows them.)
				loaded++;
				ChunkPos chunk = new ChunkPos(cx >> 4, cz >> 4);
				world.getChunk(chunk.x, chunk.z, ChunkStatus.FULL, true);
				for (int x = chunk.getStartX(); x < chunk.getStartX() + 16; x++) {
					for (int z = chunk.getStartZ(); z < chunk.getStartZ() + 16; z++) {
						int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
						int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
						if (ground > 150 || !world.getBlockState(pos.set(x, top - 1, z)).isIn(BlockTags.LEAVES)
								|| !world.getBiome(new BlockPos(x, top, z)).matchesId(icy) || world.getBiome(new BlockPos(x, ground, z)).matchesId(icy)) {
							continue;
						}
						// A crown reaching into the icy mountains over ground that isn't theirs: snow on it only if the ground's climate snows.
						crowns++;
						boolean crownSnow = world.getBlockState(pos.set(x, top, z)).isOf(Blocks.SNOW);
						snowy += crownSnow ? 1 : 0;
						RegistryEntry<Biome> under = world.getBiome(new BlockPos(x, ground, z));
						boolean cold = !under.value().doesNotSnow(new BlockPos(x, ground, z));
						if (crownSnow && !cold) {
							wrong.add(x + " " + top + " " + z + " over " + under.getKey().orElseThrow().getValue());
						}
					}
				}
			}
		}
		EternalReturn.LOGGER.info("Treetop snow: {} chunks, {} crowns in the icy mountains over other ground, {} with snow, {} over warm ground", loaded, crowns, snowy, wrong.size());
		ctx.assertTrue(crowns >= 10, "only " + crowns + " crowns in the icy mountains over other ground in " + loaded + " chunks");
		ctx.assertTrue(wrong.isEmpty(), wrong.size() + " snowy crowns over ground too warm for snow, first " + wrong.subList(0, Math.min(5, wrong.size())));
		ctx.complete();
	}

}
