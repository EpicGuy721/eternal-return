package com.eternalreturn.worldgen.caves;

import com.eternalreturn.EternalReturn;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.List;

/**
 * worldgen.caves.debugLogCaveStarts: one line per cave (type, start x, y, z) appended to
 * eternalreturn-cave-starts.csv in the game folder, written when the cave's start chunk is carved.
 * The most recent starts are also kept in memory for tests.
 */
public final class CaveStartLog {
	public static final String FILE_NAME = "eternalreturn-cave-starts.csv";
	private static final int KEPT = 50_000;
	private static final ArrayDeque<Start> RECENT = new ArrayDeque<>();
	private static Writer writer;

	public record Start(String type, int x, int y, int z) {
	}

	private CaveStartLog() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getGameDir().resolve(FILE_NAME);
	}

	public static synchronized void log(String type, int x, int y, int z) {
		if (RECENT.size() >= KEPT) {
			RECENT.pollFirst();
		}
		RECENT.add(new Start(type, x, y, z));
		try {
			if (writer == null) {
				Path path = file();
				boolean fresh = !Files.exists(path);
				writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				if (fresh) {
					writer.write("type,x,y,z\n");
				}
			}
			writer.write(type + "," + x + "," + y + "," + z + "\n");
			writer.flush();
		} catch (IOException e) {
			EternalReturn.LOGGER.warn("Could not write {}", FILE_NAME, e);
		}
	}

	/** Starts logged so far in this session (up to the last 50,000). */
	public static synchronized List<Start> recent() {
		return List.copyOf(RECENT);
	}

	public static synchronized void clearRecent() {
		RECENT.clear();
	}
}
