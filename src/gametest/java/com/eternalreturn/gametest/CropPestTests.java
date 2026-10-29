package com.eternalreturn.gametest;

import com.eternalreturn.birds.BirdEntity;
import com.eternalreturn.birds.BirdVariant;
import com.eternalreturn.birds.Birds;
import com.eternalreturn.birds.CropPests;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.util.function.Supplier;

/** Birds as crop pests: pecking seeds and young crops, scarecrows, and villagers and pets chasing them off. */
public class CropPestTests implements FabricGameTest {
	private static final BlockPos CROP = new BlockPos(4, 1, 4);

	/** Farmland with a crop of this age on it, and a wild songbird a few blocks off (mobGriefing on: birds peck only with it). */
	private static BirdEntity field(TestContext ctx, int age) {
		ctx.getWorld().getGameRules().get(GameRules.DO_MOB_GRIEFING).set(true, ctx.getWorld().getServer());
		for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 0, 0), new BlockPos(7, 0, 7))) {
			ctx.setBlockState(pos, Blocks.GRASS_BLOCK);
		}
		ctx.setBlockState(CROP.down(), Blocks.FARMLAND);
		ctx.setBlockState(CROP, ((CropBlock) Blocks.WHEAT).withAge(age));
		BirdEntity bird = ctx.spawnEntity(Birds.BIRD, new BlockPos(1, 1, 1));
		bird.setVariant(BirdVariant.pack(0, 1, 0, 0, 0, 0));
		return bird;
	}

	/** Seeds and young crops on farmland are pecked at, grown ones not; a scarecrow guards those near it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "crop_pests")
	public void whatBirdsPeck(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos crop = ctx.getAbsolutePos(new BlockPos(1, 1, 1));
		ctx.setBlockState(new BlockPos(1, 0, 1), Blocks.FARMLAND);
		ctx.setBlockState(new BlockPos(1, 1, 1), ((CropBlock) Blocks.WHEAT).withAge(0));
		ctx.assertTrue(CropPests.peckable(world, crop), "a seed");
		ctx.setBlockState(new BlockPos(1, 1, 1), ((CropBlock) Blocks.WHEAT).withAge(3));
		ctx.assertTrue(CropPests.peckable(world, crop), "a young crop");
		ctx.setBlockState(new BlockPos(1, 1, 1), ((CropBlock) Blocks.WHEAT).withAge(4));
		ctx.assertFalse(CropPests.peckable(world, crop), "not half grown or more");
		ctx.assertFalse(CropPests.guarded(world, crop), "no scarecrow yet");
		ArmorStandEntity stand = ctx.spawnEntity(EntityType.ARMOR_STAND, new BlockPos(6, 1, 6));
		ctx.assertFalse(CropPests.guarded(world, crop), "a bare armor stand is no scarecrow");
		stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		ctx.assertTrue(CropPests.guarded(world, crop), "one in a carved pumpkin is");
		stand.discard();
		ctx.complete();
	}

	/**
	 * Left to itself, a songbird flies to a seed on farmland and eats it. (Time for a second go: now and then it takes off
	 * with its flock on a whim, and leaves crops alone for a minute or so after.)
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "crop_pests", tickLimit = 2400)
	public void songbirdsEatSeeds(TestContext ctx) {
		BirdEntity bird = field(ctx, 0);
		boolean[] pecked = {false};
		ctx.runAtEveryTick(() -> {
			pecked[0] |= bird.isPecking();
			if (ctx.getBlockState(CROP).isAir() && !ctx.getBlockState(CROP.down()).isAir()) {
				ctx.assertTrue(pecked[0], "it pecked at it first");
				bird.discard();
				ctx.complete();
			}
		});
	}

	/** A tamed wolf (its owner about: a pet whose owner is away stays put) runs at a bird pecking at crops and it flies off, the crop saved. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "crop_pests_wolf", tickLimit = 2400)
	public void wolvesChaseBirdsOff(TestContext ctx) {
		ServerPlayerEntity owner = FishingTests.survivalPlayer(ctx, new BlockPos(7, 1, 7));
		// (in creative, so the bird isn't wary of them)
		owner.changeGameMode(GameMode.CREATIVE);
		chasedOff(ctx, () -> {
			WolfEntity wolf = ctx.spawnEntity(EntityType.WOLF, new BlockPos(6, 1, 4));
			wolf.setOwner(owner);
			return wolf;
		}, () -> FishingTests.disconnect(owner));
	}

	/** A villager (by day) walks over to a bird pecking at crops and it flies off, the crop saved. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "crop_pests_villager", tickLimit = 2400)
	public void villagersChaseBirdsOff(TestContext ctx) {
		ctx.getWorld().setTimeOfDay(1000);
		chasedOff(ctx, () -> ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(6, 1, 6)), () -> {
		});
	}

	/**
	 * Once the bird is at the crop pecking, the chaser comes; then the bird must leave before it eats (a young crop: set
	 * back a stage). (The tests allow time for a second go: see songbirdsEatSeeds.)
	 */
	private static void chasedOff(TestContext ctx, Supplier<Entity> chaser, Runnable cleanUp) {
		BirdEntity bird = field(ctx, 3);
		Entity[] chasing = {null};
		ctx.runAtEveryTick(() -> {
			if (chasing[0] == null) {
				if (bird.isPecking()) {
					chasing[0] = chaser.get();
				}
				return;
			}
			if (!bird.isPecking() && bird.isFlocking()) {
				ctx.assertTrue(((CropBlock) Blocks.WHEAT).getAge(ctx.getBlockState(CROP)) >= 2, "chased off before it did much harm");
				ctx.assertTrue(chasing[0] instanceof VillagerEntity || chasing[0].squaredDistanceTo(bird) < 6.0 * 6.0, "by the chaser");
				chasing[0].discard();
				bird.discard();
				cleanUp.run();
				ctx.complete();
			}
		});
	}
}
