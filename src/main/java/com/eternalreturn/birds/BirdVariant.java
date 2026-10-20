package com.eternalreturn.birds;

import net.minecraft.util.math.random.Random;

/**
 * A bird's look, packed in one number (as a tropical fish's is): its shape (3 bits), base colour (4 bits), and two
 * patterns (3 bits each, 0 for none), each with its colour (4 bits); the second pattern is drawn over the first.
 */
public final class BirdVariant {
	private BirdVariant() {
	}

	public static int pack(int shape, int base, int pattern1, int colour1, int pattern2, int colour2) {
		return shape | base << 3 | pattern1 << 7 | colour1 << 10 | pattern2 << 14 | colour2 << 17;
	}

	public static int shape(int variant) {
		return variant & 7;
	}

	public static int base(int variant) {
		return variant >> 3 & 15;
	}

	/** Pattern 0 or 1: its number (0 none, else BirdTable.PATTERNS index plus one). */
	public static int pattern(int variant, int layer) {
		return variant >> (layer == 0 ? 7 : 14) & 7;
	}

	public static int colour(int variant, int layer) {
		return variant >> (layer == 0 ? 10 : 17) & 15;
	}

	public static BirdTable.Shape shapeOf(int variant) {
		int shape = shape(variant);
		return BirdTable.SHAPES.get(shape < BirdTable.SHAPES.size() ? shape : 0);
	}

	/** A new bird: one of the presets, BirdTable.PRESET_SHARE of the time; otherwise any shape, colours and up to two patterns. */
	public static int random(Random random) {
		if (random.nextDouble() < BirdTable.PRESET_SHARE) {
			return BirdTable.PRESETS.get(random.nextInt(BirdTable.PRESETS.size())).variant();
		}
		int colours = BirdTable.COLOURS.size();
		int patterns = BirdTable.PATTERNS.size();
		int layers = random.nextInt(3);
		int pattern1 = layers >= 1 ? 1 + random.nextInt(patterns) : 0;
		int pattern2 = layers >= 2 ? 1 + random.nextInt(patterns) : 0;
		if (pattern2 == pattern1) {
			pattern2 = 0;
		}
		return pack(random.nextInt(BirdTable.SHAPES.size()), random.nextInt(colours), pattern1, pattern1 > 0 ? random.nextInt(colours) : 0,
				pattern2, pattern2 > 0 ? random.nextInt(colours) : 0);
	}
}
