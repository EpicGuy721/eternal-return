package com.eternalreturn.mixin.worldgen;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** The generator's decoration feature order, for FeatureOrder (Eternal Return worlds only). */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorFeatureOrderAccessor {
	@Mutable
	@Accessor("indexedFeaturesListSupplier")
	void eternalreturn$setIndexedFeatures(Supplier<List<PlacedFeatureIndexer.IndexedFeatures>> supplier);

	@Accessor("generationSettingsGetter")
	Function<RegistryEntry<Biome>, GenerationSettings> eternalreturn$generationSettings();
}
