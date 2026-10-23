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
 * Monsters see players only in a half circle in front of them (VisionCone): one facing away doesn't spot a player 8
 * blocks behind it, one facing the player does, and so does one with the player beside it; hit from behind, it strikes
 * back; a player close behind is smelled (a zombie from 6 blocks, a skeleton hardly at all).
 * And a fighter whose target is in reach but out of walking reach keeps fighting (MeleeAttackGoalMixin); Haste and
 * Mining Fatigue speed up and slow down its swings (AttackSpeed); it leaps at a target on a pillar 2 blocks up and
 * knocks it off (LeapAttack).
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
		// Idle, it might sit down for a rest (Lounging), and see nothing: not in these tests.
		ctx.runAtEveryTick(() -> com.eternalreturn.mobs.ai.Lounging.stand(husk));
		return husk;
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void seesOnlyInFront(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(10, 2, 2));
		ZombieEntity away = facing(ctx, new BlockPos(8, 2, 10), 0.0F);
		ZombieEntity toward = facing(ctx, new BlockPos(12, 2, 10), 180.0F);
		// Facing +z, the player 8 blocks off to its side (and a little ahead).
		ZombieEntity beside = facing(ctx, new BlockPos(2, 2, 1), 0.0F);
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(toward.getTarget() == player, "the husk facing the player spotted them");
			ctx.assertTrue(away.getTarget() == null, "the one facing away didn't: " + away.getTarget());
			ctx.assertTrue(beside.getTarget() == player, "the one with the player beside it did");
			away.damage(ctx.getWorld().getDamageSources().playerAttack(player), 1.0F);
		});
		ctx.runAtTick(90, () -> {
			ctx.assertTrue(away.getTarget() == player, "hit from behind, it turned on the player");
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** 3 blocks behind: a zombie (smell 6) smells the player, a skeleton (1.5) doesn't. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void smellsByKind(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 17));
		ZombieEntity zombie = facing(ctx, new BlockPos(6, 2, 20), 0.0F);
		net.minecraft.entity.mob.SkeletonEntity skeleton = ctx.spawnEntity(EntityType.SKELETON, new BlockPos(10, 2, 20));
		((MobEntityAccessor) skeleton).eternalreturn$goalSelector().clear(goal -> true);
		skeleton.refreshPositionAndAngles(skeleton.getX(), skeleton.getY(), skeleton.getZ(), 0.0F, 0.0F);
		skeleton.setBodyYaw(0.0F);
		skeleton.setHeadYaw(0.0F);
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(zombie.getTarget() == player, "the zombie smelled the player behind it");
			ctx.assertTrue(skeleton.getTarget() == null, "the skeleton didn't: " + skeleton.getTarget());
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

	/** Three husks hitting sturdy targets for 10 seconds: one with Haste V hits most often, one with Mining Fatigue V least. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void hasteAndFatigueSetTheirPace(TestContext ctx) {
		net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect>[] effects = new net.minecraft.registry.entry.RegistryEntry[] {
				net.minecraft.entity.effect.StatusEffects.HASTE, null, net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE};
		int[] hits = new int[3];
		for (int i = 0; i < 3; i++) {
			int x = 3 + i * 5;
			ZombieEntity target = ctx.spawnEntity(EntityType.HUSK, new BlockPos(x, 2, 16));
			target.setAiDisabled(true);
			target.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
			target.setHealth(1000.0F);
			ZombieEntity fighter = ctx.spawnEntity(EntityType.HUSK, new BlockPos(x, 2, 14));
			if (effects[i] != null) {
				fighter.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(effects[i], 400, 4));
			}
			float[] health = {1000.0F};
			int lane = i;
			ctx.runAtEveryTick(() -> {
				fighter.setTarget(target);
				if (target.getHealth() < health[0]) {
					hits[lane]++;
					health[0] = target.getHealth();
				}
			});
		}
		ctx.runAtTick(220, () -> {
			ctx.assertTrue(hits[0] > hits[1] + 2 && hits[1] > hits[2] + 2, "hits with Haste V, nothing, Mining Fatigue V: " + hits[0] + ", " + hits[1] + ", " + hits[2]);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** A target on a pillar 2 blocks up: the fighter below leaps, hits it in the air and knocks it off. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void leapsToKnockTargetsOff(TestContext ctx) {
		ctx.setBlockState(new BlockPos(8, 2, 12), Blocks.STONE.getDefaultState());
		ctx.setBlockState(new BlockPos(8, 3, 12), Blocks.STONE.getDefaultState());
		ZombieEntity target = facing(ctx, new BlockPos(8, 4, 12), 0.0F);
		((MobEntityAccessor) target).eternalreturn$targetSelector().clear(goal -> true);
		double top = target.getY();
		ZombieEntity fighter = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 9));
		boolean[] leapt = {false};
		ctx.runAtEveryTick(() -> {
			fighter.setTarget(target);
			leapt[0] |= fighter.getVelocity().y > 0.38; // a leap (0.5, a tick of gravity on: 0.41), not a jump (0.33)
		});
		ctx.runAtTick(250, () -> {
			ctx.assertTrue(leapt[0], "the fighter leapt");
			ctx.assertTrue(target.getY() < top - 1.0, "and knocked its target off the pillar: " + (target.getY() - top));
			ctx.killAllEntities();
			ctx.complete();
		});
	}
}
