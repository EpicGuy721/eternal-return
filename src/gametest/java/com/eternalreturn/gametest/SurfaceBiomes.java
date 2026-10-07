package com.eternalreturn.gametest;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.lang.reflect.Method;

/**
 * The biome surface rules see at a block. Moderner Beta (runtime-only, so reached by reflection) swaps
 * the surface builder's biome lookup for its own per-block biome when its biome provider has one
 * (BiomeResolverBlock.getBiomeBlock, unfuzzed); otherwise it is vanilla's seed-fuzzed biome access over
 * the biome source.
 */
final class SurfaceBiomes {
	private final BiomeAccess fuzzed;
	private final Object provider;
	private final Method getBiomeBlock;

	SurfaceBiomes(ServerWorld world) {
		ChunkGenerator generator = world.getChunkManager().getChunkGenerator();
		NoiseConfig noise = world.getChunkManager().getNoiseConfig();
		BiomeSource source = generator.getBiomeSource();
		this.fuzzed = world.getBiomeAccess().withSource((x, y, z) -> source.getBiome(x, y, z, noise.getMultiNoiseSampler()));
		Object found = null;
		Method method = null;
		try {
			found = source.getClass().getMethod("getBiomeProvider").invoke(source);
			for (Class<?> type : found.getClass().getInterfaces()) {
				if (type.getName().endsWith(".BiomeResolverBlock")) {
					method = type.getMethod("getBiomeBlock", int.class, int.class, int.class);
				}
			}
		} catch (ReflectiveOperationException ignored) {
			// not a Moderner Beta biome source
		}
		this.provider = found;
		this.getBiomeBlock = method;
	}

	boolean usesModernerBeta() {
		return this.getBiomeBlock != null;
	}

	@SuppressWarnings("unchecked")
	RegistryEntry<Biome> at(BlockPos pos) {
		if (this.getBiomeBlock != null) {
			try {
				return (RegistryEntry<Biome>) this.getBiomeBlock.invoke(this.provider, pos.getX(), pos.getY(), pos.getZ());
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		return this.fuzzed.getBiome(pos);
	}
}
