package com.eternalreturn.worldgen.caves;

import java.util.concurrent.atomic.LongAdder;

/** Time spent in the cave engine's carver, for the timing test and the map tool. */
public final class CaveTiming {
	private static final LongAdder NANOS = new LongAdder();
	private static final LongAdder CHUNKS = new LongAdder();

	private CaveTiming() {
	}

	static void add(long nanos, boolean ownChunk) {
		NANOS.add(nanos);
		if (ownChunk) {
			CHUNKS.increment();
		}
	}

	/** Total carver time so far, in nanoseconds. */
	public static long nanos() {
		return NANOS.sum();
	}

	/** Chunks carved so far (each chunk is carved once, from all the sources around it). */
	public static long chunks() {
		return CHUNKS.sum();
	}
}
