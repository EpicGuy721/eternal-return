package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.end.Megastructures;
import com.eternalreturn.worldgen.end.OuterEnd;
import com.eternalreturn.worldgen.end.OuterEndTerrain;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The outer End of Eternal Return worlds (com.eternalreturn.worldgen.end), on the test seed: the main island is
 * vanilla's, the outer islands spread over many heights and generate as planned, chorus grows on them, and the obsidian
 * megastructures are broken, mixed with crying obsidian and amethyst, and clear of End cities. The map batch draws
 * them into docs/worldgen-maps/outer_end.
 */
public class OuterEndTests implements FabricGameTest {
	public static final String BATCH = "outer_end";
	public static final String MAP_BATCH = "endmap";

	private static ServerWorld end(TestContext ctx) {
		ServerWorld end = ctx.getWorld().getServer().getWorld(World.END);
		ctx.assertTrue(end != null, "no End");
		return end;
	}

	private static OuterEndTerrain terrain(TestContext ctx, ServerWorld end) {
		OuterEndTerrain terrain = OuterEnd.terrain(end);
		ctx.assertTrue(terrain != null, "the End of an Eternal Return world has no outer End terrain");
		return terrain;
	}

	/** Main islands of every square in a ring of the outer End, nearest to 0, 0 first. */
	private static List<OuterEndTerrain.Island> islands(OuterEndTerrain terrain, int reach) {
		List<OuterEndTerrain.Island> islands = new ArrayList<>();
		int cells = reach / OuterEndTerrain.CELL + 1;
		for (int cx = -cells; cx <= cells; cx++) {
			for (int cz = -cells; cz <= cells; cz++) {
				islands.addAll(terrain.mainIslands(cx, cz));
			}
		}
		islands.sort(Comparator.comparingDouble(island -> island.x * island.x + island.z * island.z));
		return islands;
	}

	private static void generate(ServerWorld world, int blockX, int blockZ, int radiusChunks) {
		for (int chunkX = (blockX >> 4) - radiusChunks; chunkX <= (blockX >> 4) + radiusChunks; chunkX++) {
			for (int chunkZ = (blockZ >> 4) - radiusChunks; chunkZ <= (blockZ >> 4) + radiusChunks; chunkZ++) {
				world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
			}
		}
	}

