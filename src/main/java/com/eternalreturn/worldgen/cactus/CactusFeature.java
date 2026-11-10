package com.eternalreturn.worldgen.cactus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Cacti of their biome's shape as the world is made (feature eternalreturn:cactus_shapes; CactusShapes): max_per_chunk
 * tries at random columns, each kept with chance per_chunk / max_per_chunk of the biome's entry there, rooted only on
 * the sand or red sand already there (never on other ground turned to sand: the owner's rule, 2026-11-10).
 */
public final class CactusFeature extends Feature<CactusFeature.Config> {
	public record Config(int maxPerChunk) implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.intRange(1, 16).fieldOf("max_per_chunk").forGetter(Config::maxPerChunk)).apply(instance, Config::new));
	}

	public CactusFeature() {
		super(Config.CODEC);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		BlockPos origin = context.getOrigin();
		int tries = context.getConfig().maxPerChunk();
		boolean any = false;
		for (int i = 0; i < tries; i++) {
			int x = origin.getX() + random.nextInt(16);
			int z = origin.getZ() + random.nextInt(16);
			// Drawn before the biome is looked up, so every try uses the same random numbers whatever the biome.
			float roll = random.nextFloat() * tries;
			BlockPos root = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z);
			CactusShapes.Entry entry = CactusShapes.entryFor(world.getBiome(root));
			if (entry == null || roll >= entry.perChunk() || !CactusShapes.clear(world, root)) {
				continue;
			}
			// Only on sand or red sand already there: a cactus never changes the ground under it (the owner's rule).
			if (!world.getBlockState(root.down()).isIn(BlockTags.SAND)) {
				continue;
			}
			any |= CactusShapes.placeWhole(world, root, entry.shape(), null);
		}
		return any;
	}
}
