package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.CaveMushrooms;
import com.eternalreturn.blocks.StoneDripstoneBlock;
import com.eternalreturn.blocks.StoneDripstones;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The mushroom and magma caves and the stone dripstone in Eternal Return worlds (owner's request, 2026-11-05; batch
 * underground_biomes, Eternal Return run). Round warm and cold spots, 5 x 5 chunks are generated and the middle 3 x 3
 * read: the biome every 4 blocks underground, and the blocks in each cave biome. Mushroom caves lie only under land that
 * isn't snowy, magma caves only at y 0 or below, ice caves still only under the cold; each has its own things; the stone
 * dripstone in the caves is the stone it touches. Also the Nether's caves have dripstone of their stones. Logs a /tp into
 * each cave biome.
 */
public class UndergroundBiomeTests implements FabricGameTest {
	public static final String BATCH = "underground_biomes";
	private static final Set<String> COLD = Set.of("moderner_beta:early_release_ice_plains", "moderner_beta:early_release_taiga");
	private static final Set<String> WARM = Set.of("minecraft:forest", "minecraft:desert", "minecraft:jungle", "moderner_beta:late_beta_plains",
			"minecraft:birch_forest", "minecraft:savanna");

	private static final class Census {
		int underground;
		int deep;
		final Map<String, Integer> biomes = new TreeMap<>();
		final Map<String, Integer> deepBiomes = new TreeMap<>();
		final Map<String, Map<String, Integer>> blocks = new TreeMap<>();
		final Map<String, BlockPos> firstAir = new HashMap<>();
		int highMagma;
		int dripstone;
		int dripstoneWrong;
		final List<String> wrongExamples = new ArrayList<>();
	}

	private static String id(RegistryEntry<Biome> biome) {
		return biome.getKey().map(key -> key.getValue().toString()).orElse("?");
	}

