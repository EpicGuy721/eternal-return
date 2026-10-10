package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Overhang undersides: for land squares in several biomes, every place where rock hangs over a gap with
 * rock below it again, near the surface. The gap is terrain (plain air) or a cave (cave air, written by
 * carvers); trees and plants count as gap. An underside is "visible" when the ground within a few blocks
 * lies lower than it, so it can be seen from outside: a terrain overhang or the roof of a cave mouth.
 * Undersides at the same height that touch form a flat patch; big flat patches look unnatural.
 * Moderner Beta's terrain alone leaves most terrain overhangs with a near-flat underside (on the test
 * seed, 36 percent of visible terrain undersides lay in flat patches of 12 or more columns, the biggest
 * 94); OverhangRounder arches them, so at most a fifth may lie in such patches and none may pass 48.
 * Cave roofs are counted too, for the record.
 */
public class OverhangTests implements FabricGameTest {
	public static final String OVERHANG_BATCH = "overhang";
	private static final String[] BIOMES = {"moderner_beta:early_release_extreme_hills", "minecraft:forest", "moderner_beta:early_release_taiga",
			"minecraft:savanna", "minecraft:jungle", "moderner_beta:early_release_ice_plains", "minecraft:cherry_grove"};
	private static final int SQUARE = 8;
	private static final int REACH = 5;

	private record Underside(int y, boolean cave, int floor) {
	}

	private record Patch(int size, int y, boolean cave, boolean visible, BlockPos at, int spanX, int spanZ) {
	}

