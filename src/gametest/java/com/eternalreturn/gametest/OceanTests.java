package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.ocean.OceanFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Oceans (worldgen expansion 2, phase 5; OceanFeature): around spots of sea near spawn, generated in full, the slope walls
 * between the steps are bare stone, the shelf is sand with ripple rows, the middle plain has clay patches, the abyss keeps
 * its gravel; glow lichen grows on the walls; and the counts of the rarer pieces are logged. Eternal Return run (batch oceans).
 */
public class OceanTests implements FabricGameTest {
	public static final String BATCH = "oceans";

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 12000)
	public void depthZonesDiffer(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Map<String, Map<String, Integer>> counts = new TreeMap<>();
		Map<String, Integer> pieces = new TreeMap<>();
		int[][] spots = {{32, 0}, {96, 0}, {0, 256}, {-200, 300}, {300, -200}, {600, 600}, {-600, -400}, {900, -900}};
		for (int[] spot : spots) {
			int ccx = spot[0] >> 4;
			int ccz = spot[1] >> 4;
			for (int cx = ccx - 4; cx <= ccx + 4; cx++) {
				for (int cz = ccz - 4; cz <= ccz + 4; cz++) {
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
				}
			}
			for (int x = (ccx - 3) * 16; x < (ccx + 4) * 16; x++) {
				for (int z = (ccz - 3) * 16; z < (ccz + 4) * 16; z++) {
					this.count(world, x, z, counts, pieces);
				}
			}
		}
		counts.forEach((zone, blocks) -> EternalReturn.LOGGER.info("[oceans] {}: {}", zone, blocks));
		EternalReturn.LOGGER.info("[oceans] pieces: {}", pieces);
		List<String> problems = new ArrayList<>();
		share(counts, "wall", "stone", 0.8, problems);
		share(counts, "shelf", "sand", 0.8, problems);
		share(counts, "middle", "clay", 0.04, problems);
		share(counts, "abyss", "gravel", 0.7, problems);
		if (pieces.getOrDefault("ripples", 0) < 100) {
			problems.add("only " + pieces.getOrDefault("ripples", 0) + " ripple blocks");
		}
		if (pieces.getOrDefault("glow_lichen", 0) < 20) {
			problems.add("only " + pieces.getOrDefault("glow_lichen", 0) + " glow lichen on the walls");
		}
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	/** Fails unless this block is at least this share of the zone's floor tops. */
	private static void share(Map<String, Map<String, Integer>> counts, String zone, String block, double least, List<String> problems) {
		Map<String, Integer> tops = counts.getOrDefault(zone, Map.of());
		int total = tops.values().stream().mapToInt(Integer::intValue).sum();
		int some = tops.getOrDefault(block, 0);
		if (total < 200 || some < total * least) {
			problems.add(zone + ": " + some + " " + block + " of " + total + " (expected " + (int) (least * 100) + "% or more)");
		}
	}

	private void count(ServerWorld world, int x, int z, Map<String, Map<String, Integer>> counts, Map<String, Integer> pieces) {
		int floor = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
		BlockPos top = new BlockPos(x, floor, z);
		BlockState above = world.getBlockState(top.up());
		// Pieces anywhere in the column near the floor or the beach.
		for (int y = floor - 25; y <= Math.max(floor, world.getSeaLevel()) + 6; y++) {
			BlockState state = world.getBlockState(new BlockPos(x, y, z));
			String id = Registries.BLOCK.getId(state.getBlock()).getPath();
			if (state.isOf(Blocks.GLOW_LICHEN) || state.isOf(Blocks.BONE_BLOCK) || state.isOf(Blocks.BARREL) || state.isOf(Blocks.SEA_PICKLE)
					|| state.isIn(BlockTags.LOGS) && !world.getBlockState(new BlockPos(x, y, z).up()).isIn(BlockTags.LEAVES)) {
				String where = state.isIn(BlockTags.LOGS) ? (y < world.getSeaLevel() - 1 ? "logs under water" : y <= world.getSeaLevel() + 3 ? "logs at the shore" : null) : id;
				if (where != null) {
					pieces.merge(where, 1, Integer::sum);
				}
			}
			// A tide pool's water: sea water on stone above the sea, off the sea's own biome.
			if (state.isOf(Blocks.WATER) && y >= world.getSeaLevel() - 1 && world.getBlockState(new BlockPos(x, y - 1, z)).isIn(BlockTags.BASE_STONE_OVERWORLD)
					&& !world.getBiome(new BlockPos(x, y, z)).isIn(BiomeTags.IS_OCEAN)) {
				pieces.merge("tide pool water", 1, Integer::sum);
			}
		}
		if (floor >= world.getSeaLevel() - 1 || !world.getBiome(top).isIn(BiomeTags.IS_OCEAN)
				|| !(above.isOf(Blocks.WATER) || above.isOf(Blocks.SEAGRASS) || above.isOf(Blocks.TALL_SEAGRASS) || above.isOf(Blocks.KELP) || above.isOf(Blocks.KELP_PLANT)
				|| above.isOf(Blocks.SAND) || above.isOf(Blocks.SEA_PICKLE) || above.isOf(Blocks.GLOW_LICHEN))) {
			return;
		}
		int steep = 0;
		for (int[] d : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
			steep = Math.max(steep, Math.abs(world.getTopY(Heightmap.Type.OCEAN_FLOOR, x + d[0], z + d[1]) - 1 - floor));
		}
		BlockState state = world.getBlockState(top);
		// A ripple: a ridge of sand on the shelf's sand.
		if (state.isOf(Blocks.SAND) && world.getBlockState(top.down()).isOf(Blocks.SAND) && floor >= OceanFeature.SHELF && steep <= 2) {
			pieces.merge("ripples", 1, Integer::sum);
		}
		String zone = steep >= OceanFeature.STEEP + 2 ? "wall" : steep >= OceanFeature.STEEP ? null
				: floor >= OceanFeature.SHELF + 2 ? "shelf" : floor >= OceanFeature.ABYSS + 2 && floor < OceanFeature.SHELF - 2 ? "middle"
				: floor < OceanFeature.ABYSS - 2 ? "abyss" : null;
		if (zone == null || state.isOf(Blocks.BONE_BLOCK) || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.PLANKS)) {
			return;
		}
		String name = state.isIn(BlockTags.BASE_STONE_OVERWORLD) || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.MOSSY_COBBLESTONE)
				? "stone" : Registries.BLOCK.getId(state.getBlock()).getPath();
		counts.computeIfAbsent(zone, k -> new TreeMap<>()).merge(name, 1, Integer::sum);
	}
}
