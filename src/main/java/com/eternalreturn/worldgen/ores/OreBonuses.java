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

	@SafeVarargs
	private static void bonus(String ore, RegistryKey<Biome>... biomes) {
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes), GenerationStep.Feature.UNDERGROUND_ORES, feature("ore_bonus_" + ore));
	}

	private static RegistryKey<Biome> biome(String id) {
		return RegistryKey.of(RegistryKeys.BIOME, net.minecraft.util.Identifier.of(id));
	}

	public static void register() {
		for (String ore : List.of("ore_ruby", "ore_ruby_buried", "ore_sapphire")) {
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, feature(ore));
		}
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Feature.UNDERGROUND_DECORATION, feature("ore_ruby_nether"));
		bonus("iron", biome("minecraft:old_growth_pine_taiga"), biome("minecraft:old_growth_spruce_taiga"), biome("minecraft:old_growth_birch_forest"));
		bonus("coal", biome("minecraft:jungle"), biome("minecraft:sparse_jungle"), biome("minecraft:bamboo_jungle"), biome("minecraft:swamp"),
				biome("minecraft:mangrove_swamp"), biome("moderner_beta:early_release_swampland"), biome("moderner_beta:late_beta_swampland"),
				biome("moderner_beta:beta_swampland"), biome("moderner_beta:beta_rainforest"));
		bonus("gold", biome("minecraft:badlands"), biome("minecraft:eroded_badlands"), biome("minecraft:wooded_badlands"));
		bonus("lapis", biome("minecraft:desert"), biome("moderner_beta:beta_desert"));
		bonus("emerald", biome("moderner_beta:early_release_extreme_hills"), biome("minecraft:windswept_hills"), biome("minecraft:windswept_gravelly_hills"));
		bonus("redstone", biome("minecraft:mushroom_fields"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OCEAN), GenerationStep.Feature.UNDERGROUND_ORES, feature("ore_bonus_copper"));
		bonus("diamond", biome("moderner_beta:early_release_ice_plains"), biome("moderner_beta:late_beta_ice_plains"), biome("minecraft:snowy_plains"),
				biome("minecraft:ice_spikes"));
		bonus("ruby", biome("minecraft:cherry_grove"));
		bonus("sapphire", biome("eternalreturn:autumnal_forest"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biome("minecraft:basalt_deltas")), GenerationStep.Feature.UNDERGROUND_DECORATION,
				feature("ore_bonus_ancient_debris"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(biome("minecraft:nether_wastes")), GenerationStep.Feature.UNDERGROUND_DECORATION,
				feature("ore_bonus_quartz"));
	}
}
