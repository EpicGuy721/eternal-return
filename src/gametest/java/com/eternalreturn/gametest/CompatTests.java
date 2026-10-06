package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.WorldPresetTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

/**
 * Runs in both flat-world runs: with Moderner Beta (runGametest) and without it
 * (runGametestNoModernerBeta). The Eternal Return world preset must exist exactly when Moderner Beta
 * does, and the server must start either way.
 */
public class CompatTests implements FabricGameTest {
	private static final Identifier PRESET = EternalReturn.id("eternal_return");

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "compat")
	public void eternalReturnPresetOnlyWithModernerBeta(TestContext ctx) {
		DynamicRegistryManager registries = ctx.getWorld().getRegistryManager();
		boolean modernerBeta = GameTestWorld.isModernerBetaLoaded();
		boolean worldPreset = registries.get(RegistryKeys.WORLD_PRESET).containsId(PRESET);
		boolean noiseSettings = registries.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS).containsId(PRESET);
		boolean listed = registries.get(RegistryKeys.WORLD_PRESET).getEntry(PRESET)
				.map(entry -> entry.isIn(WorldPresetTags.NORMAL)).orElse(false);
		EternalReturn.LOGGER.info("[compat] moderner_beta loaded={} | world preset={} | noise settings={} | in world type list={}",
				modernerBeta, worldPreset, noiseSettings, listed);
		ctx.assertTrue(worldPreset == modernerBeta, "Eternal Return world preset present=" + worldPreset + " but Moderner Beta loaded=" + modernerBeta);
		ctx.assertTrue(noiseSettings == modernerBeta, "Eternal Return noise settings present=" + noiseSettings + " but Moderner Beta loaded=" + modernerBeta);
		ctx.assertTrue(listed == modernerBeta, "world type list entry present=" + listed + " but Moderner Beta loaded=" + modernerBeta);
		ctx.complete();
	}
}
