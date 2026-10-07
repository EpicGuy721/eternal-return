package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.caves.CaveStartLog;
import com.eternalreturn.worldgen.caves.CaveSystemCarver;
import com.eternalreturn.worldgen.caves.types.LargeCave;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
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
		Map<String, CaveRegion> galleryRegions = new LinkedHashMap<>();
		String previous = caves.debugForceCaveType;
		try {
			for (int i = 0; i < CaveTypes.ALL.size(); i++) {
				CaveType type = CaveTypes.ALL.get(i);
				caves.debugForceCaveType = type.id();
				CaveRegion region = new CaveRegion(world, 5000 + i * 200, 5000, GALLERY_CHUNKS, null).measure(lavaY, 64);
				tiles.add(galleryTile(region, type.id(), lavaY));
				galleryRegions.put(type.id(), region);
				galleryStats.put(type.id(), String.format("%.2f%% open", region.openFraction() * 100));
			}
		} finally {
			caves.debugForceCaveType = previous;
		}
		ImageIO.write(gallerySheet(tiles), "png", out.resolve("gallery.png").toFile());
		Map<String, Object> shapes = shapeViews(world, galleryRegions.get("spiral"), galleryRegions.get("toroidal"), out, lavaY);

		// ---- the biggest caverns over a wide area, found from the seed; the best one on land is generated and drawn
		List<Map<String, Object>> giants = giantCaverns(world, newCaves);
		Map<String, Object> giant = drawGiant(world, giants, out, lavaY);

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
		stats.put("shape_views", shapes);
		stats.put("examples_near_spawn", examples);
		stats.put("biggest_caverns_within_5000_blocks", giants);
		stats.put("giant_cavern_checked", giant);
		try (Writer writer = Files.newBufferedWriter(out.resolve("caves.json"))) {
			new GsonBuilder().setPrettyPrinting().create().toJson(stats, writer);
		}
		EternalReturn.LOGGER.info("[cavemap] wrote {} | default open {} | examples {}", out, stats.get("default_open_share"), examples);
		ctx.complete();
	}

	private static final int SHAPE_SCALE = 4;

	/**
	 * shapes.png: one spiral and one toroidal room from the forced gallery squares, found by replaying
	 * the seed (CaveReplay), each drawn the way its shape shows best. The spiral: from the side, cave
	 * counted through its width (the helix shows as a ribbon swinging left and right while it drops,
	 * the solid column down the middle), and from above, coloured by height. The room: cut flat through
	 * its middle (the ring around the pillar) and cut upright through its centre (the pillar between
	 * the two halves of the ring).
	 */
	private static Map<String, Object> shapeViews(ServerWorld world, CaveRegion spirals, CaveRegion rooms, Path out, int lavaY) throws IOException {
		Map<String, Object> result = new LinkedHashMap<>();
		List<BufferedImage[]> rows = new ArrayList<>();
		List<String> captions = new ArrayList<>();
		if (spirals != null) {
			CaveTypeSettings settings = EternalReturnConfig.get().worldgen.caves.types.get("spiral");
			for (CaveReplay.Start start : CaveReplay.starts(world, "spiral", CaveTypes.byId("spiral").forcedWeight(), settings,
					spirals.chunkX0, spirals.chunkZ0, spirals.size)) {
				com.eternalreturn.worldgen.caves.types.SpiralCave.Plan plan = com.eternalreturn.worldgen.caves.types.SpiralCave.plan(start.random(), settings,
						start.x() + 0.5, start.y() + 0.5, start.z() + 0.5, CaveReplay.minCentreY(world), CaveReplay.maxCentreY(world));
				int reach = (int) Math.ceil(plan.coilRadius() + plan.tubeRadius() + 3);
				if (!insideSquare(spirals, plan.centreX(), plan.centreZ(), reach)) {
					continue;
				}
				int cx = (int) Math.floor(plan.centreX());
				int cz = (int) Math.floor(plan.centreZ());
				int top = (int) Math.ceil(plan.topY() + plan.tubeRadius() + 4);
				int bottom = (int) Math.floor(plan.topY() - plan.drop() - plan.tubeRadius() - 4);
				BufferedImage side = new BufferedImage(2 * reach + 1, top - bottom + 1, BufferedImage.TYPE_INT_RGB);
				for (int dx = -reach; dx <= reach; dx++) {
					for (int y = bottom; y <= top; y++) {
						int count = 0;
						for (int dz = -reach; dz <= reach; dz++) {
							if (spirals.isCave(cx + dx, y, cz + dz, lavaY)) {
								count++;
							}
						}
						int v = count == 0 ? 0xA8 : Math.max(20, 150 - count * 12);
						side.setRGB(dx + reach, top - y, count == 0 ? 0xA8A8A8 : (v << 16) | (v << 8) | v);
					}
				}
				BufferedImage above = new BufferedImage(2 * reach + 1, 2 * reach + 1, BufferedImage.TYPE_INT_RGB);
				for (int dx = -reach; dx <= reach; dx++) {
					for (int dz = -reach; dz <= reach; dz++) {
						int highest = Integer.MIN_VALUE;
						for (int y = bottom; y <= top; y++) {
							if (spirals.isCave(cx + dx, y, cz + dz, lavaY)) {
								highest = y;
							}
						}
						int rgb = 0xA8A8A8;
						if (highest != Integer.MIN_VALUE) {
							float t = Math.max(0, Math.min(1, (highest - bottom) / (float) (top - bottom)));
							rgb = Color.getHSBColor(0.66F * (1 - t), 0.85F, 0.9F).getRGB();
						}
						above.setRGB(dx + reach, dz + reach, rgb);
					}
				}
				rows.add(new BufferedImage[]{side, above});
				captions.add(String.format("Spiral at %d %d: drops %.0f blocks (y %d to %d) over %.1f turns, coil radius %.1f, tube %.1f. Left: from the side, cave counted through it. Right: from above, blue = low, red = high.",
						cx, cz, plan.drop(), (int) (plan.topY() - plan.drop()), (int) plan.topY(), plan.turns(), plan.coilRadius(), plan.tubeRadius()));
				result.put("spiral", String.format("%d %d %d (top of the tube: %d %d %d)", cx, (int) plan.topY(), cz,
						(int) Math.floor(plan.point(0).x), (int) Math.floor(plan.point(0).y), (int) Math.floor(plan.point(0).z)));
				break;
			}
		}
		if (rooms != null) {
			CaveTypeSettings settings = EternalReturnConfig.get().worldgen.caves.types.get("toroidal");
			for (CaveReplay.Start start : CaveReplay.starts(world, "toroidal", CaveTypes.byId("toroidal").forcedWeight(), settings,
					rooms.chunkX0, rooms.chunkZ0, rooms.size)) {
				com.eternalreturn.worldgen.caves.types.ToroidalCave.Plan plan = com.eternalreturn.worldgen.caves.types.ToroidalCave.plan(start.random(), settings,
						start.x() + 0.5, start.y() + 0.5, start.z() + 0.5, CaveReplay.minCentreY(world), CaveReplay.maxCentreY(world));
				int reach = (int) Math.ceil(plan.ringRadius() + plan.tubeRadius() + 4);
				if (plan.tilt() != 0 || !insideSquare(rooms, plan.x(), plan.z(), reach)) {
					continue;
				}
				int cx = (int) Math.floor(plan.x());
				int cy = (int) Math.floor(plan.y());
				int cz = (int) Math.floor(plan.z());
				BufferedImage flat = new BufferedImage(2 * reach + 1, 2 * reach + 1, BufferedImage.TYPE_INT_RGB);
				for (int dx = -reach; dx <= reach; dx++) {
					for (int dz = -reach; dz <= reach; dz++) {
						flat.setRGB(dx + reach, dz + reach, rooms.isCave(cx + dx, cy, cz + dz, lavaY) ? 0x1A1A1A : 0xA8A8A8);
					}
				}
				int half = (int) Math.ceil(plan.verticalExtent() + 6);
				BufferedImage upright = new BufferedImage(2 * reach + 1, 2 * half + 1, BufferedImage.TYPE_INT_RGB);
				for (int dx = -reach; dx <= reach; dx++) {
					for (int dy = -half; dy <= half; dy++) {
						upright.setRGB(dx + reach, half - dy, rooms.isCave(cx + dx, cy + dy, cz, lavaY) ? 0x1A1A1A : 0xA8A8A8);
					}
				}
				rows.add(new BufferedImage[]{flat, upright});
				captions.add(String.format("Toroidal room at %d %d %d: ring radius %.1f, tube %.1f. Left: cut flat through its middle (y %d). Right: cut upright through its centre (z %d).",
						cx, cy, cz, plan.ringRadius(), plan.tubeRadius(), cy, cz));
				var inRing = plan.toWorld(plan.ringRadius(), 0, 0);
				result.put("toroidal", String.format("centre %d %d %d, in the ring %d %d %d", cx, cy, cz,
						(int) Math.floor(inRing.x), (int) Math.floor(inRing.y), (int) Math.floor(inRing.z)));
				break;
			}
		}
		if (rows.isEmpty()) {
			return result;
		}
		int caption = 22;
		int width = rows.stream().mapToInt(row -> (row[0].getWidth() + row[1].getWidth()) * SHAPE_SCALE + 12).max().orElse(1);
		width = Math.max(width, 900);
		int height = rows.stream().mapToInt(row -> Math.max(row[0].getHeight(), row[1].getHeight()) * SHAPE_SCALE + caption + 12).sum();
		BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = sheet.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, width, height);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		int y = 0;
		for (int i = 0; i < rows.size(); i++) {
			BufferedImage[] row = rows.get(i);
			g.setColor(Color.BLACK);
			g.drawString(captions.get(i), 4, y + 15);
			g.drawImage(row[0], 0, y + caption, row[0].getWidth() * SHAPE_SCALE, row[0].getHeight() * SHAPE_SCALE, null);
			g.drawImage(row[1], row[0].getWidth() * SHAPE_SCALE + 12, y + caption, row[1].getWidth() * SHAPE_SCALE, row[1].getHeight() * SHAPE_SCALE, null);
			y += Math.max(row[0].getHeight(), row[1].getHeight()) * SHAPE_SCALE + caption + 12;
		}
		g.dispose();
		ImageIO.write(sheet, "png", out.resolve("shapes.png").toFile());
		return result;
	}

	private static boolean insideSquare(CaveRegion region, double x, double z, int reach) {
		int x0 = region.chunkX0 * 16;
		int z0 = region.chunkZ0 * 16;
		int size = region.size * 16;
		return x - reach >= x0 && x + reach < x0 + size && z - reach >= z0 && z + reach < z0 + size;
	}

	/** Search half-width in chunks: 5,000 blocks either side of 0,0. */
	private static final int GIANT_SEARCH_CHUNKS = 312;

	/**
	 * The biggest caverns in a wide square, without generating it: replays the seed Moderner Beta hands
	 * the cave carver for each chunk (its "early release" seeding: a legacy random seeded from the world
	 * seed, two salts, then chunk x * salt + chunk z * salt ^ seed), then the carver's own draws for the
	 * large type: whether a cavern starts, where, and its radius (LargeCave.radius, the first draw).
	 * Exact for one cavern per chunk, which the default weight gives. Cross-checked against the
	 * caverns actually logged in the land square.
	 */
	private static List<Map<String, Object>> giantCaverns(ServerWorld world, CaveRegion land) {
		EternalReturnConfig.CaveTweaks caves = EternalReturnConfig.get().worldgen.caves;
		CaveTypeSettings settings = caves.types.get("large");
		double expected = settings.weight * caves.density / 100.0;
		long seed = world.getSeed();
		LocalRandom random = new LocalRandom(seed);
		long saltX = random.nextLong();
		long saltZ = random.nextLong();
		List<double[]> found = new ArrayList<>();
		for (int chunkX = -GIANT_SEARCH_CHUNKS; chunkX <= GIANT_SEARCH_CHUNKS; chunkX++) {
			for (int chunkZ = -GIANT_SEARCH_CHUNKS; chunkZ <= GIANT_SEARCH_CHUNKS; chunkZ++) {
				random.setSeed((long) chunkX * saltX + (long) chunkZ * saltZ ^ seed);
				Random typeRandom = new Xoroshiro128PlusPlusRandom(CaveSystemCarver.mix(random.nextLong(), "large".hashCode()));
				int count = (int) expected + (typeRandom.nextDouble() < expected - (int) expected ? 1 : 0);
				if (count == 0) {
					continue;
				}
				int x = chunkX * 16 + typeRandom.nextInt(16);
				int z = chunkZ * 16 + typeRandom.nextInt(16);
				int y = settings.minY + typeRandom.nextInt(Math.max(1, settings.maxY - settings.minY + 1));
				found.add(new double[]{x, y, z, LargeCave.radius(typeRandom, settings)});
			}
		}
		// Check the replay against the caverns the carver logged in the land square.
		int x0 = land.chunkX0 * 16;
		int z0 = land.chunkZ0 * 16;
		int size = land.size * 16;
		long predicted = found.stream().filter(c -> c[0] >= x0 && c[0] < x0 + size && c[2] >= z0 && c[2] < z0 + size).count();
		long logged = CaveStartLog.recent().stream().filter(start -> start.type().equals("large")
				&& start.x() >= x0 && start.x() < x0 + size && start.z() >= z0 && start.z() < z0 + size).count();
		long matching = CaveStartLog.recent().stream().filter(start -> start.type().equals("large")
				&& found.stream().anyMatch(c -> (int) c[0] == start.x() && (int) c[2] == start.z())).count();
		EternalReturn.LOGGER.info("[cavemap] cavern search: {} caverns over {} chunks; land square predicted {}, logged {}, logged ones matching a prediction {}",
				found.size(), (2 * GIANT_SEARCH_CHUNKS + 1) * (2 * GIANT_SEARCH_CHUNKS + 1), predicted, logged, matching);

		found.sort((a, b) -> Double.compare(b[3], a[3]));
		net.minecraft.world.gen.chunk.ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		List<Map<String, Object>> result = new ArrayList<>();
		for (double[] cavern : found) {
			if (result.size() >= 8) {
				break;
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("x", (int) cavern[0]);
			entry.put("z", (int) cavern[2]);
			entry.put("start_y", (int) cavern[1]);
			entry.put("radius", Math.round(cavern[3] * 10) / 10.0);
			entry.put("ground_y", generator.getHeight((int) cavern[0], (int) cavern[2], net.minecraft.world.Heightmap.Type.OCEAN_FLOOR_WG,
					world, world.getChunkManager().getNoiseConfig()) - 1);
			result.add(entry);
		}
		Map<String, Long> bySize = new LinkedHashMap<>();
		bySize.put("under 20", found.stream().filter(c -> c[3] < 20).count());
		bySize.put("20 to 35", found.stream().filter(c -> c[3] >= 20 && c[3] < 35).count());
		bySize.put("35 to 50", found.stream().filter(c -> c[3] >= 35 && c[3] < 50).count());
		bySize.put("50 to 65", found.stream().filter(c -> c[3] >= 50 && c[3] < 65).count());
		bySize.put("65 and over", found.stream().filter(c -> c[3] >= 65).count());
		EternalReturn.LOGGER.info("[cavemap] cavern radii: {} | biggest: {}", bySize, result);
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("radius_counts", bySize);
		result.add(0, summary);
		return result;
	}

	/**
	 * Generates the biggest cavern found on land (ground above sea level) and draws a side view through
	 * its centre (giant_cavern.png); returns where to stand in it and how big it came out.
	 */
	private static Map<String, Object> drawGiant(ServerWorld world, List<Map<String, Object>> giants, Path out, int lavaY) throws IOException {
		Map<String, Object> pick = null;
		for (Map<String, Object> entry : giants) {
			if (entry.containsKey("radius") && ((Number) entry.get("ground_y")).intValue() >= 64) {
				pick = entry;
				break;
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		if (pick == null) {
			return result;
		}
		int x = ((Number) pick.get("x")).intValue();
		int z = ((Number) pick.get("z")).intValue();
		double radius = ((Number) pick.get("radius")).doubleValue();
		int reach = (int) Math.ceil(radius * 1.6) + 8;
		int chunks = (2 * reach) / 16 + 1;
		CaveRegion region = new CaveRegion(world, (x - reach) >> 4, (z - reach) >> 4, chunks, null);
		// Widest open run along x through the centre column, at each height; the tallest air run in the centre column.
		int bestWidth = 0;
		int bestY = 0;
		for (int y = world.getBottomY() + 1; y < 100; y++) {
			int width = 0;
			int run = 0;
			for (int dx = -reach; dx <= reach; dx++) {
				run = region.isCave(x + dx, y, z, lavaY) ? run + 1 : 0;
				width = Math.max(width, run);
			}
			if (width > bestWidth) {
				bestWidth = width;
				bestY = y;
			}
		}
		int lowest = Integer.MAX_VALUE;
		int highest = Integer.MIN_VALUE;
		for (int y = world.getBottomY() + 1; y < 100; y++) {
			if (region.isCave(x, y, z, lavaY)) {
				lowest = Math.min(lowest, y);
				highest = Math.max(highest, y);
			}
		}
		int top = 100;
		int bottom = world.getBottomY();
		BufferedImage side = new BufferedImage(2 * reach + 1, top - bottom, BufferedImage.TYPE_INT_RGB);
		for (int dx = -reach; dx <= reach; dx++) {
			for (int y = bottom; y < top; y++) {
				BlockState state = region.at(x + dx, y, z);
				int rgb = region.isCave(x + dx, y, z, lavaY) ? (state.isOf(Blocks.LAVA) ? 0xFF7A00 : 0x1A1A1A) : colour(state, y, lavaY);
				side.setRGB(dx + reach, top - 1 - y, rgb);
			}
		}
		int scale = 3;
		BufferedImage scaled = new BufferedImage(side.getWidth() * scale, side.getHeight() * scale + 22, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = scaled.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, scaled.getWidth(), scaled.getHeight());
		g.setColor(Color.BLACK);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.drawString("Giant cavern, side view through z = " + z + ", x " + (x - reach) + " to " + (x + reach) + ", y " + bottom + " to " + top
				+ " (radius drawn " + radius + ")", 4, 16);
		g.drawImage(side, 0, 22, side.getWidth() * scale, side.getHeight() * scale, null);
		g.dispose();
		ImageIO.write(scaled, "png", out.resolve("giant_cavern.png").toFile());
		result.put("x", x);
		result.put("z", z);
		result.put("radius_drawn", radius);
		result.put("widest_open_run_east_west", bestWidth);
		result.put("at_y", bestY);
		result.put("open_from_y", lowest == Integer.MAX_VALUE ? null : lowest);
		result.put("open_to_y", highest == Integer.MIN_VALUE ? null : highest);
		result.put("stand_at", x + " " + bestY + " " + z);
		EternalReturn.LOGGER.info("[cavemap] giant cavern checked: {}", result);
		return result;
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
					boolean cave = region.isCave(x0 + x, y, z0 + z, lavaY);
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
