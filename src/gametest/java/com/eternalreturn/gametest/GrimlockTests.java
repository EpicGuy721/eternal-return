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
import net.minecraft.world.LightType;
import net.minecraft.world.event.GameEvent;

/**
 * The grimlock: blind (a player in plain view isn't noticed, one close behind is smelled), drawn by a noise, loses a
 * player who slips out of its smell (going to where it last smelled them), is drawn off a player by a noise, backs out
 * of the light and won't chase into it, burns in the sun, spawns only deep down. Under a roof (dark whatever the time); the tests that make noises each in a batch of their own (a batch's tests run side by side,
 * and a grimlock hears 16 blocks).
 */
public class GrimlockTests implements FabricGameTest {
	private static final String BATCH = "grimlock";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	/**
	 * A stone roof and walls round the arena: no daylight gets in, whatever the time (other tests set it), so it's dark as
	 * a cave. (The test area's barrier walls pass light: under a roof alone, daylight came in from the sides, and by day
	 * the arena's edges counted as lit.)
	 */
	private static void roof(TestContext ctx) {
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 40; z++) {
				boolean edge = x == 0 || x == 15 || z == 0 || z == 39;
				for (int y = edge ? 2 : 13; y <= 13; y++) {
					ctx.setBlockState(new BlockPos(x, y, z), Blocks.STONE.getDefaultState());
				}
			}
		}
	}

	/**
	 * Placed light takes a while to spread, and so does the roof's shade: whether the block light at pos (test-relative)
	 * is up to a grimlock's LIGHT yet, and the daylight gone from under the roof (at pos, mid-arena and in two corners).
	 * (Tests poll this from their one every-tick task: a task added from inside another can be lost, as the test keeps
	 * them in a map it is going through.)
	 */
	private static boolean lightSpread(TestContext ctx, BlockPos pos) {
		var world = ctx.getWorld();
		if (world.getLightLevel(LightType.BLOCK, ctx.getAbsolutePos(pos)) < GrimlockEntity.LIGHT) {
			return false;
		}
		for (BlockPos shaded : new BlockPos[] {pos, new BlockPos(8, 2, 20), new BlockPos(1, 2, 1), new BlockPos(14, 2, 38)}) {
			if (world.getLightLevel(LightType.SKY, ctx.getAbsolutePos(shaded)) > 0) {
				return false;
			}
		}
		return true;
	}

	/** Where it is and how bright it is there, for failure messages. */
	private static String where(TestContext ctx, GrimlockEntity grimlock) {
		BlockPos at = grimlock.getBlockPos();
		return "at " + at.subtract(ctx.getAbsolutePos(BlockPos.ORIGIN)).toShortString() + ", block light " + ctx.getWorld().getLightLevel(LightType.BLOCK, at)
				+ ", sky light " + ctx.getWorld().getLightLevel(LightType.SKY, at) + (ctx.getWorld().isDay() ? " (day)" : " (night)") + ", target " + grimlock.getTarget();
	}

	/** Idle, a grimlock might sit down for a rest (Lounging), and sense nothing: not in these tests. */
	private static GrimlockEntity awake(TestContext ctx, GrimlockEntity grimlock) {
		ctx.runAtEveryTick(() -> com.eternalreturn.mobs.ai.Lounging.stand(grimlock));
		return grimlock;
	}

	/** A grimlock facing +z that stays put (no goals) but still picks targets. */
	private static GrimlockEntity still(TestContext ctx, BlockPos pos) {
		GrimlockEntity grimlock = ctx.spawnEntity(NewMobs.GRIMLOCK, pos);
		((MobEntityAccessor) grimlock).eternalreturn$goalSelector().clear(goal -> true);
		grimlock.refreshPositionAndAngles(grimlock.getX(), grimlock.getY(), grimlock.getZ(), 0.0F, 0.0F);
		grimlock.setBodyYaw(0.0F);
		grimlock.setHeadYaw(0.0F);
		return awake(ctx, grimlock);
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void blindButSmells(TestContext ctx) {
		roof(ctx);
		// Two lanes far enough apart that neither grimlock smells the other lane's player.
		ServerPlayerEntity inView = FishingTests.survivalPlayer(ctx, new BlockPos(4, 2, 18));
		GrimlockEntity looking = still(ctx, new BlockPos(4, 2, 4));
		ServerPlayerEntity behind = FishingTests.survivalPlayer(ctx, new BlockPos(12, 2, 26));
		GrimlockEntity sniffing = still(ctx, new BlockPos(12, 2, 32));
		// It looks for targets now and then (at random): wait for the sniffing one to find its player, and give the looking
		// one at least 60 ticks to (not) notice its own.
		boolean[] smelled = {false};
		ctx.runAtEveryTick(() -> {
			ctx.assertTrue(looking.getTarget() == null, "a player 14 blocks off in plain view goes unnoticed: " + looking.getTarget());
			smelled[0] |= sniffing.getTarget() == behind;
			if (smelled[0] && ctx.getTick() >= 60) {
				FishingTests.disconnect(inView);
				FishingTests.disconnect(behind);
				ctx.killAllEntities();
				ctx.complete();
			} else if (ctx.getTick() == 190) {
				ctx.assertTrue(false, "one 6 blocks behind is smelled: " + where(ctx, sniffing) + ", " + sniffing.distanceTo(behind) + " off, age " + sniffing.age);
			}
		});
	}

	@GameTest(templateName = ARENA, batchId = "grimlock_noise", tickLimit = 300)
	public void goesToANoise(TestContext ctx) {
		roof(ctx);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 38));
		GrimlockEntity grimlock = awake(ctx, ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 4)));
		Vec3d noise = ctx.getAbsolute(new Vec3d(8.5, 2.0, 18.5));
		double start = grimlock.getPos().distanceTo(noise);
		// The closest it came (once there, it may wander off again).
		double[] closest = {start};
		ctx.runAtTick(5, () -> ctx.getWorld().emitGameEvent(player, GameEvent.BLOCK_PLACE, noise));
		ctx.runAtEveryTick(() -> closest[0] = Math.min(closest[0], grimlock.getPos().distanceTo(noise)));
		ctx.runAtTick(200, () -> {
			ctx.assertTrue(closest[0] < 3.0, "it went to the noise: " + start + " -> " + closest[0]);
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void shunsTheLight(TestContext ctx) {
		roof(ctx);
		ctx.setBlockState(new BlockPos(7, 1, 10), Blocks.GLOWSTONE.getDefaultState());
		ctx.setBlockState(new BlockPos(9, 1, 10), Blocks.GLOWSTONE.getDefaultState());
		// In the light (once it has spread there), 150 ticks to back out of it.
		GrimlockEntity[] grimlock = {null};
		long[] litAt = {-1};
		ctx.runAtEveryTick(() -> {
			if (grimlock[0] == null) {
				if (lightSpread(ctx, new BlockPos(8, 2, 10))) {
					litAt[0] = ctx.getTick();
					grimlock[0] = ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 10));
				}
				return;
			}
			com.eternalreturn.mobs.ai.Lounging.stand(grimlock[0]);
			if (ctx.getTick() == litAt[0] + 150) {
				ctx.assertTrue(!GrimlockEntity.lit(ctx.getWorld(), grimlock[0].getBlockPos()), "it backed out into the dark: " + where(ctx, grimlock[0]));
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 300)
	public void wontChaseIntoTheLight(TestContext ctx) {
		roof(ctx);
		// A soul torch (light 10) just behind the player: their spot is lit, 5 blocks off it's dark, within smelling range.
		ctx.setBlockState(new BlockPos(8, 2, 31), Blocks.SOUL_TORCH.getDefaultState());
		// Both come once the light has spread to the player's spot (before that, the player stood in the dark, fair game).
		ServerPlayerEntity[] player = {null};
		GrimlockEntity[] grimlock = {null};
		long[] litAt = {-1};
		boolean[] smelled = {false};
		String[] steppedIntoLight = {null};
		ctx.runAtEveryTick(() -> {
			if (grimlock[0] == null) {
				if (lightSpread(ctx, new BlockPos(8, 2, 30))) {
					litAt[0] = ctx.getTick();
					player[0] = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 30));
					grimlock[0] = ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 25));
				}
				return;
			}
			com.eternalreturn.mobs.ai.Lounging.stand(grimlock[0]);
			smelled[0] |= grimlock[0].getTarget() == player[0];
			if (steppedIntoLight[0] == null && GrimlockEntity.lit(ctx.getWorld(), grimlock[0].getBlockPos())) {
				steppedIntoLight[0] = where(ctx, grimlock[0]) + " at tick " + (ctx.getTick() - litAt[0]);
			}
			if (ctx.getTick() == litAt[0] + 150) {
				ctx.assertTrue(smelled[0], "it smelled the player");
				ctx.assertTrue(steppedIntoLight[0] == null, "but never stepped into the light around them: " + steppedIntoLight[0]);
				ctx.assertTrue(player[0].getHealth() == player[0].getMaxHealth(), "nor hurt them: " + player[0].getHealth());
				FishingTests.disconnect(player[0]);
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	/** Smelled at 4 blocks, the player slips 15 blocks off without a sound: it loses them, and goes to where it last smelled them. */
	@GameTest(templateName = ARENA, batchId = "grimlock_track", tickLimit = 300)
	public void losesTrackOutOfSmell(TestContext ctx) {
		roof(ctx);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 14));
		GrimlockEntity grimlock = awake(ctx, ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 10)));
		Vec3d smelledAt = player.getPos();
		Vec3d away = ctx.getAbsolute(new Vec3d(8.5, 2.0, 36.5));
		// Once it has smelled the player (it checks for targets now and then), they slip away; 90 ticks on, the check.
		long[] slippedAt = {-1};
		double[] closest = {Double.MAX_VALUE};
		ctx.runAtEveryTick(() -> {
			if (slippedAt[0] >= 0) {
				closest[0] = Math.min(closest[0], grimlock.getPos().distanceTo(smelledAt));
			}
			if (slippedAt[0] < 0 && grimlock.getTarget() == player) {
				slippedAt[0] = ctx.getTick();
				player.requestTeleport(away.x, away.y, away.z);
			}
		});
		ctx.runAtEveryTick(() -> {
			if (slippedAt[0] < 0 || ctx.getTick() != slippedAt[0] + 90) {
				return;
			}
			ctx.assertTrue(grimlock.getTarget() == null, "it lost them: " + grimlock.getTarget());
			ctx.assertTrue(grimlock.getPos().distanceTo(away) > 12.0, "and didn't follow them: " + grimlock.getPos().distanceTo(away) + " off");
			ctx.assertTrue(closest[0] < 2.0, "it went to where it last smelled them: came within " + closest[0]);
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** On the hunt, the player 5 blocks off: a noise 10 blocks the other way draws it off them. */
	@GameTest(templateName = ARENA, batchId = "grimlock_lure", tickLimit = 300)
	public void drawnOffByANoise(TestContext ctx) {
		roof(ctx);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 25));
		GrimlockEntity grimlock = awake(ctx, ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 20)));
		((MobEntityAccessor) grimlock).eternalreturn$goalSelector().clear(goal -> true);
		Vec3d noise = ctx.getAbsolute(new Vec3d(8.5, 2.0, 10.5));
		// Once it has smelled the player (it checks for targets now and then), the noise; 30 ticks on, the check.
		long[] noisedAt = {-1};
		ctx.runAtEveryTick(() -> {
			if (noisedAt[0] < 0 && grimlock.getTarget() == player) {
				noisedAt[0] = ctx.getTick();
				ctx.getWorld().emitGameEvent(null, GameEvent.BLOCK_PLACE, noise);
				ctx.assertTrue(grimlock.getTarget() == null, "a noise elsewhere drew it off");
			} else if (noisedAt[0] >= 0 && ctx.getTick() == noisedAt[0] + 30) {
				ctx.assertTrue(grimlock.getTarget() == null, "and it isn't straight back on them: " + grimlock.getTarget());
				FishingTests.disconnect(player);
				ctx.killAllEntities();
				ctx.complete();
			}
		});
	}

	@GameTest(templateName = ARENA, batchId = "grimlock_day", skyAccess = true, tickLimit = 200)
	public void burnsInTheSun(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(6000);
		GrimlockEntity grimlock = awake(ctx, ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 10)));
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

	/**
	 * Shot at from out of its smell (a pillager 24 blocks off, arrows every 2 seconds): it heads toward the shooter, not
	 * back to where it stood when hit.
	 */
	@GameTest(templateName = ARENA, batchId = "grimlock_shot", tickLimit = 400)
	public void headsTowardAShooter(TestContext ctx) {
		roof(ctx);
		net.minecraft.entity.mob.PillagerEntity pillager = ctx.spawnEntity(net.minecraft.entity.EntityType.PILLAGER, new BlockPos(8, 2, 32));
		pillager.setAiDisabled(true);
		GrimlockEntity grimlock = awake(ctx, ctx.spawnEntity(NewMobs.GRIMLOCK, new BlockPos(8, 2, 8)));
		double start = grimlock.distanceTo(pillager);
		double[] closest = {start};
		ctx.runAtEveryTick(() -> {
			closest[0] = Math.min(closest[0], grimlock.distanceTo(pillager));
			if (ctx.getTick() % 40 == 5 && grimlock.isAlive()) {
				net.minecraft.entity.projectile.ArrowEntity arrow = new net.minecraft.entity.projectile.ArrowEntity(ctx.getWorld(), pillager,
						new net.minecraft.item.ItemStack(net.minecraft.item.Items.ARROW), null);
				Vec3d to = grimlock.getPos().add(0.0, grimlock.getHeight() * 0.6, 0.0).subtract(arrow.getPos());
				arrow.setVelocity(to.x, to.y + to.horizontalLength() * 0.03, to.z, 2.4F, 0.0F);
				arrow.setDamage(0.2);
				ctx.getWorld().spawnEntity(arrow);
			}
		});
		ctx.runAtTick(300, () -> {
			ctx.assertTrue(closest[0] < start - 12.0, "it went toward the shooter: " + start + " -> " + closest[0]);
			ctx.killAllEntities();
			ctx.complete();
		});
	}
}
