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
		return forBiome(biome, random, null);
	}

	/**
	 * A wild bird of the place: as forBiome, the place's patch (Personality, in an Eternal Return overworld with personality
	 * rolls on) making about half of the presets living there common, three times as likely as the rest.
	 */
	public static int forPlace(net.minecraft.world.ServerWorldAccess world, net.minecraft.util.math.BlockPos pos, Random random) {
		RegistryEntry<Biome> biome = world.getBiome(pos);
		com.eternalreturn.worldgen.personality.Personality.Roll roll = null;
		net.minecraft.server.world.ServerWorld server = world.toServerWorld();
		if (com.eternalreturn.config.EternalReturnConfig.get().worldgen.personalityRolls && com.eternalreturn.worldgen.EternalReturnServer.active()
				&& server.getRegistryKey() == net.minecraft.world.World.OVERWORLD && biome.getKey().isPresent()) {
			roll = com.eternalreturn.worldgen.personality.Personality.at(com.eternalreturn.worldgen.personality.Personality.salt(server.getSeed()),
					biome.getKey().get().getValue(), pos.getX(), pos.getZ());
		}
		return forBiome(biome, random, roll);
	}

	private static int forBiome(RegistryEntry<Biome> biome, Random random, @org.jetbrains.annotations.Nullable com.eternalreturn.worldgen.personality.Personality.Roll roll) {
		List<BirdTable.Preset> here = BirdTable.PRESETS.stream().filter(preset -> preset.habitats().stream().anyMatch(biome::isIn)).toList();
		if (here.isEmpty()) {
			return random(random);
		}
		BirdTable.Preset preset = roll == null ? here.get(random.nextInt(here.size())) : common(here, roll, random);
		if (random.nextDouble() < BirdTable.PRESET_SHARE) {
			return preset.variant();
		}
		return mixed(random, shape(preset.variant()));
	}

	/** One of the place's presets, those common in the patch three times as likely (all alike if none or all are). */
	private static BirdTable.Preset common(List<BirdTable.Preset> here, com.eternalreturn.worldgen.personality.Personality.Roll roll, Random random) {
		int[] weights = new int[here.size()];
		int total = 0;
		for (int i = 0; i < here.size(); i++) {
			weights[i] = roll.commonBird(here.get(i).id()) ? 3 : 1;
			total += weights[i];
		}
		int at = random.nextInt(total);
		for (int i = 0; i < here.size(); i++) {
			at -= weights[i];
			if (at < 0) {
				return here.get(i);
			}
		}
		return here.get(here.size() - 1);
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
