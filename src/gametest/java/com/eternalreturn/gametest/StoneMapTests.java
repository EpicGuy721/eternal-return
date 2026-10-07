package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.stone.HardenedBlocks;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Biome stone map for the map tool (runWorldmapStone): a top-down cut at y=30 through a square of fully
 * decorated chunks, chosen near spawn to hold as many host stones as possible, coloured by block with a
 * legend and each block's share, biome borders drawn as thin lines. Also finds one spot per host stone
 * for the in-game checklist. Writes stone_y30.png and stone.json to eternalreturn.worldmap.out.
 */
public class StoneMapTests implements FabricGameTest {
	public static final String STONEMAP_BATCH = "stonemap";
	private static final int MAP_Y = 30;
	private static final int SIZE_CHUNKS = 40;
	private static final int SEARCH = 3072;

	private static final Map<String, Integer> COLORS = new LinkedHashMap<>();

	static {
		COLORS.put("stone", 0x7F7F7F);
		COLORS.put("andesite", 0x5E8C6A);
		COLORS.put("diorite", 0xE8E8F0);
		COLORS.put("granite", 0xB0604A);
		COLORS.put("hardened_sandstone", 0xE6D27A);
		COLORS.put("red_sandstone", 0xD2691E);
		COLORS.put("hardened_packed_ice", 0x8DB4FF);
		COLORS.put("tuff", 0x4A4A55);
		COLORS.put("dirt", 0x6B4A2A);
		COLORS.put("gravel", 0x9A8A80);
		COLORS.put("ores", 0xFF00FF);
		COLORS.put("cave (air)", 0x000000);
		COLORS.put("water", 0x2050D0);
		COLORS.put("lava", 0xFF8C00);
		COLORS.put("other", 0x40E0D0);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = STONEMAP_BATCH, tickLimit = 2_000_000)
	public void renderStoneMap(TestContext ctx) throws IOException {
		ServerWorld world = ctx.getWorld();
		Path out = Path.of(System.getProperty("eternalreturn.worldmap.out"));
		Files.createDirectories(out);
		Map<String, Block> table = BiomeStoneTests.expectedTable();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();

		// ---- the square: the window near spawn (from the biome source, every 32 blocks) with the most host stones
		int size = SIZE_CHUNKS * 16;
		int bestX = 0;
		int bestZ = 0;
		int bestScore = -1;
		for (int wx = -SEARCH; wx + size <= SEARCH; wx += 128) {
			for (int wz = -SEARCH; wz + size <= SEARCH; wz += 128) {
				Map<Block, Integer> counts = new LinkedHashMap<>();
				for (int x = wx; x < wx + size; x += 32) {
					for (int z = wz; z < wz + size; z += 32) {
						String biome = BiomeStoneTests.id(generator.getBiomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(MAP_Y),
								BiomeCoords.fromBlock(z), noise.getMultiNoiseSampler()));
						counts.merge(table.getOrDefault(biome, Blocks.STONE), 1, Integer::sum);
					}
				}
				// Host stones present with at least 4 samples (about 4000 blocks of area), ties to the nearer window.
				int score = (int) counts.values().stream().filter(count -> count >= 4).count() * 1_000_000 - (Math.abs(wx + size / 2) + Math.abs(wz + size / 2));
				if (score > bestScore) {
					bestScore = score;
					bestX = wx;
					bestZ = wz;
				}
			}
		}
		int x0 = Math.floorDiv(bestX, 16) * 16;
		int z0 = Math.floorDiv(bestZ, 16) * 16;

