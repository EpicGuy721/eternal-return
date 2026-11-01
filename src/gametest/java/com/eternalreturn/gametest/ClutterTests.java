package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.stone.BiomeStoneProcessor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
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
 * Clutter (com.eternalreturn.worldgen.clutter): boulders on the land of Eternal Return worlds, made of the biome's
 * stone, and none in other worlds.
 */
public class ClutterTests implements FabricGameTest {
	public static final String CLUTTER_BATCH = "clutter";
	private static final RegistryKey<PlacedFeature> BOULDER = RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id("boulder"));
	/** Ground a boulder can rest on; stone straight on top of these is never the terrain's own. */
	private static final Set<Block> TOPSOIL = Set.of(Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.RED_SAND, Blocks.PODZOL, Blocks.MYCELIUM,
			Blocks.COARSE_DIRT, Blocks.SNOW_BLOCK);

	private static boolean isStone(BlockState state) {
		return state.isOf(Blocks.STONE) || state.isIn(com.eternalreturn.worldgen.stone.BiomeStoneSurface.HOST_STONES);
	}

	/**
	 * Around eight land spots on the test seed (sampled from a grid out to 1,536 blocks), 5 x 5 chunks each are
	 * generated in full. A boulder block is stone or a host stone resting straight on topsoil (grass, sand, podzol and
	 * so on). Boulders must turn up in at least a fifth of the land chunks, and nine boulders in ten must be the ground's
	 * own stone somewhere under them (BiomeStoneProcessor reads it under each boulder's middle). How many are the stone
	 * of the biome they are in is logged.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = CLUTTER_BATCH, tickLimit = 2_000_000)
	public void bouldersStandOnTheLand(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		List<ChunkPos> centres = new ArrayList<>();
		for (int gx = -3; gx <= 3 && centres.size() < 8; gx++) {
			for (int gz = -3; gz <= 3 && centres.size() < 8; gz++) {
				int x = gx * 512 + 8;
				int z = gz * 512 + 8;
				int top = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
				var biome = generator.getBiomeSource().getBiome(x >> 2, top >> 2, z >> 2, noise.getMultiNoiseSampler());
				if (top > generator.getSeaLevel() + 2 && !biome.isIn(BiomeTags.IS_OCEAN) && !biome.isIn(BiomeTags.IS_RIVER) && !biome.isIn(BiomeTags.IS_BEACH)) {
					centres.add(new ChunkPos(x >> 4, z >> 4));
				}
			}
		}
		ctx.assertTrue(centres.size() >= 4, "only " + centres.size() + " land spots found");
		int landChunks = 0;
		Set<ChunkPos> withBoulders = new HashSet<>();
		// Each boulder block on topsoil, with the ground's stone under it (the first stone or host stone below the topsoil).
		Map<BlockPos, Block[]> found = new java.util.HashMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (ChunkPos centre : centres) {
			for (int cx = centre.x - 2; cx <= centre.x + 2; cx++) {
				for (int cz = centre.z - 2; cz <= centre.z + 2; cz++) {
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
				}
			}
			for (int cx = centre.x - 2; cx <= centre.x + 2; cx++) {
				for (int cz = centre.z - 2; cz <= centre.z + 2; cz++) {
					boolean land = false;
					for (int x = cx * 16; x < cx * 16 + 16; x++) {
						for (int z = cz * 16; z < cz * 16 + 16; z++) {
							int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
							land |= top > generator.getSeaLevel() && world.getFluidState(pos.set(x, top - 1, z)).isEmpty();
							for (int y = Math.max(top - 12, world.getBottomY() + 1); y < top; y++) {
								BlockState state = world.getBlockState(pos.set(x, y, z));
								if (!isStone(state) || !TOPSOIL.contains(world.getBlockState(pos.set(x, y - 1, z)).getBlock())) {
									continue;
								}
								withBoulders.add(new ChunkPos(cx, cz));
								Block ground = null;
								for (int down = y - 2; down > y - 16 && ground == null; down--) {
									BlockState below = world.getBlockState(pos.set(x, down, z));
									if (isStone(below)) {
										ground = below.getBlock();
									}
								}
								found.put(new BlockPos(x, y, z), new Block[]{state.getBlock(), ground});
							}
						}
					}
					if (land) {
						landChunks++;
					}
				}
			}
		}
		// Boulders: blocks of one stone touching side by side or corner to corner, within two blocks up or down. One
		// matches when its stone is the ground's somewhere under it; a boulder across a biome border stands partly over
		// the neighbour's stone, whose border wanders a block or two from column to column.
		int boulders = 0;
		int matching = 0;
		int ofBiome = 0;
		Map<String, Integer> byStone = new TreeMap<>();
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos start : found.keySet()) {
			if (!seen.add(start)) {
				continue;
			}
			Block stone = found.get(start)[0];
			boolean matches = false;
			java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(List.of(start));
			while (!queue.isEmpty()) {
				BlockPos block = queue.poll();
				matches |= found.get(block)[1] == stone;
				for (BlockPos near : BlockPos.iterate(block.add(-1, -2, -1), block.add(1, 2, 1))) {
					Block[] other = found.get(near);
					if (other != null && other[0] == stone && seen.add(near.toImmutable())) {
						queue.add(near.toImmutable());
					}
				}
			}
			boulders++;
			byStone.merge(stone.getTranslationKey(), 1, Integer::sum);
			if (matches) {
				matching++;
			} else {
				EternalReturn.LOGGER.info("[clutter] boulder at {} is {}, ground {}", start.toShortString(), stone, found.get(start)[1]);
			}
			if (stone == BiomeStoneProcessor.stoneOf(world.getBiome(start))) {
				ofBiome++;
			}
		}
		EternalReturn.LOGGER.info("[clutter] {} land spots, {} land chunks, boulders in {} of them; {} boulders ({} on their own stone, {} of the biome they are in), by stone {}",
				centres.size(), landChunks, withBoulders.size(), boulders, matching, ofBiome, byStone);
		ctx.assertTrue(withBoulders.size() * 5 >= landChunks, "boulders in only " + withBoulders.size() + " of " + landChunks + " land chunks");
		ctx.assertTrue(matching * 10 >= boulders * 9, "only " + matching + " of " + boulders + " boulders are the ground's stone");
		ctx.complete();
	}

	/** Outside Eternal Return worlds the boulder feature places nothing (flat runs). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "clutter_other_worlds")
	public void noBouldersInOtherWorlds(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		PlacedFeature boulder = world.getRegistryManager().get(RegistryKeys.PLACED_FEATURE).getOrThrow(BOULDER);
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
		BlockPos chunkStart = new ChunkPos(origin).getStartPos().withY(origin.getY());
		ctx.assertFalse(boulder.generate(world, world.getChunkManager().getChunkGenerator(), Random.create(1), chunkStart),
				"the boulder feature placed something in a world that isn't Eternal Return");
		ctx.complete();
	}
}
