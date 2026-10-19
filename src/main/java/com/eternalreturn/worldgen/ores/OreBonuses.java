package com.eternalreturn.worldgen.ores;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.EternalReturnServer;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;

import java.util.List;
import java.util.stream.Stream;

/**
 * Eternal Return worlds' extra ores (resources from tools/gems/build_gems.py): ruby and sapphire ore in every
 * overworld biome, ruby ore in the Nether, and each biome's bonus ore (the list below). Every one of these placed
 * features carries the eternalreturn:eternal_return_only placement, which places nothing in other worlds, so they
 * generate exactly as before.
 */
public final class OreBonuses {
	/** Places only in Eternal Return worlds: the overworld by its own settings, the Nether and the End by the server's. */
	public static final class EternalReturnOnly extends PlacementModifier {
		public static final EternalReturnOnly INSTANCE = new EternalReturnOnly();
		public static final MapCodec<EternalReturnOnly> CODEC = MapCodec.unit(() -> INSTANCE);

		@Override
		public Stream<BlockPos> getPositions(FeaturePlacementContext context, Random random, BlockPos pos) {
			return inEternalReturn(context) ? Stream.of(pos) : Stream.empty();
		}

		@Override
		public PlacementModifierType<?> getType() {
			return TYPE;
		}
	}

	/** Places only where the biome is in the given tag (eternalreturn:biome_tag; the per-biome bonus ores). */
	public static final class BiomeTagFilter extends PlacementModifier {
		public static final MapCodec<BiomeTagFilter> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
				net.minecraft.registry.tag.TagKey.unprefixedCodec(RegistryKeys.BIOME).fieldOf("tag").forGetter(filter -> filter.tag)).apply(instance, BiomeTagFilter::new));
		private final net.minecraft.registry.tag.TagKey<Biome> tag;

		public BiomeTagFilter(net.minecraft.registry.tag.TagKey<Biome> tag) {
			this.tag = tag;
		}

		@Override
		public Stream<BlockPos> getPositions(FeaturePlacementContext context, Random random, BlockPos pos) {
			return context.getWorld().getBiome(pos).isIn(this.tag) ? Stream.of(pos) : Stream.empty();
		}

		@Override
		public PlacementModifierType<?> getType() {
			return BIOME_TAG;
		}
	}

	public static final PlacementModifierType<BiomeTagFilter> BIOME_TAG = Registry.register(Registries.PLACEMENT_MODIFIER_TYPE,
			EternalReturn.id("biome_tag"), () -> BiomeTagFilter.CODEC);

	public static final PlacementModifierType<EternalReturnOnly> TYPE = Registry.register(Registries.PLACEMENT_MODIFIER_TYPE,
			EternalReturn.id("eternal_return_only"), () -> EternalReturnOnly.CODEC);

	private OreBonuses() {
	}

	static boolean inEternalReturn(FeaturePlacementContext context) {
		if (context.getWorld().toServerWorld().getRegistryKey() != World.OVERWORLD) {
			return EternalReturnServer.active();
		}
		return context.getChunkGenerator() instanceof NoiseChunkGenerator generator
				&& CaveWorlds.usesEternalReturnSettings(generator, context.getWorld().getRegistryManager());
	}

	private static RegistryKey<PlacedFeature> feature(String id) {
		return RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id(id));
	}

	public static void register() {
		for (String ore : List.of("ore_ruby", "ore_ruby_buried", "ore_sapphire")) {
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, feature(ore));
		}
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Feature.UNDERGROUND_DECORATION, feature("ore_ruby_nether"));
		// Every bonus goes into every biome of its dimension, filtered to its own biomes by tag (BiomeTagFilter): a feature
		// in only some biomes' lists can land in a different place in the decoration order from one start to the next,
		// and so place differently.
		for (String ore : List.of("iron", "coal", "gold", "lapis", "emerald", "redstone", "copper", "diamond", "ruby", "sapphire")) {
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, feature("ore_bonus_" + ore));
		}
		for (String ore : List.of("ancient_debris", "quartz")) {
			BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Feature.UNDERGROUND_DECORATION, feature("ore_bonus_" + ore));
		}
	}
}
