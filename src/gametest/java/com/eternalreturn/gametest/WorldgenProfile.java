package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Time spent in each decoration feature, structure and carver while chunks generate, for the worldgen
 * benchmark (WorldgenBenchmarkTests). On only with -Deternalreturn.gametest.featureTiming=true, because
 * timing every feature call costs a little itself. Only top-level calls count (a feature that places
 * other features is counted once, with them inside).
 */
public final class WorldgenProfile {
	public static final boolean ON = Boolean.getBoolean("eternalreturn.gametest.featureTiming");
	private static final Map<String, LongAdder[]> TIMES = new ConcurrentHashMap<>();
	private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

	private WorldgenProfile() {
	}

	/** Enter a timed call; true when it is the outermost one on this thread. */
	public static boolean enter() {
		return DEPTH.get()[0]++ == 0;
	}

	public static void exit(boolean outer, String key, long nanos) {
		DEPTH.get()[0]--;
		if (outer) {
			LongAdder[] slot = TIMES.computeIfAbsent(key, k -> new LongAdder[]{new LongAdder(), new LongAdder()});
			slot[0].add(nanos);
			slot[1].increment();
		}
	}

	public static void reset() {
		TIMES.clear();
	}

	/** Logs the slowest entries, total milliseconds (summed over all threads) and calls. */
	public static void log(String label, int limit) {
		if (!ON) {
			return;
		}
		StringBuilder out = new StringBuilder();
		TIMES.entrySet().stream()
				.sorted((a, b) -> Long.compare(b.getValue()[0].sum(), a.getValue()[0].sum()))
				.limit(limit)
				.forEach(e -> out.append(String.format("%n  %9.1f ms %8d calls  %s", e.getValue()[0].sum() / 1e6, e.getValue()[1].sum(), e.getKey())));
		long total = TIMES.values().stream().mapToLong(v -> v[0].sum()).sum();
		EternalReturn.LOGGER.info("[bench] {} timed total {} ms (summed over threads):{}", label, total / 1_000_000, out);
	}
}
