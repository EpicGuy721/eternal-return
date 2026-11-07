package com.eternalreturn.worldgen.perf;

/**
 * A small per-thread memory of a column height Moderner Beta's biome injection samples per 4 x 4 column (its top
 * height, and the lowest floor height of the column and its eight neighbours): the same for every cell up the column,
 * so it is worked out once instead of once per cell (ModernerBetaBiomeCacheMixin). Direct-mapped like QuartBiomeCache;
 * every slot holds its full key.
 */
public final class ColumnHeightCache {
	private static final int BITS = 12;
	private static final int SIZE = 1 << BITS;
	private static final int MASK = SIZE - 1;

	private final long[] keysA = new long[SIZE];
	private final int[] keysB = new int[SIZE];
	private final int[] values = new int[SIZE];
	private final boolean[] filled = new boolean[SIZE];

	public static int keyB(int bottomY, int height) {
		return (bottomY << 16) ^ (height & 0xFFFF);
	}

	private static int slot(long a, int b) {
		long h = a * 0x9E3779B97F4A7C15L + b;
		return (int) (h ^ (h >>> 31)) & MASK;
	}

	/** Whether this column is stored; its height is then {@link #value(long, int)}. */
	public boolean has(long a, int b) {
		int i = slot(a, b);
		return this.filled[i] && this.keysA[i] == a && this.keysB[i] == b;
	}

	public int value(long a, int b) {
		return this.values[slot(a, b)];
	}

	public void put(long a, int b, int height) {
		int i = slot(a, b);
		this.keysA[i] = a;
		this.keysB[i] = b;
		this.values[i] = height;
		this.filled[i] = true;
	}
}
