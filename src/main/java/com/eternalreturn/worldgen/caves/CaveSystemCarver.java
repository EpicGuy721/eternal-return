package com.eternalreturn.worldgen.caves;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.config.EternalReturnConfig.CaveTypeSettings;
import com.eternalreturn.worldgen.terrain.OverhangRounder;
import com.mojang.serialization.Codec;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.carver.Carver;
import net.minecraft.world.gen.carver.CarverConfig;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

import java.util.function.Function;

/**
 * The cave engine's one carver (eternalreturn:caves), attached to every overworld biome. The generator
 * calls it for the chunk being carved once per nearby source chunk (up to 8 chunks away), with a random
 * seeded from the world seed and that source chunk. From that seed each type gets its own random
 * (so turning one type off leaves the others' caves where they were), decides how many caves start in
 * the source chunk, and simulates them in full; only the parts inside the chunk being carved are written.
 * Carving works only from the seed, the source position and the chunk's own blocks, so it matches
 * across chunk borders and doesn't depend on which chunks generated first.
 * Before any cave is carved in a chunk, it also rounds the chunk's overhangs (OverhangRounder).
 * Outside Eternal Return worlds it does nothing.
 */
public class CaveSystemCarver extends Carver<CarverConfig> {
	/** Reused per thread while one chunk is carved from all its sources (the generator does that in one go). */
	private static final ThreadLocal<ChunkCarver> CURRENT = new ThreadLocal<>();

	public CaveSystemCarver(Codec<CarverConfig> configCodec) {
		super(configCodec);
	}

	@Override
	public boolean shouldCarve(CarverConfig config, Random random) {
		return true;
	}

	@Override
	public boolean carve(CarverContext context, CarverConfig config, Chunk chunk, Function<BlockPos, RegistryEntry<Biome>> posToBiome,
			Random random, AquiferSampler aquiferSampler, ChunkPos source, CarvingMask mask) {
		EternalReturnConfig.CaveTweaks caves = EternalReturnConfig.get().worldgen.caves;
		if (!CaveWorlds.isEternalReturn(context)) {
			return false;
		}
		// The first call for a chunk rounds its overhangs, before any cave is carved.
		ChunkCarver target = this.target(context, config, chunk, mask);
		if (!caves.enabled) {
			return false;
		}
		long started = System.nanoTime();
		ChunkPos here = chunk.getPos();
		boolean ownChunk = source.equals(here);
		// No shape from a source this far away can reach this chunk.
		if (!ownChunk && (gap(source.getStartX() + 8, here.getStartX()) > CaveBuilder.REACH || gap(source.getStartZ() + 8, here.getStartZ()) > CaveBuilder.REACH)) {
			return false;
		}

		long before = target.carvedBlocks();
		CaveBuilder builder = new CaveBuilder(target, source);
		long sourceSeed = random.nextLong();
		String forced = caves.debugForceCaveType;
		boolean forcing = forced != null && !forced.isEmpty();

		for (CaveType type : CaveTypes.ALL) {
			CaveTypeSettings settings = caves.types.getOrDefault(type.id(), type.defaults());
			double weight;
			if (forcing) {
				if (!forced.equals(type.id())) {
					continue;
				}
				weight = type.forcedWeight();
			} else {
				if (!settings.enabled) {
					continue;
				}
				weight = settings.weight * caves.density;
			}
			if (weight <= 0) {
				continue;
			}
			Random typeRandom = new Xoroshiro128PlusPlusRandom(mix(sourceSeed, type.id().hashCode()));
			double expected = weight / 100.0;
			int count = (int) expected + (typeRandom.nextDouble() < expected - (int) expected ? 1 : 0);
			for (int i = 0; i < count; i++) {
				int x = source.getStartX() + typeRandom.nextInt(16);
				int z = source.getStartZ() + typeRandom.nextInt(16);
				int y = settings.minY + typeRandom.nextInt(Math.max(1, settings.maxY - settings.minY + 1));
				net.minecraft.util.math.Vec3d inside = type.generate(builder, typeRandom, x + 0.5, y + 0.5, z + 0.5, settings);
				if (ownChunk && caves.debugLogCaveStarts) {
					CaveStartLog.log(type.id(), (int) Math.floor(inside.x), (int) Math.floor(inside.y), (int) Math.floor(inside.z));
				}
			}
		}
		CaveTiming.add(System.nanoTime() - started, ownChunk);
		return target.carvedBlocks() > before;
	}

	private ChunkCarver target(CarverContext context, CarverConfig config, Chunk chunk, CarvingMask mask) {
		ChunkCarver current = CURRENT.get();
		if (current != null && current.chunk() == chunk) {
			return current;
		}
		NoiseChunkGenerator generator = context instanceof CaveWorlds.Context extras ? extras.eternalreturn$generator() : null;
		int minY = context.getMinY() + 1;
		int maxY = context.getMinY() + context.getHeight() - 1 - 7;
		int seaLevel = generator != null ? generator.getSeaLevel() : 63;
		ChunkCarver.NeighbourWater water = generator == null ? null
				: (x, z) -> generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, chunk, context.getNoiseConfig());
		if (EternalReturnConfig.get().worldgen.roundOverhangs) {
			OverhangRounder.round(chunk, context.getNoiseConfig(), seaLevel);
		}
		current = new ChunkCarver(chunk, mask, state -> state.isIn(config.replaceable), minY, maxY, config.lavaLevel.getY(context), seaLevel, water);
		CURRENT.set(current);
		return current;
	}

	/** Distance from x to the nearest block of the 16 starting at start. */
	private static int gap(int x, int start) {
		return x < start ? start - x : Math.max(0, x - (start + 15));
	}

	/** SplitMix64 of a seed and a salt. */
	public static long mix(long seed, long salt) {
		long z = seed + salt * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
