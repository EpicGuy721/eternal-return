package com.eternalreturn.worldgen.end;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import org.jetbrains.annotations.Nullable;

/**
 * The value the End's biome source reads (the noise router's erosion) in Eternal Return worlds
 * (eternalreturn:outer_end_biome): OuterEndTerrain.biomeValue past the void gap, so highlands, midlands and barrens
 * follow the new islands; vanilla's ("inner") with worldgen.outerEnd.islands off. Within 1,024 blocks of 0, 0 the biome
 * source doesn't read it (it is always the_end there).
 */
public final class OuterEndBiomeDensity implements DensityFunction {
	public static final MapCodec<OuterEndBiomeDensity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			DensityFunction.FUNCTION_CODEC.fieldOf("inner").forGetter(density -> density.inner),
			DensityFunction.Noise.CODEC.fieldOf("noise").forGetter(density -> density.noise)).apply(instance, OuterEndBiomeDensity::new));
	private static final CodecHolder<OuterEndBiomeDensity> CODEC_HOLDER = CodecHolder.of(CODEC);

	private final DensityFunction inner;
	private final DensityFunction.Noise noise;
	@Nullable
	private final OuterEndTerrain terrain;

	public OuterEndBiomeDensity(DensityFunction inner, DensityFunction.Noise noise) {
		this(inner, noise, OuterEndDensity.seededTerrain(noise));
	}

	private OuterEndBiomeDensity(DensityFunction inner, DensityFunction.Noise noise, @Nullable OuterEndTerrain terrain) {
		this.inner = inner;
		this.noise = noise;
		this.terrain = terrain;
	}

	@Override
	public double sample(NoisePos pos) {
		if (terrain == null || !terrain.settings().enabled()) {
			return inner.sample(pos);
		}
		return terrain.biomeValue(pos.blockX(), pos.blockZ());
	}

	@Override
	public void fill(double[] densities, EachApplier applier) {
		applier.fill(densities, this);
	}

	@Override
	public DensityFunction apply(DensityFunctionVisitor visitor) {
		DensityFunction.Noise seeded = visitor.apply(noise);
		OuterEndTerrain known = terrain != null && seeded.noise() == noise.noise() ? terrain : OuterEndDensity.seededTerrain(seeded);
		return visitor.apply(new OuterEndBiomeDensity(inner.apply(visitor), seeded, known));
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
