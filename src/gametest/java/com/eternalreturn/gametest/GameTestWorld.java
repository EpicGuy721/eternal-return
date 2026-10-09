package com.eternalreturn.gametest;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Which world the current test server was started with (see the runs in build.gradle). */
public final class GameTestWorld {
	/** Debug tunnel check; runs in every generated-world run. */
	public static final String WORLDGEN_BATCH = "worldgen";
	/** Checks that only make sense in an Eternal Return preset world. */
	public static final String ETERNAL_RETURN_BATCH = "worldgen_er";
	/** The headless map tool; only runs in the worldmap runs. */
	public static final String WORLDMAP_BATCH = "worldmap";
	/** Batches that need a generated world, so they never run in the flat-world runs. */
	private static final Set<String> GENERATED_WORLD_BATCHES = Set.of(WORLDGEN_BATCH, ETERNAL_RETURN_BATCH, WORLDMAP_BATCH,
			ControlFingerprintTests.FINGERPRINT_BATCH, OreCensusTests.ORE_BATCH, BiomeStoneTests.BIOME_STONE_BATCH, StoneMapTests.STONEMAP_BATCH, CoastTests.COAST_BATCH);

	public static final String ETERNAL_RETURN_PRESET = "eternalreturn:eternal_return";

	private GameTestWorld() {
	}

	/** World preset id, or null for the vanilla test server's flat world. */
	@Nullable
	public static String presetProperty() {
		return System.getProperty("eternalreturn.gametest.preset");
	}

	@Nullable
	public static Long seedProperty() {
		String seed = System.getProperty("eternalreturn.gametest.seed");
		return seed == null ? null : Long.parseLong(seed);
	}

	/**
	 * eternalreturn.gametest.batches is a comma-separated list of batches to run. Without it (the flat
	 * world runs) every batch runs except the ones that need a generated world.
	 */
	public static boolean selects(String batchId) {
		String batches = System.getProperty("eternalreturn.gametest.batches");
		if (batches == null) {
			return !GENERATED_WORLD_BATCHES.contains(batchId) && !batchId.startsWith("cave");
		}
		return Set.of(batches.split(",")).contains(batchId);
	}

	public static boolean isModernerBetaLoaded() {
		return FabricLoader.getInstance().isModLoaded("moderner_beta");
	}

	/** Datapacks a new world leaves off unless the player picks them: Moderner Beta's optional packs. */
	public static boolean isOptionalPack(String id) {
		return id.equals("moderner_beta:reduced_height") || id.equals("moderner_beta:deepslate_blobs");
	}
}
