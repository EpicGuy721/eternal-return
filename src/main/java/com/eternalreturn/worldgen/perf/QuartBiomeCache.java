package com.eternalreturn.worldgen.perf;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

/**
 * A small per-thread memory of Moderner Beta's injected biome per 4 x 4 x 4 cell (ModernerBetaBiomeCacheMixin).
 * <p>
 * Moderner Beta works out a block's biome by picking one of the eight cells around it and running its biome
 * injection rules (cave biomes, oceans, beaches, with column heights) for that cell. The surface rules ask that for
 * every block, and in Eternal Return worlds the biome stone rules ask it from the topsoil down to bedrock, so the same
 * cell was worked out again for each of the 64 blocks around it: four fifths of all generation time. The answer only
 * depends on the cell (and the rule step asked for), so it is kept here and handed back the next time.
 * <p>
 * Direct-mapped: each key has one slot, a newer key simply replaces an older one. Every slot holds its full key, so a
 * hit is always the exact same question.
 */
public final class QuartBiomeCache {
	private static final int BITS = 14;
	private static final int SIZE = 1 << BITS;
	private static final int MASK = SIZE - 1;
	/** Stands for a stored null (no injected biome). */
	private static final Object NONE = new Object();

	private final long[] keysA = new long[SIZE];
	private final long[] keysB = new long[SIZE];
	private final Object[] values = new Object[SIZE];
	private final byte[] afterNeeds = new byte[SIZE];

	/** Part one of a key: the cell's x and z. */
	public static long keyA(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	/** Part two of a key: the cell's y, the step, the needs, the flag and the height range asked about. */
	public static long keyB(int y, int step, int needs, boolean flag, int bottomY, int height) {
		return ((long) (y & 0xFFFF) << 48) | ((long) (step & 0xF) << 44) | ((long) (needs & 0xFF) << 36) | (flag ? 1L << 35 : 0)
				| ((long) (bottomY & 0xFFFF) << 16) | (height & 0xFFFF);
	}

	private static int slot(long a, long b) {
		long h = a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL;
		return (int) (h ^ (h >>> 29) ^ (h >>> 47)) & MASK;
	}

	/**
	 * The stored answer, or null when this question isn't stored. A stored "no biome" comes back as {@link #none()}.
	 */
	public Object get(long a, long b) {
		int i = slot(a, b);
		Object value = this.values[i];
		if (value != null && this.keysA[i] == a && this.keysB[i] == b) {
			return value;
		}
		return null;
	}

	/** The needs as Moderner Beta left them after answering the question last time (it may drop one it can't use). */
	public int afterNeeds(long a, long b) {
		return this.afterNeeds[slot(a, b)];
	}

	public void put(long a, long b, RegistryEntry<Biome> biome, int needsAfter) {
		int i = slot(a, b);
		this.keysA[i] = a;
		this.keysB[i] = b;
		this.values[i] = biome == null ? NONE : biome;
		this.afterNeeds[i] = (byte) needsAfter;
	}

	public static Object none() {
		return NONE;
	}
}
