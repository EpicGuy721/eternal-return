package com.eternalreturn.gametest;

import com.eternalreturn.mobs.ai.Lounging;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;

/**
 * Lounging monsters (Lounging): sitting, they ignore a player in plain view and stay put; a noise gets them up facing
 * it, then they spot the player; a sneaking player's steps don't; being hurt or their time running out does. Who may sit.
 */
public class LoungingTests implements FabricGameTest {
	private static final String BATCH = "lounging";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	/** A husk sitting at z 10 facing +z (toward a player at z 14). */
	private static ZombieEntity sitter(TestContext ctx, int ticks) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 10));
		husk.refreshPositionAndAngles(husk.getX(), husk.getY(), husk.getZ(), 0.0F, 0.0F);
		husk.setBodyYaw(0.0F);
		husk.setHeadYaw(0.0F);
		Lounging.sit(husk, ticks);
		return husk;
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void sittingIgnoresPlayersUntilANoise(TestContext ctx) {
		// 3 blocks off: once it's up and turned to the noise (away from them), it smells them.
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 13));
		ZombieEntity husk = sitter(ctx, 2000);
		Vec3d start = husk.getPos();
		// Once the noise has got it up, kept up: an idle husk may sit down again now and then.
		ctx.runAtEveryTick(() -> {
			if (ctx.getTick() > 60) {
				Lounging.stand(husk);
			}
		});
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(Lounging.isLounging(husk), "still sitting");
			ctx.assertTrue(husk.getTarget() == null, "ignoring the player in plain view: " + husk.getTarget());
			ctx.assertTrue(husk.getPos().squaredDistanceTo(start) < 0.01, "and not moving");
			// A block placed 5 blocks off, behind it.
			ctx.getWorld().emitGameEvent(player, GameEvent.BLOCK_PLACE, ctx.getAbsolute(new Vec3d(8.5, 2.0, 5.5)));
			ctx.assertTrue(!Lounging.isLounging(husk) && husk.isInPose(EntityPose.STANDING), "the noise got it up");
			ctx.assertTrue(Math.abs(net.minecraft.util.math.MathHelper.wrapDegrees(husk.getHeadYaw() - 180.0F)) < 20.0F,
					"facing the noise: " + husk.getHeadYaw());
		});
		ctx.runAtTick(140, () -> {
			ctx.assertTrue(husk.getTarget() == player, "then it noticed the player");
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void sneakingStepsAreQuiet(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 13));
		player.setSneaking(true);
		ZombieEntity husk = sitter(ctx, 2000);
		ctx.runAtTick(10, () -> {
			ctx.getWorld().emitGameEvent(player, GameEvent.STEP, player.getPos());
			ctx.assertTrue(Lounging.isLounging(husk), "a sneaking step didn't wake it");
			player.setSneaking(false);
			ctx.getWorld().emitGameEvent(player, GameEvent.STEP, player.getPos());
			ctx.assertTrue(!Lounging.isLounging(husk), "a plain step did");
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void hurtOrTimeUpGetsItUp(TestContext ctx) {
		ZombieEntity hurt = sitter(ctx, 2000);
		ZombieEntity rested = ctx.spawnEntity(EntityType.HUSK, new BlockPos(4, 2, 20));
		Lounging.sit(rested, 20);
		// Up once its time is up (an idle husk may sit down again, now and then, after that).
		boolean[] up = {false};
		ctx.runAtEveryTick(() -> up[0] |= !Lounging.isLounging(rested));
		ctx.runAtTick(5, () -> {
			ctx.assertTrue(!up[0], "the rested one still sitting");
			hurt.damage(ctx.getWorld().getDamageSources().generic(), 1.0F);
			ctx.assertTrue(!Lounging.isLounging(hurt), "hurt, it got up");
		});
		ctx.runAtTick(40, () -> {
			ctx.assertTrue(up[0], "its time up, it got up");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void whoMaySit(TestContext ctx) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(4, 2, 10));
		// Idle means not walking anywhere either: no goals to send it off wandering, nor after the pillager (the undead and
		// illagers fight).
		((com.eternalreturn.mixin.mobs.MobEntityAccessor) husk).eternalreturn$goalSelector().clear(goal -> true);
		((com.eternalreturn.mixin.mobs.MobEntityAccessor) husk).eternalreturn$targetSelector().clear(goal -> true);
		PillagerEntity pillager = ctx.spawnEntity(EntityType.PILLAGER, new BlockPos(12, 2, 10));
		ZombieEntity hunter = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 30));
		ctx.runAtTick(20, () -> {
			hunter.setTarget(husk);
			// Up first: it may have sat down already, as an idle husk does now and then.
			Lounging.stand(husk);
			ctx.assertTrue(Lounging.canLounge(husk), "an idle husk may sit");
			ctx.assertTrue(!Lounging.canLounge(pillager), "an illager never does");
			ctx.assertTrue(!Lounging.canLounge(hunter), "nor a husk on the hunt");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** A sitting piglin gets up to trade: handed something (in its off hand), or a player near holding gold. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void piglinsGetUpToTrade(TestContext ctx) {
		net.minecraft.entity.mob.PiglinEntity handed = ctx.spawnEntity(EntityType.PIGLIN, new BlockPos(4, 2, 10));
		net.minecraft.entity.mob.PiglinEntity watching = ctx.spawnEntity(EntityType.PIGLIN, new BlockPos(8, 2, 30));
		for (var piglin : new net.minecraft.entity.mob.PiglinEntity[] {handed, watching}) {
			piglin.setImmuneToZombification(true);
			Lounging.sit(piglin, 2000);
		}
		handed.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new net.minecraft.item.ItemStack(com.eternalreturn.items.Gems.RUBY));
		net.minecraft.server.network.ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 34));
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.GOLD_INGOT));
		// Got up (the one handed a ruby trades it at once, and with its hands empty may sit down again after).
		boolean[] up = {false, false};
		ctx.runAtEveryTick(() -> {
			up[0] |= !Lounging.isLounging(handed);
			up[1] |= !Lounging.isLounging(watching);
		});
		ctx.runAtTick(40, () -> {
			try {
				ctx.assertTrue(up[0], "the piglin handed a ruby got up");
				ctx.assertTrue(up[1], "the one near a player holding gold got up");
			} finally {
				FishingTests.disconnect(player);
				ctx.killAllEntities();
			}
			ctx.complete();
		});
	}
}