	private static void census(ServerWorld world, List<ChunkPos> spots, Census census) {
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
						BlockState state = world.getBlockState(pos);
						if (state.getBlock() instanceof StoneDripstoneBlock dripstone) {
							census.dripstone++;
							// Each column's root: the piece fixed to the stone; the stone must be its own.
							Direction pointing = state.get(StoneDripstoneBlock.VERTICAL_DIRECTION);
							BlockState behind = world.getBlockState(pos.offset(pointing.getOpposite()));
							if (!behind.isOf(dripstone) && StoneDripstones.forHost(behind.getBlock()) != dripstone) {
								census.dripstoneWrong++;
								if (census.wrongExamples.size() < 5) {
									census.wrongExamples.add(Registries.BLOCK.getId(dripstone).getPath() + " on " + Registries.BLOCK.getId(behind.getBlock()).getPath() + " at " + pos.toShortString());
								}
							}
						}
						boolean sample = (x & 3) == 0 && (z & 3) == 0 && (y & 3) == 0;
						if (!sample && y >= surface - 4) {
							continue;
						}
						String biome = id(world.getBiome(pos));
						if (sample) {
							census.underground++;
							census.biomes.merge(biome, 1, Integer::sum);
							if (y <= 0) {
								census.deep++;
								census.deepBiomes.merge(biome, 1, Integer::sum);
							}
							if (biome.equals("eternalreturn:magma_caves") && y > 4) {
								census.highMagma++;
							}
						}
						if (biome.startsWith("eternalreturn:") && biome.endsWith("_caves")) {
							census.blocks.computeIfAbsent(biome, b -> new TreeMap<>()).merge(Registries.BLOCK.getId(state.getBlock()).getPath(), 1, Integer::sum);
							if (state.isAir() && !census.firstAir.containsKey(biome) && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
								census.firstAir.put(biome, pos.toImmutable());
							}
						}
					}
				}
			}
		}
	}

	private static double share(Map<String, Integer> biomes, int of, String biome) {
		return biomes.getOrDefault("eternalreturn:" + biome, 0) / (double) Math.max(1, of);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 4_000_000)
	public void newCaveBiomesWhereTheyBelong(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<ChunkPos> warm = IceCaveTests.spotsIn(world, WARM, 5);
		List<ChunkPos> cold = IceCaveTests.spotsIn(world, COLD, 3);
		ctx.assertTrue(warm.size() >= 3 && cold.size() >= 2, warm.size() + " warm and " + cold.size() + " cold spots found");
		Census under = new Census();
		Census frozen = new Census();
		census(world, warm, under);
		census(world, cold, frozen);
		double mushroom = share(under.biomes, under.underground, "mushroom_caves");
		double magmaWarm = share(under.deepBiomes, under.deep, "magma_caves");
		double magmaCold = share(frozen.deepBiomes, frozen.deep, "magma_caves");
		double ice = share(frozen.biomes, frozen.underground, "ice_caves");
		EternalReturn.LOGGER.info("[underground] warm {}: {} samples, mushroom caves {}%, magma caves {}% of {} at y 0 or below; cold {}: ice caves {}%, magma {}%, mushroom {}",
				warm, under.underground, String.format("%.1f", mushroom * 100), String.format("%.1f", magmaWarm * 100), under.deep, cold,
				String.format("%.1f", ice * 100), String.format("%.1f", magmaCold * 100), frozen.biomes.getOrDefault("eternalreturn:mushroom_caves", 0));
		for (Census c : List.of(under, frozen)) {
			c.firstAir.forEach((biome, at) -> EternalReturn.LOGGER.info("[underground] {}: /tp @s {} {} {}", biome, at.getX(), at.getY(), at.getZ()));
			c.blocks.forEach((biome, blocks) -> EternalReturn.LOGGER.info("[underground] blocks in {}: {}", biome, blocks));
		}
		EternalReturn.LOGGER.info("[underground] stone dripstone: {} pieces under warm land, {} under cold; {} and {} on another stone {}",
				under.dripstone, frozen.dripstone, under.dripstoneWrong, frozen.dripstoneWrong, under.wrongExamples);
		List<String> problems = new ArrayList<>();
		if (!Boolean.TRUE.equals(com.eternalreturn.compat.ModernerBetaHooks.APPLIED.get("ModernerBetaCaveBiomeMixin"))
				|| com.eternalreturn.compat.ModernerBetaHooks.CAVE_FILTER_CALLS.get() == 0) {
			problems.add("the cave biome hook into Moderner Beta isn't applied or never ran: " + com.eternalreturn.compat.ModernerBetaHooks.APPLIED);
		}
		if (mushroom < 0.03 || mushroom > 0.35) {
			problems.add(String.format("mushroom caves are %.1f%% of the underground under warm land", mushroom * 100));
		}
		if (magmaWarm + magmaCold < 0.03) {
			problems.add(String.format("magma caves are %.1f%% and %.1f%% of the deep underground", magmaWarm * 100, magmaCold * 100));
		}
		if (frozen.biomes.getOrDefault("eternalreturn:mushroom_caves", 0) > 0) {
			problems.add("mushroom caves under the cold");
		}
		if (under.biomes.getOrDefault("eternalreturn:ice_caves", 0) > 0) {
			problems.add("ice caves under warm land");
		}
		if (ice < 0.12 || ice > 0.40) {
			problems.add(String.format("ice caves are %.1f%% under the cold", ice * 100));
		}
		if (under.highMagma + frozen.highMagma > 0) {
			problems.add((under.highMagma + frozen.highMagma) + " magma cave samples above y 4");
		}
		Map<String, Integer> mush = under.blocks.getOrDefault("eternalreturn:mushroom_caves", Map.of());
		// Huge mushrooms need room: the domes (red, green) fit most tunnels, the flat caps (brown, purple, 7 wide) the wider
		// rooms or press into a tunnel's walls, the blue cones (11 or more tall) only big caverns, which the sample may miss. Several kinds must turn up.
		int kinds = 0;
		int caps = 0;
		for (Block block : List.of(Blocks.RED_MUSHROOM_BLOCK, Blocks.BROWN_MUSHROOM_BLOCK, CaveMushrooms.PUFFED_PURPLE_MUSHROOM_BLOCK, CaveMushrooms.GREEN_MUSHROOM_BLOCK,
				CaveMushrooms.PUFFED_BLUE_MUSHROOM_BLOCK)) {
			int n = mush.getOrDefault(Registries.BLOCK.getId(block).getPath(), 0);
			kinds += n > 0 ? 1 : 0;
			caps += n;
		}
		if (kinds < 3 || caps < 100 || mush.getOrDefault("puffed_purple_mushroom_block", 0) == 0) {
			problems.add(kinds + " kinds of huge mushroom, " + caps + " cap blocks in mushroom caves (" + mush.getOrDefault("puffed_purple_mushroom_block", 0) + " purple)");
		}
		for (Block block : List.of(CaveMushrooms.PUFFED_PURPLE_MUSHROOM, CaveMushrooms.PUFFED_BLUE_MUSHROOM, CaveMushrooms.GREEN_MUSHROOM, Blocks.MUSHROOM_STEM)) {
			if (mush.getOrDefault(Registries.BLOCK.getId(block).getPath(), 0) < 5) {
				problems.add("few " + Registries.BLOCK.getId(block).getPath() + " in mushroom caves");
			}
		}
		for (Block never : List.of(Blocks.MYCELIUM, CaveMushrooms.PURPLE_MUSHROOM, CaveMushrooms.BLUE_MUSHROOM, CaveMushrooms.PURPLE_MUSHROOM_BLOCK,
				CaveMushrooms.BLUE_MUSHROOM_BLOCK)) {
			if (mush.getOrDefault(Registries.BLOCK.getId(never).getPath(), 0) > 0) {
				problems.add(Registries.BLOCK.getId(never).getPath() + " in mushroom caves");
			}
		}
		Map<String, Integer> magma = new TreeMap<>(under.blocks.getOrDefault("eternalreturn:magma_caves", Map.of()));
		frozen.blocks.getOrDefault("eternalreturn:magma_caves", Map.of()).forEach((k, v) -> magma.merge(k, v, Integer::sum));
		if (magma.getOrDefault("magma_block", 0) < 100 || magma.getOrDefault("lava", 0) < 10) {
			problems.add("magma caves hold " + magma.getOrDefault("magma_block", 0) + " magma and " + magma.getOrDefault("lava", 0) + " lava");
		}
		int dripstone = under.dripstone + frozen.dripstone;
		int wrong = under.dripstoneWrong + frozen.dripstoneWrong;
		if (dripstone < 30 || wrong * 50 > dripstone) {
			problems.add(dripstone + " stone dripstone pieces, " + wrong + " on another stone: " + under.wrongExamples + frozen.wrongExamples);
		}
		ctx.assertTrue(problems.isEmpty(), String.join("; ", problems));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 4_000_000)
	public void stoneDripstoneInTheNether(TestContext ctx) {
		ServerWorld nether = ctx.getWorld().getServer().getWorld(World.NETHER);
		ctx.assertTrue(nether != null, "no Nether");
		Map<String, Integer> found = new TreeMap<>();
		for (int cx = 10; cx <= 14; cx++) {
			for (int cz = 10; cz <= 14; cz++) {
				nether.getChunk(cx, cz, ChunkStatus.FULL, true);
			}
		}
		BlockPos at = null;
		for (BlockPos pos : BlockPos.iterate(176, 1, 176, 223, 126, 223)) {
			Block block = nether.getBlockState(pos).getBlock();
			if (block instanceof StoneDripstoneBlock) {
				found.merge(Registries.BLOCK.getId(block).getPath(), 1, Integer::sum);
				at = at == null ? pos.toImmutable() : at;
			}
		}
		EternalReturn.LOGGER.info("[underground] Nether dripstone in 3 x 3 chunks: {}; /execute in minecraft:the_nether run tp @s {}", found,
				at == null ? "-" : at.toShortString().replace(",", ""));
		ctx.assertTrue(found.values().stream().mapToInt(Integer::intValue).sum() >= 5, "Nether dripstone: " + found);
		ctx.complete();
	}
}
