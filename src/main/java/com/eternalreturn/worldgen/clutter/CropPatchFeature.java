package com.eternalreturn.worldgen.clutter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.RandomPatchFeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wild crop patches (worldgen expansion 2, phase 1; feature eternalreturn:crop_patch,
 * data/eternalreturn/worldgen/configured_feature/crop_patches.json). Each chunk picks one spot on open grass or dirt under the sky; of
 * the crops whose ideal-biome tag holds the biome there, each rolls its own chance (one in "chance" chunks) and the first
 * to succeed grows a patch. A "crop" patch is a loose clump of fully grown crops standing on the ground as it is (a grown
 * crop may stand on grass or dirt, GrownCropFootingMixin; seeds still need farmland); a "fruit" patch is loose pumpkins or
 * melons on the grass. Grass and the biome's own flowers grow round it. One patch in "overgrown_chance" is an overgrown field instead:
 * larger, in rough rows, with a few broken fence posts of the wood whose logs stand nearest (oak where there are none).
 */
public final class CropPatchFeature extends Feature<CropPatchFeature.Config> {
	/** One crop: its block, its ideal-biome tag, one patch per "chance" chunks of those biomes, and "crop" or "fruit". */
	public record Crop(Block block, TagKey<Biome> biomes, int chance, boolean fruit) {
		public static final Codec<Crop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Registries.BLOCK.getCodec().fieldOf("block").forGetter(Crop::block),
				TagKey.unprefixedCodec(RegistryKeys.BIOME).fieldOf("biomes").forGetter(Crop::biomes),
				Codec.intRange(1, 100000).fieldOf("chance").forGetter(Crop::chance),
				Codec.STRING.xmap(kind -> kind.equals("fruit"), fruit -> fruit ? "fruit" : "crop").fieldOf("kind").forGetter(Crop::fruit))
				.apply(instance, Crop::new));
	}

	public record Config(List<Crop> crops, IntProvider size, int overgrownChance, IntProvider rows, IntProvider rowLength, IntProvider fencePosts)
			implements FeatureConfig {
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Crop.CODEC.listOf().fieldOf("crops").forGetter(Config::crops),
				IntProvider.createValidatingCodec(1, 40).fieldOf("size").forGetter(Config::size),
				Codec.intRange(1, 1000).fieldOf("overgrown_chance").forGetter(Config::overgrownChance),
				IntProvider.createValidatingCodec(1, 6).fieldOf("rows").forGetter(Config::rows),
				IntProvider.createValidatingCodec(2, 12).fieldOf("row_length").forGetter(Config::rowLength),
				IntProvider.createValidatingCodec(0, 12).fieldOf("fence_posts").forGetter(Config::fencePosts)).apply(instance, Config::new));
	}

	public CropPatchFeature() {
		super(Config.CODEC);
	}

	@Override
	public boolean generate(FeatureContext<Config> context) {
		StructureWorldAccess world = context.getWorld();
		Random random = context.getRandom();
		Config config = context.getConfig();
		BlockPos origin = context.getOrigin();
		int x = origin.getX() + random.nextInt(16);
		int z = origin.getZ() + random.nextInt(16);
		// Every crop's roll is drawn whatever the biome, so the random numbers after it never depend on the biome.
		boolean[] rolled = new boolean[config.crops().size()];
		for (int i = 0; i < rolled.length; i++) {
			rolled[i] = random.nextInt(config.crops().get(i).chance()) == 0;
		}
		boolean overgrown = random.nextInt(config.overgrownChance()) == 0;
		BlockPos ground = ground(world, x, z);
		if (ground == null) {
			return false;
		}
		RegistryEntry<Biome> biome = world.getBiome(ground.up());
		for (int i = 0; i < rolled.length; i++) {
			Crop crop = config.crops().get(i);
			if (rolled[i] && biome.isIn(crop.biomes())) {
				return build(world, context.getGenerator(), random, ground, crop, overgrown, config);
			}
		}
		return false;
	}

	/** Grows a patch (or an overgrown field) of this crop round the ground block "center". True if any crop was placed. */
	public static boolean build(StructureWorldAccess world, @Nullable ChunkGenerator generator, Random random, BlockPos center, Crop crop,
			boolean overgrown, Config config) {
		List<BlockPos> planted = new ArrayList<>();
		if (overgrown) {
			field(world, random, center, crop, config, planted);
		} else {
			int wanted = config.size().get(random);
			List<BlockPos> spots = new ArrayList<>();
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					if (dx * dx + dz * dz <= 10) {
						spots.add(center.add(dx, 0, dz));
					}
				}
			}
			// Nearest the middle first, loosely: each spot's distance gets a random nudge, so the clump is ragged but holds together.
			java.util.Map<BlockPos, Float> order = new java.util.HashMap<>();
			for (BlockPos spot : spots) {
				order.put(spot, (float) spot.getSquaredDistance(center) + random.nextFloat() * 6.0F);
			}
			spots.sort(java.util.Comparator.comparing(order::get));
			for (BlockPos spot : spots) {
				if (planted.size() >= wanted) {
					break;
				}
				plant(world, random, spot, center, crop, planted);
			}
		}
		if (planted.isEmpty()) {
			return false;
		}
		surround(world, generator, random, center, overgrown ? 8 : 4, planted);
		return true;
	}

	/** Rough rows of the crop, a grass row between each, with a few broken fence posts round the edge. */
	private static void field(StructureWorldAccess world, Random random, BlockPos center, Crop crop, Config config, List<BlockPos> planted) {
		boolean alongX = random.nextBoolean();
		int rows = config.rows().get(random);
		int length = config.rowLength().get(random);
		int width = rows * 2 - 1;
		int startA = -length / 2;
		int startB = -width / 2;
		for (int row = 0; row < rows; row++) {
			int jitter = random.nextInt(3) - 1;
			for (int a = startA + jitter; a < startA + jitter + length; a++) {
				int b = startB + row * 2;
				if (random.nextFloat() < (crop.fruit() ? 0.45F : 0.85F)) {
					plant(world, random, center.add(alongX ? a : b, 0, alongX ? b : a), center, crop, planted);
				}
			}
		}
		if (planted.isEmpty()) {
			return;
		}
		// Fence posts one block outside the field, of the wood that grows nearest.
		Block fence = nearestFence(world, center);
		List<BlockPos> edge = new ArrayList<>();
		for (int a = startA - 2; a <= startA + length + 1; a++) {
			for (int b = startB - 2; b <= startB + width + 1; b++) {
				boolean border = a == startA - 2 || a == startA + length + 1 || b == startB - 2 || b == startB + width + 1;
				if (border) {
					edge.add(center.add(alongX ? a : b, 0, alongX ? b : a));
				}
			}
		}
		net.minecraft.util.Util.shuffle(edge, random);
		int posts = config.fencePosts().get(random);
		for (int i = 0; i < edge.size() && posts > 0; i++) {
			BlockPos post = surfaceNear(world, edge.get(i), center);
			if (post == null) {
				continue;
			}
			posts--;
			world.setBlockState(post, fence.getDefaultState(), Block.NOTIFY_LISTENERS);
			// Now and then a second length still joined to it, the rest of a fence long fallen.
			if (random.nextInt(3) == 0) {
				Direction side = Direction.Type.HORIZONTAL.random(random);
				BlockPos next = surfaceNear(world, edge.get(i).offset(side), center);
				if (next != null && next.getY() == post.getY()) {
					world.setBlockState(next, fence.getDefaultState().with(side(side.getOpposite()), true), Block.NOTIFY_LISTENERS);
					world.setBlockState(post, fence.getDefaultState().with(side(side), true), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	private static BooleanProperty side(Direction direction) {
		return switch (direction) {
			case NORTH -> FenceBlock.NORTH;
			case SOUTH -> FenceBlock.SOUTH;
			case EAST -> FenceBlock.EAST;
			default -> FenceBlock.WEST;
		};
	}

	/** Plants one grown crop or fruit on the ground at this column, if its ground is within a block of the centre's. */
	private static void plant(StructureWorldAccess world, Random random, BlockPos spot, BlockPos center, Crop crop, List<BlockPos> planted) {
		BlockPos ground = ground(world, spot.getX(), spot.getZ());
		if (ground == null || Math.abs(ground.getY() - center.getY()) > 1) {
			return;
		}
		BlockPos above = ground.up();
		if (planted.contains(above)) {
			return;
		}
		// Clear the plant or snow standing there; grass under fruit loses its snowy look with the snow gone.
		BlockState groundState = world.getBlockState(ground);
		if (groundState.isOf(Blocks.GRASS_BLOCK) && groundState.get(net.minecraft.block.SnowyBlock.SNOWY)) {
			world.setBlockState(ground, groundState.with(net.minecraft.block.SnowyBlock.SNOWY, false), Block.NOTIFY_LISTENERS);
		}
		if (world.getBlockState(above.up()).getBlock() instanceof net.minecraft.block.TallPlantBlock) {
			world.setBlockState(above.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		if (crop.fruit()) {
			world.setBlockState(above, crop.block().getDefaultState(), Block.NOTIFY_LISTENERS);
		} else {
			BlockState grown = crop.block() instanceof CropBlock cropBlock ? cropBlock.withAge(cropBlock.getMaxAge()) : crop.block().getDefaultState();
			world.setBlockState(above, grown, Block.NOTIFY_LISTENERS);
		}
		planted.add(above);
	}

	/** Short grass and a few of the biome's own flowers (as bone meal grows them) on the open grass round the patch. */
	private static void surround(StructureWorldAccess world, @Nullable ChunkGenerator generator, Random random, BlockPos center, int radius,
			List<BlockPos> planted) {
		int flowers = 1 + random.nextInt(3);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius + 1) {
					continue;
				}
				BlockPos ground = ground(world, center.getX() + dx, center.getZ() + dz);
				if (ground == null || !world.getBlockState(ground).isOf(Blocks.GRASS_BLOCK) || !world.isAir(ground.up())
						|| Math.abs(ground.getY() - center.getY()) > 2) {
					continue;
				}
				float roll = random.nextFloat();
				if (roll < 0.06F && flowers > 0 && generator != null) {
					flowers--;
					List<ConfiguredFeature<?, ?>> list = world.getBiome(ground.up()).value().getGenerationSettings().getFlowerFeatures();
					if (!list.isEmpty() && list.get(0).config() instanceof RandomPatchFeatureConfig patch) {
						patch.feature().value().generateUnregistered(world, generator, random, ground.up());
					}
				} else if (roll < 0.4F) {
					world.setBlockState(ground.up(), Blocks.SHORT_GRASS.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
	}

	/** The open ground block of this column: grass, dirt, podzol or coarse dirt under open sky, with nothing but plants or snow on it. */
	@Nullable
	static BlockPos ground(StructureWorldAccess world, int x, int z) {
		BlockPos ground = new BlockPos(x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
		BlockState state = world.getBlockState(ground);
		if (!(state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.COARSE_DIRT))) {
			return null;
		}
		BlockState above = world.getBlockState(ground.up());
		if (!(above.isAir() || above.isReplaceable() || above.isIn(BlockTags.FLOWERS)) || !above.getFluidState().isEmpty()) {
			return null;
		}
		// Open sky: nothing, leaves included, over it (in a forest, patches grow in the clearings).
		if (world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) > ground.getY() + 1) {
			return null;
		}
		return ground;
	}

	/** Where a fence post can stand in this column: on the ground, within a block of the centre's height. */
	@Nullable
	private static BlockPos surfaceNear(StructureWorldAccess world, BlockPos column, BlockPos center) {
		BlockPos top = new BlockPos(column.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()), column.getZ());
		if (Math.abs(top.getY() - 1 - center.getY()) > 1 || !world.getBlockState(top.down()).isSideSolidFullSquare(world, top.down(), Direction.UP)) {
			return null;
		}
		BlockState there = world.getBlockState(top);
		if (!there.isAir() && !there.isReplaceable()) {
			return null;
		}
		return top;
	}

	/**
	 * The fence of the wood whose logs stand nearest (looked for down from the tops of columns up to 16 blocks round): a
	 * log "namespace:x_log" gives the fence "namespace:x_fence" if there is one. Oak where no such log stands.
	 */
	public static Block nearestFence(StructureWorldAccess world, BlockPos center) {
		Block best = Blocks.OAK_FENCE;
		int bestDistance = Integer.MAX_VALUE;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -16; dx <= 16; dx += 2) {
			for (int dz = -16; dz <= 16; dz += 2) {
				int distance = dx * dx + dz * dz;
				if (distance >= bestDistance || distance > 256) {
					continue;
				}
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				for (int y = top; y > top - 16 && y > world.getBottomY(); y--) {
					BlockState state = world.getBlockState(pos.set(x, y, z));
					if (state.isIn(BlockTags.LOGS)) {
						Identifier log = Registries.BLOCK.getId(state.getBlock());
						String path = log.getPath().replace("stripped_", "");
						if (path.endsWith("_log")) {
							Identifier fence = Identifier.of(log.getNamespace(), path.substring(0, path.length() - "_log".length()) + "_fence");
							if (Registries.BLOCK.containsId(fence)) {
								best = Registries.BLOCK.get(fence);
								bestDistance = distance;
							}
						}
						break;
					}
				}
			}
		}
		return best;
	}
}
