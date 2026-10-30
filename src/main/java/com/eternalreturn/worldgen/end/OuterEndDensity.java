package com.eternalreturn.worldgen.end;

import com.eternalreturn.EternalReturn;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import org.jetbrains.annotations.Nullable;

/**
 * The End's density in Eternal Return worlds (eternalreturn:outer_end, the final density of the noise settings
 * eternalreturn:eternal_return_end): vanilla's own density ("inner") within OuterEndTerrain.INNER blocks of 0, 0 and
 * below y 128 (vanilla's End stops there), so the main island, its pillars, portal and gateways come out block for
 * block as in any other world; past that, the outer islands of OuterEndTerrain. With worldgen.outerEnd.islands off it
 * is vanilla's everywhere.
 * <p>
 * The world seed reaches density functions only through their noises, so the terrain's seed is drawn from the
 * eternalreturn:outer_end noise once the game has seeded it.
 */
public final class OuterEndDensity implements DensityFunction {
	public static final MapCodec<OuterEndDensity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			DensityFunction.FUNCTION_CODEC.fieldOf("inner").forGetter(density -> density.inner),
			DensityFunction.Noise.CODEC.fieldOf("noise").forGetter(density -> density.noise)).apply(instance, OuterEndDensity::new));
	private static final CodecHolder<OuterEndDensity> CODEC_HOLDER = CodecHolder.of(CODEC);
	/** Vanilla's End has no blocks from here up. */
	private static final int VANILLA_TOP = 128;

	private final DensityFunction inner;
	private final DensityFunction.Noise noise;
	@Nullable
	private final OuterEndTerrain terrain;

	public OuterEndDensity(DensityFunction inner, DensityFunction.Noise noise) {
		this(inner, noise, seededTerrain(noise));
	}

	private OuterEndDensity(DensityFunction inner, DensityFunction.Noise noise, @Nullable OuterEndTerrain terrain) {
		this.inner = inner;
		this.noise = noise;
		this.terrain = terrain;
	}

	public static void register() {
		Registry.register(Registries.DENSITY_FUNCTION_TYPE, EternalReturn.id("outer_end"), CODEC);
		Registry.register(Registries.DENSITY_FUNCTION_TYPE, EternalReturn.id("outer_end_biome"), OuterEndBiomeDensity.CODEC);
	}

	/** The terrain of a seeded noise; null before the game seeds it (as decoded from the data). */
	@Nullable
	static OuterEndTerrain seededTerrain(DensityFunction.Noise noise) {
		DoublePerlinNoiseSampler sampler = noise.noise();
		if (sampler == null) {
			return null;
		}
		long seed = 0x9E3779B97F4A7C15L;
		for (int i = 0; i < 4; i++) {
			seed = (seed ^ Double.doubleToLongBits(sampler.sample(1234.567 + i * 311.13, 71.25 * i, -987.65 + i * 53.7))) * 0xBF58476D1CE4E5B9L;
			seed ^= seed >>> 31;
		}
		return OuterEndTerrain.forSeed(seed);
	}

	/**
	 * The outer End terrain behind a noise router's final density (the data wraps every function in a holder), or
	 * null when it isn't this function or isn't seeded.
	 */
	@Nullable
	public static OuterEndTerrain terrainOf(DensityFunction function) {
		while (function instanceof net.minecraft.world.gen.densityfunction.DensityFunctionTypes.RegistryEntryHolder holder) {
			function = holder.function().value();
		}
		return function instanceof OuterEndDensity density ? density.terrain : null;
	}

	/** This world's outer End, or null in a density function the game hasn't seeded. */
	@Nullable
	public OuterEndTerrain terrain() {
		return terrain;
	}

	/** Whether the outer islands replace vanilla's here (worldgen.outerEnd.islands). */
	private boolean outer() {
		return terrain != null && terrain.settings().enabled();
	}

	@Override
	public double sample(NoisePos pos) {
		int x = pos.blockX();
		int z = pos.blockZ();
		if ((long) x * x + (long) z * z < (long) OuterEndTerrain.INNER * OuterEndTerrain.INNER || !outer()) {
			return pos.blockY() >= VANILLA_TOP ? -1.0 : inner.sample(pos);
		}
		return terrain.density(x, pos.blockY(), z);
	}

	@Override
	public void fill(double[] densities, EachApplier applier) {
		applier.fill(densities, this);
	}

	@Override
	public DensityFunction apply(DensityFunctionVisitor visitor) {
		DensityFunction.Noise seeded = visitor.apply(noise);
		OuterEndTerrain known = terrain != null && seeded.noise() == noise.noise() ? terrain : seededTerrain(seeded);
		return visitor.apply(new OuterEndDensity(inner.apply(visitor), seeded, known));
	}

	@Override
	public double minValue() {
		return Math.min(-1.0, inner.minValue());
	}

	@Override
	public double maxValue() {
		return Math.max(1.0, inner.maxValue());
	}

	@Override
	public CodecHolder<? extends DensityFunction> getCodecHolder() {
		return CODEC_HOLDER;
	}
}
