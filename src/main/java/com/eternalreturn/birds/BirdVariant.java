package com.eternalreturn.birds;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;

import java.util.List;

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
		return mixed(random, random.nextInt(BirdTable.SHAPES.size()));
	}

	/**
	 * A wild bird of this biome: one of the presets that live there, BirdTable.PRESET_SHARE of the time; otherwise a random
	 * bird of a shape one of them has. Anywhere no preset lives, any bird.
	 */
	public static int forBiome(RegistryEntry<Biome> biome, Random random) {
		List<BirdTable.Preset> here = BirdTable.PRESETS.stream().filter(preset -> preset.habitats().stream().anyMatch(biome::isIn)).toList();
		if (here.isEmpty()) {
			return random(random);
		}
		BirdTable.Preset preset = here.get(random.nextInt(here.size()));
		if (random.nextDouble() < BirdTable.PRESET_SHARE) {
			return preset.variant();
		}
		return mixed(random, shape(preset.variant()));
	}

	/** A random bird of this shape: any base colour, up to two patterns in any colours. */
	private static int mixed(Random random, int shape) {
		int colours = BirdTable.COLOURS.size();
		int patterns = BirdTable.PATTERNS.size();
		int layers = random.nextInt(3);
		int pattern1 = layers >= 1 ? 1 + random.nextInt(patterns) : 0;
		int pattern2 = layers >= 2 ? 1 + random.nextInt(patterns) : 0;
		if (pattern2 == pattern1) {
			pattern2 = 0;
		}
		return pack(shape, random.nextInt(colours), pattern1, pattern1 > 0 ? random.nextInt(colours) : 0,
				pattern2, pattern2 > 0 ? random.nextInt(colours) : 0);
	}
}
