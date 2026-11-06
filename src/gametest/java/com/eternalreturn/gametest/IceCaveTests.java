package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.IceBlocks;
import com.eternalreturn.worldgen.stone.HardenedBlocks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Ice caves (phase 6): a cave biome only under snowy land and frozen seas (Moderner Beta's injection rule and cave biome
 * map, tools/worldgen/build_preset.py), about a quarter of the underground there, at least 8 blocks below the surface;
 * hardened packed ice is their stone (and only theirs), and icicles, ice crystals and snow grow in them. The icy
 * mountains are the high ground of the cold land and the extreme hills: above y 105, never low. Logs a /tp into each.
 */
public class IceCaveTests implements FabricGameTest {
	public static final String BATCH = "ice_caves";
	private static final String ICE_CAVES = "eternalreturn:ice_caves";
	private static final Set<String> COLD = Set.of("moderner_beta:early_release_ice_plains", "moderner_beta:early_release_taiga");
	private static final Set<String> WARM = Set.of("minecraft:forest", "minecraft:desert", "minecraft:jungle", "moderner_beta:late_beta_plains");

	/** Up to n chunk centres well inside these biomes (their neighbours 32 blocks off the same), nearest 0, 0 first. */
	static List<ChunkPos> spotsIn(ServerWorld world, Set<String> wanted, int n) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		BiomeSource source = generator.getBiomeSource();
		List<ChunkPos> found = new ArrayList<>();
		for (int ring = 0; ring <= 60 && found.size() < n; ring++) {
			for (int i = -ring; i <= ring && found.size() < n; i++) {
				for (int j = -ring; j <= ring && found.size() < n; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
						continue;
					}
					int x = i * 96;
					int z = j * 96;
					String here = BiomeStoneTests.id(source.getBiome(x >> 2, 16, z >> 2, noise.getMultiNoiseSampler()));
					if (!wanted.contains(here)) {
						continue;
					}
					boolean inside = true;
					for (int[] o : new int[][]{{32, 0}, {-32, 0}, {0, 32}, {0, -32}}) {
						inside &= here.equals(BiomeStoneTests.id(source.getBiome((x + o[0]) >> 2, 16, (z + o[1]) >> 2, noise.getMultiNoiseSampler())));
					}
					if (inside && found.stream().noneMatch(c -> Math.abs(c.x - (x >> 4)) < 8 && Math.abs(c.z - (z >> 4)) < 8)) {
						found.add(new ChunkPos(x >> 4, z >> 4));
					}
				}
			}
		}
		return found;
	}

	private static final Map<String, Integer> HIGH_BIOMES = new java.util.concurrent.ConcurrentHashMap<>();

	private record Census(int underground, int iceCave, int iceCaveNearSurface, Map<String, Integer> blocks, BlockPos firstIceCaveAir) {
	}

	/** Generates 3 x 3 chunks round each spot and reads the biome every 4 blocks underground, and the blocks in ice caves. */
	private static Census census(ServerWorld world, List<ChunkPos> spots) {
		int underground = 0;
		int iceCave = 0;
		int nearSurface = 0;
		BlockPos firstAir = null;
		Map<String, Integer> blocks = new TreeMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (ChunkPos centre : spots) {
			for (int cx = centre.x - 2; cx <= centre.x + 2; cx++) {
				for (int cz = centre.z - 2; cz <= centre.z + 2; cz++) {
					world.getChunk(cx, cz, ChunkStatus.FULL, true);
				}
			}
			for (int x = (centre.x - 1) * 16; x < (centre.x + 2) * 16; x++) {
				for (int z = (centre.z - 1) * 16; z < (centre.z + 2) * 16; z++) {
					int surface = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
					for (int y = world.getBottomY() + 4; y < surface; y++) {
						pos.set(x, y, z);
						boolean sample = (x & 3) == 0 && (z & 3) == 0 && (y & 3) == 0;
						RegistryEntry<Biome> biome = sample || y < surface - 4 ? world.getBiome(pos) : null;
						boolean ice = biome != null && biome.matchesId(EternalReturn.id("ice_caves"));
						if (sample) {
							underground++;
							if (ice) {
								iceCave++;
								if (y > surface - 6) {
									nearSurface++;
								}
							}
						}
						if (ice) {
							BlockState state = world.getBlockState(pos);
							Block block = state.getBlock();
							if (block == HardenedBlocks.get("hardened_packed_ice") || block == Blocks.STONE || block == IceBlocks.ICICLE || block == IceBlocks.ICE_CRYSTAL
									|| block == Blocks.SNOW || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE || block == Blocks.CALCITE) {
								blocks.merge(net.minecraft.registry.Registries.BLOCK.getId(block).getPath(), 1, Integer::sum);
							}
							if (state.isAir()) {
								blocks.merge("(air)", 1, Integer::sum);
							}
							if (firstAir == null && state.isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
								firstAir = pos.toImmutable();
							}
						}
					}
				}
			}
		}
		return new Census(underground, iceCave, nearSurface, blocks, firstAir);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 4_000_000)
	public void iceCavesUnderTheColdOnly(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<ChunkPos> cold = spotsIn(world, COLD, 4);
		List<ChunkPos> warm = spotsIn(world, WARM, 3);
		ctx.assertTrue(cold.size() >= 3 && warm.size() >= 2, cold.size() + " cold and " + warm.size() + " warm spots found");
		// Decoration only runs for biomes the biome source lists: the injected ones must be there.
		EternalReturn.LOGGER.info("[ice-caves] biome source lists ice caves: {}, icy mountains: {}",
				world.getChunkManager().getChunkGenerator().getBiomeSource().getBiomes().stream().anyMatch(b -> b.matchesId(EternalReturn.id("ice_caves"))),
				world.getChunkManager().getChunkGenerator().getBiomeSource().getBiomes().stream().anyMatch(b -> b.matchesId(EternalReturn.id("icy_mountains"))));
		Census under = census(world, cold);
		Census elsewhere = census(world, warm);
		double share = under.iceCave() / (double) Math.max(1, under.underground());
		EternalReturn.LOGGER.info("[ice-caves] under the cold ({}): {} of {} underground samples ice caves ({}%), {} within 6 of the surface; blocks in them {}; "
						+ "/tp @s {} {} {}; under warm land ({}): {} of {}",
				cold, under.iceCave(), under.underground(), String.format("%.1f", share * 100), under.iceCaveNearSurface(), under.blocks(),
				under.firstIceCaveAir() == null ? 0 : under.firstIceCaveAir().getX(), under.firstIceCaveAir() == null ? 0 : under.firstIceCaveAir().getY(),
				under.firstIceCaveAir() == null ? 0 : under.firstIceCaveAir().getZ(), warm, elsewhere.iceCave(), elsewhere.underground());
		List<String> problems = new ArrayList<>();
		if (share < 0.12 || share > 0.40) {
			problems.add(String.format("ice caves are %.1f%% of the underground under the cold, expected about 25%%", share * 100));
		}
		if (elsewhere.iceCave() > 0) {
			problems.add(elsewhere.iceCave() + " ice cave samples under warm land");
		}
		if (under.iceCaveNearSurface() > 0) {
			problems.add(under.iceCaveNearSurface() + " ice cave samples within 6 blocks of the surface");
		}
		Map<String, Integer> blocks = under.blocks();
		int hardened = blocks.getOrDefault("hardened_packed_ice", 0);
		int stone = blocks.getOrDefault("stone", 0);
		if (hardened < 1000 || stone * 10 > hardened) {
			problems.add("ice caves' stone: " + hardened + " hardened packed ice, " + stone + " stone");
		}
		for (String grows : List.of("icicle", "ice_crystal", "snow", "packed_ice")) {
			if (blocks.getOrDefault(grows, 0) < 5) {
				problems.add("few " + grows + " in ice caves: " + blocks.getOrDefault(grows, 0));
			}
		}
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	/** The icy mountains: around cold and extreme hills spots, the ground above y 105 is icy mountains, and none is low. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 4_000_000)
	public void icyMountainsOnTheHighGround(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		Set<String> parents = Set.of("moderner_beta:early_release_ice_plains", "moderner_beta:early_release_taiga", "moderner_beta:early_release_extreme_hills");
		// The highest ground near the cold and hills spots (sampled every 32 blocks), until one stands above 112.
		BlockPos high = null;
		for (ChunkPos spot : spotsIn(world, parents, 24)) {
			for (int dx = -96; dx <= 96 && high == null; dx += 32) {
				for (int dz = -96; dz <= 96 && high == null; dz += 32) {
					int x = spot.getCenterX() + dx;
					int z = spot.getCenterZ() + dz;
					int y = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, noise);
					if (y > 112) {
						high = new BlockPos(x, y, z);
					}
				}
			}
			if (high != null) {
				break;
			}
		}
		ctx.assertTrue(high != null, "no cold or hills ground above y 112 found");
		ChunkPos centre = new ChunkPos(high);
		for (int cx = centre.x - 1; cx <= centre.x + 1; cx++) {
			for (int cz = centre.z - 1; cz <= centre.z + 1; cz++) {
				world.getChunk(cx, cz, ChunkStatus.FULL, true);
			}
		}
		int icy = 0;
		int highColumns = 0;
		int lowIcy = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = centre.getStartX() - 16; x < centre.getStartX() + 32; x += 2) {
			for (int z = centre.getStartZ() - 16; z < centre.getStartZ() + 32; z += 2) {
				int y = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
				String biome = BiomeStoneTests.id(world.getBiome(pos.set(x, y, z)));
				boolean isIcy = biome.equals("eternalreturn:icy_mountains");
				// Only the land the rule covers: the cold and the extreme hills, their variants and sub-biomes.
				boolean covered = isIcy || parents.contains(com.eternalreturn.worldgen.variants.BiomeVariants.baseOf(net.minecraft.util.Identifier.of(biome)).toString());
				if (y >= 110 && covered) {
					highColumns++;
					icy += isIcy ? 1 : 0;
				}
				if (isIcy && y < 100) {
					lowIcy++;
				}
				if (y >= 110) {
					HIGH_BIOMES.merge(BiomeStoneTests.id(world.getBiome(pos.set(x, y, z))) + " @" + (y / 10 * 10), 1, Integer::sum);
				}
			}
		}
		EternalReturn.LOGGER.info("[ice-caves] the high columns' biomes {}", HIGH_BIOMES);
		EternalReturn.LOGGER.info("[ice-caves] icy mountains: /tp @s {} {} {}; {} of {} columns at y 110 or more are icy mountains, {} icy columns below y 100",
				high.getX(), high.getY() + 2, high.getZ(), icy, highColumns, lowIcy);
		ctx.assertTrue(highColumns > 0 && icy * 10 >= highColumns * 8, icy + " of " + highColumns + " high columns are icy mountains");
		ctx.assertTrue(lowIcy == 0, lowIcy + " icy mountain columns below y 100");
		ctx.complete();
	}
}