		// ---- the cut at y=30 through fully decorated chunks (ores and stone blobs included)
		int legendWidth = 330;
		BufferedImage image = new BufferedImage(size + legendWidth, Math.max(size, 420), BufferedImage.TYPE_INT_RGB);
		Map<String, Integer> counts = new LinkedHashMap<>();
		COLORS.keySet().forEach(key -> counts.put(key, 0));
		String[][] biomes = new String[size][size];
		BlockPos.Mutable pos = new BlockPos.Mutable();
		SurfaceBiomes surfaceBiomes = new SurfaceBiomes(world);
		for (int cx = 0; cx < SIZE_CHUNKS; cx++) {
			for (int cz = 0; cz < SIZE_CHUNKS; cz++) {
				Chunk chunk = world.getChunk((x0 >> 4) + cx, (z0 >> 4) + cz, ChunkStatus.FULL, true);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						int px = cx * 16 + x;
						int pz = cz * 16 + z;
						pos.set(x0 + px, MAP_Y, z0 + pz);
						String key = category(chunk.getBlockState(pos));
						counts.merge(key, 1, Integer::sum);
						image.setRGB(px, pz, COLORS.get(key));
						biomes[px][pz] = BiomeStoneTests.id(surfaceBiomes.at(pos));
					}
				}
			}
		}
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				boolean border = (x + 1 < size && !biomes[x][z].equals(biomes[x + 1][z])) || (z + 1 < size && !biomes[x][z].equals(biomes[x][z + 1]));
				if (border) {
					image.setRGB(x, z, 0xFFFFFF);
				}
			}
		}
		Map<String, String> shares = new LinkedHashMap<>();
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x202020));
		g.fillRect(size, 0, legendWidth, image.getHeight());
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
		g.setColor(Color.WHITE);
		g.drawString("y = " + MAP_Y + ", x " + x0 + " to " + (x0 + size) + ", z " + z0 + " to " + (z0 + size), size + 10, 18);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		g.drawString("seed " + world.getSeed() + "; white lines = biome borders", size + 10, 36);
		int y = 62;
		double total = size * size;
		for (Map.Entry<String, Integer> entry : counts.entrySet()) {
			String share = String.format("%.1f%%", entry.getValue() / total * 100);
			shares.put(entry.getKey(), share);
			g.setColor(new Color(COLORS.get(entry.getKey())));
			g.fillRect(size + 12, y - 12, 16, 16);
			g.setColor(Color.GRAY);
			g.drawRect(size + 12, y - 12, 16, 16);
			g.setColor(Color.WHITE);
			g.drawString(entry.getKey().replace('_', ' ') + "  " + share, size + 36, y + 1);
			y += 22;
		}
		g.dispose();
		ImageIO.write(image, "png", out.resolve("stone_y" + MAP_Y + ".png").toFile());

		// ---- biomes in the square, and one checklist spot per host stone (the nearest biome interior to spawn)
		Map<String, Integer> biomeColumns = new TreeMap<>();
		for (String[] row : biomes) {
			for (String biome : row) {
				biomeColumns.merge(biome, 1, Integer::sum);
			}
		}
		Map<String, Object> spots = new LinkedHashMap<>();
		Set<Block> done = new HashSet<>();
		for (Map.Entry<String, Block> entry : table.entrySet()) {
			if (!done.add(entry.getValue())) {
				continue;
			}
			ChunkPos centre = OreCensusTests.findChunkInside(world, entry.getKey());
			if (centre == null) {
				spots.put(BiomeStoneTests.name(entry.getValue()), "no " + entry.getKey() + " interior found");
				continue;
			}
			int x = centre.getStartX() + 8;
			int z = centre.getStartZ() + 8;
			Chunk chunk = world.getChunk(centre.x, centre.z, ChunkStatus.FULL, true);
			int ground = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x & 15, z & 15);
			int firstHost = Integer.MIN_VALUE;
			StringBuilder column = new StringBuilder();
			String last = null;
			for (int cy = ground; cy > world.getBottomY(); cy--) {
				BlockState state = chunk.getBlockState(pos.set(x, cy, z));
				String name = BiomeStoneTests.name(state.getBlock());
				if (!name.equals(last)) {
					column.append(column.isEmpty() ? "" : ", ").append(name).append(" from y=").append(cy);
					last = name;
				}
				if (firstHost == Integer.MIN_VALUE && state.isOf(entry.getValue())) {
					firstHost = cy;
				}
				if (column.length() > 400) {
					break;
				}
			}
			Map<String, Object> spot = new LinkedHashMap<>();
			spot.put("biome", entry.getKey());
			spot.put("x", x);
			spot.put("z", z);
			spot.put("ground_y", ground);
			spot.put("first_host_stone_y", firstHost);
			spot.put("column_from_the_top", column.toString());
			spot.put("drops", dropName(entry.getValue()));
			spots.put(BiomeStoneTests.name(entry.getValue()), spot);
		}

		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("seed", world.getSeed());
		stats.put("map", "y=" + MAP_Y + ", " + size + " x " + size + " blocks from " + x0 + ", " + z0);
		stats.put("share_at_y" + MAP_Y, shares);
		stats.put("biome_columns_in_square", biomeColumns);
		stats.put("checklist_spots", spots);
		try (Writer writer = Files.newBufferedWriter(out.resolve("stone.json"))) {
			new GsonBuilder().setPrettyPrinting().create().toJson(stats, writer);
		}
		EternalReturn.LOGGER.info("[stonemap] wrote {} | {} | shares {} | spots {}", out, stats.get("map"), shares, spots);
		ctx.complete();
	}

	private static String dropName(Block block) {
		for (HardenedBlocks.Definition definition : HardenedBlocks.DEFINITIONS) {
			if (HardenedBlocks.get(definition.name()) == block) {
				return Registries.ITEM.getId(definition.drop()).getPath();
			}
		}
		return block == Blocks.STONE ? "cobblestone" : Registries.BLOCK.getId(block).getPath();
	}

	private static String category(BlockState state) {
		String name = Registries.BLOCK.getId(state.getBlock()).getPath();
		if (state.isAir()) {
			return "cave (air)";
		}
		if (name.endsWith("_ore")) {
			return "ores";
		}
		if (state.isOf(Blocks.WATER)) {
			return "water";
		}
		if (state.isOf(Blocks.LAVA)) {
			return "lava";
		}
		return COLORS.containsKey(name) && !List.of("ores", "water", "lava", "other").contains(name) ? name : "other";
	}
}
