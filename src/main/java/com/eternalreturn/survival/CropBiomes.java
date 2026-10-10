package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;

/**
 * Crops have ideal biomes (survival.cropGrowthOutsideIdealBiome): a crop grows at full speed only in the
 * biomes of its tag eternalreturn:ideal_biomes/<crop block> (wheat in plains and savannas, carrots in
 * forests, potatoes in taigas and hills, beetroots in the cold, melons and cocoa in jungles, pumpkins
 * in forests and taigas, sugar cane by swamps, rivers and beaches, cactus in deserts and badlands,
 * sweet berries in taigas, nether wart in the Nether). Elsewhere each of its random growth steps goes
 * ahead only a quarter of the time. Crops without a tag (torchflowers, say) grow as usual; bone meal
 * works as usual everywhere.
 */
public final class CropBiomes {
	private CropBiomes() {
	}

	public static TagKey<Biome> idealBiomes(Block crop) {
		return TagKey.of(RegistryKeys.BIOME, EternalReturn.id("ideal_biomes/" + Registries.BLOCK.getId(crop).getPath()));
	}

	/** Whether this crop is in an ideal biome here (or has no ideal biomes at all). */
	public static boolean isIdeal(ServerWorld world, BlockPos pos, Block crop) {
		TagKey<Biome> tag = idealBiomes(crop);
		boolean hasTag = world.getRegistryManager().get(RegistryKeys.BIOME).getEntryList(tag).map(list -> list.size() > 0).orElse(false);
		return !hasTag || world.getBiome(pos).isIn(tag);
	}

	/** From a crop's random tick: false skips this growth step. */
	public static boolean mayGrow(ServerWorld world, BlockPos pos, Block crop, Random random) {
		double chance = EternalReturnConfig.get().survival.cropGrowthOutsideIdealBiome;
		return chance >= 1.0 || isIdeal(world, pos, crop) || random.nextDouble() < chance;
	}
}
