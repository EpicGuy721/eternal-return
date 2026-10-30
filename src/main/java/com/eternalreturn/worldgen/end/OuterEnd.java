package com.eternalreturn.worldgen.end;

import com.eternalreturn.EternalReturn;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeaturePlacementContext;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifierType;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * The outer End of Eternal Return worlds (see "The outer End" in docs/worldgen.md): the terrain's density functions,
 * the megastructure feature and chorus plants for the islands vanilla's biomes don't give them. Only the End of an
 * Eternal Return world uses any of it: its noise settings (eternalreturn:eternal_return_end) are the only ones that
 * name the density functions, and the features check for them and do nothing anywhere else.
 */
public final class OuterEnd {
	public static final Feature<DefaultFeatureConfig> MEGASTRUCTURE = Registry.register(Registries.FEATURE,
			EternalReturn.id("megastructure"), new MegastructureFeature(DefaultFeatureConfig.CODEC));

	public static final PlacementModifierType<OnOuterIsland> ON_OUTER_ISLAND = Registry.register(Registries.PLACEMENT_MODIFIER_TYPE,
			EternalReturn.id("on_outer_end_island"), () -> OnOuterIsland.CODEC);

	private OuterEnd() {
	}

	public static void register() {
		OuterEndDensity.register();
		// Into every End biome, so their place in the decoration order is the same in each (the step they go in is
		// empty in vanilla's End, and the chorus goes after vanilla's chorus): nothing else in the End's decoration moves.
		BiomeModifications.addFeature(BiomeSelectors.foundInTheEnd(), GenerationStep.Feature.LOCAL_MODIFICATIONS,
				RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id("end_megastructure")));
		BiomeModifications.addFeature(BiomeSelectors.foundInTheEnd(), GenerationStep.Feature.VEGETAL_DECORATION,
				RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id("outer_end_chorus_plant")));
		// The settings are read when a world's End terrain is first made: start each server afresh.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			OuterEndTerrain.reset();
			Megastructures.reset();
		});
	}

	/** The outer End terrain of a world, or null unless it is Eternal Return's End. */
	@Nullable
	public static OuterEndTerrain terrain(ServerWorld world) {
		if (world.getRegistryKey() != World.END) {
			return null;
		}
		OuterEndTerrain terrain = OuterEndDensity.terrainOf(world.getChunkManager().getNoiseConfig().getNoiseRouter().finalDensity());
		return terrain != null && terrain.settings().enabled() ? terrain : null;
	}

	/**
	 * Chorus plants on the outer islands that aren't highlands (vanilla's own chorus is there): islands too high for
	 * an End city, and the edges and stacked islands. Places only on an island of Eternal Return's outer End.
	 */
	public static final class OnOuterIsland extends PlacementModifier {
		public static final OnOuterIsland INSTANCE = new OnOuterIsland();
		public static final MapCodec<OnOuterIsland> CODEC = MapCodec.unit(() -> INSTANCE);

		@Override
		public Stream<BlockPos> getPositions(FeaturePlacementContext context, Random random, BlockPos pos) {
			OuterEndTerrain terrain = terrain(context.getWorld().toServerWorld());
			if (terrain == null || context.getWorld().getBiome(pos).matchesKey(BiomeKeys.END_HIGHLANDS)) {
				return Stream.empty();
			}
			int top = terrain.islandTop(pos.getX(), pos.getZ());
			return top != Integer.MIN_VALUE && Math.abs(top + 1 - pos.getY()) <= 3 ? Stream.of(pos) : Stream.empty();
		}

		@Override
		public PlacementModifierType<?> getType() {
			return ON_OUTER_ISLAND;
		}
	}
}
