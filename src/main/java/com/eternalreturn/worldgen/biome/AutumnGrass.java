package com.eternalreturn.worldgen.biome;

import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;

/**
 * The autumnal forest's grass: mostly golden, with patches of orange and, here and there, red, varying smoothly
 * across the ground (BiomeGrassColorMixin). The biome marks itself with the grass colour GOLD
 * (tools/woods/build_woods.py writes it), so no lookup of the biome's id is needed where colours are worked out.
 */
public final class AutumnGrass {
	public static final int GOLD = 0xD4A23C;
	private static final int ORANGE = 0xD2742A;
	private static final int RED = 0xB0402C;

	private AutumnGrass() {
	}

	/** The colour at x, z: gold where the noise is low (most ground), shading to orange, then red, where it is high. */
	public static int colorAt(double x, double z) {
		double n = Biome.FOLIAGE_NOISE.sample(x * 0.03, z * 0.03, false);
		if (n < 0.1) {
			return GOLD;
		}
		if (n < 0.45) {
			return mix(GOLD, ORANGE, (n - 0.1) / 0.35);
		}
		return mix(ORANGE, RED, Math.min(1.0, (n - 0.45) / 0.35));
	}

	private static int mix(int a, int b, double t) {
		int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}
}
