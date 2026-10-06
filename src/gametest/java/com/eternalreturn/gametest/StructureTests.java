package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureSetKeys;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.UpgradeData;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.structure.Structure;

import java.util.Map;
import java.util.TreeMap;

/** Structure checks for the generated-world runs. */
public class StructureTests implements FabricGameTest {
	/** Half-width of the searched square, in blocks. */
	private static final int SEARCH_HALF = 4096;

	/**
	 * Counts trial chamber starts over a large square: at every chunk where their placement could
	 * put one, the world's own generator computes the structure starts on a fresh chunk (the same
	 * call world generation makes, independent of the test world's "generate structures" switch).
	 * Eternal Return worlds must have none; other worlds must have some, which shows the count works.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = GameTestWorld.WORLDGEN_BATCH, tickLimit = 200_000)
	public void trialChambersOnlyOutsideEternalReturn(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		StructurePlacementCalculator calculator = world.getChunkManager().getStructurePlacementCalculator();
		RegistryEntry<StructureSet> trialChambers = world.getRegistryManager().get(RegistryKeys.STRUCTURE_SET).entryOf(StructureSetKeys.TRIAL_CHAMBERS);
		RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) trialChambers.value().placement();
		Registry<Structure> structures = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
		boolean setActive = calculator.getStructureSets().stream().anyMatch(set -> set.matchesKey(StructureSetKeys.TRIAL_CHAMBERS));

		int spacing = placement.getSpacing();
		int regions = (SEARCH_HALF / 16) / spacing;
		int candidates = 0;
		Map<String, Integer> starts = new TreeMap<>();
		for (int regionX = -regions; regionX <= regions; regionX++) {
			for (int regionZ = -regions; regionZ <= regions; regionZ++) {
				ChunkPos candidate = placement.getStartChunk(calculator.getStructureSeed(), regionX * spacing, regionZ * spacing);
				candidates++;
				ProtoChunk chunk = new ProtoChunk(candidate, UpgradeData.NO_UPGRADE_DATA, world, world.getRegistryManager().get(RegistryKeys.BIOME), null);
				generator.setStructureStarts(world.getRegistryManager(), calculator, world.getStructureAccessor(), chunk, world.getStructureTemplateManager());
				chunk.getStructureStarts().forEach((structure, start) -> {
					if (start.hasChildren()) {
						starts.merge(String.valueOf(structures.getId(structure)), 1, Integer::sum);
					}
				});
			}
		}
		int trialStarts = starts.getOrDefault("minecraft:trial_chambers", 0);
		boolean eternalReturn = GameTestWorld.ETERNAL_RETURN_PRESET.equals(GameTestWorld.presetProperty());
		EternalReturn.LOGGER.info("[structure-test] preset={} trial chamber set active={} | {} candidate chunks over {}x{} blocks | structure starts there: {}",
				GameTestWorld.presetProperty(), setActive, candidates, 2 * SEARCH_HALF, 2 * SEARCH_HALF, starts);
		if (eternalReturn) {
			ctx.assertTrue(!setActive && trialStarts == 0, "Eternal Return world has " + trialStarts + " trial chamber starts (set active: " + setActive + ")");
		} else {
			ctx.assertTrue(setActive && trialStarts > 0, "control world should have trial chambers, found " + trialStarts + " (set active: " + setActive + ")");
		}
		ctx.complete();
	}
}
