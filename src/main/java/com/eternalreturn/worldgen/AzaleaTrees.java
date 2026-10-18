package com.eternalreturn.worldgen;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.TreeFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Azalea trees (an azalea trunk under azalea and flowering azalea leaves, data eternalreturn:azalea_tree) now and
 * then (worldgen.azaleaTreeChance, 1 in 20) grow in place of an oak as Eternal Return worlds are generated
 * (TreeFeatureAzaleaMixin). Oak saplings still grow oaks, and other worlds are untouched.
 */
public final class AzaleaTrees {
	public static final RegistryKey<ConfiguredFeature<?, ?>> TREE = RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, EternalReturn.id("azalea_tree"));

	private AzaleaTrees() {
	}

	/** Whether this tree, about to generate, becomes an azalea tree instead. */
	public static boolean replaces(FeatureContext<TreeFeatureConfig> context) {
		double chance = EternalReturnConfig.get().worldgen.azaleaTreeChance;
		return chance > 0.0 && context.getWorld() instanceof ChunkRegion
				&& context.getConfig().trunkProvider.get(context.getRandom(), context.getOrigin()).isOf(Blocks.OAK_LOG)
				&& context.getGenerator() instanceof NoiseChunkGenerator generator
				&& CaveWorlds.usesEternalReturnSettings(generator, context.getWorld().getRegistryManager())
				&& context.getRandom().nextDouble() < chance;
	}

	public static boolean grow(FeatureContext<TreeFeatureConfig> context) {
		return context.getWorld().getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).getEntry(TREE)
				.map(tree -> tree.value().generate(context.getWorld(), context.getGenerator(), context.getRandom(), context.getOrigin()))
				.orElse(false);
	}
}
