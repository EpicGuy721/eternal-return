package com.eternalreturn.gametest;

import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.entity.GrimlockEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.GameEvent;

/**
 * The grimlock: blind (a player in plain view isn't noticed, one close behind is smelled), drawn by a noise, backs out
 * of the light and won't chase into it, burns in the sun, spawns only deep down. At night (the arena is open to the
 * sky), so its own batch.
 */
public class GrimlockTests implements FabricGameTest {
	private static final String BATCH = "grimlock";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	/** A grimlock facing +z that stays put (no goals) but still picks targets. */
	private static GrimlockEntity still(TestContext ctx, BlockPos pos) {
		GrimlockEntity grimlock = ctx.spawnEntity(NewMobs.GRIMLOCK, pos);
		((MobEntityAccessor) grimlock).eternalreturn$goalSelector().clear(goal -> true);
		grimlock.refreshPositionAndAngles(grimlock.getX(), grimlock.getY(), grimlock.getZ(), 0.0F, 0.0F);
		grimlock.setBodyYaw(0.0F);
		grimlock.setHeadYaw(0.0F);
		return grimlock;
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void blindButSmells(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(18000);
		// Two lanes far enough apart that neither grimlock smells the other lane's player.
		ServerPlayerEntity inView = FishingTests.survivalPlayer(ctx, new BlockPos(4, 2, 18));
		GrimlockEntity looking = still(ctx, new BlockPos(4, 2, 4));
		ServerPlayerEntity behind = FishingTests.survivalPlayer(ctx, new BlockPos(12, 2, 26));
		GrimlockEntity sniffing = still(ctx, new BlockPos(12, 2, 32));
		ctx.runAtTick(60, () -> {
			ctx.assertTrue(looking.getTarget() == null, "a player 14 blocks off in plain view goes unnoticed: " + looking.getTarget());
			ctx.assertTrue(sniffing.getTarget() == behind, "one 6 blocks behind is smelled");
			FishingTests.disconnect(inView);
			FishingTests.disconnect(behind);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void goesToANoise(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(18000);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 38));
		GrimlockEntity grimlock = ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 4));
		Vec3d noise = ctx.getAbsolute(new Vec3d(8.5, 2.0, 18.5));
		double start = grimlock.getPos().distanceTo(noise);
		ctx.runAtTick(5, () -> ctx.getWorld().emitGameEvent(player, GameEvent.BLOCK_PLACE, noise));
		ctx.runAtTick(200, () -> {
			ctx.assertTrue(grimlock.getPos().distanceTo(noise) < 4.0, "it went to the noise: " + start + " -> " + grimlock.getPos().distanceTo(noise));
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void shunsTheLight(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(18000);
		ctx.setBlockState(new BlockPos(7, 1, 10), Blocks.GLOWSTONE.getDefaultState());
		ctx.setBlockState(new BlockPos(9, 1, 10), Blocks.GLOWSTONE.getDefaultState());
		GrimlockEntity grimlock = ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 10));
		ctx.runAtTick(5, () -> ctx.assertTrue(GrimlockEntity.lit(ctx.getWorld(), grimlock.getBlockPos()), "it starts in the light"));
		ctx.runAtTick(200, () -> {
			ctx.assertTrue(!GrimlockEntity.lit(ctx.getWorld(), grimlock.getBlockPos()), "it backed out into the dark");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void wontChaseIntoTheLight(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(18000);
		ctx.setBlockState(new BlockPos(8, 1, 30), Blocks.GLOWSTONE.getDefaultState());
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 30));
		// Just out of the glowstone's light (light 5), within smelling range of the player standing in it.
		GrimlockEntity grimlock = ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 21));
		boolean[] smelled = {false};
		ctx.runAtEveryTick(() -> smelled[0] |= grimlock.getTarget() == player);
		ctx.runAtTick(200, () -> {
			ctx.assertTrue(smelled[0], "it smelled the player");
			ctx.assertTrue(grimlock.distanceTo(player) > 3.0, "but kept out of the light around them: " + grimlock.distanceTo(player));
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = "grimlock_day", skyAccess = true, tickLimit = 200)
	public void burnsInTheSun(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		GrimlockEntity grimlock = ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 10));
		ctx.runAtEveryTick(() -> {
			if (grimlock.isOnFire()) {
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void spawnsDeepDown(TestContext ctx) {
		var world = ctx.getWorld();
		ctx.assertTrue(!GrimlockEntity.canSpawn(NewMobs.GRIMLOCK, world, SpawnReason.NATURAL, new BlockPos(0, 40, 0), world.getRandom()),
				"not above y 0");
		ctx.complete();
	}
}
