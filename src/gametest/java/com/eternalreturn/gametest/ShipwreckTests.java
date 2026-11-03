package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureSetKeys;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;

import java.util.Optional;

/**
 * Eternal Return world (batch shipwrecks): shipwrecks come back at 1% of vanilla's rate, each with its buried treasure
 * in the same chunk, and vanilla's own shipwreck and buried treasure sets stay out. (The test world has structures
 * off, so this checks the placements; CraftRulesTests buries a chest by hand.)
 */
public class ShipwreckTests implements FabricGameTest {
	public static final String BATCH = "shipwrecks";

	private static Optional<RegistryEntry<StructureSet>> set(StructurePlacementCalculator calculator, RegistryKey<StructureSet> key) {
		return calculator.getStructureSets().stream().filter(set -> set.matchesKey(key)).findFirst();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void rareShipwrecksWithTreasure(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		StructurePlacementCalculator calculator = world.getChunkManager().getStructurePlacementCalculator();
		ctx.assertTrue(set(calculator, StructureSetKeys.SHIPWRECKS).isEmpty() && set(calculator, StructureSetKeys.BURIED_TREASURES).isEmpty(),
				"vanilla's shipwrecks and buried treasure are out");
		RegistryEntry<StructureSet> wrecks = set(calculator, RegistryKey.of(RegistryKeys.STRUCTURE_SET, EternalReturn.id("shipwrecks"))).orElse(null);
		RegistryEntry<StructureSet> treasure = set(calculator, RegistryKey.of(RegistryKeys.STRUCTURE_SET, EternalReturn.id("shipwreck_treasure"))).orElse(null);
		ctx.assertTrue(wrecks != null && treasure != null, "the rare shipwrecks and their treasure are placed");
		RandomSpreadStructurePlacement wreckPlacement = (RandomSpreadStructurePlacement) wrecks.value().placement();
		ctx.assertTrue(wreckPlacement.getSpacing() == 24 && wreckPlacement.getSeparation() == 4, "vanilla's spacing");
		int wreckChunks = 0;
		int candidates = 0;
		for (int x = -1200; x < 1200; x++) {
			for (int z = -60; z < 60; z++) {
				boolean start = wreckPlacement.getStartChunk(calculator.getStructureSeed(), x, z).equals(new net.minecraft.util.math.ChunkPos(x, z));
				if (start) {
					candidates++;
				}
				boolean wreck = wrecks.value().placement().shouldGenerate(calculator, x, z);
				boolean chest = treasure.value().placement().shouldGenerate(calculator, x, z);
				ctx.assertTrue(wreck == chest, "chunk " + x + ", " + z + ": a shipwreck and its treasure together");
				if (wreck) {
					wreckChunks++;
				}
			}
		}
		// Vanilla would start one in every candidate chunk; 1% of them here (about 5 of some 500).
		ctx.assertTrue(candidates > 300 && wreckChunks <= candidates / 20,
				wreckChunks + " shipwreck chunks of " + candidates + " vanilla ones");
		EternalReturn.LOGGER.info("[shipwreck-test] {} shipwreck chunks of {} vanilla ones", wreckChunks, candidates);
		ctx.complete();
	}
}
