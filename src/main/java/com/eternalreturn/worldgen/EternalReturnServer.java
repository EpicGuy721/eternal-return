package com.eternalreturn.worldgen;

import com.eternalreturn.EternalReturn;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.worldgen.caves.CaveWorlds;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureKeys;
import net.minecraft.registry.RegistryKey;

import java.util.List;
import java.util.Set;

/**
 * Whether the running server's world is an Eternal Return world (its overworld uses this mod's noise
 * settings), worked out as the server creates its dimensions, so rules can apply to all three
 * dimensions of such a world. Used for the structures (worldgen.onlyEssentialStructures: only
 * strongholds, mineshafts, nether fossils, end cities, this mod's own landmarks, and shipwrecks at 1% of vanilla's rate
 * with buried treasure beside each (ShipwreckTreasure) are placed, in every dimension) and for blazes
 * outside fortresses (Blazes).
 */
public final class EternalReturnServer {
	private static final Set<RegistryKey<Structure>> KEPT = Set.of(StructureKeys.STRONGHOLD, StructureKeys.MINESHAFT, StructureKeys.MINESHAFT_MESA,
			StructureKeys.NETHER_FOSSIL, StructureKeys.END_CITY);

	private static volatile boolean active;

	private EternalReturnServer() {
	}

	/** From MinecraftServer.createWorlds, before any dimension (or its structure placement) is made. */
	public static void detect(MinecraftServer server) {
		Registry<DimensionOptions> dimensions = server.getCombinedDynamicRegistries().getCombinedRegistryManager().get(RegistryKeys.DIMENSION);
		DimensionOptions overworld = dimensions.get(DimensionOptions.OVERWORLD);
		active = overworld != null && overworld.chunkGenerator() instanceof NoiseChunkGenerator noise
				&& CaveWorlds.usesEternalReturnSettings(noise, server.getRegistryManager());
		server.getRegistryManager().getOptional(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
				.flatMap(registry -> registry.getOrEmpty(CaveWorlds.NOISE_SETTINGS))
				.ifPresent(settings -> CaveWorlds.rememberRouter(settings.noiseRouter()));
		// Every dimension generated with Eternal Return's terrain decorates in the same order on every start (FeatureOrder).
		for (DimensionOptions dimension : dimensions) {
			if (dimension.chunkGenerator() instanceof NoiseChunkGenerator noise && CaveWorlds.usesEternalReturnSettings(noise, server.getRegistryManager())) {
				com.eternalreturn.worldgen.perf.FeatureOrder.fix(noise);
			}
		}
	}

	private static boolean essentialOnly(RegistryEntry<StructureSet> set) {
		return set.getKey().map(ESSENTIAL_ONLY_SETS::contains).orElse(false);
	}

	public static boolean active() {
		return active;
	}

	/**
	 * Sets standing in for vanilla's in Eternal Return worlds with only the essential structures: the rare shipwrecks
	 * and their treasure (ShipwreckTreasure). Placed nowhere else.
	 */
	private static final Set<RegistryKey<StructureSet>> ESSENTIAL_ONLY_SETS = Set.of(
			RegistryKey.of(RegistryKeys.STRUCTURE_SET, EternalReturn.id("shipwrecks")),
			RegistryKey.of(RegistryKeys.STRUCTURE_SET, EternalReturn.id("shipwreck_treasure")));

	/** The structure sets a dimension places: in Eternal Return worlds only those holding a kept structure. */
	public static List<RegistryEntry<StructureSet>> filter(List<RegistryEntry<StructureSet>> sets) {
		if (!active || !EternalReturnConfig.get().worldgen.onlyEssentialStructures) {
			if (sets.stream().noneMatch(EternalReturnServer::essentialOnly)) {
				return sets;
			}
			return sets.stream().filter(set -> !essentialOnly(set)).toList();
		}
		// This mod's own structures (the terrain landmarks) are kept too.
		return sets.stream().filter(set -> essentialOnly(set) || set.value().structures().stream().anyMatch(entry -> KEPT.stream().anyMatch(entry.structure()::matchesKey)
				|| entry.structure().getKey().map(key -> key.getValue().getNamespace().equals(EternalReturn.MOD_ID)).orElse(false))).toList();
	}
}
