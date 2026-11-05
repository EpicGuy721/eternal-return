package com.eternalreturn.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Zombies that reach a player hit them, whatever they hold (bare, a shovel, a pickaxe, a fishing rod) and wherever the player stands. */
public class ZombieAttackTests implements FabricGameTest {
	private static final String ARENA = "eternalreturn_test:arena";

	@GameTest(templateName = ARENA, batchId = "zombie_attack", tickLimit = 400)
	public void zombiesThatReachYouHitYou(TestContext ctx) {
		// Lanes 8 blocks apart along z; in each a player and, 4 blocks off, a zombie holding something.
		List<ItemStack> held = List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_SHOVEL), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.FISHING_ROD),
				new ItemStack(Items.IRON_SWORD));
		List<ServerPlayerEntity> players = new ArrayList<>();
		List<ZombieEntity> zombies = new ArrayList<>();
		for (int i = 0; i < held.size(); i++) {
			int z = 3 + i * 8;
			// Odd lanes: the player stands on a block a step up.
			BlockPos stand = new BlockPos(8, 2, z);
			if (i % 2 == 1) {
				ctx.setBlockState(stand, Blocks.STONE);
				stand = stand.up();
			}
			players.add(FishingTests.survivalPlayer(ctx, stand));
			ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(4, 2, z));
			zombie.equipStack(EquipmentSlot.MAINHAND, held.get(i).copy());
			// Facing the player (+x), so it sees them (VisionCone).
			zombie.refreshPositionAndAngles(zombie.getX(), zombie.getY(), zombie.getZ(), -90.0F, 0.0F);
			zombie.setHeadYaw(-90.0F);
			zombie.setBodyYaw(-90.0F);
			zombies.add(zombie);
		}
		ctx.runAtEveryTick(() -> zombies.forEach(com.eternalreturn.mobs.ai.Lounging::stand));
		ctx.runAtTick(300, () -> {
			StringBuilder missed = new StringBuilder();
			for (int i = 0; i < players.size(); i++) {
				ServerPlayerEntity player = players.get(i);
				if (player.getHealth() >= player.getMaxHealth()) {
					ZombieEntity zombie = zombies.get(i);
					missed.append(" [").append(held.get(i)).append(i % 2 == 1 ? " (step up)" : "").append(": target ").append(zombie.getTarget() == player)
							.append(", distance ").append(String.format("%.2f", zombie.distanceTo(player))).append(", goals ")
							.append(((com.eternalreturn.mixin.mobs.MobEntityAccessor) zombie).eternalreturn$goalSelector().getGoals().stream().filter(net.minecraft.entity.ai.goal.PrioritizedGoal::isRunning)
									.map(goal -> goal.getGoal().getClass().getSimpleName()).toList()).append("]");
				}
			}
			ctx.assertTrue(missed.isEmpty(), "zombies next to a player that never hit:" + missed);
			players.forEach(FishingTests::disconnect);
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/**
	 * A zombie that hasn't noticed a player right behind it (out of its sight cone) doesn't sit down for a rest there
	 * (Lounging), where with its AI off it would never notice them: it smells them and turns on them.
	 */
	@GameTest(templateName = ARENA, batchId = "zombie_lounge", tickLimit = 200)
	public void noRestBesideAPlayer(TestContext ctx) {
		com.eternalreturn.config.EternalReturnConfig.MobTweaks cfg = com.eternalreturn.config.EternalReturnConfig.get().mobs;
		int saved = cfg.loungeChance;
		cfg.loungeChance = 1;
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 12));
		ZombieEntity zombie = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(8, 2, 10));
		// Facing away from the player (-z).
		zombie.refreshPositionAndAngles(zombie.getX(), zombie.getY(), zombie.getZ(), 180.0F, 0.0F);
		zombie.setHeadYaw(180.0F);
		zombie.setBodyYaw(180.0F);
		boolean[] sat = {false};
		ctx.runAtEveryTick(() -> sat[0] |= com.eternalreturn.mobs.ai.Lounging.isLounging(zombie));
		ctx.runAtTick(120, () -> {
			cfg.loungeChance = saved;
			ctx.assertTrue(!sat[0], "it never sat down beside the player");
			ctx.assertTrue(zombie.getTarget() == player, "it noticed them: " + zombie.getTarget());
			FishingTests.disconnect(player);
			ctx.killAllEntities();
			ctx.complete();
		});
	}
}
