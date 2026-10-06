package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSource;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Headless world map tool. Renders top-down maps (height, biomes, land vs water), a terrain cross
 * section and statistics for the run's world preset and seed, into eternalreturn.worldmap.out.
 * Run through the runWorldmap* tasks (./gradlew generateWorldMaps).
 */
public class WorldmapTests implements FabricGameTest {
	/** The map covers HALF blocks either side of its centre (eternalreturn.worldmap.half, default 2048). */
	private static final int HALF = Integer.getInteger("eternalreturn.worldmap.half", 2048);
	/** Map centre, "x,z" in blocks (eternalreturn.worldmap.centre, default 0,0), rounded to whole chunks. */
	private static final int[] CENTRE = blockPair(System.getProperty("eternalreturn.worldmap.centre", "0,0"), 16);
	private static final int CX = CENTRE[0];
	private static final int CZ = CENTRE[1];
	/** Blocks per map pixel. */
	private static final int STEP = 4;
	private static final int SIZE = 2 * HALF / STEP;
	/** Cross section through the map centre's x, SLICE_HALF blocks either side, one pixel per block. */
	private static final int SLICE_HALF = Math.min(1024, HALF);
	/** Size of the one-pixel-per-block close-up of the most rugged area. */
	private static final int CLOSEUP = 512;
	/** Chunks fully generated (terrain and surface) across the map to measure overhangs. */
	private static final int WEIRDNESS_GRID = 24;

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.WORLDMAP_BATCH, tickLimit = 2_000_000)
	public void renderMaps(TestContext ctx) throws IOException {
		ServerWorld world = ctx.getWorld();
		Path out = Path.of(System.getProperty("eternalreturn.worldmap.out"));
		Files.createDirectories(out);
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		BiomeSource biomeSource = generator.getBiomeSource();
		int sea = generator.getSeaLevel();
		long started = System.currentTimeMillis();

		// ---- sample heights and biomes, chunk by chunk so the generator's per-chunk height cache is reused
		int[][] height = new int[SIZE][SIZE];
		String[][] biome = new String[SIZE][SIZE];
		for (int chunkZ = CZ - HALF; chunkZ < CZ + HALF; chunkZ += 16) {
			for (int chunkX = CX - HALF; chunkX < CX + HALF; chunkX += 16) {
				for (int dz = 0; dz < 16; dz += STEP) {
					for (int dx = 0; dx < 16; dx += STEP) {
						int x = chunkX + dx;
						int z = chunkZ + dz;
						int px = (x - CX + HALF) / STEP;
						int pz = (z - CZ + HALF) / STEP;
						int h = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
						height[px][pz] = h;
						RegistryEntry<Biome> entry = biomeSource.getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(Math.max(h, sea)),
								BiomeCoords.fromBlock(z), noise.getMultiNoiseSampler());
						biome[px][pz] = entry.getKey().map(key -> key.getValue().toString()).orElse("?");
					}
				}
			}
		}
		long sampled = System.currentTimeMillis();

		// ---- statistics
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("name", System.getProperty("eternalreturn.worldmap.name"));
		stats.put("preset", GameTestWorld.presetProperty());
		stats.put("seed", world.getSeed());
		stats.put("area_blocks", (2 * HALF) + " x " + (2 * HALF) + " centred on " + CX + "," + CZ);
		stats.put("world_height", world.getBottomY() + " to " + world.getTopY());
		stats.put("sea_level", sea);

		boolean[][] land = new boolean[SIZE][SIZE];
		int landCount = 0;
		int oceanBiomeCount = 0;
		List<Integer> landHeights = new ArrayList<>();
		List<Integer> waterDepths = new ArrayList<>();
		Map<String, Integer> biomeCounts = new HashMap<>();
		double slopeSum = 0;
		int slopeCount = 0;
		for (int px = 0; px < SIZE; px++) {
			for (int pz = 0; pz < SIZE; pz++) {
				int h = height[px][pz];
				land[px][pz] = h > sea;
				if (land[px][pz]) {
					landCount++;
					landHeights.add(h);
					if (px + 1 < SIZE && height[px + 1][pz] > sea) {
						slopeSum += Math.abs(height[px + 1][pz] - h);
						slopeCount++;
					}
				} else {
					waterDepths.add(sea - h);
				}
				if (biome[px][pz].contains("ocean")) {
					oceanBiomeCount++;
				}
				biomeCounts.merge(biome[px][pz], 1, Integer::sum);
			}
		}
		int total = SIZE * SIZE;
		stats.put("water_share", round(1.0 - (double) landCount / total));
		stats.put("ocean_biome_share", round((double) oceanBiomeCount / total));
		stats.put("land_height", percentiles(landHeights));
		stats.put("water_depth", percentiles(waterDepths));
		stats.put("land_roughness_blocks_per_4", round(slopeCount == 0 ? 0 : slopeSum / slopeCount));
		stats.put("landmasses", landmasses(land));
		stats.put("terrain_mix", terrainMix(height, biome, sea));

		Map<String, Double> biomeShare = new LinkedHashMap<>();
		biomeCounts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.forEach(e -> biomeShare.put(e.getKey(), round((double) e.getValue() / total)));
		stats.put("biome_share", biomeShare);

		// ---- real terrain: a cross section through the most mountainous row, and an overhang count
		// Both can be pinned (eternalreturn.worldmap.slice_z, eternalreturn.worldmap.closeup "x,z") so runs compare the same ground.
		int sliceZ = Integer.getInteger("eternalreturn.worldmap.slice_z", mostRuggedRow(height, sea));
		stats.put("slice_z", sliceZ);
		BufferedImage slice = slice(world, sea, sliceZ);
		String closeupProperty = System.getProperty("eternalreturn.worldmap.closeup");
		int[] closeupCentre = closeupProperty != null ? blockPair(closeupProperty, 1) : mostRuggedWindow(height, sea);
		stats.put("closeup_centre", closeupCentre[0] + ", " + closeupCentre[1]);
		BufferedImage closeup = closeup(world, generator, noise, sea, closeupCentre[0], closeupCentre[1]);
		stats.put("weirdness", weirdness(world, sea));
		long generated = System.currentTimeMillis();
		stats.put("timing_ms", Map.of("sampling", sampled - started, "chunks", generated - sampled));

		// ---- images
		ImageIO.write(heightImage(height, SIZE, sea, world.getTopY(), 0.06F), "png", out.resolve("height.png").toFile());
		ImageIO.write(biomeImage(biome, biomeShare), "png", out.resolve("biomes.png").toFile());
		ImageIO.write(landImage(land), "png", out.resolve("land.png").toFile());
		ImageIO.write(slice, "png", out.resolve("slice.png").toFile());
		ImageIO.write(closeup, "png", out.resolve("closeup.png").toFile());
		try (Writer writer = Files.newBufferedWriter(out.resolve("stats.json"))) {
			new GsonBuilder().setPrettyPrinting().create().toJson(stats, writer);
		}
		EternalReturn.LOGGER.info("[worldmap] wrote {} | water {} | landmasses {} | weirdness {}", out, stats.get("water_share"),
				stats.get("landmasses"), stats.get("weirdness"));
		ctx.complete();
	}

	// ------------------------------------------------------------------ statistics helpers

	/** Parses "x,z", each rounded down to a multiple of the given step. */
	private static int[] blockPair(String value, int step) {
		String[] parts = value.split(",");
		return new int[]{Math.floorDiv(Integer.parseInt(parts[0].trim()), step) * step, Math.floorDiv(Integer.parseInt(parts[1].trim()), step) * step};
	}

	private static double round(double value) {
		return Math.round(value * 1000.0) / 1000.0;
	}

	private static Map<String, Integer> percentiles(List<Integer> values) {
		Map<String, Integer> result = new LinkedHashMap<>();
		if (values.isEmpty()) {
			return result;
		}
		int[] sorted = values.stream().mapToInt(Integer::intValue).sorted().toArray();
		for (int p : new int[]{5, 50, 90, 99}) {
			result.put("p" + p, sorted[Math.min(sorted.length - 1, sorted.length * p / 100)]);
		}
		result.put("max", sorted[sorted.length - 1]);
		return result;
	}

	/** Connected land areas (4-neighbour) in the land mask, in blocks. */
	private static Map<String, Object> landmasses(boolean[][] land) {
		int[][] label = new int[SIZE][SIZE];
		List<Integer> sizes = new ArrayList<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				if (!land[x][z] || label[x][z] != 0) {
					continue;
				}
				int id = sizes.size() + 1;
				int count = 0;
				label[x][z] = id;
				queue.add(new int[]{x, z});
				while (!queue.isEmpty()) {
					int[] p = queue.poll();
					count++;
					for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int nx = p[0] + d[0];
						int nz = p[1] + d[1];
						if (nx >= 0 && nz >= 0 && nx < SIZE && nz < SIZE && land[nx][nz] && label[nx][nz] == 0) {
							label[nx][nz] = id;
							queue.add(new int[]{nx, nz});
						}
					}
				}
				sizes.add(count);
			}
		}
		sizes.sort((a, b) -> b - a);
		int landTotal = sizes.stream().mapToInt(Integer::intValue).sum();
		int blocksPerPixel = STEP * STEP;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("count_over_1_chunk", sizes.stream().filter(s -> s * blocksPerPixel >= 256).count());
		result.put("count_over_256x256", sizes.stream().filter(s -> s * blocksPerPixel >= 65536).count());
		result.put("largest_blocks", sizes.isEmpty() ? 0 : (long) sizes.get(0) * blocksPerPixel);
		result.put("largest_share_of_land", sizes.isEmpty() ? 0 : round((double) sizes.get(0) / landTotal));
		return result;
	}

	/** Window size, in blocks, for measuring local relief (highest minus lowest ground in the window). */
	private static final int RELIEF_WINDOW = 32;
	/** Relief classes: upper bounds in blocks, and their names. */
	private static final int[] RELIEF_LIMITS = {8, 20, 40, 80, Integer.MAX_VALUE};
	private static final String[] RELIEF_NAMES = {"flat", "rolling", "hilly", "mountains", "extreme"};

	/**
	 * How the land is split between flat, rolling, hilly, mountainous and extreme ground: each
	 * mostly-land window of RELIEF_WINDOW blocks is classed by its relief. Also the median relief of
	 * each biome's windows, which shows how much the terrain changes from one biome to the next.
	 */
	private static Map<String, Object> terrainMix(int[][] height, String[][] biome, int sea) {
		int window = RELIEF_WINDOW / STEP;
		int[] classCounts = new int[RELIEF_LIMITS.length];
		int windows = 0;
		Map<String, List<Integer>> reliefByBiome = new HashMap<>();
		for (int px = 0; px + window <= SIZE; px += window) {
			for (int pz = 0; pz + window <= SIZE; pz += window) {
				int high = Integer.MIN_VALUE;
				int low = Integer.MAX_VALUE;
				int land = 0;
				for (int x = px; x < px + window; x++) {
					for (int z = pz; z < pz + window; z++) {
						if (height[x][z] > sea) {
							land++;
							high = Math.max(high, height[x][z]);
							low = Math.min(low, height[x][z]);
						}
					}
				}
				if (land * 4 < window * window * 3) {
					continue;
				}
				int relief = high - low;
				windows++;
				for (int i = 0; i < RELIEF_LIMITS.length; i++) {
					if (relief < RELIEF_LIMITS[i]) {
						classCounts[i]++;
						break;
					}
				}
				reliefByBiome.computeIfAbsent(biome[px + window / 2][pz + window / 2], key -> new ArrayList<>()).add(relief);
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("window_blocks", RELIEF_WINDOW);
		result.put("land_windows", windows);
		for (int i = 0; i < RELIEF_LIMITS.length; i++) {
			result.put(RELIEF_NAMES[i], windows == 0 ? 0 : round((double) classCounts[i] / windows));
		}
		Map<String, Integer> medians = new LinkedHashMap<>();
		reliefByBiome.entrySet().stream()
				.filter(e -> e.getValue().size() >= 20)
				.sorted(Map.Entry.comparingByKey())
				.forEach(e -> medians.put(e.getKey().substring(e.getKey().indexOf(':') + 1),
						e.getValue().stream().mapToInt(Integer::intValue).sorted().toArray()[e.getValue().size() / 2]));
		result.put("median_relief_by_biome", medians);
		return result;
	}

	/**
	 * Fully generates (to the surface step) a grid of chunks spread over the map and counts land
	 * columns that have air under solid ground above sea level: overhangs, arches, floating land.
	 */
	private static Map<String, Object> weirdness(ServerWorld world, int sea) {
		int spacing = (2 * HALF / 16) / WEIRDNESS_GRID;
		int landColumns = 0;
		int overhangColumns = 0;
		int floatingBlocks = 0;
		int tallest = world.getBottomY();
		for (int gx = 0; gx < WEIRDNESS_GRID; gx++) {
			for (int gz = 0; gz < WEIRDNESS_GRID; gz++) {
				int chunkX = (CX - HALF) / 16 + gx * spacing + spacing / 2;
				int chunkZ = (CZ - HALF) / 16 + gz * spacing + spacing / 2;
				Chunk chunk = world.getChunk(chunkX, chunkZ, ChunkStatus.SURFACE, true);
				BlockPos.Mutable pos = new BlockPos.Mutable();
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						int top = world.getBottomY();
						boolean seenSolid = false;
						boolean overhang = false;
						for (int y = world.getTopY() - 1; y > sea; y--) {
							boolean solid = isGround(chunk.getBlockState(pos.set(chunkX * 16 + x, y, chunkZ * 16 + z)));
							if (solid && !seenSolid) {
								seenSolid = true;
								top = y;
							} else if (!solid && seenSolid) {
								overhang = true;
								floatingBlocks++;
							}
						}
						if (seenSolid) {
							landColumns++;
							tallest = Math.max(tallest, top);
							if (overhang) {
								overhangColumns++;
							}
						}
					}
				}
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("chunks_sampled", WEIRDNESS_GRID * WEIRDNESS_GRID);
		result.put("land_columns_with_overhang", landColumns == 0 ? 0 : round((double) overhangColumns / landColumns));
		result.put("air_blocks_under_ground_per_land_column", landColumns == 0 ? 0 : round((double) floatingBlocks / landColumns));
		result.put("tallest_ground_y", tallest);
		return result;
	}

	private static boolean isGround(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && state.blocksMovement();
	}

	// ------------------------------------------------------------------ images

	/** The z (in blocks) of the map row with the most land height above sea level. */
	private static int mostRuggedRow(int[][] height, int sea) {
		int bestRow = SIZE / 2;
		long best = -1;
		for (int pz = 0; pz < SIZE; pz++) {
			long sum = 0;
			for (int px = (SIZE - 2 * SLICE_HALF / STEP) / 2; px < (SIZE + 2 * SLICE_HALF / STEP) / 2; px++) {
				sum += Math.max(0, height[px][pz] - sea);
			}
			if (sum > best) {
				best = sum;
				bestRow = pz;
			}
		}
		return bestRow * STEP - HALF + CZ;
	}

	/** Centre (x, z in blocks) of the CLOSEUP-sized window with the greatest height range. */
	private static int[] mostRuggedWindow(int[][] height, int sea) {
		int window = CLOSEUP / STEP;
		int best = -1;
		int[] centre = {0, 0};
		for (int px = 0; px + window <= SIZE; px += window / 2) {
			for (int pz = 0; pz + window <= SIZE; pz += window / 2) {
				int high = Integer.MIN_VALUE;
				int land = 0;
				for (int x = px; x < px + window; x += 2) {
					for (int z = pz; z < pz + window; z += 2) {
						high = Math.max(high, height[x][z]);
						if (height[x][z] > sea) {
							land++;
						}
					}
				}
				int score = land > window * window / 8 ? high : -1;
				if (score > best) {
					best = score;
					centre = new int[]{(px + window / 2) * STEP - HALF + CX, (pz + window / 2) * STEP - HALF + CZ};
				}
			}
		}
		return centre;
	}

	/** One pixel per block, hill-shaded, around the given centre. */
	private static BufferedImage closeup(ServerWorld world, ChunkGenerator generator, NoiseConfig noise, int sea, int cx, int cz) {
		int[][] h = new int[CLOSEUP][CLOSEUP];
		int x0 = cx - CLOSEUP / 2;
		int z0 = cz - CLOSEUP / 2;
		for (int chunkZ = z0 >> 4; chunkZ <= (z0 + CLOSEUP - 1) >> 4; chunkZ++) {
			for (int chunkX = x0 >> 4; chunkX <= (x0 + CLOSEUP - 1) >> 4; chunkX++) {
				for (int dz = 0; dz < 16; dz++) {
					for (int dx = 0; dx < 16; dx++) {
						int x = chunkX * 16 + dx - x0;
						int z = chunkZ * 16 + dz - z0;
						if (x >= 0 && z >= 0 && x < CLOSEUP && z < CLOSEUP) {
							h[x][z] = generator.getHeight(chunkX * 16 + dx, chunkZ * 16 + dz, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
						}
					}
				}
			}
		}
		return heightImage(h, CLOSEUP, sea, world.getTopY(), 0.25F);
	}

	private static BufferedImage slice(ServerWorld world, int sea, int sliceZ) {
		int bottom = world.getBottomY();
		int top = world.getTopY();
		BufferedImage image = new BufferedImage(2 * SLICE_HALF, top - bottom, BufferedImage.TYPE_INT_RGB);
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int chunkX = (CX - SLICE_HALF) / 16; chunkX < (CX + SLICE_HALF) / 16; chunkX++) {
			Chunk chunk = world.getChunk(chunkX, sliceZ >> 4, ChunkStatus.SURFACE, true);
			for (int dx = 0; dx < 16; dx++) {
				int x = chunkX * 16 + dx;
				for (int y = bottom; y < top; y++) {
					BlockState state = chunk.getBlockState(pos.set(x, y, sliceZ));
					int rgb;
					if (state.isAir()) {
						rgb = y == sea ? 0x9DB8D9 : 0xCFE3F5;
					} else {
						rgb = state.getMapColor(world, pos).color;
						if (rgb == 0) {
							rgb = 0x707070;
						}
					}
					image.setRGB(x - CX + SLICE_HALF, top - 1 - y, rgb);
				}
			}
		}
		return image;
	}

	private static BufferedImage heightImage(int[][] height, int size, int sea, int worldTop, float shadeStrength) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				int h = height[x][z];
				Color color;
				if (h <= sea) {
					float depth = Math.min(1.0F, (sea - h) / 60.0F);
					color = blend(new Color(0x5AA0E6), new Color(0x0A1E5A), depth);
				} else {
					float t = Math.min(1.0F, (float) (h - sea) / (worldTop - sea));
					color = ramp(t);
					int nw = x > 0 && z > 0 ? height[x - 1][z - 1] : h;
					float shade = Math.max(0.55F, Math.min(1.35F, 1.0F + (nw - h) * shadeStrength));
					color = new Color(clamp(color.getRed() * shade), clamp(color.getGreen() * shade), clamp(color.getBlue() * shade));
				}
				image.setRGB(x, z, color.getRGB());
			}
		}
		return image;
	}

	/** Land colour by height above sea level, 0 to 1: lowland green, hills olive, mountains brown, grey, then white peaks. */
	private static Color ramp(float t) {
		float[] stops = {0.0F, 0.08F, 0.2F, 0.35F, 0.55F, 1.0F};
		Color[] colors = {new Color(0x4F9A3A), new Color(0x8DB04A), new Color(0xB59B5A), new Color(0x8A6E4E), new Color(0x9A9A9A), new Color(0xFFFFFF)};
		for (int i = 1; i < stops.length; i++) {
			if (t <= stops[i]) {
				return blend(colors[i - 1], colors[i], (t - stops[i - 1]) / (stops[i] - stops[i - 1]));
			}
		}
		return colors[colors.length - 1];
	}

	private static BufferedImage landImage(boolean[][] land) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				image.setRGB(x, z, land[x][z] ? 0x4F9A3A : 0x1E4F9A);
			}
		}
		return image;
	}

	private static BufferedImage biomeImage(String[][] biome, Map<String, Double> share) {
		int legendWidth = 300;
		BufferedImage image = new BufferedImage(SIZE + legendWidth, SIZE, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				image.setRGB(x, z, biomeColor(biome[x][z]));
			}
		}
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(0x202020));
		g.fillRect(SIZE, 0, legendWidth, SIZE);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
		int y = 24;
		for (Map.Entry<String, Double> entry : share.entrySet()) {
			g.setColor(new Color(biomeColor(entry.getKey())));
			g.fillRect(SIZE + 12, y - 12, 16, 16);
			g.setColor(Color.WHITE);
			g.drawString(String.format("%s  %.1f%%", entry.getKey().replace("minecraft:", "").replace("moderner_beta:", "mb:"), entry.getValue() * 100), SIZE + 36, y + 1);
			y += 22;
		}
		g.dispose();
		return image;
	}

	private static final Map<String, Integer> BIOME_COLORS = new HashMap<>();

	static {
		Object[][] palette = {
				{"ocean", 0x000070}, {"frozen_ocean", 0x7070D6}, {"deep_ocean", 0x000030}, {"cold_ocean", 0x202070}, {"lukewarm_ocean", 0x000090},
				{"warm_ocean", 0x0000AC}, {"river", 0x0000FF}, {"frozen_river", 0xA0A0FF}, {"beach", 0xFADE55}, {"snowy_beach", 0xFAF0C0},
				{"desert", 0xFA9418}, {"forest", 0x056621}, {"birch_forest", 0x307444}, {"dark_forest", 0x40511A}, {"jungle", 0x537B09},
				{"savanna", 0xBDB25F}, {"badlands", 0xD94515}, {"plains", 0x8DB360}, {"swamp", 0x07F9B2}, {"taiga", 0x0B6659},
				{"snowy_plains", 0xFFFFFF}, {"snowy_taiga", 0x31554A}, {"mushroom_fields", 0xFF00FF}, {"windswept_hills", 0x606060},
				{"late_beta_plains", 0x8DB360}, {"early_release_extreme_hills", 0x606060}, {"early_release_taiga", 0x31554A},
				{"early_release_swampland", 0x07F9B2}, {"early_release_ice_plains", 0xFFFFFF}, {"late_beta_extreme_hills", 0x606060},
				{"late_beta_taiga", 0x31554A}, {"late_beta_swampland", 0x07F9B2}, {"late_beta_ice_plains", 0xFFFFFF},
				{"beta_forest", 0x056621}, {"beta_rainforest", 0x537B09}, {"beta_seasonal_forest", 0x2D8E49}, {"beta_swampland", 0x07F9B2},
				{"beta_savanna", 0xBDB25F}, {"beta_shrubland", 0x9C9C46}, {"beta_taiga", 0x31554A}, {"beta_desert", 0xFA9418},
				{"beta_plains", 0x8DB360}, {"beta_ice_desert", 0xE6D9B3}, {"beta_tundra", 0xFFFFFF}, {"beta_ocean", 0x000070},
				{"beta_frozen_ocean", 0x7070D6}, {"beta_cold_ocean", 0x202070}, {"beta_lukewarm_ocean", 0x000090}, {"beta_warm_ocean", 0x0000AC},
				{"lush_caves", 0x7BA331}, {"dripstone_caves", 0x86562A}, {"deep_dark", 0x0A2A2A},
		};
		for (Object[] entry : palette) {
			BIOME_COLORS.put((String) entry[0], (Integer) entry[1]);
		}
	}

	private static int biomeColor(String id) {
		String path = id.substring(id.indexOf(':') + 1);
		Integer color = BIOME_COLORS.get(path);
		return color != null ? color : (id.hashCode() & 0xFFFFFF) | 0x404040;
	}

	private static Color blend(Color a, Color b, float t) {
		return new Color(clamp(a.getRed() + (b.getRed() - a.getRed()) * t), clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				clamp(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}

	private static int clamp(float value) {
		return Math.max(0, Math.min(255, Math.round(value)));
	}
}
