package com.eternalreturn.gametest;

import com.eternalreturn.blocks.LeafDistance;
import com.eternalreturn.worldgen.landmarks.ElderTrees;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Elder trees (worldgen expansion 2, phase 4; ElderTrees): every species' giant is its height, has wood and a crown,
 * every leaf non-persistent and within its distance of the wood (so a cut elder decays), a hollow under its trunk,
 * nests and roosts; a forest's elder is one of the forest's own trees. Flat runs (batch elder_trees).
 */
public class ElderTreeTests implements FabricGameTest {
	private static final String BATCH = "elder_trees";

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void everySpeciesGrowsWhole(TestContext ctx) {
		Map<String, ElderTrees.Species> all = ElderTrees.allSpecies();
		ctx.assertTrue(all.size() > 40, "the elder table didn't load: " + all.size());
		for (ElderTrees.Species species : all.values()) {
			for (long seed = 1; seed <= 2; seed++) {
				ElderTrees.Plan plan = ElderTrees.plan(species, seed * 7919);
				int logs = 0;
				int leaves = 0;
				int top = 0;
				for (Map.Entry<BlockPos, BlockState> entry : plan.blocks.entrySet()) {
					BlockState state = entry.getValue();
					if (state.isOf(species.log())) {
						logs++;
					} else if (state.isIn(BlockTags.LEAVES)) {
						leaves++;
						ctx.assertTrue(!state.get(LeavesBlock.PERSISTENT) && state.get(LeavesBlock.DISTANCE) < LeafDistance.MAX,
								species.id() + ": a leaf persistent or out of reach of the wood: " + state);
					}
					top = Math.max(top, entry.getKey().getY());
				}
				ctx.assertTrue(plan.height >= species.minHeight() && plan.height <= species.maxHeight() && top >= plan.height * 0.8,
						species.id() + ": height " + plan.height + ", top " + top);
				// (A palm is mostly trunk: fewer leaves.)
				ctx.assertTrue(logs > 100 && leaves > (species.form() == ElderTrees.Form.PALM ? 100 : 150), species.id() + ": " + logs + " logs, " + leaves + " leaves");
				ctx.assertTrue(plan.blocks.get(plan.hollow) != null && plan.blocks.get(plan.hollow).isAir(), species.id() + ": no hollow");
				ctx.assertTrue(!plan.roosts.isEmpty() && !plan.saplings.isEmpty(), species.id() + ": no roosts or saplings");
			}
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void elderIsALocalTree(TestContext ctx) {
		var biomes = ctx.getWorld().getRegistryManager().get(RegistryKeys.BIOME);
		var forest = biomes.getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier.ofVanilla("forest"))).orElseThrow();
		ElderTrees.Species species = ElderTrees.pick(forest, 1234);
		ctx.assertTrue(species != null && (species.log() == net.minecraft.block.Blocks.OAK_LOG || species.log() == net.minecraft.block.Blocks.BIRCH_LOG),
				"a forest's elder isn't an oak or a birch: " + (species == null ? "none" : species.id()));
		var ocean = biomes.getEntry(RegistryKey.of(RegistryKeys.BIOME, Identifier.ofVanilla("deep_ocean"))).orElseThrow();
		ctx.assertTrue(ElderTrees.pick(ocean, 1234) == null, "an elder in the deep ocean");
		ctx.complete();
	}
}
