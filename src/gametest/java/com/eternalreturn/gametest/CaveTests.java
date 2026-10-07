package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveStartLog;
import com.eternalreturn.worldgen.caves.CaveTiming;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.CaveTypes;
import com.eternalreturn.worldgen.caves.ChunkCarver;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.carver.CarvingMask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cave engine checks, in an Eternal Return world with a twin dimension (same generator, same seed)
 * used for the order-independence check. Runs in gametestCaves only.
 */
public class CaveTests implements FabricGameTest {
	public static final String CAVES_BATCH = "caves";
	public static final RegistryKey<World> TWIN = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("eternalreturn_test", "twin"));
	public static final TagKey<Block> CARVABLE = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("carvable"));
	/** Inner square, in chunks, measured by each forced-type test. */
	private static final int SQUARE = 8;
	/** The world's lava level for carvers: 8 blocks above the floor (configured_carver/caves.json). */
	private static final int LAVA_ABOVE_BOTTOM = 8;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "caves_spaghetti", tickLimit = 400_000)
	public void spaghetti(TestContext ctx) {
		forcedType(ctx, "spaghetti", 0, 0.002, 0.15);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "caves_ravine", tickLimit = 400_000)
	public void ravine(TestContext ctx) {
		forcedType(ctx, "ravine", 1, 0.002, 0.10);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "caves_large", tickLimit = 400_000)
	public void large(TestContext ctx) {
		forcedType(ctx, "large", 2, 0.002, 0.40);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "caves_vertical", tickLimit = 400_000)
	public void vertical(TestContext ctx) {
		forcedType(ctx, "vertical", 3, 0.002, 0.12);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "caves_zigzag", tickLimit = 400_000)
	public void zigzag(TestContext ctx) {
		forcedType(ctx, "zigzag", 4, 0.002, 0.08);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "caves_ribbed", tickLimit = 400_000)
	public void ribbed(TestContext ctx) {
		forcedType(ctx, "ribbed", 5, 0.002, 0.10);
	}

	/**
	 * Only one type, at its forced (high) frequency, in a square of its own far from the others:
	 * caves exist; the open share of the underground is within [minOpen, maxOpen]; nothing is carved in
	 * the bedrock floor, at or below the lava level (lava there), or within 8 blocks of the top; no lava
	 * above the lava level; no cave block touches an ocean, river or lake; cave borders line up across
	 * chunk edges; and a chunk generated first or last comes out identical (twin dimension).
	 */
	private static void forcedType(TestContext ctx, String type, int index, double minOpen, double maxOpen) {
		EternalReturnConfig.CaveTweaks caves = EternalReturnConfig.get().worldgen.caves;
		String previous = caves.debugForceCaveType;
		caves.debugForceCaveType = type;
		try {
			ServerWorld world = ctx.getWorld();
			ServerWorld twin = world.getServer().getWorld(TWIN);
			ctx.assertTrue(twin != null, "twin dimension missing (run with preset eternalreturn_test:eternal_return_twin)");
			int chunkX0 = 3000 + index * 400;
			int chunkZ0 = 3000;
			// One chunk generated after everything around it here, first in the twin; another the other way round.
			ChunkPos lastHere = new ChunkPos(chunkX0 + SQUARE / 2, chunkZ0 + SQUARE / 2);
			ChunkPos firstHere = new ChunkPos(chunkX0 + 1, chunkZ0 + 1);
			Chunk twinFirst = twin.getChunk(lastHere.x, lastHere.z, ChunkStatus.CARVERS, true);
			int lavaY = world.getBottomY() + LAVA_ABOVE_BOTTOM;
			CaveRegion region = new CaveRegion(world, chunkX0, chunkZ0, SQUARE, lastHere).measure(lavaY, 64);
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx != 0 || dz != 0) {
						twin.getChunk(firstHere.x + dx, firstHere.z + dz, ChunkStatus.CARVERS, true);
					}
				}
			}
			Chunk twinLast = twin.getChunk(firstHere.x, firstHere.z, ChunkStatus.CARVERS, true);
			int orderDifferences = differences(world, region.chunk(lastHere.x, lastHere.z), twinFirst)
					+ differences(world, region.chunk(firstHere.x, firstHere.z), twinLast);

			List<CaveStartLog.Start> starts = CaveStartLog.recent().stream()
					.filter(start -> start.x() >> 4 >= chunkX0 - 1 && start.x() >> 4 <= chunkX0 + SQUARE && start.z() >> 4 >= chunkZ0 - 1 && start.z() >> 4 <= chunkZ0 + SQUARE)
					.toList();
			CaveTypeSettings settings = caves.types.get(type);
			long outOfRange = starts.stream().filter(start -> !start.type().equals(type) || start.y() < settings.minY || start.y() > settings.maxY).count();
			EternalReturn.LOGGER.info("[cave-test] {}: open {} of underground ({} cave air, {} lava) | per band {} | starts in square {} (wrong type or depth {}) "
							+ "| seams: border mismatch {} vs inside {} | water: open-water breaches {}, other water touched {} | order differences {} | e.g. {}",
					type, String.format("%.2f%%", region.openFraction() * 100), region.caveAir, region.carvedLava, region.bandSummary(), starts.size(), outOfRange,
					String.format("%.4f", region.borderMismatchRate()), String.format("%.4f", region.interiorMismatchRate()),
					region.breachesIntoOpenWater, region.touchingOtherWater, orderDifferences, starts.stream().limit(3).toList());

			ctx.assertTrue(region.caveAir > 1000, type + ": almost no caves carved (" + region.caveAir + " cave air blocks)");
			ctx.assertTrue(region.openFraction() >= minOpen && region.openFraction() <= maxOpen,
					type + ": open share " + region.openFraction() + " outside " + minOpen + ".." + maxOpen);
			ctx.assertTrue(region.bedrockFloorGaps == 0, type + ": " + region.bedrockFloorGaps + " gaps in the bedrock floor");
			ctx.assertTrue(region.caveAirAtOrBelowLavaLevel == 0, type + ": " + region.caveAirAtOrBelowLavaLevel + " cave air at or below the lava level");
			ctx.assertTrue(region.lavaAboveLavaLevel == 0, type + ": " + region.lavaAboveLavaLevel + " lava above the lava level");
			ctx.assertTrue(region.caveAirTooHigh == 0, type + ": " + region.caveAirTooHigh + " cave air within 8 blocks of the top");
			ctx.assertTrue(region.breachesIntoOpenWater == 0, type + ": " + region.breachesIntoOpenWater + " cave blocks touch open water");
			ctx.assertTrue(region.borderMismatchRate() <= region.interiorMismatchRate() * 1.5 + 0.001,
					type + ": caves don't line up across chunk borders (" + region.borderMismatchRate() + " vs " + region.interiorMismatchRate() + " inside chunks)");
			ctx.assertTrue(orderDifferences == 0, type + ": " + orderDifferences + " blocks differ between a chunk generated first and last");
			ctx.assertTrue(!starts.isEmpty() && outOfRange == 0, type + ": " + starts.size() + " logged starts, " + outOfRange + " of the wrong type or outside the depth range");
		} finally {
			caves.debugForceCaveType = previous;
		}
		ctx.complete();
	}

	/** Blocks that differ between the same chunk position in two worlds. */
	private static int differences(ServerWorld world, Chunk a, Chunk b) {
		int count = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		ChunkPos chunkPos = a.getPos();
		for (int y = world.getBottomY(); y < world.getTopY(); y++) {
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					pos.set(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z);
					if (a.getBlockState(pos) != b.getBlockState(pos)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	/**
	 * Every block in eternalreturn:carvable is cut by the carvers' shared code; bedrock and obsidian
	 * (not in the tag) are not. Water is in the tag (vanilla's carver tag has it) but a shape touching
	 * water is skipped whole, the rule that keeps caves out of oceans: checked on its own.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = CAVES_BATCH, tickLimit = 20_000)
	public void carvableTagIsCarved(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<Block> blocks = new ArrayList<>();
		for (RegistryEntry<Block> entry : Registries.BLOCK.iterateEntries(CARVABLE)) {
			blocks.add(entry.value());
		}
		List<Block> solid = blocks.stream().filter(block -> block.getDefaultState().getFluidState().isEmpty()).toList();
		ctx.assertTrue(solid.contains(Blocks.STONE) && solid.contains(Blocks.GRANITE) && solid.contains(Blocks.DIRT),
				"carvable tag is missing vanilla's carver replaceables: " + blocks);

		ProtoChunk chunk = testChunk(world, -6000, -6000);
		int baseY = 0;
		int startX = chunk.getPos().getStartX();
		int startZ = chunk.getPos().getStartZ();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i < solid.size(); i++) {
			chunk.setBlockState(pos.set(startX + 2 + i % 12, baseY + i / 144, startZ + 2 + (i / 12) % 12), solid.get(i).getDefaultState(), false);
		}
		BlockPos bedrock = new BlockPos(startX + 13, baseY, startZ + 13);
		BlockPos obsidian = new BlockPos(startX + 13, baseY, startZ + 12);
		chunk.setBlockState(bedrock, Blocks.BEDROCK.getDefaultState(), false);
		chunk.setBlockState(obsidian, Blocks.OBSIDIAN.getDefaultState(), false);
		carver(world, chunk).ellipsoid(startX + 8, baseY + 0.5, startZ + 8, 10, 3, 10, -1);

		List<String> uncut = new ArrayList<>();
		for (int i = 0; i < solid.size(); i++) {
			BlockState after = chunk.getBlockState(pos.set(startX + 2 + i % 12, baseY + i / 144, startZ + 2 + (i / 12) % 12));
			if (!after.isOf(Blocks.CAVE_AIR)) {
				uncut.add(Registries.BLOCK.getId(solid.get(i)) + " -> " + Registries.BLOCK.getId(after.getBlock()));
			}
		}
		boolean keptBedrock = chunk.getBlockState(bedrock).isOf(Blocks.BEDROCK);
		boolean keptObsidian = chunk.getBlockState(obsidian).isOf(Blocks.OBSIDIAN);

		// Water beside a shape: the whole shape is skipped.
		ProtoChunk wet = testChunk(world, -6010, -6000);
		int wetX = wet.getPos().getStartX() + 8;
		int wetZ = wet.getPos().getStartZ() + 8;
		for (int x = -4; x <= 4; x++) {
			for (int y = -4; y <= 4; y++) {
				for (int z = -4; z <= 4; z++) {
					wet.setBlockState(pos.set(wetX + x, baseY + y, wetZ + z), Blocks.STONE.getDefaultState(), false);
				}
			}
		}
		wet.setBlockState(pos.set(wetX + 3, baseY, wetZ), Blocks.WATER.getDefaultState(), false);
		ChunkCarver wetCarver = carver(world, wet);
		boolean carvedNearWater = wetCarver.ellipsoid(wetX + 0.5, baseY + 0.5, wetZ + 0.5, 2, 2, 2, -1);
		boolean stoneKept = wet.getBlockState(pos.set(wetX, baseY, wetZ)).isOf(Blocks.STONE);
		boolean waterKept = wet.getBlockState(pos.set(wetX + 3, baseY, wetZ)).isOf(Blocks.WATER);

		// At and below the lava level, carved blocks become lava.
		int lavaY = world.getBottomY() + LAVA_ABOVE_BOTTOM;
		ProtoChunk deep = testChunk(world, -6020, -6000);
		int deepX = deep.getPos().getStartX() + 8;
		int deepZ = deep.getPos().getStartZ() + 8;
		for (int y = lavaY - 3; y <= lavaY + 3; y++) {
			deep.setBlockState(pos.set(deepX, y, deepZ), Blocks.STONE.getDefaultState(), false);
		}
		carver(world, deep).ellipsoid(deepX + 0.5, lavaY + 0.5, deepZ + 0.5, 1.2, 3.5, 1.2, -1);
		boolean lavaBelow = deep.getBlockState(pos.set(deepX, lavaY, deepZ)).isOf(Blocks.LAVA)
				&& deep.getBlockState(pos.set(deepX, lavaY - 2, deepZ)).isOf(Blocks.LAVA);
		boolean airAbove = deep.getBlockState(pos.set(deepX, lavaY + 1, deepZ)).isOf(Blocks.CAVE_AIR);

		EternalReturn.LOGGER.info("[cave-test] carvable tag: {} blocks ({} solid, all cut: {}); bedrock kept {}, obsidian kept {}; shape beside water skipped {}; lava at/below y {} {}, air above {}",
				blocks.size(), solid.size(), uncut.isEmpty(), keptBedrock, keptObsidian, !carvedNearWater && stoneKept && waterKept, lavaY, lavaBelow, airAbove);
		ctx.assertTrue(uncut.isEmpty(), "carvable blocks not cut: " + uncut);
		ctx.assertTrue(keptBedrock && keptObsidian, "blocks outside the tag were carved");
		ctx.assertTrue(!carvedNearWater && stoneKept && waterKept, "a shape beside water was carved");
		ctx.assertTrue(lavaBelow && airAbove, "lava level not followed");
		ctx.complete();
	}

	private static ProtoChunk testChunk(ServerWorld world, int chunkX, int chunkZ) {
		return new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.NO_UPGRADE_DATA, world, world.getRegistryManager().get(RegistryKeys.BIOME), null);
	}

	private static ChunkCarver carver(ServerWorld world, Chunk chunk) {
		return new ChunkCarver(chunk, new CarvingMask(world.getHeight(), world.getBottomY()), state -> state.isIn(CARVABLE),
				world.getBottomY() + 1, world.getTopY() - 8, world.getBottomY() + LAVA_ABOVE_BOTTOM, 63, null);
	}

	/**
	 * Normal settings: how open the underground is (to compare with Release 1.6.4, see the cavestats
	 * runs), per-type starts near spawn for the in-game checklist, and chunk generation time with the
	 * cave engine on and off. The timing generates the same squares in the overworld and the twin
	 * dimension (identical terrain), engine on in one and off in the other, then swaps, so terrain and
	 * warm-up cancel out.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = CAVES_BATCH, tickLimit = 2_000_000)
	public void defaultCavesDensityAndTiming(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerWorld twin = world.getServer().getWorld(TWIN);
		EternalReturnConfig.CaveTweaks caves = EternalReturnConfig.get().worldgen.caves;
		int lavaY = world.getBottomY() + LAVA_ABOVE_BOTTOM;

		// Density over the land square (the same square cavestats measures in the control worlds).
		CaveRegion land = new CaveRegion(world, CaveRegion.LAND_CHUNK_X0, CaveRegion.LAND_CHUNK_Z0, CaveRegion.LAND_SIZE, null).measure(lavaY, 64);
		Map<String, Long> startCounts = new TreeMap<>();
		CaveStartLog.recent().stream()
				.filter(start -> start.x() >> 4 >= CaveRegion.LAND_CHUNK_X0 && start.x() >> 4 < CaveRegion.LAND_CHUNK_X0 + CaveRegion.LAND_SIZE
						&& start.z() >> 4 >= CaveRegion.LAND_CHUNK_Z0 && start.z() >> 4 < CaveRegion.LAND_CHUNK_Z0 + CaveRegion.LAND_SIZE)
				.forEach(start -> startCounts.merge(start.type(), 1L, Long::sum));
		EternalReturn.LOGGER.info("[cave-density] default caves, land square ({} chunks): open {} of underground | per band {} | starts {}",
				CaveRegion.LAND_SIZE * CaveRegion.LAND_SIZE, String.format("%.2f%%", land.openFraction() * 100), land.bandSummary(), startCounts);

		// Timing: warm up, then the same squares with the engine on and off.
		new CaveRegion(world, 900, 900, 6, null);
		new CaveRegion(twin, 900, 900, 6, null);
		boolean wasEnabled = caves.enabled;
		boolean wasOld = caves.oldCaves;
		long[] on = new long[2];
		long[] off = new long[2];
		long[] old = new long[2];
		long carverNanos = 0;
		long carverChunks = 0;
		try {
			for (int round = 0; round < 2; round++) {
				int chunkX0 = 1200 + round * 100;
				ServerWorld first = round == 0 ? world : twin;
				ServerWorld second = round == 0 ? twin : world;
				caves.enabled = true;
				long nanosBefore = CaveTiming.nanos();
				long chunksBefore = CaveTiming.chunks();
				CaveRegion withCaves = new CaveRegion(first, chunkX0, 1200, 12, null);
				carverNanos += CaveTiming.nanos() - nanosBefore;
				carverChunks += CaveTiming.chunks() - chunksBefore;
				on[0] += withCaves.generationNanos;
				on[1] += withCaves.chunkCount();
				caves.enabled = false;
				CaveRegion without = new CaveRegion(second, chunkX0, 1200, 12, null);
				off[0] += without.generationNanos;
				off[1] += without.chunkCount();
				caves.oldCaves = true;
				CaveRegion oldCaves = new CaveRegion(round == 0 ? world : twin, chunkX0, 1400, 12, null);
				old[0] += oldCaves.generationNanos;
				old[1] += oldCaves.chunkCount();
				caves.oldCaves = wasOld;
			}
		} finally {
			caves.enabled = wasEnabled;
			caves.oldCaves = wasOld;
		}
		double msOn = on[0] / 1e6 / on[1];
		double msOff = off[0] / 1e6 / off[1];
		double msOld = old[0] / 1e6 / old[1];
		double carverMs = carverChunks == 0 ? 0 : carverNanos / 1e6 / carverChunks;
		EternalReturn.LOGGER.info("[cave-timing] chunk generation to the carving step: new caves {} ms/chunk, no caves {} ms/chunk, old caves only {} ms/chunk "
						+ "({} chunks each); time inside the cave carver {} ms/chunk",
				String.format("%.2f", msOn), String.format("%.2f", msOff), String.format("%.2f", msOld), on[1], String.format("%.2f", carverMs));
		ctx.assertTrue(land.openFraction() > 0.005, "default settings carve almost nothing: " + land.openFraction());
		ctx.assertTrue(carverMs < 20, "cave carver takes " + carverMs + " ms per chunk");
		ctx.complete();
	}

	/**
	 * Reference numbers for any generated world: how open the underground is over the land square
	 * (the same square as defaultCavesDensityAndTiming). Runs in the Release 1.6.4 control world and in
	 * Eternal Return with the old caves (same terrain as the new caves), to compare densities.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "cavestats", tickLimit = 1_000_000)
	public void caveDensityReport(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		// Moderner Beta's carvers put lava 10 blocks above the floor, vanilla's and ours 8; count either as cave.
		CaveRegion land = new CaveRegion(world, CaveRegion.LAND_CHUNK_X0, CaveRegion.LAND_CHUNK_Z0, CaveRegion.LAND_SIZE, null).measure(world.getBottomY() + 10, 64);
		EternalReturn.LOGGER.info("[cave-density] {} (caves {}, old caves {}), land square ({} chunks): open {} of underground | per band {}",
				GameTestWorld.presetProperty(), EternalReturnConfig.get().worldgen.caves.enabled, EternalReturnConfig.get().worldgen.caves.oldCaves,
				CaveRegion.LAND_SIZE * CaveRegion.LAND_SIZE, String.format("%.2f%%", land.openFraction() * 100), land.bandSummary());
		ctx.complete();
	}

	/** All configured types are known and have sane settings (guards against typos in the config). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = CAVES_BATCH)
	public void typeSettingsAreSane(TestContext ctx) {
		for (CaveType type : CaveTypes.ALL) {
			CaveTypeSettings settings = EternalReturnConfig.get().worldgen.caves.types.get(type.id());
			ctx.assertTrue(settings != null, "no settings for " + type.id());
			ctx.assertTrue(settings.minY <= settings.maxY && settings.minRadius <= settings.maxRadius && settings.minRadius > 0,
					type.id() + " has a bad depth or radius range");
		}
		ctx.complete();
	}
}
