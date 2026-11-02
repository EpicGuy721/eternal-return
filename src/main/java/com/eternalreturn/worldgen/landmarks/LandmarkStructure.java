package com.eternalreturn.worldgen.landmarks;

import com.eternalreturn.worldgen.caves.CaveWorlds;
import com.eternalreturn.worldgen.stone.BiomeStoneProcessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One landmark kind as a structure (type eternalreturn:landmark): "landmark" names the kind and "min_size" and
 * "max_size" its size (what size means depends on the kind, docs/worldgen.md). It starts at a random spot of the chunk
 * on dry land, and only in Eternal Return worlds; the stone of the biome there is its stone.
 */
public final class LandmarkStructure extends Structure {
	public static final MapCodec<LandmarkStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			configCodecBuilder(instance),
			Codec.STRING.fieldOf("landmark").forGetter(s -> s.kind.id()),
			Codec.intRange(1, 512).fieldOf("min_size").forGetter(s -> s.minSize),
			Codec.intRange(1, 512).fieldOf("max_size").forGetter(s -> s.maxSize)).apply(instance, LandmarkStructure::new));

	private final Landmarks.Kind kind;
	private final int minSize;
	private final int maxSize;

	public LandmarkStructure(Structure.Config config, String kind, int minSize, int maxSize) {
		super(config);
		this.kind = Landmarks.Kind.of(kind);
		this.minSize = minSize;
		this.maxSize = Math.max(minSize, maxSize);
	}

	@Override
	public Optional<StructurePosition> getStructurePosition(Context context) {
		ChunkGenerator generator = context.chunkGenerator();
		if (!(generator instanceof NoiseChunkGenerator noise) || !CaveWorlds.usesEternalReturnSettings(noise, context.dynamicRegistryManager())) {
			return Optional.empty();
		}
		Random random = context.random();
		int x = context.chunkPos().getStartX() + random.nextInt(16);
		int z = context.chunkPos().getStartZ() + random.nextInt(16);
		// The biome first, as vanilla's structures check it: the height asks Moderner Beta for the terrain round the
		// column (near the coast, the deep oceans' distance to land), far too slow for every spot /locate tries.
		int probe = generator.getSeaLevel() + 8;
		if (!context.biomePredicate().test(context.biomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(probe),
				BiomeCoords.fromBlock(z), context.noiseConfig().getMultiNoiseSampler()))) {
			return Optional.empty();
		}
		// No landmark by the sea: and the height of ground near it is slow to work out (the deep oceans' distance to land).
		for (int[] o : new int[][]{{-24, 0}, {24, 0}, {0, -24}, {0, 24}}) {
			if (context.biomeSource().getBiome(BiomeCoords.fromBlock(x + o[0]), BiomeCoords.fromBlock(probe), BiomeCoords.fromBlock(z + o[1]),
					context.noiseConfig().getMultiNoiseSampler()).isIn(net.minecraft.registry.tag.BiomeTags.IS_OCEAN)) {
				return Optional.empty();
			}
		}
		int y = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());

		if (y <= generator.getSeaLevel()) {
			return Optional.empty();
		}
		int size = this.minSize + random.nextInt(this.maxSize - this.minSize + 1);
		long seed = random.nextLong();
		Block stone = BiomeStoneProcessor.stoneOf(context.biomeSource().getBiome(BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y),
				BiomeCoords.fromBlock(z), context.noiseConfig().getMultiNoiseSampler()));
		int[] path = this.kind == Landmarks.Kind.LAVA_FLOW ? lavaPath(context, x, z, size, Random.create(seed)) : new int[0];
		BlockPos centre = new BlockPos(x, y, z);
		return Optional.of(new StructurePosition(centre, collector -> collector.addPiece(new LandmarkPiece(this.kind, centre, size, seed, stone, path))));
	}

	/**
	 * A lava flow's course from its vent: a block and a half a step, its heading wandering by up to a fifth of a turn
	 * each way, size steps, from the landmark's seed alone (steering downhill asked for the terrain's height hundreds
	 * of times a flow, which made placing them crawl). The lava lies in each column's own ground as the chunk is built.
	 * Points as x, y (the vent's ground), z.
	 */
	private static int[] lavaPath(Context context, int x, int z, int length, Random random) {
		List<Integer> points = new ArrayList<>();
		double px = x;
		double pz = z;
		double heading = random.nextDouble() * Math.PI * 2;
		int y = context.chunkGenerator().getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, context.world(), context.noiseConfig());
		for (int step = 0; step < length; step++) {
			points.add((int) Math.floor(px));
			points.add(y);
			points.add((int) Math.floor(pz));
			heading += (random.nextDouble() - 0.5) * 0.6;
			px += Math.cos(heading) * 1.5;
			pz += Math.sin(heading) * 1.5;
		}
		return points.stream().mapToInt(Integer::intValue).toArray();
	}

	@Override
	public StructureType<?> getType() {
		return Landmarks.TYPE;
	}
}