	/**
	 * Within 1,000 blocks of 0, 0 the density is vanilla's End density at every point sampled (so the main island,
	 * its pillars, portal and gateways come out block for block), and nothing above y 128, where vanilla's End stops.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void innerEndIsVanilla(TestContext ctx) {
		ServerWorld end = end(ctx);
		terrain(ctx, end);
		ChunkGeneratorSettings vanillaSettings = end.getRegistryManager().get(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
				.getOrThrow(ChunkGeneratorSettings.END);
		NoiseConfig vanilla = NoiseConfig.create(vanillaSettings, end.getRegistryManager().getWrapperOrThrow(RegistryKeys.NOISE_PARAMETERS), end.getSeed());
		DensityFunction ours = end.getChunkManager().getNoiseConfig().getNoiseRouter().finalDensity();
		DensityFunction theirs = vanilla.getNoiseRouter().finalDensity();
		int compared = 0;
		int solid = 0;
		for (int x = -992; x <= 992; x += 24) {
			for (int z = -992; z <= 992; z += 24) {
				if (x * x + z * z >= OuterEndTerrain.INNER * OuterEndTerrain.INNER) {
					continue;
				}
				for (int y = 0; y < 128; y += 3) {
					DensityFunction.UnblendedNoisePos pos = new DensityFunction.UnblendedNoisePos(x, y, z);
					double a = ours.sample(pos);
					double b = theirs.sample(pos);
					ctx.assertTrue(a == b, "density at " + x + " " + y + " " + z + " is " + a + ", vanilla's " + b);
					compared++;
					solid += a > 0 ? 1 : 0;
				}
				ctx.assertTrue(ours.sample(new DensityFunction.UnblendedNoisePos(x, 130, z)) < 0, "solid above y 128 at " + x + " " + z);
			}
		}
		// The main island must be in there.
		ctx.assertTrue(solid > 200, "only " + solid + " solid points of " + compared + " near 0, 0");
		EternalReturn.LOGGER.info("[outer end] inner End: {} points match vanilla, {} solid", compared, solid);
		ctx.complete();
	}

	/**
	 * Over 12,000 x 12,000 blocks: plenty of islands, none within the void gap; most in the middle band, but some low over
	 * the void and some high above the rest; and the gaps between neighbours small enough to bridge.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void outerIslandsSpreadInHeight(TestContext ctx) {
		OuterEndTerrain terrain = terrain(ctx, end(ctx));
		List<OuterEndTerrain.Island> islands = islands(terrain, 6000);
		ctx.assertTrue(islands.size() > 1000, "only " + islands.size() + " islands");
		int low = 0, high = 0, middle = 0;
		for (OuterEndTerrain.Island island : islands) {
			double r = Math.sqrt(island.x * island.x + island.z * island.z);
			ctx.assertTrue(r - island.radius() * 1.3 >= OuterEndTerrain.START - 1, "island too near the main island: " + island.x + " " + island.z);
			low += island.level < 55 ? 1 : 0;
			high += island.level > 150 ? 1 : 0;
			middle += island.level >= 60 && island.level <= 140 ? 1 : 0;
		}
		int n = islands.size();
		ctx.assertTrue(low > n * 0.08 && high > n * 0.08 && middle > n * 0.5, "heights: " + low + " low, " + middle + " middle, " + high + " high of " + n);
		// Gap from each island's edge to its nearest neighbour's.
		List<Double> gaps = new ArrayList<>();
		for (OuterEndTerrain.Island a : islands.subList(0, 400)) {
			double best = Double.MAX_VALUE;
			for (OuterEndTerrain.Island b : islands) {
				if (a != b) {
					double d = Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.z - b.z, 2)) - a.radius() - b.radius();
					best = Math.min(best, d);
				}
			}
			gaps.add(best);
		}
		gaps.sort(Double::compare);
		double median = gaps.get(gaps.size() / 2);
		double ninety = gaps.get(gaps.size() * 9 / 10);
		EternalReturn.LOGGER.info("[outer end] {} islands: {} low (y < 55), {} middle (60-140), {} high (y > 150); gap to the nearest island: median {}, 90th percentile {}",
				n, low, middle, high, Math.round(median), Math.round(ninety));
		ctx.assertTrue(ninety < 140, "gaps too wide to bridge: 90th percentile " + ninety);
		ctx.complete();
	}

	/**
	 * The nearest islands generate as the terrain plans them: every block of their middle chunk is end stone exactly
	 * where the density is positive (leaving out what decoration put there), and their tops come in more than one step.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void outerIslandsGenerateAsPlanned(TestContext ctx) {
		ServerWorld end = end(ctx);
		OuterEndTerrain terrain = terrain(ctx, end);
		List<OuterEndTerrain.Island> islands = islands(terrain, 2400);
		int checked = 0;
		int stepped = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (OuterEndTerrain.Island island : islands.subList(0, 4)) {
			int x0 = (int) island.x;
			int z0 = (int) island.z;
			generate(end, x0, z0, 1);
			WorldChunk chunk = end.getChunk(x0 >> 4, z0 >> 4);
			java.util.Set<Integer> tops = new java.util.TreeSet<>();
			for (int x = chunk.getPos().getStartX(); x <= chunk.getPos().getEndX(); x++) {
				for (int z = chunk.getPos().getStartZ(); z <= chunk.getPos().getEndZ(); z++) {
					int top = terrain.islandTop(x, z);
					if (top != Integer.MIN_VALUE) {
						tops.add(top);
					}
					for (int y = 1; y < 255; y++) {
						BlockState state = chunk.getBlockState(pos.set(x, y, z));
						if (!state.isAir() && !state.isOf(Blocks.END_STONE)) {
							continue;
						}
						boolean planned = terrain.density(x, y, z) > 0;
						ctx.assertTrue(planned == state.isOf(Blocks.END_STONE), "at " + x + " " + y + " " + z + ": " + state + ", density " + terrain.density(x, y, z));
						checked++;
					}
				}
			}
			if (island.tiers > 1 && tops.size() > 2) {
				stepped++;
			}
			EternalReturn.LOGGER.info("[outer end] island at {} {} {}: {} by {} blocks, {} tiers, tops in its middle chunk {}",
					x0, island.level, z0, Math.round(island.radiusA * 2), Math.round(island.radiusB * 2), island.tiers, tops);
		}
		ctx.assertTrue(checked > 10_000, "only " + checked + " blocks checked");
		ctx.assertTrue(islands.subList(0, 4).stream().noneMatch(island -> island.tiers > 1) || stepped > 0, "no island with tiers has steps");
		ctx.complete();
	}

	/** Chorus grows on a highlands island (vanilla's chorus) and on a high island (this mod's). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void chorusGrowsOnIslands(TestContext ctx) {
		ServerWorld end = end(ctx);
		OuterEndTerrain terrain = terrain(ctx, end);
		OuterEndTerrain.Island highlands = null;
		OuterEndTerrain.Island high = null;
		for (OuterEndTerrain.Island island : islands(terrain, 4000)) {
			if (island.radius() < 28) {
				continue;
			}
			int x = (int) island.x, z = (int) island.z;
			boolean isHighlands = end.getBiome(new BlockPos(x, 64, z)).matchesKey(BiomeKeys.END_HIGHLANDS);
			if (highlands == null && isHighlands) {
				highlands = island;
			} else if (high == null && island.level > OuterEndTerrain.CITY_MAX_TOP) {
				high = island;
			}
		}
		ctx.assertTrue(highlands != null && high != null, "no big highlands island or high island within 4,000 blocks");
		for (OuterEndTerrain.Island island : List.of(highlands, high)) {
			int x0 = (int) island.x, z0 = (int) island.z;
			generate(end, x0, z0, 3);
			int chorus = 0;
			BlockPos.Mutable pos = new BlockPos.Mutable();
			for (int x = x0 - 40; x <= x0 + 40; x++) {
				for (int z = z0 - 40; z <= z0 + 40; z++) {
					for (int y = 1; y < 255; y++) {
						chorus += end.getBlockState(pos.set(x, y, z)).isOf(Blocks.CHORUS_FLOWER) ? 1 : 0;
					}
				}
			}
			EternalReturn.LOGGER.info("[outer end] island at {} {} {}: {} chorus flowers", x0, island.level, z0, chorus);
			ctx.assertTrue(chorus > 0, "no chorus on the island at " + x0 + " " + island.level + " " + z0);
		}
		ctx.complete();
	}

	/**
	 * The nearest megastructures: obsidian with crying obsidian through it, amethyst clusters and buds but no amethyst
	 * blocks, and no End city within reach.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void megastructuresAreShattered(TestContext ctx) {
		ServerWorld end = end(ctx);
		Megastructures megastructures = Megastructures.of(end);
		ctx.assertTrue(megastructures != null, "no megastructures");
		List<Megastructures.Structure> found = new ArrayList<>();
		int spacing = megastructures.settings().spacing();
		for (int cx = -4000 / spacing; cx <= 4000 / spacing; cx++) {
			for (int cz = -4000 / spacing; cz <= 4000 / spacing; cz++) {
				Megastructures.Structure structure = megastructures.inCell(cx, cz);
				if (structure != null) {
					found.add(structure);
				}
			}
		}
		ctx.assertTrue(found.size() > 40, "only " + found.size() + " megastructures within 4,000 blocks");
		java.util.Map<Megastructures.Kind, Integer> kinds = new java.util.EnumMap<>(Megastructures.Kind.class);
		java.util.Map<Megastructures.Setting, Integer> settings = new java.util.EnumMap<>(Megastructures.Setting.class);
		int massive = 0;
		for (Megastructures.Structure structure : found) {
			kinds.merge(structure.kind, 1, Integer::sum);
			settings.merge(structure.setting, 1, Integer::sum);
			massive += structure.massive ? 1 : 0;
			ctx.assertTrue(Math.sqrt(structure.x * structure.x + structure.z * structure.z) - structure.size * 1.9 >= Megastructures.EXCLUSION - 1,
					"megastructure in the gateways' landing ring at " + structure.x + " " + structure.z);
		}
		EternalReturn.LOGGER.info("[outer end] {} megastructures within 4,000 blocks ({} massive): {}, {}", found.size(), massive, kinds, settings);
		ctx.assertTrue(kinds.size() == Megastructures.Kind.values().length, "missing kinds: " + kinds);
		ctx.assertTrue(settings.size() == Megastructures.Setting.values().length, "missing settings: " + settings);

		found.sort(Comparator.comparingDouble(s -> s.x * s.x + s.z * s.z));
		RegistryEntry<Structure> city = end.getRegistryManager().get(RegistryKeys.STRUCTURE).getEntry(StructureKeys.END_CITY).orElseThrow();
		int obsidian = 0, crying = 0, amethyst = 0, blocks = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (Megastructures.Structure s : found.subList(0, 3)) {
			int x0 = Math.max(s.minX, (int) s.x - 40), x1 = Math.min(s.maxX, (int) s.x + 40);
			int z0 = Math.max(s.minZ, (int) s.z - 40), z1 = Math.min(s.maxZ, (int) s.z + 40);
			for (int chunkX = x0 >> 4; chunkX <= x1 >> 4; chunkX++) {
				for (int chunkZ = z0 >> 4; chunkZ <= z1 >> 4; chunkZ++) {
					end.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
				}
			}
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					for (int y = s.minY; y <= s.maxY; y++) {
						BlockState state = end.getBlockState(pos.set(x, y, z));
						ctx.assertTrue(!state.isOf(Blocks.AMETHYST_BLOCK) && !state.isOf(Blocks.BUDDING_AMETHYST), "amethyst block at " + pos);
						obsidian += state.isOf(Blocks.OBSIDIAN) ? 1 : 0;
						crying += state.isOf(Blocks.CRYING_OBSIDIAN) ? 1 : 0;
						amethyst += state.isOf(Blocks.AMETHYST_CLUSTER) || state.isOf(Blocks.LARGE_AMETHYST_BUD) || state.isOf(Blocks.MEDIUM_AMETHYST_BUD)
								|| state.isOf(Blocks.SMALL_AMETHYST_BUD) ? 1 : 0;
					}
				}
			}
			blocks = obsidian + crying;
			Pair<BlockPos, RegistryEntry<Structure>> nearestCity = end.getChunkManager().getChunkGenerator()
					.locateStructure(end, RegistryEntryList.of(city), BlockPos.ofFloored(s.x, s.y, s.z), 16, false);
			double reach = Math.max(s.maxX - s.minX, s.maxZ - s.minZ) / 2.0;
			if (nearestCity != null) {
				double d = Math.sqrt(nearestCity.getFirst().getSquaredDistance(s.x, nearestCity.getFirst().getY(), s.z));
				EternalReturn.LOGGER.info("[outer end] {} at {} {} {}: nearest End city {} blocks away", s.kind, Math.round(s.x), Math.round(s.y), Math.round(s.z), Math.round(d));
				ctx.assertTrue(d > reach + 32, s.kind + " at " + s.x + " " + s.z + " is " + d + " blocks from an End city (reach " + reach + ")");
			}
		}
		double share = crying / (double) Math.max(1, blocks);
		EternalReturn.LOGGER.info("[outer end] the nearest three megastructures: {} obsidian, {} crying obsidian ({} percent), {} amethyst clusters and buds",
				obsidian, crying, Math.round(share * 100), amethyst);
		ctx.assertTrue(blocks > 500, "only " + blocks + " obsidian blocks");
		ctx.assertTrue(share > 0.06 && share < 0.45, "crying obsidian share " + share);
		ctx.assertTrue(amethyst > 10, "only " + amethyst + " amethyst clusters and buds");
		ctx.complete();
	}

	/** Time to generate outer End chunks in full, against vanilla's End code (the void round the main island). */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 2_000_000)
	public void outerEndChunksAreQuick(TestContext ctx) {
		ServerWorld end = end(ctx);
		terrain(ctx, end);
		long vanilla = time(end, 40, 0);
		long outer = time(end, 300, 0);
		long vanilla2 = time(end, 0, 40);
		long outer2 = time(end, 0, 300);
		double vanillaMs = (vanilla + vanilla2) / 2.0 / 64 / 1e6;
		double outerMs = (outer + outer2) / 2.0 / 64 / 1e6;
		EternalReturn.LOGGER.info("[outer end] full chunk generation, 8 x 8 chunks each: vanilla's code {} ms a chunk, outer End {} ms a chunk",
				String.format("%.1f", vanillaMs), String.format("%.1f", outerMs));
		ctx.assertTrue(outerMs < 80, "outer End chunks take " + outerMs + " ms each");
		ctx.complete();
	}

	private static long time(ServerWorld world, int chunkX, int chunkZ) {
		long start = System.nanoTime();
		for (int x = chunkX; x < chunkX + 8; x++) {
			for (int z = chunkZ; z < chunkZ + 8; z++) {
				world.getChunk(x, z, ChunkStatus.FULL, true);
			}
		}
		return System.nanoTime() - start;
	}

	// ------------------------------------------------------------------ map

	/**
	 * docs/worldgen-maps/outer_end: islands.png, the outer End from above (2,048 blocks square east of the main island,
	 * coloured by height, megastructures dark), and side views through a massive megastructure and a medium one.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = MAP_BATCH, tickLimit = 2_000_000)
	public void drawOuterEnd(TestContext ctx) throws Exception {
		ServerWorld end = end(ctx);
		OuterEndTerrain terrain = terrain(ctx, end);
		Megastructures megastructures = Megastructures.of(end);
		Path out = Path.of(System.getProperty("eternalreturn.worldmap.out"));
		Files.createDirectories(out);
		int size = 2048, x0 = 1200, z0 = -1024;
		BufferedImage top = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		for (int px = 0; px < size; px++) {
			for (int pz = 0; pz < size; pz++) {
				int h = terrain.surface(x0 + px, z0 + pz);
				top.setRGB(px, pz, h == Integer.MIN_VALUE ? 0x0B0A12 : heightColour(h));
			}
		}
		double[] scratch = new double[3];
		List<Megastructures.Structure> structures = new ArrayList<>();
		for (int cx = Math.floorDiv(x0, megastructures.settings().spacing()); cx <= Math.floorDiv(x0 + size, megastructures.settings().spacing()); cx++) {
			for (int cz = Math.floorDiv(z0, megastructures.settings().spacing()); cz <= Math.floorDiv(z0 + size, megastructures.settings().spacing()); cz++) {
				Megastructures.Structure s = megastructures.inCell(cx, cz);
				if (s != null) {
					structures.add(s);
				}
			}
		}
		for (Megastructures.Structure s : structures) {
			for (int x = s.minX; x <= s.maxX; x++) {
				for (int z = s.minZ; z <= s.maxZ; z++) {
					if (x < x0 || x >= x0 + size || z < z0 || z >= z0 + size) {
						continue;
					}
					for (int y = s.maxY; y >= s.minY; y--) {
						int v = megastructures.sample(s, x, y, z, scratch);
						if (v != Megastructures.EMPTY) {
							top.setRGB(x - x0, z - z0, (v & 3) == Megastructures.CRYING ? 0x8A2BE2 : 0x2A1840);
							break;
						}
					}
				}
			}
		}
		ImageIO.write(top, "png", out.resolve("islands.png").toFile());
		// One of each kind (nearest to the square's middle), and the nearest massive one, seen from the south.
		List<Megastructures.Structure> picks = new ArrayList<>();
		int spacing = megastructures.settings().spacing();
		List<Megastructures.Structure> all = new ArrayList<>();
		for (int cx = -4000 / spacing; cx <= 4000 / spacing; cx++) {
			for (int cz = -4000 / spacing; cz <= 4000 / spacing; cz++) {
				Megastructures.Structure s = megastructures.inCell(cx, cz);
				if (s != null) {
					all.add(s);
				}
			}
		}
		all.sort(Comparator.comparingDouble(s -> Math.pow(s.x - 2200, 2) + s.z * s.z));
		for (Megastructures.Kind kind : Megastructures.Kind.values()) {
			all.stream().filter(s -> s.kind == kind && !s.massive).findFirst().ifPresent(picks::add);
		}
		all.stream().filter(s -> s.massive).limit(2).forEach(picks::add);
		int tile = 384;
		BufferedImage gallery = new BufferedImage(tile * 4, 256 * 2, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < picks.size(); i++) {
			Megastructures.Structure s = picks.get(i);
			BufferedImage view = view(terrain, megastructures, s, all, tile);
			gallery.getGraphics().drawImage(view, (i % 4) * tile, (i / 4) * 256, null);
			EternalReturn.LOGGER.info("[outer end] gallery {}: {} {}{} at {} {} {}", i, s.kind, s.setting, s.massive ? " massive" : "",
					Math.round(s.x), Math.round(s.y), Math.round(s.z));
		}
		ImageIO.write(gallery, "png", out.resolve("megastructures.png").toFile());
		ctx.complete();
	}

	/**
	 * A view from the south, width blocks wide round a structure, y 0 to 256: the nearest solid block along each line of
	 * sight through the structure's box and 96 blocks of terrain either side, darker the further back it is.
	 */
	private static BufferedImage view(OuterEndTerrain terrain, Megastructures megastructures, Megastructures.Structure s,
			List<Megastructures.Structure> all, int width) {
		BufferedImage image = new BufferedImage(width, 256, BufferedImage.TYPE_INT_RGB);
		int xStart = (int) s.x - width / 2;
		int zNear = Math.max(s.maxZ, (int) s.z + 96), zFar = Math.min(s.minZ, (int) s.z - 96);
		int[] colour = new int[width * 256];
		boolean[] done = new boolean[width * 256];
		java.util.Arrays.fill(colour, 0x0B0A12);
		double[] scratch = new double[3];
		List<Megastructures.Structure> near = all.stream().filter(o -> o.maxX >= xStart && o.minX < xStart + width && o.maxZ >= zFar && o.minZ <= zNear).toList();
		for (int z = zNear; z >= zFar; z--) {
			float shade = 1.0f - 0.6f * (zNear - z) / (float) (zNear - zFar);
			for (int px = 0; px < width; px++) {
				int x = xStart + px;
				for (int y = 1; y < 255; y++) {
					int index = px * 256 + y;
					if (done[index]) {
						continue;
					}
					int found = -1;
					for (Megastructures.Structure o : near) {
						if (x >= o.minX && x <= o.maxX && z >= o.minZ && z <= o.maxZ && y >= o.minY && y <= o.maxY) {
							int v = megastructures.sample(o, x, y, z, scratch);
							if (v != Megastructures.EMPTY) {
								found = (v & 3) == Megastructures.CRYING ? 0x9A3CF0 : (v & Megastructures.DAMAGED) != 0 ? 0x5A3A80 : 0x3A2858;
								break;
							}
						}
					}
					// Terrain in front of the structure would hide it: only what is level with it or behind.
					if (found < 0 && z <= s.maxZ && terrain.density(x, y, z) > 0) {
						found = 0xDBDE9E;
					}
					if (found >= 0) {
						done[index] = true;
						int r = (int) ((found >> 16 & 255) * shade), g = (int) ((found >> 8 & 255) * shade), b = (int) ((found & 255) * shade);
						colour[index] = r << 16 | g << 8 | b;
					}
				}
			}
		}
		for (int px = 0; px < width; px++) {
			for (int y = 0; y < 256; y++) {
				image.setRGB(px, 255 - y, colour[px * 256 + y]);
			}
		}
		return image;
	}

	private static int heightColour(int h) {
		float t = Math.max(0, Math.min(1, h / 230f));
		int r = (int) (60 + 195 * t), g = (int) (40 + 190 * t), b = (int) (120 + 40 * (1 - t));
		return r << 16 | g << 8 | b;
	}
}
