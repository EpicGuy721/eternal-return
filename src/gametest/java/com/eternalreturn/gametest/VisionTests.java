package com.eternalreturn.gametest;

import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/**
 * Monsters see players only in a cone in front of them (VisionCone): one facing away doesn't spot a player 6 blocks
 * behind it, one facing the player does; hit from behind, it strikes back; a player close behind is noticed anyway.
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
}
