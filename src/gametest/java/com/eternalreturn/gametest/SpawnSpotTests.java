package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.survival.RandomSpawns;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.SpawnLocating;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Random spawn spots in an Eternal Return world (seed 173164): every spot RandomSpawns picks has solid ground with a
 * full top and two free, dry blocks above it. Vanilla's spawn finder, which trusts the chunk's height map, is checked
 * on the same chunks and its bad spots logged, for comparison.
 */
public class SpawnSpotTests implements FabricGameTest {
	public static final String BATCH = "spawn_spots";
	private static final int PLACES = 120;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100_000)
	public void spawnSpotsHaveRoom(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Random random = Random.create(173164L);
		int found = 0;
		int vanillaFound = 0;
		List<String> vanillaBad = new ArrayList<>();
		List<String> bad = new ArrayList<>();
		for (int i = 0; i < PLACES; i++) {
			BlockPos candidate = RandomSpawns.candidate(world, random);
			if (candidate == null) {
				continue;
			}
			ChunkPos chunk = new ChunkPos(candidate);
			BlockPos spot = RandomSpawns.standingSpot(world, chunk);
			if (spot != null) {
				found++;
				if (!RandomSpawns.stillStanding(world, spot)) {
					bad.add(describe(world, spot));
				}
			}
			BlockPos vanilla = SpawnLocating.findServerSpawnPoint(world, chunk);
			if (vanilla != null) {
				vanillaFound++;
				if (!roomy(world, vanilla)) {
					vanillaBad.add(describe(world, vanilla));
				}
			}
		}
		EternalReturn.LOGGER.info("[spawns] {} places: spots found {}, bad {}; vanilla's finder found {}, bad {}: {}", PLACES, found, bad.size(), vanillaFound,
				vanillaBad.size(), vanillaBad.subList(0, Math.min(12, vanillaBad.size())));
		ctx.assertTrue(bad.isEmpty(), "spawn spots without room: " + bad);
		ctx.assertTrue(found >= PLACES * 3 / 4, "a spot in most dry chunks: " + found + " of " + PLACES);
		ctx.complete();
	}

	private static boolean roomy(ServerWorld world, BlockPos feet) {
		return RandomSpawns.stillStanding(world, feet);
	}

	private static String describe(ServerWorld world, BlockPos feet) {
		BlockState below = world.getBlockState(feet.down());
		return feet.toShortString() + " below " + name(below) + (Block.isFaceFullSquare(below.getCollisionShape(world, feet.down()), Direction.UP) ? "" : "(not full)")
				+ " feet " + name(world.getBlockState(feet)) + " head " + name(world.getBlockState(feet.up()))
				+ " heightmap " + world.getChunk(feet).sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, feet.getX() & 15, feet.getZ() & 15)
				+ " world surface " + world.getChunk(feet).sampleHeightmap(Heightmap.Type.WORLD_SURFACE, feet.getX() & 15, feet.getZ() & 15);
	}

	private static String name(BlockState state) {
		return net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
	}
}
