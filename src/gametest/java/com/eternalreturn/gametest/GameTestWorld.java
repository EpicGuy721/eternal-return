package com.eternalreturn.gametest;

import org.jetbrains.annotations.Nullable;

/** Which world the current test server was started with (see the runs in build.gradle). */
public final class GameTestWorld {
	/** Tests in batches starting with this run only in the worldgen runs, and only they run there. */
	public static final String WORLDGEN_BATCH = "worldgen";

	private GameTestWorld() {
	}

	/** World preset id, or null for the vanilla test server's flat world. */
	@Nullable
	public static String presetProperty() {
		return System.getProperty("eternalreturn.gametest.preset");
	}

	/** Datapacks a new world leaves off unless the player picks them: Moderner Beta's optional packs. */
	public static boolean isOptionalPack(String id) {
		return id.equals("moderner_beta:reduced_height") || id.equals("moderner_beta:deepslate_blobs");
	}

	public static boolean isWorldgenRun() {
		return WORLDGEN_BATCH.equals(System.getProperty("eternalreturn.gametest.batches"));
	}
}