	private static boolean gap(BlockState state) {
		return state.getFluidState().isEmpty() && (state.isAir() || state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS) || state.isReplaceable());
	}

	private static boolean solid(BlockState state) {
		return state.getFluidState().isEmpty() && !gap(state);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = OVERHANG_BATCH, tickLimit = 2_000_000)
	public void overhangUndersides(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Map<String, String> summary = new TreeMap<>();
		Map<Integer, Integer> visibleFlatSizes = new TreeMap<>();
		List<Patch> biggest = new ArrayList<>();
		int visibleTerrain = 0;
		int flatTerrain = 0;
		int biggestTerrain = 0;
		// A place to stand under the tallest visible terrain overhang, for the README checklist.
		String standUnder = "none";
		int tallest = 0;
		for (String biome : BIOMES) {
			ChunkPos centre = OreCensusTests.findChunkInside(world, biome, 8);
			if (centre == null) {
				continue;
			}
			int x0 = (centre.x - SQUARE / 2) * 16;
			int z0 = (centre.z - SQUARE / 2) * 16;
			int side = SQUARE * 16;
			int[][] ground = new int[side + 2 * REACH][side + 2 * REACH];
			for (int i = 0; i < ground.length; i++) {
				for (int j = 0; j < ground.length; j++) {
					ground[i][j] = generator.getHeight(x0 - REACH + i, z0 - REACH + j, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
				}
			}
			Map<Long, Underside> undersides = new HashMap<>();
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int cx = centre.x - SQUARE / 2; cx < centre.x + SQUARE / 2; cx++) {
				for (int cz = centre.z - SQUARE / 2; cz < centre.z + SQUARE / 2; cz++) {
					Chunk chunk = world.getChunk(cx, cz, ChunkStatus.CARVERS, true);
					for (int x = cx * 16; x < cx * 16 + 16; x++) {
						for (int z = cz * 16; z < cz * 16 + 16; z++) {
							int top = ground[x - x0 + REACH][z - z0 + REACH] + 12;
							// Down from above the ground: rock, then a gap, then rock again within 60 blocks of the top.
							boolean inSolid = false;
							for (int y = top; y > top - 50 && y > world.getBottomY(); y--) {
								BlockState state = chunk.getBlockState(pos.set(x, y, z));
								if (solid(state)) {
									inSolid = true;
									continue;
								}
								if (inSolid && gap(state)) {
									int gapBottom = y;
									while (gapBottom > top - 70 && gap(chunk.getBlockState(pos.set(x, gapBottom, z)))) {
										gapBottom--;
									}
									if (solid(chunk.getBlockState(pos.set(x, gapBottom, z)))) {
										undersides.put(BlockPos.asLong(x, 0, z), new Underside(y + 1, chunk.getBlockState(pos.set(x, y, z)).isOf(Blocks.CAVE_AIR), gapBottom + 1));
									}
									break;
								}
								if (inSolid) {
									break;
								}
							}
						}
					}
				}
			}
			// Flat patches: undersides at the same height joined side by side.
			Map<Long, Boolean> seen = new HashMap<>();
			int[] counts = new int[6];
			for (Map.Entry<Long, Underside> entry : undersides.entrySet()) {
				if (seen.containsKey(entry.getKey())) {
					continue;
				}
				Underside first = entry.getValue();
				if (!first.cave() && first.y() - first.floor() > tallest) {
					int x = BlockPos.unpackLongX(entry.getKey());
					int z = BlockPos.unpackLongZ(entry.getKey());
					boolean open = false;
					for (int i = -REACH; i <= REACH && !open; i++) {
						for (int j = -REACH; j <= REACH && !open; j++) {
							open = ground[x - x0 + REACH + i][z - z0 + REACH + j] < first.y() - 1;
						}
					}
					if (open) {
						tallest = first.y() - first.floor();
						standUnder = x + " " + first.floor() + " " + z + " (" + biome + ", underside at y " + first.y() + ")";
					}
				}
				ArrayDeque<Long> queue = new ArrayDeque<>();
				queue.add(entry.getKey());
				seen.put(entry.getKey(), true);
				int size = 0;
				boolean visible = false;
				int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
				while (!queue.isEmpty()) {
					long key = queue.poll();
					int x = BlockPos.unpackLongX(key);
					int z = BlockPos.unpackLongZ(key);
					size++;
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
					for (int i = -REACH; i <= REACH && !visible; i++) {
						for (int j = -REACH; j <= REACH && !visible; j++) {
							visible = ground[x - x0 + REACH + i][z - z0 + REACH + j] < first.y() - 1;
						}
					}
					for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						long next = BlockPos.asLong(x + d[0], 0, z + d[1]);
						Underside other = undersides.get(next);
						if (other != null && other.y() == first.y() && !seen.containsKey(next)) {
							seen.put(next, true);
							queue.add(next);
						}
					}
				}
				if (visible && !first.cave()) {
					visibleTerrain += size;
					flatTerrain += size >= 12 ? size : 0;
					biggestTerrain = Math.max(biggestTerrain, size);
				}
				int kind = (first.cave() ? 0 : 3);
				counts[kind] += size;
				if (visible) {
					counts[kind + 1] += size;
					if (size >= 12) {
						counts[kind + 2] += size;
						visibleFlatSizes.merge(Math.min(size / 8 * 8, 200), 1, Integer::sum);
						biggest.add(new Patch(size, first.y(), first.cave(), true, new BlockPos((minX + maxX) / 2, first.y(), (minZ + maxZ) / 2), maxX - minX + 1, maxZ - minZ + 1));
					}
				}
			}
			summary.put(biome, "cave undersides " + counts[0] + " (visible " + counts[1] + ", in flat patches of 12+ " + counts[2] + "), terrain undersides " + counts[3]
					+ " (visible " + counts[4] + ", in flat patches of 12+ " + counts[5] + ")");
		}
		biggest.sort((a, b) -> b.size() - a.size());
		EternalReturn.LOGGER.info("[overhang] {} | visible flat patches by size {} | visible terrain undersides {}, {} in flat patches of 12+, biggest {} | stand under {} | biggest patches {}",
				summary, visibleFlatSizes, visibleTerrain, flatTerrain, biggestTerrain, standUnder, biggest.subList(0, Math.min(20, biggest.size())));
		String out = System.getProperty("eternalreturn.worldmap.out");
		if (out != null) {
			try {
				java.nio.file.Files.createDirectories(java.nio.file.Path.of(out));
				for (int index = 0; index < Math.min(8, biggest.size()); index++) {
					Patch patch = biggest.get(index);
					for (int axis = 0; axis < 2; axis++) {
						java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(96 * 4, 64 * 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
						for (int d = -48; d < 48; d++) {
							for (int dy = -40; dy < 24; dy++) {
								BlockState state = world.getBlockState(patch.at().add(axis == 0 ? d : 0, dy, axis == 0 ? 0 : d));
								int color = state.isOf(Blocks.CAVE_AIR) ? 0x202020 : state.isAir() ? 0xBFD8F0 : !state.getFluidState().isEmpty() ? 0x3050D0
										: state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS) ? 0x2E6B2E : state.isOf(Blocks.GRASS_BLOCK) ? 0x5A9A3A
										: state.isOf(Blocks.DIRT) ? 0x7A5A3A : 0x808080;
								if (d == 0 && dy == 0) {
									color = 0xFF0000;
								}
								for (int px = 0; px < 4; px++) {
									for (int py = 0; py < 4; py++) {
										image.setRGB((d + 48) * 4 + px, (23 - dy) * 4 + py, color);
									}
								}
							}
						}
						javax.imageio.ImageIO.write(image, "png", java.nio.file.Path.of(out, "patch_" + index + (axis == 0 ? "x" : "z") + "_" + patch.size() + (patch.cave() ? "_cave_" : "_terrain_")
								+ patch.at().getX() + "_" + patch.at().getY() + "_" + patch.at().getZ() + ".png").toFile());
					}
				}
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
		}
		ctx.assertTrue(visibleTerrain > 300, "only " + visibleTerrain + " visible terrain undersides: the squares no longer hold enough overhangs to judge");
		ctx.assertTrue(flatTerrain * 5 <= visibleTerrain && biggestTerrain <= 48, flatTerrain + " of " + visibleTerrain
				+ " visible terrain undersides lie in flat patches of 12 or more columns (biggest " + biggestTerrain + "): overhangs have flat bottoms again");
		ctx.complete();
	}
}
