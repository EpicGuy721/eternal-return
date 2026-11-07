package com.eternalreturn.worldgen.perf;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mixin.worldgen.ChunkGeneratorFeatureOrderAccessor;
import com.google.common.base.Suppliers;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * The same decoration in an Eternal Return world every time it is generated.
 * <p>
 * Every chunk's decoration features (ores, trees, plants, this mod's features) run in an order the game works out
 * once from the biomes' feature lists, and each feature takes its random numbers from its place in that order.
 * Vanilla lists its biomes in a fixed order, but Moderner Beta hands them over in an order that changes each time the
 * game starts, so the features' order, and with it where every ore, tree and plant lands, came out different on each
 * start: the same seed never made the same world, and a world's decoration could not be checked against a recording.
 * Eternal Return worlds now list the biomes by name, so the order is the same every time. Other worlds are untouched.
 */
public final class FeatureOrder {
	private FeatureOrder() {
	}

	/** From EternalReturnServer.detect, before any chunk is decorated. */
	public static void fix(ChunkGenerator generator) {
		List<RegistryEntry<Biome>> biomes = new ArrayList<>(generator.getBiomeSource().getBiomes());
		biomes.sort(Comparator.comparing(entry -> entry.getKey().map(key -> key.getValue().toString()).orElse("")));
		ChunkGeneratorFeatureOrderAccessor access = (ChunkGeneratorFeatureOrderAccessor) generator;
		Function<RegistryEntry<Biome>, GenerationSettings> settings = access.eternalreturn$generationSettings();
		List<RegistryEntry<Biome>> fixed = List.copyOf(biomes);
		access.eternalreturn$setIndexedFeatures(Suppliers.memoize(() -> PlacedFeatureIndexer.collectIndexedFeatures(fixed, biome -> settings.apply(biome).getFeatures(), true)));
		EternalReturn.LOGGER.debug("Eternal Return: decoration order fixed over {} biomes", fixed.size());
	}
}
