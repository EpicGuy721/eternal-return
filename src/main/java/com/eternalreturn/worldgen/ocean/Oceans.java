package com.eternalreturn.worldgen.ocean;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.WorldgenFeatures;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.Feature;

import java.util.function.BooleanSupplier;

/**
 * The oceans' depth zones and the coasts (worldgen expansion 2, phase 5): OceanFeature, one placed feature per part
 * (data/eternalreturn/worldgen/configured_feature/ocean/*.json, how rare each is), each with its own setting. Eternal
 * Return worlds only (the placed features carry eternalreturn:eternal_return_only).
 */
public final class Oceans {
	public static final Feature<OceanFeature.Config> FEATURE = Registry.register(Registries.FEATURE, EternalReturn.id("ocean"), new OceanFeature());

	private Oceans() {
	}

	/**
	 * Every overworld biome gets every part, last of the underground decoration (so no other feature's place in the
	 * decoration order moves, and the plants grow round them after); each part checks the biome itself (sea, beach,
	 * cliff). The floors come first, so the grottoes run into bare walls and the rest lie on the new floors.
	 */
	public static void register() {
		add("ocean/floors", () -> EternalReturnConfig.get().worldgen.oceanFloors);
		add("ocean/grottoes", () -> EternalReturnConfig.get().worldgen.seaCaves);
		add("ocean/sea_caves", () -> EternalReturnConfig.get().worldgen.seaCaves);
		add("ocean/boulders", () -> EternalReturnConfig.get().worldgen.oceanFloors);
		add("ocean/sunken_forest", () -> EternalReturnConfig.get().worldgen.sunkenForests);
		add("ocean/ship_debris", () -> EternalReturnConfig.get().worldgen.shipDebris);
		add("ocean/whale_fall", () -> EternalReturnConfig.get().worldgen.whaleFalls);
		add("ocean/tide_pools", () -> EternalReturnConfig.get().worldgen.tidePools);
		add("ocean/driftwood", () -> EternalReturnConfig.get().worldgen.driftwood);
	}

	private static void add(String id, BooleanSupplier on) {
		BiomeModifications.addFeature(WorldgenFeatures.overworld().and(context -> on.getAsBoolean()),
				GenerationStep.Feature.UNDERGROUND_DECORATION, RegistryKey.of(RegistryKeys.PLACED_FEATURE, EternalReturn.id(id)));
	}
}
