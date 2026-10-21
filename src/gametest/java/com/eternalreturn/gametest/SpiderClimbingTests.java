package com.eternalreturn.gametest;

import com.eternalreturn.mobs.climbing.Climber;
import com.eternalreturn.mobs.climbing.SurfaceWalker;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Spiders walk on walls and ceilings (Climber): over a wall to a target on top, hanging from a ceiling, dropping from
 * it onto a target below, and the long way round (up a wall, along a ceiling, round its lip and over the top) to a
 * target on an overhanging ledge.
 */
public class SpiderClimbingTests implements FabricGameTest {
	private static final String BATCH = "climbing";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static Climber climber(SpiderEntity spider) {
		return ((SurfaceWalker) spider).eternalreturn$climber();
	}

	private static ZombieEntity husk(TestContext ctx, BlockPos pos) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, pos);
		husk.setAiDisabled(true);
		return husk;
	}

	private static void fill(TestContext ctx, int x1, int y1, int z1, int x2, int y2, int z2) {
		for (BlockPos pos : BlockPos.iterate(x1, y1, z1, x2, y2, z2)) {
			ctx.setBlockState(pos, Blocks.STONE.getDefaultState());
		}
	}

	/** A wall 5 high across the arena, the target on top: up the wall and over. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 400)
	public void climbsOverAWall(TestContext ctx) {
		fill(ctx, 0, 1, 18, 15, 5, 18);
		ZombieEntity husk = husk(ctx, new BlockPos(8, 6, 18));
		SpiderEntity spider = ctx.spawnEntity(EntityType.SPIDER, new BlockPos(8, 2, 13));
		boolean[] onWall = {false};
		ctx.runAtEveryTick(() -> {
			spider.setTarget(husk);
			onWall[0] |= climber(spider).attach().getAxis().isHorizontal();
		});
		ctx.runAtTick(300, () -> {
			ctx.assertTrue(onWall[0], "it went up the wall");
			ctx.assertTrue(husk.getHealth() < husk.getMaxHealth(), "and reached the target on top: spider at y " + (spider.getY() - ctx.getAbsolutePos(BlockPos.ORIGIN).getY()));
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** Clinging to a ceiling with nothing to do: it hangs there. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void hangsFromACeiling(TestContext ctx) {
		fill(ctx, 2, 6, 2, 13, 6, 13);
		SpiderEntity spider = ctx.spawnEntity(EntityType.SPIDER, new BlockPos(8, 5, 8));
		// No goals (it would wander off), but its AI on: a mob with its AI off doesn't move at all.
		((com.eternalreturn.mixin.mobs.MobEntityAccessor) spider).eternalreturn$goalSelector().clear(goal -> true);
		((com.eternalreturn.mixin.mobs.MobEntityAccessor) spider).eternalreturn$targetSelector().clear(goal -> true);
		climber(spider).setAttach(Direction.UP);
		double ceiling = ctx.getAbsolutePos(new BlockPos(8, 6, 8)).getY();
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(climber(spider).attach() == Direction.UP, "still clinging: " + climber(spider).attach());
			ctx.assertTrue(spider.getBoundingBox().maxY > ceiling - 0.1, "against the ceiling: top at " + (spider.getBoundingBox().maxY - ceiling));
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** On a ceiling, its target below and a little way off: it crawls over and drops on it. */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 200)
	public void dropsOntoItsTarget(TestContext ctx) {
		fill(ctx, 2, 6, 2, 13, 6, 13);
		SpiderEntity spider = ctx.spawnEntity(EntityType.SPIDER, new BlockPos(5, 5, 8));
		climber(spider).setAttach(Direction.UP);
		ZombieEntity husk = husk(ctx, new BlockPos(9, 2, 8));
		ctx.runAtEveryTick(() -> spider.setTarget(husk));
		ctx.runAtTick(120, () -> {
			ctx.assertTrue(climber(spider).attach() == Direction.DOWN, "it let go");
			ctx.assertTrue(spider.getY() < ctx.getAbsolutePos(new BlockPos(0, 5, 0)).getY(), "and dropped");
			ctx.assertTrue(husk.getHealth() < husk.getMaxHealth(), "on its target");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/**
	 * A ledge overhanging the floor (a wall 4 high at the back, a 4-block roof out from its top), the target on top: up
	 * the back wall under the roof, along the roof's underside, round its lip and up over the top.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, skyAccess = true, tickLimit = 600)
	public void goesRoundAnOverhang(TestContext ctx) {
		fill(ctx, 0, 1, 24, 15, 5, 24);
		fill(ctx, 0, 5, 20, 15, 5, 23);
		ZombieEntity husk = husk(ctx, new BlockPos(8, 6, 22));
		SpiderEntity spider = ctx.spawnEntity(EntityType.SPIDER, new BlockPos(8, 2, 14));
		boolean[] onCeiling = {false};
		ctx.runAtEveryTick(() -> {
			spider.setTarget(husk);
			onCeiling[0] |= climber(spider).attach() == Direction.UP;
		});
		ctx.runAtTick(500, () -> {
			ctx.assertTrue(husk.getHealth() < husk.getMaxHealth(), "it reached the target on the ledge: spider at "
					+ spider.getBlockPos().subtract(ctx.getAbsolutePos(BlockPos.ORIGIN)) + " clinging " + climber(spider).attach() + ", went under the roof: " + onCeiling[0]);
			ctx.killAllEntities();
			ctx.complete();
		});
	}
}
