package com.eternalreturn.gametest;

import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Monsters see players only in a cone in front of them (VisionCone): one facing away doesn't spot a player 6 blocks
 * behind it, one facing the player does; hit from behind, it strikes back; a player close behind is noticed anyway.
 * And a fighter whose target is in reach but out of walking reach keeps fighting (MeleeAttackGoalMixin).
 */
public class VisionTests implements FabricGameTest {
	private static final String BATCH = "vision";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	/** A husk that keeps facing one way (no goals to turn it; it still looks for targets). yaw 0 faces +z, 180 faces -z. */
	private static ZombieEntity facing(TestContext ctx, BlockPos pos, float yaw) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, pos);
		((MobEntityAccessor) husk).eternalreturn$goalSelector().clear(goal -> true);
		husk.refreshPositionAndAngles(husk.getX(), husk.getY(), husk.getZ(), yaw, 0.0F);
		husk.setBodyYaw(yaw);
		husk.setHeadYaw(yaw);
		return husk;
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void seesOnlyInFront(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(10, 2, 4));
		ZombieEntity away = facing(ctx, new BlockPos(8, 2, 10), 0.0F);
		ZombieEntity toward = facing(ctx, new BlockPos(12, 2, 10), 180.0F);
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(toward.getTarget() == player, "the husk facing the player spotted them");
			ctx.assertTrue(away.getTarget() == null, "the one facing away didn't: " + away.getTarget());
			away.damage(ctx.getWorld().getDamageSources().playerAttack(player), 1.0F);
		});
		ctx.runAtTick(90, () -> {
			ctx.assertTrue(away.getTarget() == player, "hit from behind, it turned on the player");
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void sensesCloseBehind(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 28));
		ZombieEntity away = facing(ctx, new BlockPos(8, 2, 30), 0.0F);
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(away.getTarget() == player, "a player two blocks behind is noticed");
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** Its target on a pillar 2 high right beside it: it can't walk closer, but it can reach, so it keeps swinging. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void fightsATargetItCantWalkTo(TestContext ctx) {
		ctx.setBlockState(new BlockPos(8, 2, 11), Blocks.STONE.getDefaultState());
		ctx.setBlockState(new BlockPos(8, 3, 11), Blocks.STONE.getDefaultState());
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 4, 11));
		husk.setAiDisabled(true);
		WitherSkeletonEntity skeleton = ctx.spawnEntity(EntityType.WITHER_SKELETON, new BlockPos(8, 2, 10));
		float health = husk.getHealth();
		ctx.runAtEveryTick(() -> skeleton.setTarget(husk));
		ctx.runAtTick(120, () -> {
			ctx.assertTrue(husk.getHealth() < health, "it hit the target on the pillar: " + husk.getHealth() + " of " + health);
			ctx.killAllEntities();
			ctx.complete();
		});
	}


	/**
	 * Toe to toe with a target that stands still: it stays in its fighting stance (vanilla's melee goal let go each
	 * time the mob ran out of path, so its attack pose flickered off and on about every half second) and keeps hitting.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void staysInTheFight(TestContext ctx) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 16));
		husk.setAiDisabled(true);
		husk.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
		husk.setHealth(1000.0F);
		WitherSkeletonEntity skeleton = ctx.spawnEntity(EntityType.WITHER_SKELETON, new BlockPos(8, 2, 10));
		int[] flips = {0};
		int[] hits = {0};
		boolean[] attacking = {false};
		float[] health = {husk.getHealth()};
		ctx.runAtEveryTick(() -> {
			skeleton.setTarget(husk);
			if (skeleton.isAttacking() != attacking[0]) {
				flips[0]++;
				attacking[0] = skeleton.isAttacking();
			}
			if (husk.getHealth() < health[0]) {
				hits[0]++;
				health[0] = husk.getHealth();
			}
		});
		ctx.runAtTick(250, () -> {
			ctx.assertTrue(flips[0] == 1, "it took up its fighting stance once and kept it: switched " + flips[0] + " times");
			ctx.assertTrue(hits[0] >= 8, "and kept hitting: " + hits[0] + " hits");
			ctx.killAllEntities();
			ctx.complete();
		});
	}


	/**
	 * A player under a roof too low for a wither skeleton (2.4 blocks tall), the roof's edge hiding them from it: it
	 * keeps its fighting stance at the edge until it gives up the hunt (vanilla flickered it on and off every second).
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void staysInStanceOutOfSight(TestContext ctx) {
		for (int x = 5; x <= 11; x++) {
			for (int z = 14; z <= 20; z++) {
				ctx.setBlockState(new BlockPos(x, 4, z), Blocks.STONE.getDefaultState());
			}
		}
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 16));
		WitherSkeletonEntity skeleton = ctx.spawnEntity(EntityType.WITHER_SKELETON, new BlockPos(8, 2, 10));
		skeleton.setTarget(player);
		int[] flips = {0};
		boolean[] attacking = {false};
		ctx.runAtEveryTick(() -> {
			if (skeleton.getTarget() != null && skeleton.isAttacking() != attacking[0]) {
				flips[0]++;
				attacking[0] = skeleton.isAttacking();
			}
		});
		ctx.runAtTick(90, () -> {
			ctx.assertTrue(attacking[0] && flips[0] == 1, "it kept its stance: switched " + flips[0] + " times");
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}
}
