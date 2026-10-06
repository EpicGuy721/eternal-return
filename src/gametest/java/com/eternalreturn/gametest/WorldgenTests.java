package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.carver.DebugTunnelCarver;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Worldgen checks. These run only in the worldgen test runs (Moderner Beta and vanilla generator
 * worlds), never in the flat world.
 */
public class WorldgenTests implements FabricGameTest {
	/** Chunk x positions along the tunnel row (chunk z = 0), spread out so they land in different biomes. */
	private static final int[] CHUNK_XS = {-31, -7, 0, 9, 40, 77, 150, 300};
	private static final int TUNNEL_Y = 20;
	/** A parallel row two chunks away, where no tunnel is carved: shows what the ground looks like there naturally. */
	private static final int CONTROL_Z = DebugTunnelCarver.TUNNEL_Z + 32;

	/**
	 * Generates chunks along the debug tunnel's row and counts open (air or fluid) blocks in its 3x3
	 * cross-section. With the tunnel on, it must be open nearly everywhere; with it off, mostly solid.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.WORLDGEN_BATCH, tickLimit = 12000)
	public void debugTunnelFollowsToggle(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		boolean expected = EternalReturnConfig.get().worldgen.debugTunnel;
		String generator = world.getChunkManager().getChunkGenerator().getClass().getName();
		String preset = GameTestWorld.presetProperty();
		if (preset != null && !preset.startsWith("minecraft:")) {
			ctx.assertTrue(generator.contains("modernerbeta"), "expected a Moderner Beta world, got generator " + generator);
		}

		int tunnelOpen = 0;
		int controlOpen = 0;
		int cells = 0;
		List<String> perChunk = new ArrayList<>();
		Map<String, Integer> solidInTunnel = new TreeMap<>();
		for (int chunkX : CHUNK_XS) {
			world.getChunk(chunkX, 0, ChunkStatus.FULL, true);
			world.getChunk(chunkX, CONTROL_Z >> 4, ChunkStatus.FULL, true);
			int open = 0;
			for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
				for (int y = TUNNEL_Y - 1; y <= TUNNEL_Y + 1; y++) {
					for (int dz = -1; dz <= 1; dz++) {
						cells++;
						BlockState state = world.getBlockState(new BlockPos(x, y, DebugTunnelCarver.TUNNEL_Z + dz));
						if (isOpen(state)) {
							open++;
						} else {
							solidInTunnel.merge(state.getBlock().getTranslationKey().replace("block.minecraft.", ""), 1, Integer::sum);
						}
						if (isOpen(world.getBlockState(new BlockPos(x, y, CONTROL_Z + dz)))) {
							controlOpen++;
						}
					}
				}
			}
			tunnelOpen += open;
			String biome = world.getBiome(new BlockPos(chunkX * 16 + 8, 64, DebugTunnelCarver.TUNNEL_Z))
					.getKey().map(key -> key.getValue().toString()).orElse("?");
			perChunk.add("chunk " + chunkX + " (" + biome + "): " + open + "/144");
		}

		EternalReturn.LOGGER.info("[worldgen-test] generator={} preset={} debugTunnel={} | tunnel row open {}/{} | control row open {}/{} | {} | solid blocks left in tunnel row: {}",
				generator, preset, expected, tunnelOpen, cells, controlOpen, cells, perChunk, solidInTunnel);

		double ratio = (double) tunnelOpen / cells;
		if (expected) {
			ctx.assertTrue(ratio >= 0.97, "debug tunnel missing: only " + tunnelOpen + "/" + cells + " open " + perChunk);
		} else {
			ctx.assertTrue(ratio <= 0.5, "tunnel row is open although the tunnel is off: " + tunnelOpen + "/" + cells + " " + perChunk);
		}
		ctx.complete();
	}

	private static boolean isOpen(BlockState state) {
		return state.isAir() || !state.getFluidState().isEmpty();
	}
}
