package com.eternalreturn.worldgen.caves;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.carver.Carver;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.jetbrains.annotations.Nullable;

/**
 * Which worlds the cave engine owns. A world is an Eternal Return world when its generator uses this
 * mod's noise settings (eternalreturn:eternal_return), which only the Eternal Return preset references.
 * Every carver gets told through CarverContext (see CarverContextMixin).
 */
public final class CaveWorlds {
	public static final RegistryKey<ChunkGeneratorSettings> NOISE_SETTINGS = RegistryKey.of(RegistryKeys.CHUNK_GENERATOR_SETTINGS, EternalReturn.id("eternal_return"));

	private CaveWorlds() {
	}

	/** Implemented by CarverContext through CarverContextMixin. */
	public interface Context {
		@Nullable
		NoiseChunkGenerator eternalreturn$generator();

		boolean eternalreturn$isEternalReturn();
	}

	/**
	 * Moderner Beta hands its generator a holder without a registry key, and rebuilds the settings
	 * object when the preset overrides the sea level (ours does) or adds deepslate. It always keeps the
	 * noise router object from the registry entry, though, so the check compares that with the router
	 * of eternalreturn:eternal_return. No other noise settings share it.
	 */
	public static boolean usesEternalReturnSettings(NoiseChunkGenerator generator, DynamicRegistryManager registries) {
		ChunkGeneratorSettings eternalReturn = registries.getOptional(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
				.flatMap(registry -> registry.getOrEmpty(NOISE_SETTINGS))
				.orElse(null);
		return eternalReturn != null && generator.getSettings().value().noiseRouter() == eternalReturn.noiseRouter();
	}

	public static boolean isEternalReturn(CarverContext context) {
		return context instanceof Context extras && extras.eternalreturn$isEternalReturn();
	}

	/**
	 * In Eternal Return worlds the cave engine's carvers are the only ones: vanilla's cave and canyon
	 * carvers, Moderner Beta's Beta-style replacements and any other mod's carvers are skipped, unless
	 * worldgen.caves.oldCaves is on. Everywhere else nothing is skipped.
	 */
	public static boolean skipsCarver(CarverContext context, Carver<?> carver) {
		if (!isEternalReturn(context) || EternalReturnConfig.get().worldgen.caves.oldCaves) {
			return false;
		}
		Identifier id = Registries.CARVER.getId(carver);
		return id == null || !id.getNamespace().equals(EternalReturn.MOD_ID);
	}
}
