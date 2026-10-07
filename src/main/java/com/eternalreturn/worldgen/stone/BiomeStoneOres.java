package com.eternalreturn.worldgen.stone;

import com.eternalreturn.worldgen.caves.CaveWorlds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Temporary ore fallback for biome stone, until each host stone gets its own ores: the existing ore
 * features (and the granite, diorite, andesite, tuff, dirt and gravel blobs) place their plain stone
 * blocks in every host stone. The two hardened blocks are simply in the stone tags
 * (minecraft:stone_ore_replaceables and minecraft:base_stone_overworld). Red sandstone is a vanilla block
 * that vanilla badlands also have, so instead of tagging it (which would change vanilla worlds) ore
 * features see it as stone only while generating in an Eternal Return world (OreFeatureMixin).
 */
public final class BiomeStoneOres {
	private static final ThreadLocal<Boolean> IN_ETERNAL_RETURN_ORE = ThreadLocal.withInitial(() -> false);

	private BiomeStoneOres() {
	}

	public static void enter(FeatureContext<OreFeatureConfig> context) {
		IN_ETERNAL_RETURN_ORE.set(context.getGenerator() instanceof NoiseChunkGenerator generator
				&& CaveWorlds.usesEternalReturnSettings(generator, context.getWorld().getRegistryManager()));
	}

	public static void exit() {
		IN_ETERNAL_RETURN_ORE.set(false);
	}

	/** The block an ore feature should test: stone in place of red sandstone in an Eternal Return world. */
	public static BlockState asStone(BlockState state) {
		return state.isOf(Blocks.RED_SANDSTONE) && IN_ETERNAL_RETURN_ORE.get() ? Blocks.STONE.getDefaultState() : state;
	}
}
