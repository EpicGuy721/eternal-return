package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.landmarks.LandmarkPiece;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Terrain landmarks (com.eternalreturn.worldgen.landmarks): each kind is found near spawn on the test seed, built in
 * full, and holds what it should, of the stone of the biome it started in. The spots are logged as /tp commands.
 */
public class LandmarkTests implements FabricGameTest {
	public static final String BATCH = "landmarks";

	private static int count(ServerWorld world, BlockPos centre, int half, int below, int above, java.util.function.Predicate<BlockState> what) {
		int n = 0;
		for (BlockPos p : BlockPos.iterate(centre.add(-half, -below, -half), centre.add(half, above, half))) {
			if (what.test(world.getBlockState(p))) {
				n++;
			}
		}
		return n;
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void everyLandmarkIsBuilt(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Registry<Structure> structures = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
		Map<String, String> report = new TreeMap<>();
		List<String> problems = new ArrayList<>();
		// kind, search radius in spacing cells (about 3,000 blocks for the small ones, 6,000 for the big ones), chunks to
		// generate round the centre
		Object[][] kinds = {{"arch", 5, 3}, {"spire", 5, 2}, {"tropical_pillars", 5, 3}, {"crater", 5, 3}, {"lava_flow", 3, 6},
				{"volcano", 3, 7}, {"glacier", 3, 6}, {"ice_wall", 5, 4},
				// The mesa styles' rock forms (phase 6): only in their sub-biomes, so searched further, in more cells.
				{"butte", 45, 2}, {"table", 35, 3}, {"hoodoos", 90, 2}, {"hoodoo_cluster", 40, 1},
				// Batch C: glaciers and ice walls crowded in the glacier field and icy mountains.
				{"glacier_cluster", 25, 6}, {"ice_wall_cluster", 45, 4},
				// Batch D: crowded pillars, volcanoes and lava flows in their jungles; islands, sea stacks and reefs at sea.
				{"pillar_cluster", 60, 3}, {"volcanic_volcano", 25, 6}, {"volcanic_lava_flow", 50, 6},
				{"islets", 60, 5}, {"sea_pillars", 45, 3}, {"reef", 90, 2},
				// Expansion 2, phase 6, batch 1: icebergs in the iceberg sea, sea stacks in their own.
				{"iceberg", 90, 2}, {"sea_stacks", 70, 2},
				// Batch 2: shoals in their own sea.
				{"shoals", 70, 3},
				// Batch 3: atolls in the warm sea, seamounts in the deep ones.
				{"atoll", 70, 4}, {"seamount", 60, 6}};
		var calculator = world.getChunkManager().getStructurePlacementCalculator();
		var generator = world.getChunkManager().getChunkGenerator();
		for (Object[] k : kinds) {
			String kind = (String) k[0];
			RegistryEntry<Structure> entry = structures.getEntry(RegistryKey.of(RegistryKeys.STRUCTURE, EternalReturn.id(kind))).orElseThrow();
			// The test world is made with structures off (vanilla's test level), so no chunk starts a landmark by itself:
			// take the nearest spacing cell whose start chunk holds a valid one, as chunk generation would, and build it.
			long started = System.nanoTime();
			var placement = (net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement) calculator.getPlacements(entry).get(0);
			StructureStart structureStart = null;
			search:
			for (int ring = 0; ring <= (int) k[1]; ring++) {
				for (int cellX = -ring; cellX <= ring; cellX++) {
					for (int cellZ = -ring; cellZ <= ring; cellZ++) {
						if (Math.max(Math.abs(cellX), Math.abs(cellZ)) != ring) {
							continue;
						}
						ChunkPos start = placement.getStartChunk(calculator.getStructureSeed(), cellX * placement.getSpacing(), cellZ * placement.getSpacing());
						StructureStart candidate = entry.value().createStructureStart(world.getRegistryManager(), generator, generator.getBiomeSource(),
								calculator.getNoiseConfig(), world.getStructureTemplateManager(), calculator.getStructureSeed(), start, 0, world,
								entry.value().getValidBiomes()::contains);
						if (candidate.hasChildren()) {
							structureStart = candidate;
							break search;
						}
					}
				}
			}
			EternalReturn.LOGGER.info("[landmarks] found {} in {} ms", kind, (System.nanoTime() - started) / 1_000_000);
			if (structureStart == null) {
				problems.add(kind + ": none within " + k[1] + " spacing cells");
				continue;
			}
			LandmarkPiece piece = null;
			for (StructurePiece p : structureStart.getChildren()) {
				if (p instanceof LandmarkPiece landmark) {
					piece = landmark;
				}
			}
			if (piece == null) {
				problems.add(kind + ": no landmark piece");
				continue;
			}
			int reach = (int) k[2];
			long building = System.nanoTime();
			ChunkPos c = new ChunkPos(piece.centre);
			for (int x = c.x - reach; x <= c.x + reach; x++) {
				for (int z = c.z - reach; z <= c.z + reach; z++) {
					world.getChunk(x, z, ChunkStatus.FULL, true);
				}
			}
			for (int x = c.x - reach; x <= c.x + reach; x++) {
				for (int z = c.z - reach; z <= c.z + reach; z++) {
					ChunkPos at = new ChunkPos(x, z);
					structureStart.place(world, world.getStructureAccessor(), generator, net.minecraft.util.math.random.Random.create(at.toLong()),
							new net.minecraft.util.math.BlockBox(at.getStartX(), world.getBottomY(), at.getStartZ(), at.getEndX(), world.getTopY() - 1, at.getEndZ()), at);
				}
			}
			EternalReturn.LOGGER.info("[landmarks] built {} ({} chunks) in {} ms", kind, (2 * reach + 1) * (2 * reach + 1), (System.nanoTime() - building) / 1_000_000);
			BlockPos centre = piece.centre;
			Block stone = piece.stone;
			int half = Math.min(reach * 16, 48);
			String what;
			boolean ok;
			switch (kind) {
				case "arch" -> {
					// Rock of its stone with air under it, well above the ground: the span.
					int span = 0;
					for (BlockPos p : BlockPos.iterate(centre.add(-half, 3, -half), centre.add(half, piece.size + 6, half))) {
						if (world.getBlockState(p).isOf(stone) && world.getBlockState(p.down()).isAir()) {
							span++;
						}
					}
					what = span + " " + stone + " blocks over open air";
					ok = span >= 5;
				}
				case "spire", "tropical_pillars", "pillar_cluster", "sea_pillars" -> {
					int high = count(world, centre, 12, -piece.size / 2, piece.size / 2 + 6, s -> s.isOf(stone));
					what = high + " " + stone + " blocks in its upper half";
					ok = high >= 6;
				}
				case "crater" -> {
					// The bowl: open air below the old ground level near the middle (the test places it into finished chunks,
					// where trees stood, so the heightmap may still see a treetop over it).
					int bowl = 0;
					for (BlockPos p : BlockPos.iterate(centre.add(-piece.size / 2, -piece.size, -piece.size / 2), centre.add(piece.size / 2, -2, piece.size / 2))) {
						if (world.getBlockState(p).isAir()) {
							bowl++;
						}
					}
					int ores = count(world, centre, piece.size * 3 / 2 + 2, 12, 8, s -> s.getBlock().getTranslationKey().contains("ore"));
					what = bowl + " open blocks in the bowl, " + ores + " ores";
					ok = bowl >= 20 && ores >= 3;
				}
				case "islets" -> {
					// Land above the sea: grass and sand beaches.
					int land = count(world, centre, half, 0, 12, s -> s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.SAND));
					what = land + " grass and sand at or above the sea, chain " + piece.size + " long";
					ok = land >= 30;
				}
				case "reef" -> {
					// A shelf 3 to 6 under the surface.
					int shelf = count(world, centre.down(3), half, 4, 0, s -> s.isOf(Blocks.SAND) || s.isOf(Blocks.GRAVEL) || s.isIn(net.minecraft.registry.tag.BlockTags.CORAL_BLOCKS));
					what = shelf + " reef blocks 3 to 7 under the surface, radius " + piece.size;
					ok = shelf >= 100;
				}
				case "iceberg" -> {
					// Packed and blue ice, more of it under the water than over it.
					java.util.function.Predicate<BlockState> ice = s -> s.isOf(Blocks.PACKED_ICE) || s.isOf(Blocks.BLUE_ICE) || s.isOf(Blocks.SNOW_BLOCK);
					int over = count(world, centre, half, 0, 20, ice);
					int under = count(world, centre.down(), half, 40, 0, ice);
					int blue = count(world, centre.down(), half, 40, 0, s -> s.isOf(Blocks.BLUE_ICE));
					what = over + " ice over the water, " + under + " under, " + blue + " blue, radius " + piece.size;
					ok = over >= 15 && under > over && blue >= 5;
				}
				case "atoll" -> {
					// The reef ring at the surface, sand islets above it.
					int reef = count(world, centre.down(3), Math.min(piece.size + 6, half), 3, 0, s -> s.isOf(Blocks.SAND) || s.isIn(net.minecraft.registry.tag.BlockTags.CORAL_BLOCKS));
					int islet = count(world, centre, Math.min(piece.size + 6, half), 0, 4, s -> s.isOf(Blocks.SAND));
					what = reef + " reef blocks just under the surface, " + islet + " sand out of the sea, radius " + piece.size;
					ok = reef >= 60 && islet >= 5;
				}
				case "seamount" -> {
					// Its top just under the surface: the landmark's stone, gravel or sand 4 to 12 down.
					int top = count(world, centre.down(12), 10, 8, 0, s -> s.isOf(stone) || s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.SAND));
					what = top + " blocks 4 to 12 under the surface over its middle, radius " + piece.size;
					ok = top >= 40;
				}
				case "shoals" -> {
					// Sandbar crests at or just above the surface.
					int crests = count(world, centre.down(), half, 0, 2, s -> s.isOf(Blocks.SAND));
					what = crests + " sand at the surface, field radius " + piece.size;
					ok = crests >= 20;
				}
				case "sea_stacks" -> {
					int high = count(world, centre.up(2), half, 0, 34, s -> s.isOf(stone) || s.isOf(Blocks.GRASS_BLOCK));
					what = high + " " + stone + " and grass blocks above the sea, cluster radius " + piece.size;
					ok = high >= 60;
				}
				case "lava_flow", "volcanic_lava_flow" -> {
					int lava = count(world, centre, half, 20, 6, s -> s.isOf(Blocks.LAVA));
					int charred = count(world, centre, half, 20, 6, s -> s.isOf(Blocks.COAL_BLOCK) || s.isOf(Blocks.BASALT));
					what = lava + " lava, " + charred + " coal and basalt";
					ok = lava >= 10 && charred >= 20;
				}
				case "volcano", "volcanic_volcano" -> {
					int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centre.getX() + 20, centre.getZ());
					int lava = count(world, centre, 20, -20, 90, s -> s.isOf(Blocks.LAVA));
					what = "slope 20 out at " + (top - centre.getY()) + " above the base, " + lava + " lava up top, radius " + piece.size;
					ok = top >= centre.getY() + 15 && lava >= 10;
				}
				case "glacier", "ice_wall", "glacier_cluster", "ice_wall_cluster" -> {
					Block hardened = net.minecraft.registry.Registries.BLOCK.get(EternalReturn.id("hardened_packed_ice"));
					int ice = count(world, centre, half, 10, 40, s -> s.isOf(hardened) || s.isOf(Blocks.PACKED_ICE) || s.isOf(Blocks.BLUE_ICE));
					what = ice + " ice blocks";
					ok = ice >= 100;
				}
				case "butte", "table", "hoodoos", "hoodoo_cluster" -> {
					// Banded rock of its mesa style standing well above the old ground (the bands repeat by height, as the ground's).
					com.eternalreturn.worldgen.landmarks.MesaBands.Style style = com.eternalreturn.worldgen.landmarks.MesaBands.of(piece.style);
					java.util.Set<BlockState> bands = style == null ? java.util.Set.of() : java.util.Set.copyOf(style.layers());
					int risen = count(world, centre, Math.min(piece.size + 4, half), -4, 40, bands::contains);
					int needed = switch (kind) {
						case "butte", "table" -> 200;
						case "hoodoos" -> 40;
						default -> 15;
					};
					what = risen + " banded blocks above the ground (" + piece.style + ", size " + piece.size + ")";
					ok = style != null && risen >= needed;
				}
				default -> throw new IllegalStateException(kind);
			}
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, centre.getX(), centre.getZ()) + 1;
			report.put(kind, "/tp @s " + centre.getX() + " " + y + " " + centre.getZ() + " (" + what + ")");
			if (!ok) {
				problems.add(kind + ": " + what);
			}
		}
		report.forEach((kind, line) -> EternalReturn.LOGGER.info("[landmarks] {} {}", kind, line));
		ctx.assertTrue(problems.isEmpty(), "landmarks: " + problems);
		ctx.complete();
	}

	/** Outside Eternal Return worlds no landmark starts (flat runs). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "landmarks_other_worlds")
	public void noLandmarksInOtherWorlds(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Registry<Structure> structures = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
		for (String kind : List.of("arch", "crater", "volcano")) {
			RegistryEntry<Structure> entry = structures.getEntry(RegistryKey.of(RegistryKeys.STRUCTURE, EternalReturn.id(kind))).orElseThrow();
			ctx.assertTrue(world.getChunkManager().getChunkGenerator().locateStructure(world, RegistryEntryList.of(entry), BlockPos.ORIGIN, 20, false) == null,
					"a " + kind + " was found in a world that isn't Eternal Return");
		}
		ctx.complete();
	}
}
