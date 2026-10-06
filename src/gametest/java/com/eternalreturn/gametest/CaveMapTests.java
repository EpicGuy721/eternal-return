package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.caves.CaveStartLog;
import com.eternalreturn.worldgen.caves.CaveType;
import com.eternalreturn.worldgen.caves.CaveTypes;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cave images for the map tool (runWorldmapCaves): horizontal slices at four depths over a land
 * square, new caves next to the old ones on identical terrain (the twin dimension, with the cave
 * engine off and the old caves on); a gallery with each type forced in its own square (seen from
 * above and from the side); and real cave starts in the square for the in-game checklist. Writes to
 * eternalreturn.worldmap.out (docs/worldgen-maps/caves).
 */
public class CaveMapTests implements FabricGameTest {
	public static final String CAVEMAP_BATCH = "cavemap";
	private static final int[] SLICE_YS = {-50, -20, 20, 50};
	/** Gallery squares, in chunks. */
	private static final int GALLERY_CHUNKS = 8;
	private static final int GALLERY_SCALE = 3;
	private static final int GALLERY_LOW_Y = -64;
	private static final int GALLERY_HIGH_Y = 96;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = CAVEMAP_BATCH, tickLimit = 2_000_000)
	public void renderCaveMaps(TestContext ctx) throws IOException {
		ServerWorld world = ctx.getWorld();
		Path out = Path.of(System.getProperty("eternalreturn.worldmap.out"));
		Files.createDirectories(out);
		EternalReturnConfig.CaveTweaks caves = EternalReturnConfig.get().worldgen.caves;
		int lavaY = world.getBottomY() + 8;

		// ---- the land square: new caves here, the old caves on the same terrain in the twin dimension
		int x0 = CaveRegion.LAND_CHUNK_X0 * 16;
		int z0 = CaveRegion.LAND_CHUNK_Z0 * 16;
		int size = CaveRegion.LAND_SIZE * 16;
		CaveRegion newCaves = new CaveRegion(world, CaveRegion.LAND_CHUNK_X0, CaveRegion.LAND_CHUNK_Z0, CaveRegion.LAND_SIZE, null).measure(lavaY, 64);
		ServerWorld twin = world.getServer().getWorld(CaveTests.TWIN);
		CaveRegion oldCaves = null;
		if (twin != null) {
			boolean wasEnabled = caves.enabled;
			boolean wasOld = caves.oldCaves;
			try {
				caves.enabled = false;
				caves.oldCaves = true;
				oldCaves = new CaveRegion(twin, CaveRegion.LAND_CHUNK_X0, CaveRegion.LAND_CHUNK_Z0, CaveRegion.LAND_SIZE, null).measure(world.getBottomY() + 10, 64);
			} finally {
				caves.enabled = wasEnabled;
				caves.oldCaves = wasOld;
			}
		}
		List<BufferedImage[]> rows = new ArrayList<>();
		for (int y : SLICE_YS) {
			BufferedImage[] row = {slice(newCaves, x0, z0, size, y, lavaY), oldCaves == null ? null : slice(oldCaves, x0, z0, size, y, world.getBottomY() + 10)};
			ImageIO.write(row[0], "png", out.resolve("slice_y" + y + ".png").toFile());
			rows.add(row);
		}
		ImageIO.write(sliceSheet(rows, x0, z0, size), "png", out.resolve("slices.png").toFile());

		// ---- real starts in the square, nearest its centre first, for the checklist
		int centreX = x0 + size / 2;
		int centreZ = z0 + size / 2;
		Map<String, List<Map<String, Object>>> examples = new LinkedHashMap<>();
		for (CaveType type : CaveTypes.ALL) {
			List<Map<String, Object>> list = new ArrayList<>();
			CaveStartLog.recent().stream()
					.filter(start -> start.type().equals(type.id()) && start.x() >= x0 + 16 && start.x() < x0 + size - 16 && start.z() >= z0 + 16 && start.z() < z0 + size - 16)
					.sorted((a, b) -> Long.compare(distanceSquared(a, centreX, centreZ), distanceSquared(b, centreX, centreZ)))
					.limit(4)
					.forEach(start -> {
						Map<String, Object> entry = new LinkedHashMap<>();
						entry.put("x", start.x());
						entry.put("y", start.y());
						entry.put("z", start.z());
						entry.put("cave_air_within_2_blocks", nearCave(newCaves, start.x(), start.y(), start.z()));
						entry.put("ground_y", world.getChunkManager().getChunkGenerator().getHeight(start.x(), start.z(),
								net.minecraft.world.Heightmap.Type.OCEAN_FLOOR_WG, world, world.getChunkManager().getNoiseConfig()) - 1);
						list.add(entry);
					});
			examples.put(type.id(), list);
		}

		// ---- gallery: each type forced in its own square
		List<BufferedImage> tiles = new ArrayList<>();
		Map<String, Object> galleryStats = new LinkedHashMap<>();
		String previous = caves.debugForceCaveType;
		try {
			for (int i = 0; i < CaveTypes.ALL.size(); i++) {
				CaveType type = CaveTypes.ALL.get(i);
				caves.debugForceCaveType = type.id();
				CaveRegion region = new CaveRegion(world, 5000 + i * 200, 5000, GALLERY_CHUNKS, null).measure(lavaY, 64);
				tiles.add(galleryTile(region, type.id(), lavaY));
				galleryStats.put(type.id(), String.format("%.2f%% open", region.openFraction() * 100));
			}
		} finally {
			caves.debugForceCaveType = previous;
		}
		ImageIO.write(gallerySheet(tiles), "png", out.resolve("gallery.png").toFile());

		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("seed", world.getSeed());
		stats.put("slice_area", size + " x " + size + " blocks from " + x0 + ", " + z0);
		stats.put("default_open_share", String.format("%.2f%%", newCaves.openFraction() * 100));
		stats.put("default_open_share_by_band", newCaves.bandSummary());
		if (oldCaves != null) {
			stats.put("old_caves_open_share", String.format("%.2f%%", oldCaves.openFraction() * 100));
			stats.put("old_caves_open_share_by_band", oldCaves.bandSummary());
		}
		stats.put("gallery_forced_open_share", galleryStats);
		stats.put("examples_near_spawn", examples);
		try (Writer writer = Files.newBufferedWriter(out.resolve("caves.json"))) {
			new GsonBuilder().setPrettyPrinting().create().toJson(stats, writer);
		}
		EternalReturn.LOGGER.info("[cavemap] wrote {} | default open {} | examples {}", out, stats.get("default_open_share"), examples);
		ctx.complete();
	}

	private static boolean nearCave(CaveRegion region, int x, int y, int z) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (region.at(x + dx, y + dy, z + dz).isOf(Blocks.CAVE_AIR)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static int colour(BlockState state, int y, int lavaY) {
		if (state.isOf(Blocks.CAVE_AIR)) {
			return 0x1A1A1A;
		}
		if (state.isOf(Blocks.LAVA)) {
			return 0xFF7A00;
		}
		if (state.isAir()) {
			return 0xCFE3F5;
		}
		if (state.getFluidState().isIn(FluidTags.WATER)) {
			return 0x2F5FD0;
		}
		if (state.isOf(Blocks.BEDROCK)) {
			return 0x333333;
		}
		if (state.isIn(BlockTags.BASE_STONE_OVERWORLD)) {
			return 0xA8A8A8;
		}
		if (state.isIn(BlockTags.DIRT)) {
			return 0x8B6A3E;
		}
		if (state.isIn(BlockTags.SAND) || state.isOf(Blocks.SANDSTONE)) {
			return 0xE3D39A;
		}
		return 0xC9C2B0;
	}

	private static long distanceSquared(CaveStartLog.Start start, int x, int z) {
		long dx = start.x() - x;
		long dz = start.z() - z;
		return dx * dx + dz * dz;
	}

	/** Air below the ground counts as cave whatever kind it is (Moderner Beta's old caves carve plain air). */
	private static BufferedImage slice(CaveRegion region, int x0, int z0, int size, int y, int lavaY) {
		net.minecraft.world.gen.chunk.ChunkGenerator generator = region.world.getChunkManager().getChunkGenerator();
		net.minecraft.world.gen.noise.NoiseConfig noise = region.world.getChunkManager().getNoiseConfig();
		BufferedImage slice = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				BlockState state = region.at(x0 + x, y, z0 + z);
				boolean underground = state.isAir() && y < generator.getHeight(x0 + x, z0 + z, net.minecraft.world.Heightmap.Type.OCEAN_FLOOR_WG, region.world, noise);
				slice.setRGB(x, z, underground ? 0x1A1A1A : colour(state, y, lavaY));
			}
		}
		return slice;
	}

	/** One row per depth: new caves on the left, old caves on the same terrain on the right. */
	private static BufferedImage sliceSheet(List<BufferedImage[]> rows, int x0, int z0, int size) {
		int caption = 22;
		int header = 26;
		BufferedImage sheet = new BufferedImage(2 * size + 10, header + rows.size() * (size + caption + 10), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
		g.drawString(size + " x " + size + " blocks from " + x0 + ", " + z0 + " (seed 173164). Black = cave, grey = stone, blue = water, orange = lava, brown = dirt.", 4, 17);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		for (int i = 0; i < rows.size(); i++) {
			int y = header + i * (size + caption + 10);
			g.drawString("y = " + SLICE_YS[i] + ", new caves", 4, y + 16);
			g.drawImage(rows.get(i)[0], 0, y + caption, null);
			if (rows.get(i)[1] != null) {
				g.drawString("y = " + SLICE_YS[i] + ", old caves (Moderner Beta 1.6.4 style), same terrain", size + 14, y + 16);
				g.drawImage(rows.get(i)[1], size + 10, y + caption, null);
			}
		}
		g.dispose();
		return sheet;
	}

	/**
	 * One type's square: from above (each column coloured by the height of its highest cave block, deep
	 * blue to red, darker where the cave is taller) and from the side (cave blocks counted through the
	 * square, darker = more cave behind; orange = lava).
	 */
	private static BufferedImage galleryTile(CaveRegion region, String type, int lavaY) {
		int blocks = GALLERY_CHUNKS * 16;
		int height = GALLERY_HIGH_Y - GALLERY_LOW_Y;
		int x0 = region.chunkX0 * 16;
		int z0 = region.chunkZ0 * 16;
		BufferedImage top = new BufferedImage(blocks, blocks, BufferedImage.TYPE_INT_RGB);
		int[][] side = new int[blocks][height];
		boolean[][] sideLava = new boolean[blocks][height];
		for (int x = 0; x < blocks; x++) {
			for (int z = 0; z < blocks; z++) {
				int highest = Integer.MIN_VALUE;
				int count = 0;
				for (int y = GALLERY_LOW_Y; y < GALLERY_HIGH_Y; y++) {
					BlockState state = region.at(x0 + x, y, z0 + z);
					boolean cave = state.isOf(Blocks.CAVE_AIR) || (state.isOf(Blocks.LAVA) && y <= lavaY);
					if (cave) {
						highest = y;
						count++;
						side[x][y - GALLERY_LOW_Y]++;
						if (state.isOf(Blocks.LAVA)) {
							sideLava[x][y - GALLERY_LOW_Y] = true;
						}
					}
				}
				int rgb = 0xD8D8D8;
				if (count > 0) {
					float t = Math.max(0, Math.min(1, (highest - GALLERY_LOW_Y) / (float) height));
					Color c = Color.getHSBColor(0.66F * (1 - t), 0.85F, Math.max(0.35F, 1.0F - count / 40.0F));
					rgb = c.getRGB();
				}
				top.setRGB(x, z, rgb);
			}
		}
		BufferedImage sideImage = new BufferedImage(blocks, height, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < blocks; x++) {
			for (int y = 0; y < height; y++) {
				int rgb;
				if (sideLava[x][y]) {
					rgb = 0xFF7A00;
				} else if (side[x][y] == 0) {
					rgb = 0xD8D8D8;
				} else {
					int v = Math.max(20, 200 - side[x][y] * 18);
					rgb = (v << 16) | (v << 8) | v;
				}
				sideImage.setRGB(x, height - 1 - y, rgb);
			}
		}
		int scale = GALLERY_SCALE;
		int caption = 20;
		BufferedImage tile = new BufferedImage(blocks * scale * 2 + 10, Math.max(blocks, height) * scale + caption, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = tile.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, tile.getWidth(), tile.getHeight());
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.setColor(Color.BLACK);
		g.drawString(type + ": from above (blue = deep, red = high) and from the side (y " + GALLERY_LOW_Y + " to " + GALLERY_HIGH_Y + ")", 4, 15);
		g.drawImage(top, 0, caption, blocks * scale, blocks * scale, null);
		g.drawImage(sideImage, blocks * scale + 10, caption, blocks * scale, height * scale, null);
		g.dispose();
		return tile;
	}

	private static BufferedImage gallerySheet(List<BufferedImage> tiles) {
		int width = tiles.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
		int height = tiles.stream().mapToInt(tile -> tile.getHeight() + 12).sum();
		BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		int y = 0;
		for (BufferedImage tile : tiles) {
			g.drawImage(tile, 0, y, null);
			y += tile.getHeight() + 12;
		}
		g.dispose();
		return sheet;
	}

}
