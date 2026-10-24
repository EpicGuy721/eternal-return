package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.compat.ModernerBetaHooks;
import com.eternalreturn.worldgen.terrain.FloatingIslands;
import com.eternalreturn.worldgen.terrain.Monoliths;
import com.eternalreturn.worldgen.terrain.TerrainShape;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Eternal Return terrain features (TerrainShape, FloatingIslands): stepped deep oceans, sinkholes and
 * floating islands, on the test seed.
 */
public class TerrainFeatureTests implements FabricGameTest {
	public static final String TERRAIN_BATCH = "terrain_features";

	private static String biome(ServerWorld world, ChunkGenerator generator, NoiseConfig noise, int x, int y, int z) {
		return BiomeStoneTests.id(generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z), noise.getMultiNoiseSampler()));
	}

	/**
	 * Over 8,000 x 8,000 blocks, the floor of every ocean column (sampled every 32 blocks): the deepest
	 * is around y -30 and none lies below y -40; most of the open sea is far below the old floors (y 33
	 * to 47); and the floor gathers on the three steps (y 30, 0 and -30) rather than spreading evenly,
	 * with steep drops between them. Sinkholes are left out.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = TERRAIN_BATCH, tickLimit = 2_000_000)
	public void oceansFallInSteps(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		int sea = generator.getSeaLevel();
		Map<Integer, Integer> floors = new TreeMap<>();
		int columns = 0;
		int lowest = Integer.MAX_VALUE;
		int nearSteps = 0;
		int between = 0;
		int steepDrops = 0;
		// For the checklist: the column nearest spawn on each step.
		int[][] examples = new int[3][];
		int[] steps = {(int) TerrainShape.SHELF_Y, (int) TerrainShape.MIDDLE_Y, (int) TerrainShape.DEEP_Y};
		// The sinkholes, mostly in the sea, drop far below the floor; they have a test of their own.
		List<TerrainShape.Sinkhole> holes = new ArrayList<>();
		for (int cx = Math.floorDiv(-4000, TerrainShape.SINKHOLE_CELL) - 1; cx <= Math.floorDiv(4000, TerrainShape.SINKHOLE_CELL) + 1; cx++) {
			for (int cz = Math.floorDiv(-4000, TerrainShape.SINKHOLE_CELL) - 1; cz <= Math.floorDiv(4000, TerrainShape.SINKHOLE_CELL) + 1; cz++) {
				TerrainShape.Sinkhole hole = TerrainShape.plannedSinkhole(world.getSeed(), cx, cz);
				if (hole != null) {
					holes.add(hole);
				}
			}
		}
		for (int x = -4000; x < 4000; x += 32) {
			for (int z = -4000; z < 4000; z += 32) {
				int columnX = x;
				int columnZ = z;
				if (holes.stream().anyMatch(hole -> Math.pow(columnX - hole.x(), 2) + Math.pow(columnZ - hole.z(), 2) < Math.pow(hole.radius() + 16, 2))) {
					continue;
				}
				int floor = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise) - 1;
				if (floor >= sea - 2 || !biome(world, generator, noise, x, sea, z).contains("ocean")) {
					continue;
				}
				columns++;
				lowest = Math.min(lowest, floor);
				for (int i = 0; i < 3; i++) {
					if (Math.abs(floor - steps[i]) <= 2 && (examples[i] == null || Math.abs(x) + Math.abs(z) < Math.abs(examples[i][0]) + Math.abs(examples[i][2]))) {
						examples[i] = new int[]{x, floor, z};
					}
				}
				floors.merge(Math.floorDiv(floor, 10) * 10, 1, Integer::sum);
				if (Math.abs(floor - TerrainShape.SHELF_Y) <= 6 || Math.abs(floor - TerrainShape.MIDDLE_Y) <= 6 || Math.abs(floor - TerrainShape.DEEP_Y) <= 6) {
					nearSteps++;
				} else if ((floor > TerrainShape.MIDDLE_Y + 8 && floor < TerrainShape.SHELF_Y - 8) || (floor > TerrainShape.DEEP_Y + 8 && floor < TerrainShape.MIDDLE_Y - 8)) {
					between++;
				}
				int next = generator.getHeight(x + 4, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise) - 1;
				if (Math.abs(next - floor) >= 10) {
					steepDrops++;
				}
			}
		}
		int deep = floors.entrySet().stream().filter(e -> e.getKey() < TerrainShape.MIDDLE_Y + 10).mapToInt(Map.Entry::getValue).sum();
		EternalReturn.LOGGER.info("[terrain] ocean floors (sampled every 32 blocks): {} columns, deepest y {}, by 10-block band {}, near a step {}, between steps {}, 10+ block drops over 4 blocks {} | nearest spawn on each step (x floor z): {}",
				columns, lowest, floors, nearSteps, between, steepDrops, java.util.Arrays.stream(examples).map(java.util.Arrays::toString).toList());
		ctx.assertTrue(columns > 2_500, "only " + columns + " ocean columns");
		ctx.assertTrue(lowest <= TerrainShape.DEEP_Y + 6 && lowest >= TerrainShape.DEEP_Y - 10, "the deepest ocean floor is at y " + lowest + ", not around y -30");
		ctx.assertTrue(deep * 3 > columns, "only " + deep + " of " + columns + " ocean columns are below y 10");
		ctx.assertTrue(nearSteps > between * 3, nearSteps + " ocean columns near a step against " + between + " between them: the floor isn't stepped");
		ctx.assertTrue(steepDrops > 15, "only " + steepDrops + " steep drops in the sea floor");
		ctx.assertTrue(Boolean.TRUE.equals(ModernerBetaHooks.APPLIED.get("ModernerBetaHeightMixin")) && ModernerBetaHooks.HEIGHT_PROVIDERS.get() > 0,
				"the column-height hook into Moderner Beta is not applied: " + ModernerBetaHooks.APPLIED);
		ctx.complete();
	}

	/**
	 * The sinkholes made within 8,192 blocks of spawn (most in the sea, a few on land): every one is a pit
	 * reaching near the bottom of the world, open down to the water (sea level) and flooded from there.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = TERRAIN_BATCH, tickLimit = 2_000_000)
	public void sinkholesReachTheBottom(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		int sea = generator.getSeaLevel();
		List<String> found = new ArrayList<>();
		List<String> shallow = new ArrayList<>();
		List<Integer> deepest = new ArrayList<>();
		List<String> dry = new ArrayList<>();
		int inSea = 0;
		TerrainShape.Sinkhole nearest = null;
		int cells = 8192 / TerrainShape.SINKHOLE_CELL;
		for (int cx = -cells; cx < cells; cx++) {
			for (int cz = -cells; cz < cells; cz++) {
				TerrainShape.Sinkhole hole = TerrainShape.plannedSinkhole(world.getSeed(), cx, cz);
				if (hole == null) {
					continue;
				}
				String atCentre = biome(world, generator, noise, hole.x(), sea, hole.z());
				if (!TerrainShape.keeps(hole, atCentre)) {
					continue;
				}
				if (atCentre.contains("ocean")) {
					inSea++;
				}
				int floor = generator.getHeight(hole.x(), hole.z(), Heightmap.Type.OCEAN_FLOOR_WG, world, noise) - 1;
				int water = generator.getHeight(hole.x(), hole.z(), Heightmap.Type.WORLD_SURFACE_WG, world, noise) - 1;
				int rim = generator.getHeight(hole.x() + hole.radius() * 2, hole.z(), Heightmap.Type.OCEAN_FLOOR_WG, world, noise) - 1;
				found.add(hole.x() + " " + hole.z() + " r" + hole.radius() + " " + atCentre + ": floor y " + floor + ", water to y " + water + ", ground beside y " + rim);
				deepest.add(floor);
				if (floor > -25) {
					shallow.add(hole.x() + ", " + hole.z() + " down to y " + floor);
				}
				if (water < sea - 1) {
					dry.add(hole.x() + ", " + hole.z() + " water to y " + water);
				}
				if (nearest == null || Math.abs(hole.x()) + Math.abs(hole.z()) < Math.abs(nearest.x()) + Math.abs(nearest.z())) {
					nearest = hole;
				}
			}
		}
		ctx.assertTrue(found.size() >= 3, "only " + found.size() + " sinkholes within 8,192 blocks: " + found);
		ctx.assertTrue(inSea * 2 > found.size(), "most sinkholes are in the sea, but only " + inSea + " of " + found.size() + ": " + found);
		// The nearest one, generated for real: what fills it at the bottom.
		String bottom = "?";
		if (nearest != null) {
			world.getChunk(nearest.x() >> 4, nearest.z() >> 4, ChunkStatus.FULL, true);
			int floor = world.getTopY(Heightmap.Type.OCEAN_FLOOR, nearest.x(), nearest.z()) - 1;
			StringBuilder column = new StringBuilder();
			for (int y = -64; y < sea; y += 4) {
				column.append(y).append(':').append(net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(new BlockPos(nearest.x(), y, nearest.z())).getBlock()).getPath()).append(' ');
			}
			bottom = "floor y " + floor + " | column " + column;
			ctx.assertTrue(!world.getBlockState(new BlockPos(nearest.x(), sea - 10, nearest.z())).getFluidState().isEmpty(), "the nearest sinkhole holds no water: " + bottom);
			// One clean lake: nothing solid between its floor (as the generator plans it) and the surface.
			int planned = world.getChunkManager().getChunkGenerator().getHeight(nearest.x(), nearest.z(), Heightmap.Type.OCEAN_FLOOR_WG, world, noise) - 1;
			int solid = 0;
			for (int y = planned + 3; y < sea; y++) {
				if (world.getBlockState(new BlockPos(nearest.x(), y, nearest.z())).getFluidState().isEmpty()) {
					solid++;
				}
			}
			ctx.assertTrue(solid == 0, solid + " non-fluid blocks in the nearest sinkhole between its floor (y " + planned + ") and sea level: " + bottom);
		}
		EternalReturn.LOGGER.info("[terrain] sinkholes within 8,192 blocks ({} in the sea): {} | nearest {} | at its bottom {}", inSea, found, nearest, bottom);
		ctx.assertTrue(shallow.isEmpty(), "sinkholes that don't reach y -25: " + shallow);
		long bottom45 = deepest.stream().filter(floor -> floor <= -45).count();
		ctx.assertTrue(bottom45 * 4 >= deepest.size() * 3, "only " + bottom45 + " of " + deepest.size() + " sinkholes reach y -45");
		ctx.assertTrue(dry.isEmpty(), "sinkholes not flooded to sea level: " + dry);
		ctx.complete();
	}

	/**
	 * The floating islands within 2,048 blocks of spawn, built for real: each is above y 228, made of the
	 * ground below it (the same top block as the ground straight under its middle, or sand over water),
	 * with a pointed underside: deepest under the middle, 10 to 40 blocks below the top, and shallow at its edge.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = TERRAIN_BATCH, tickLimit = 2_000_000)
	public void floatingIslandsMatchTheGroundBelow(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		int sea = world.getChunkManager().getChunkGenerator().getSeaLevel();
		List<FloatingIslands.Island> islands = new ArrayList<>();
		int cells = 2048 / FloatingIslands.CELL;
		for (int cx = -cells; cx < cells; cx++) {
			for (int cz = -cells; cz < cells; cz++) {
				FloatingIslands.Island island = FloatingIslands.inCell(noise, cx, cz);
				if (island != null) {
					islands.add(island);
				}
			}
		}
		islands.sort(Comparator.comparingInt(island -> Math.abs(island.x()) + Math.abs(island.z())));
		List<String> all = new ArrayList<>();
		for (FloatingIslands.Island island : islands) {
			all.add(island.x() + " " + island.top() + " " + island.z() + " r" + island.radius() + " over "
					+ biome(world, world.getChunkManager().getChunkGenerator(), noise, island.x(), sea, island.z()));
		}
		EternalReturn.LOGGER.info("[terrain] every floating island within 2,048 blocks, nearest first (x top z): {}", all);
		ctx.assertTrue(islands.size() >= 10, "only " + islands.size() + " floating islands within 2,048 blocks");
		List<String> checked = new ArrayList<>();
		for (FloatingIslands.Island island : islands.subList(0, 4)) {
			int reach = island.radius() + 2;
			for (int x = (island.x() - reach) >> 4; x <= (island.x() + reach) >> 4; x++) {
				for (int z = (island.z() - reach) >> 4; z <= (island.z() + reach) >> 4; z++) {
					world.getChunk(x, z, ChunkStatus.FULL, true);
				}
			}
			SimplexNoiseSampler shape = new SimplexNoiseSampler(Random.create(island.seed()));
			FloatingIslands.Column middle = FloatingIslands.column(island, shape, island.x(), island.z());
			ctx.assertTrue(middle != null, "no column at the middle of " + island);
			int x = island.x();
			int z = island.z();
			BlockPos.Mutable pos = new BlockPos.Mutable();
			int top = middle.top();
			while (top < world.getTopY() && !world.getBlockState(pos.set(x, top + 1, z)).isAir() && !world.getBlockState(pos).isIn(BlockTags.LOGS)
					&& !world.getBlockState(pos).isIn(BlockTags.LEAVES)) {
				top++;
			}
			int bottom = middle.bottom();
			ctx.assertTrue(top >= FloatingIslands.MIN_TOP, island + " tops out at y " + top);
			ctx.assertTrue(!world.getBlockState(pos.set(x, bottom, z)).isAir() && world.getBlockState(pos.set(x, bottom - 1, z)).isAir(),
					island + " has no rock at its point (y " + bottom + ")");
			int hang = top - bottom;
			ctx.assertTrue(hang >= 8 && hang <= 46, island + " hangs " + hang + " blocks under its middle");
			// Pointed: at two thirds of the radius the underside is much shallower than in the middle.
			int edgeX = x + island.radius() * 2 / 3;
			FloatingIslands.Column side = FloatingIslands.column(island, shape, edgeX, z);
			if (side != null) {
				ctx.assertTrue(side.bottom() - bottom >= 3, island + " is not pointed: bottom y " + bottom + " in the middle, " + side.bottom() + " towards the edge");
			}
			// Made of the ground below: its top block is the ground's (or sand over water).
			BlockState islandTop = world.getBlockState(pos.set(x, middle.top(), z));
			int ground = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
			int scan = Math.min(FloatingIslands.MIN_TOP - 50, world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z));
			while (scan > world.getBottomY() && (world.getBlockState(pos.set(x, scan, z)).isAir() || world.getBlockState(pos).isIn(BlockTags.LEAVES)
					|| world.getBlockState(pos).isIn(BlockTags.LOGS) || (world.getBlockState(pos).isReplaceable() && world.getBlockState(pos).getFluidState().isEmpty()))) {
				scan--;
			}
			BlockState below = world.getBlockState(pos.set(x, scan, z));
			boolean water = !below.getFluidState().isEmpty();
			String biomeBelow = biome(world, world.getChunkManager().getChunkGenerator(), noise, x, sea, z);
			checked.add(island + " in " + biomeBelow + ": top " + islandTop.getBlock() + " at y " + middle.top() + ", point y " + bottom + ", ground " + below.getBlock() + " at y " + scan);
			if (water) {
				ctx.assertTrue(islandTop.isIn(BlockTags.SAND), island + " over water is topped with " + islandTop + ", not sand");
			} else {
				ctx.assertTrue(islandTop.getBlock() == below.getBlock() || (islandTop.isIn(BlockTags.DIRT) && below.isIn(BlockTags.DIRT)),
						island + " is topped with " + islandTop + " over ground of " + below + " (ground at y " + ground + ")");
			}
		}
		EternalReturn.LOGGER.info("[terrain] floating islands within 2,048 blocks: {} | the nearest four: {}", islands.size(), checked);
		ctx.complete();
	}

	/**
	 * The monolith nearest spawn (one in each 10,000-block square): generated and checked in its middle: rock all the
	 * way from 60 blocks under its top to the top, which is y 188 or higher, nothing solid above it, and air beside it.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = TERRAIN_BATCH, tickLimit = 2_000_000)
	public void monolithsRiseToTheSky(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		List<Monoliths.Monolith> monoliths = new ArrayList<>();
		for (int cx = -1; cx <= 0; cx++) {
			for (int cz = -1; cz <= 0; cz++) {
				monoliths.add(Monoliths.inCell(noise, cx, cz));
			}
		}
		monoliths.sort(Comparator.comparingInt(monolith -> Math.abs(monolith.x()) + Math.abs(monolith.z())));
		EternalReturn.LOGGER.info("[terrain] the monoliths of the four squares around spawn, nearest first: {}", monoliths);
		Monoliths.Monolith monolith = monoliths.get(0);
		int x = monolith.x();
		int z = monolith.z();
		for (int chunkX = (x >> 4) - 1; chunkX <= (x >> 4) + 1; chunkX++) {
			for (int chunkZ = (z >> 4) - 1; chunkZ <= (z >> 4) + 1; chunkZ++) {
				world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
			}
		}
		int top = Monoliths.top(monolith, new SimplexNoiseSampler(Random.create(monolith.seed())), x, z);
		ctx.assertTrue(top >= Monoliths.MIN_TOP - 2, monolith + " tops out at y " + top);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int y = top - 60; y <= top; y++) {
			BlockState state = world.getBlockState(pos.set(x, y, z));
			ctx.assertTrue(!state.isAir() && state.getFluidState().isEmpty(), monolith + ": no rock at y " + y + " in the middle: " + state);
		}
		BlockState above = world.getBlockState(pos.set(x, top + 1, z));
		ctx.assertTrue(above.isAir() || above.isReplaceable() || above.isIn(BlockTags.LOGS) || above.isIn(BlockTags.LEAVES) || above.isOf(Blocks.SNOW),
				monolith + ": rock above its top: " + above);
		int besideX = x + monolith.halfX() + 30;
		world.getChunk(besideX >> 4, z >> 4, ChunkStatus.FULL, true);
		ctx.assertTrue(world.getBlockState(pos.set(besideX, top - 10, z)).isAir(), monolith + ": not standing alone");
		ctx.complete();
	}

	/** Mushrooms on cave floors: in the 8 x 8 chunks around spawn, brown ones below y 50 with rock overhead. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = TERRAIN_BATCH, tickLimit = 2_000_000)
	public void mushroomsGrowInCaves(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		int brown = 0;
		int red = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int chunkX = -4; chunkX < 4; chunkX++) {
			for (int chunkZ = -4; chunkZ < 4; chunkZ++) {
				world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
				for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
					for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
						for (int y = world.getBottomY() + 1; y < 50; y++) {
							BlockState state = world.getBlockState(pos.set(x, y, z));
							if ((state.isOf(Blocks.BROWN_MUSHROOM) || state.isOf(Blocks.RED_MUSHROOM)) && !world.isSkyVisible(pos)) {
								if (state.isOf(Blocks.BROWN_MUSHROOM)) {
									brown++;
								} else {
									red++;
								}
							}
						}
					}
				}
			}
		}
		EternalReturn.LOGGER.info("[terrain] cave mushrooms in the 8 x 8 chunks around spawn: {} brown, {} red", brown, red);
		ctx.assertTrue(brown > 0, "no brown mushrooms in caves (" + red + " red)");
		ctx.complete();
	}
}
