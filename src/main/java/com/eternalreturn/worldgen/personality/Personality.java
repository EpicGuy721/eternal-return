package com.eternalreturn.worldgen.personality;

import com.eternalreturn.worldgen.variants.BiomeVariants;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Biome personality rolls (worldgen expansion 2, phase 7, docs/worldgen.md): every 256-block cell of a biome rolls small
 * shifts inside the biome's identity, so no two patches of it look the same yet each reads at a glance as its biome. A
 * roll only reweights or picks from what the biome already has. The cell's roll comes from the world's salt (from its
 * seed), the cell and the biome's family: a peak, edge or river variant rolls as its base (so a patch and its peaks
 * agree); a sub-biome rolls as itself. One cell in RARE is the biome at its most itself.
 * <p>The traits (each read where it acts): density (PersonalityFeatures: trees, grass and boulders, within a quarter of
 * normal), canopy mix (BiomeTrees: the biome's own tree table reweighted, its signature tree kept), flower palette
 * (PersonalityFeatures: two to four of the biome's own flowers), tint (client: grass and leaves a little greener,
 * yellower or bluer, lighter or darker) and bird mix (BirdVariant: which of the place's birds are common). Eternal
 * Return worlds only; switched by worldgen.personalityRolls.</p>
 */
public final class Personality {
	/** The cells: 256 blocks a side, as the sub-biomes' areas. */
	public static final int CELL_SHIFT = 8;
	/** One cell in this many is the biome at its most itself (the owner's call: 1 in 40). */
	public static final int RARE = 40;
	/** Density: within this fraction of normal either way. */
	public static final double DENSITY_RANGE = 0.25;
	/** Tint: the most a patch shifts hue (as a fraction of the colour wheel) and brightness and saturation (as a fraction). */
	public static final float TINT_HUE = 0.018F;
	public static final float TINT_VALUE = 0.07F;

	/** The traits' own streams of the roll. */
	private static final int DENSITY = 1;
	private static final int TINT_H = 2;
	private static final int TINT_S = 3;
	private static final int TINT_V = 4;
	private static final int RARITY = 5;
	static final int CANOPY = 100;
	static final int FLOWERS = 200;
	static final int BIRDS = 300;

	private Personality() {
	}

	/** A world's salt from its seed: what the rolls are made from (sent to players' games for the tint, never the seed). */
	public static long salt(long seed) {
		long salt = mix(seed ^ 0x9E2A_70C1_5EED_0007L);
		return salt == 0 ? 1 : salt;
	}

	/** The family a biome rolls as: a peak, edge or river variant's base, else the biome itself. */
	public static Identifier family(Identifier biome) {
		BiomeVariants.Variant variant = BiomeVariants.get(biome);
		if (variant != null && ("peak".equals(variant.kind()) || "edge".equals(variant.kind()) || "river".equals(variant.kind()))) {
			return variant.base();
		}
		return biome;
	}

	/** The roll of the cell holding this block for this biome. */
	public static Roll at(long salt, Identifier biome, int blockX, int blockZ) {
		return cell(salt, family(biome), blockX >> CELL_SHIFT, blockZ >> CELL_SHIFT);
	}

	static Roll cell(long salt, Identifier family, int cellX, int cellZ) {
		long bits = mix(salt ^ mix(family.hashCode() * 0x9E37_79B9_7F4A_7C15L ^ cellX * 0xC2B2_AE3D_27D4_EB4FL ^ cellZ * 0x1656_67B1_9E37_79F9L));
		return new Roll(family, bits);
	}

	/** One cell's roll for one biome family. */
	public record Roll(Identifier family, long bits) {
		/** A number from 0 to 1 for one trait (stream), the same every time. */
		public double unit(int stream) {
			return (mix(this.bits + stream * 0x9E37_79B9_7F4A_7C15L) >>> 11) * 0x1.0p-53;
		}

		/** A number from 0 to 1 for one trait and one thing (a tree, a flower, a bird). */
		public double unit(int stream, String thing) {
			return (mix(this.bits + stream * 0x9E37_79B9_7F4A_7C15L ^ mix(thing.hashCode())) >>> 11) * 0x1.0p-53;
		}

		/** Whether this patch is its biome at its most itself (one in RARE). */
		public boolean rare() {
			return this.unit(RARITY) < 1.0 / RARE;
		}

		/** How dense its trees, grass and boulders are against normal: 0.75 to 1.25, a rare patch the most. */
		public double density() {
			return this.rare() ? 1 + DENSITY_RANGE : 1 - DENSITY_RANGE + 2 * DENSITY_RANGE * this.unit(DENSITY);
		}

		/**
		 * Weights for a list of the biome's choices (trees by name): each but the signature (the most weighted, the first
		 * of equals) scaled by 0.4 to 1.6, a rare patch all at half; a signature that was at least half of the list stays
		 * at least half of it.
		 */
		public double[] canopy(List<String> names, int[] weights) {
			int signature = 0;
			for (int i = 1; i < weights.length; i++) {
				if (weights[i] > weights[signature]) {
					signature = i;
				}
			}
			double total = 0;
			for (int weight : weights) {
				total += weight;
			}
			double[] out = new double[weights.length];
			double others = 0;
			for (int i = 0; i < weights.length; i++) {
				if (i == signature) {
					out[i] = weights[i];
				} else {
					out[i] = weights[i] * (this.rare() ? 0.5 : 0.4 + 1.2 * this.unit(CANOPY, names.get(i)));
					others += out[i];
				}
			}
			if (total > 0 && weights[signature] / total >= 0.5 && others > out[signature]) {
				double scale = out[signature] / others;
				for (int i = 0; i < out.length; i++) {
					if (i != signature) {
						out[i] *= scale;
					}
				}
			}
			return out;
		}

		/**
		 * The flowers this patch keeps of the biome's own (ids): with three or more, two to four of them (fewer than the
		 * biome has), picked by the roll; with fewer, all; a rare patch all.
		 */
		public List<String> palette(List<String> flowers) {
			if (flowers.size() < 3 || this.rare()) {
				return flowers;
			}
			int most = Math.min(4, flowers.size() - 1);
			int keep = 2 + (int) (this.unit(FLOWERS) * (most - 1));
			List<String> sorted = new ArrayList<>(flowers);
			sorted.sort(Comparator.comparingDouble(flower -> this.unit(FLOWERS, flower)));
			return List.copyOf(sorted.subList(0, Math.min(keep, sorted.size())));
		}

		/** Whether a bird (preset id) is common in this patch: about half of the place's, at least one. */
		public boolean commonBird(String bird) {
			return this.unit(BIRDS, bird) < 0.5;
		}

		/** The tint's three shifts (hue, saturation, brightness), each -1 to 1. */
		public float[] tint() {
			return new float[]{(float) (this.unit(TINT_H) * 2 - 1), (float) (this.unit(TINT_S) * 2 - 1), (float) (this.unit(TINT_V) * 2 - 1)};
		}
	}

	/**
	 * A grass or foliage colour tinted for its patch: the four nearest cells' shifts blended by distance (so a patch
	 * fades into the next, with no seam), applied to hue, saturation and brightness.
	 */
	public static int tint(long salt, Identifier biome, int rgb, double x, double z) {
		Identifier family = family(biome);
		double fx = x / (1 << CELL_SHIFT) - 0.5;
		double fz = z / (1 << CELL_SHIFT) - 0.5;
		int cx = MathHelper.floor(fx);
		int cz = MathHelper.floor(fz);
		double tx = smooth(fx - cx);
		double tz = smooth(fz - cz);
		float[] a = cell(salt, family, cx, cz).tint();
		float[] b = cell(salt, family, cx + 1, cz).tint();
		float[] c = cell(salt, family, cx, cz + 1).tint();
		float[] d = cell(salt, family, cx + 1, cz + 1).tint();
		float[] shift = new float[3];
		for (int i = 0; i < 3; i++) {
			shift[i] = (float) MathHelper.lerp(tz, MathHelper.lerp(tx, a[i], b[i]), MathHelper.lerp(tx, c[i], d[i]));
		}
		float[] hsb = java.awt.Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
		float hue = hsb[0] + shift[0] * TINT_HUE;
		float saturation = MathHelper.clamp(hsb[1] * (1 + shift[1] * TINT_VALUE), 0, 1);
		float brightness = MathHelper.clamp(hsb[2] * (1 + shift[2] * TINT_VALUE), 0, 1);
		return java.awt.Color.HSBtoRGB(hue - (float) Math.floor(hue), saturation, brightness) & 0xFFFFFF;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D0_49BB_1331_11EBL;
		return z ^ (z >>> 31);
	}
}
