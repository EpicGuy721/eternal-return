package com.eternalreturn.survival;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;

/**
 * Crops have ideal biomes (survival.cropGrowthOutsideIdealBiome): a crop grows at full speed only in the
 * biomes of its tag eternalreturn:ideal_biomes/<crop block> (wheat in plains and savannas, carrots in
 * forests, potatoes in taigas and hills, beetroots in the cold, melons and cocoa in jungles, pumpkins
 * in forests and taigas, sugar cane by swamps, rivers and beaches, cactus in deserts and badlands,
 * sweet berries in taigas, nether wart in the Nether). Elsewhere each of its random growth steps goes
 * ahead only a quarter of the time. Crops without a tag (torchflowers, say) grow as usual; bone meal
 * works as usual everywhere. A crop in a greenhouse (glass the first thing above it, rather than the open air or
 * another roof) grows as though it were in an ideal biome.
 */
public final class CropBiomes {
	private CropBiomes() {
	}

	public static TagKey<Biome> idealBiomes(Block crop) {
		return TagKey.of(RegistryKeys.BIOME, EternalReturn.id("ideal_biomes/" + Registries.BLOCK.getId(crop).getPath()));
	}

	/** Whether this crop is in an ideal biome here (or has no ideal biomes at all, or is in a greenhouse). */
	public static boolean isIdeal(ServerWorld world, BlockPos pos, Block crop) {
		if (inGreenhouse(world, pos, crop)) {
			return true;
		}
		TagKey<Biome> tag = idealBiomes(crop);
		boolean hasTag = world.getRegistryManager().get(RegistryKeys.BIOME).getEntryList(tag).map(list -> list.size() > 0).orElse(false);
		return !hasTag || world.getBiome(pos).isIn(tag);
	}

	/** The first block above the crop (past more of it, as a tall cane or cactus) is glass or a glass pane. */
	public static boolean inGreenhouse(ServerWorld world, BlockPos pos, Block crop) {
		BlockPos.Mutable above = pos.mutableCopy();
		int top = Math.min(world.getTopY(), pos.getY() + 64);
		while (above.move(Direction.UP).getY() < top) {
			BlockState state = world.getBlockState(above);
			if (state.isAir() || state.isOf(crop)) {
				continue;
			}
			return state.isIn(ConventionalBlockTags.GLASS_BLOCKS) || state.isIn(ConventionalBlockTags.GLASS_PANES);
		}
		return false;
	}

	/** From a crop's random tick: false skips this growth step. */
	public static boolean mayGrow(ServerWorld world, BlockPos pos, Block crop, Random random) {
		double chance = EternalReturnConfig.get().survival.cropGrowthOutsideIdealBiome;
		return chance >= 1.0 || isIdeal(world, pos, crop) || random.nextDouble() < chance;
	}
}
