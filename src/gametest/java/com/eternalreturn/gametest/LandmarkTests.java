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
				{"volcano", 3, 7}, {"glacier", 3, 6}, {"ice_wall", 5, 4}};
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
				case "spire", "tropical_pillars" -> {
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
				case "lava_flow" -> {
					int lava = count(world, centre, half, 20, 6, s -> s.isOf(Blocks.LAVA));
					int charred = count(world, centre, half, 20, 6, s -> s.isOf(Blocks.COAL_BLOCK) || s.isOf(Blocks.BASALT));
					what = lava + " lava, " + charred + " coal and basalt";
					ok = lava >= 10 && charred >= 20;
				}
				case "volcano" -> {
					int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centre.getX() + 20, centre.getZ());
					int lava = count(world, centre, 20, -20, 90, s -> s.isOf(Blocks.LAVA));
					what = "slope 20 out at " + (top - centre.getY()) + " above the base, " + lava + " lava up top, radius " + piece.size;
					ok = top >= centre.getY() + 15 && lava >= 10;
				}
				case "glacier", "ice_wall" -> {
					Block hardened = net.minecraft.registry.Registries.BLOCK.get(EternalReturn.id("hardened_packed_ice"));
					int ice = count(world, centre, half, 10, 40, s -> s.isOf(hardened) || s.isOf(Blocks.PACKED_ICE) || s.isOf(Blocks.BLUE_ICE));
					what = ice + " ice blocks";
					ok = ice >= 100;
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
