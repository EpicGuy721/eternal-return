package com.eternalreturn.gametest;

import com.eternalreturn.survival.ColdExposure;
import com.eternalreturn.worldgen.landmarks.HotSprings;
import com.eternalreturn.worldgen.landmarks.LandmarkPiece;
import com.eternalreturn.worldgen.landmarks.Landmarks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Hot springs (worldgen expansion 2, phase 3; HotSprings): the plan's pools step down from the deepest, never touching;
 * built, the first pool has water over a magma vent, calcite rims hold the water, hidden magma keeps the springs warm,
 * snow and ice stay off within 8 blocks of the rims and not beyond; a geyser spring's vent sits on lava, and its eruption
 * (Geysers) throws a pig over the vent up; magma erupts only with lava under it and water over it.
 * Flat runs (batch hot_springs).
 */
public class HotSpringTests implements FabricGameTest {
	private static final String BATCH = "hot_springs";
	private static final String ARENA = "eternalreturn_test:arena";

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void plansStepDown(TestContext ctx) {
		for (long seed = 0; seed < 60; seed++) {
			HotSprings.Spring spring = HotSprings.plan(new BlockPos(1000, 80, -500), 2 + (int) (seed % 5), seed);
			List<HotSprings.Pool> pools = spring.pools();
			ctx.assertTrue(pools.size() == spring.size() && pools.get(0).depth() == 3, "pools and the deep first one: " + pools);
			for (int i = 1; i < pools.size(); i++) {
				ctx.assertTrue(pools.get(i).level() < pools.get(i - 1).level(), "pool " + i + " doesn't step down");
				for (int j = 0; j < i; j++) {
					HotSprings.Pool a = pools.get(i);
					HotSprings.Pool b = pools.get(j);
					double gap = Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.z() - b.z(), 2));
					ctx.assertTrue(gap > a.radius() + b.radius() + 1, "pools " + j + " and " + i + " touch: " + gap);
				}
			}
		}
		ctx.complete();
	}

	/** A spring of two pools that fits the arena, with a geyser. */
	private static HotSprings.Spring fitting(BlockPos centre, int minX, int maxX, int minZ, int maxZ) {
		for (long seed = 0; seed < 5000; seed++) {
			HotSprings.Spring spring = HotSprings.plan(centre, 2, seed);
			boolean fits = spring.geyser();
			for (HotSprings.Pool pool : spring.pools()) {
				fits &= pool.x() - pool.radius() - 2 >= minX && pool.x() + pool.radius() + 2 <= maxX && pool.z() - pool.radius() - 2 >= minZ
						&& pool.z() + pool.radius() + 2 <= maxZ;
			}
			if (fits) {
				return spring;
			}
		}
		throw new AssertionError("no fitting spring");
	}

	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 300)
	public void springWarmsClearsAndErupts(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		HotSprings.testing = true;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 40; z++) {
				for (int y = 0; y < 14; y++) {
					ctx.setBlockState(new BlockPos(x, y, z), y < 5 ? Blocks.STONE.getDefaultState() : y == 5 ? Blocks.GRASS_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState());
				}
			}
		}
		BlockPos corner = ctx.getAbsolutePos(BlockPos.ORIGIN);
		BlockPos far = ctx.getAbsolutePos(new BlockPos(15, 13, 39));
		int minX = Math.min(corner.getX(), far.getX());
		int minZ = Math.min(corner.getZ(), far.getZ());
		BlockPos centre = new BlockPos(minX + 8, corner.getY() + 6, minZ + 20);
		HotSprings.Spring spring = fitting(centre, minX + 1, minX + 14, minZ + 1, minZ + 38);
		LandmarkPiece piece = new LandmarkPiece(Landmarks.Kind.HOT_SPRING, centre, 2, spring.seed(), Blocks.STONE, new int[0], "");
		HotSprings.build(piece, world, new BlockBox(minX, corner.getY(), minZ, minX + 15, corner.getY() + 13, minZ + 39));
		HotSprings.Pool vent = spring.vent();
		BlockPos ventFloor = new BlockPos(vent.x(), vent.level() - vent.depth(), vent.z());
		ctx.assertTrue(world.getBlockState(ventFloor).isOf(Blocks.MAGMA_BLOCK), "no magma vent at the deep pool's bottom: " + world.getBlockState(ventFloor));
		ctx.assertTrue(world.getBlockState(ventFloor.up()).isOf(Blocks.WATER) && world.getBlockState(ventFloor.up(vent.depth())).isOf(Blocks.WATER)
				&& world.getBlockState(ventFloor.up(vent.depth() + 1)).isAir(), "the deep pool isn't 3 water deep");
		BlockPos rim = new BlockPos(vent.x() + vent.radius() + 1, vent.level(), vent.z());
		BlockState rimState = world.getBlockState(rim);
		ctx.assertTrue(rimState.isOf(Blocks.CALCITE) || world.getBlockState(rim).isOf(Blocks.WATER), "no calcite rim round the pool: " + rimState);
		// Warm at the pool's edge (the hidden magma), snow and ice kept off nearby but not far off.
		ctx.assertTrue(ColdExposure.warm(world, new BlockPos(vent.x() + vent.radius(), vent.level() + 1, vent.z())), "not warm at the pool's edge");
		ctx.assertTrue(HotSprings.keepsClear(world, new BlockPos(vent.x() + vent.radius() + 6, vent.level() + 1, vent.z())), "snow can settle 6 blocks off the rim");
		ctx.assertTrue(!HotSprings.keepsClear(world, new BlockPos(vent.x() + 60, vent.level() + 1, vent.z() + 60)), "snow kept off 60 blocks away");
		ctx.assertTrue(!world.getBiome(rim.up()).value().canSetIce(world, ventFloor.up(vent.depth()), false), "the pool can freeze");
		// The vent sits on lava, so it's a geyser (Geysers); started, it throws a pig swimming over the vent up.
		ctx.assertTrue(world.getBlockState(ventFloor.down()).isOf(Blocks.LAVA) && com.eternalreturn.blocks.Geysers.canErupt(world, ventFloor),
				"the geyser spring's vent isn't on lava under water");
		PigEntity pig = EntityType.PIG.create(world);
		pig.refreshPositionAndAngles(vent.x() + 0.5, vent.level() + 1.0, vent.z() + 0.5, 0, 0);
		world.spawnEntity(pig);
		ctx.assertTrue(com.eternalreturn.blocks.Geysers.start(world, ventFloor), "the geyser won't start");
		ctx.runAtTick(com.eternalreturn.blocks.Geysers.WARNING + 20, () -> {
			ctx.assertTrue(pig.getY() > vent.level() + 5, "the pig didn't fly: y " + pig.getY() + " over the water at " + vent.level());
			pig.discard();
			HotSprings.testing = false;
			ctx.complete();
		});
	}
	/** Magma erupts only with lava right under it and water right over it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void geyserNeedsLavaAndWater(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos magma = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		BlockPos dry = ctx.getAbsolutePos(new BlockPos(5, 2, 2));
		BlockPos cold = ctx.getAbsolutePos(new BlockPos(2, 2, 5));
		for (BlockPos pos : java.util.List.of(magma, dry, cold)) {
			world.setBlockState(pos, Blocks.MAGMA_BLOCK.getDefaultState());
		}
		world.setBlockState(magma.down(), Blocks.LAVA.getDefaultState());
		world.setBlockState(magma.up(), Blocks.WATER.getDefaultState());
		world.setBlockState(dry.down(), Blocks.LAVA.getDefaultState());
		world.setBlockState(cold.down(), Blocks.STONE.getDefaultState());
		world.setBlockState(cold.up(), Blocks.WATER.getDefaultState());
		ctx.assertTrue(com.eternalreturn.blocks.Geysers.canErupt(world, magma), "magma on lava under water won't erupt");
		ctx.assertTrue(!com.eternalreturn.blocks.Geysers.canErupt(world, dry), "magma with no water over it erupts");
		ctx.assertTrue(!com.eternalreturn.blocks.Geysers.canErupt(world, cold), "magma with no lava under it erupts");
		ctx.assertTrue(Blocks.MAGMA_BLOCK.getDefaultState().hasRandomTicks(), "magma has no random ticks");
		for (BlockPos pos : java.util.List.of(magma.up(), cold.up(), magma.down(), dry.down())) {
			world.setBlockState(pos, Blocks.AIR.getDefaultState());
		}
		ctx.complete();
	}
}
