package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.worldgen.clutter.CropPatchFeature;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.ConfiguredFeature;

import java.util.List;

/**
 * Wild crop patches (worldgen expansion 2, phase 1; CropPatchFeature): a crop patch is fully grown crops on the grass, a
 * fruit patch loose pumpkins on grass, an overgrown field rows with fence posts of the nearest tree's wood, and the
 * farmland holds under a crop and turns back to dirt once it's harvested. Flat runs (batch crop_patches).
 */
public class CropPatchTests implements FabricGameTest {
	private static final String BATCH = "crop_patches";
	private static final String ARENA = "eternalreturn_test:arena";

	private static CropPatchFeature.Crop crop(Block block, String tag, boolean fruit) {
		return new CropPatchFeature.Crop(block, TagKey.of(RegistryKeys.BIOME, EternalReturn.id("ideal_biomes/" + tag)), 1, fruit);
	}

	private static CropPatchFeature.Config config() {
		return new CropPatchFeature.Config(List.of(), UniformIntProvider.create(4, 12), 10, ConstantIntProvider.create(4),
				ConstantIntProvider.create(8), ConstantIntProvider.create(4));
	}

	/** A grass floor at test y 1 over the arena, open sky above. */
	private static void grass(TestContext ctx) {
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 40; z++) {
				ctx.setBlockState(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 8; y++) {
					ctx.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	private static int count(TestContext ctx, java.util.function.Predicate<BlockState> test) {
		int found = 0;
		for (int x = -4; x < 20; x++) {
			for (int z = -4; z < 44; z++) {
				for (int y = 1; y < 5; y++) {
					if (test.test(ctx.getBlockState(new BlockPos(x, y, z)))) {
						found++;
					}
				}
			}
		}
		return found;
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void cropPatchOnFarmland(TestContext ctx) {
		grass(ctx);
		ServerWorld world = ctx.getWorld();
		BlockPos center = ctx.getAbsolutePos(new BlockPos(8, 1, 10));
		ctx.assertTrue(CropPatchFeature.build(world, null, Random.create(1), center, crop(Blocks.CARROTS, "carrots", false), false, config()),
				"no patch grew");
		int crops = count(ctx, state -> state.isOf(Blocks.CARROTS));
		int grown = count(ctx, state -> state.isOf(Blocks.CARROTS) && ((CropBlock) Blocks.CARROTS).isMature(state));
		int farmland = count(ctx, state -> state.isOf(Blocks.FARMLAND));
		ctx.assertTrue(crops >= 4 && crops <= 12, "4 to 12 carrots, got " + crops);
		ctx.assertTrue(grown == crops && farmland == 0, crops + " carrots, " + grown + " grown, " + farmland + " farmland");
		// They stay on the grass: a neighbour update keeps them.
		BlockPos one = null;
		for (BlockPos pos : BlockPos.iterate(center.add(-4, 1, -4), center.add(4, 1, 4))) {
			if (world.getBlockState(pos).isOf(Blocks.CARROTS)) {
				one = pos.toImmutable();
				break;
			}
		}
		ctx.assertTrue(one != null && world.getBlockState(one).canPlaceAt(world, one), "a grown carrot can't stand on grass");
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void fruitPatchOnGrass(TestContext ctx) {
		grass(ctx);
		BlockPos center = ctx.getAbsolutePos(new BlockPos(8, 1, 10));
		ctx.assertTrue(CropPatchFeature.build(ctx.getWorld(), null, Random.create(2), center, crop(Blocks.PUMPKIN, "pumpkin_stem", true), false, config()),
				"no patch grew");
		int pumpkins = count(ctx, state -> state.isOf(Blocks.PUMPKIN));
		ctx.assertTrue(pumpkins >= 4 && pumpkins <= 12, "4 to 12 pumpkins, got " + pumpkins);
		ctx.assertTrue(count(ctx, state -> state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.PUMPKIN_STEM)) == 0, "farmland or stems under pumpkins");
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void overgrownFieldWithLocalFences(TestContext ctx) {
		grass(ctx);
		// A spruce trunk nearby: the field's broken fence posts are spruce.
		for (int y = 2; y < 7; y++) {
			ctx.setBlockState(new BlockPos(2, y, 34), Blocks.SPRUCE_LOG);
		}
		BlockPos center = ctx.getAbsolutePos(new BlockPos(8, 1, 22));
		ctx.assertTrue(CropPatchFeature.build(ctx.getWorld(), null, Random.create(3), center, crop(Blocks.WHEAT, "wheat", false), true, config()),
				"no field grew");
		int wheat = count(ctx, state -> state.isOf(Blocks.WHEAT));
		int spruce = count(ctx, state -> state.isOf(Blocks.SPRUCE_FENCE));
		int oak = count(ctx, state -> state.isOf(Blocks.OAK_FENCE));
		ctx.assertTrue(wheat > 12, "an overgrown field is bigger than a patch, got " + wheat + " wheat");
		ctx.assertTrue(spruce >= 1 && oak == 0, spruce + " spruce and " + oak + " oak fence posts");
		ctx.complete();
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void farmlandHoldsUntilHarvested(TestContext ctx) {
		grass(ctx);
		ServerWorld world = ctx.getWorld();
		BlockPos held = ctx.getAbsolutePos(new BlockPos(4, 1, 4));
		BlockPos bare = ctx.getAbsolutePos(new BlockPos(10, 1, 4));
		for (BlockPos pos : List.of(held, bare)) {
			world.setBlockState(pos, Blocks.FARMLAND.getDefaultState().with(FarmlandBlock.MOISTURE, 7));
		}
		world.setBlockState(held.up(), ((CropBlock) Blocks.WHEAT).withAge(7));
		for (int tick = 0; tick < 12; tick++) {
			for (BlockPos pos : List.of(held, bare)) {
				BlockState state = world.getBlockState(pos);
				if (state.isOf(Blocks.FARMLAND)) {
					state.randomTick(world, pos, world.getRandom());
				}
			}
		}
		ctx.assertTrue(world.getBlockState(held).isOf(Blocks.FARMLAND), "farmland under a crop dried back to dirt");
		ctx.assertTrue(world.getBlockState(bare).isOf(Blocks.DIRT), "bare farmland stayed farmland");
		ctx.complete();
	}

	/** A grown crop stands on grass or dirt; a young one (a planted seed) needs farmland. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true)
	public void onlyGrownCropsStandOnGrass(TestContext ctx) {
		grass(ctx);
		ServerWorld world = ctx.getWorld();
		BlockPos spot = ctx.getAbsolutePos(new BlockPos(4, 2, 4));
		CropBlock wheat = (CropBlock) Blocks.WHEAT;
		ctx.assertTrue(wheat.withAge(7).canPlaceAt(world, spot), "grown wheat can't stand on grass");
		ctx.assertTrue(!wheat.withAge(0).canPlaceAt(world, spot), "a seed can be planted on grass");
		ctx.assertTrue(!wheat.withAge(6).canPlaceAt(world, spot), "half-grown wheat stands on grass");
		world.setBlockState(spot.down(), Blocks.SAND.getDefaultState());
		ctx.assertTrue(!wheat.withAge(7).canPlaceAt(world, spot), "grown wheat stands on sand");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void patchTableLoads(TestContext ctx) {
		ConfiguredFeature<?, ?> feature = ctx.getWorld().getRegistryManager().get(RegistryKeys.CONFIGURED_FEATURE).get(EternalReturn.id("crop_patches"));
		ctx.assertTrue(feature != null && feature.config() instanceof CropPatchFeature.Config config && config.crops().size() == 6,
				"the six crops of crop_patches.json");
		ctx.complete();
	}
}
