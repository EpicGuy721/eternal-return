package com.eternalreturn.gametest;

import com.eternalreturn.fishing.Overfishing;
import com.eternalreturn.mobs.ModEntities;
import com.eternalreturn.mobs.entity.BearEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.PointOfInterestTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;

import java.util.List;

/** Bears' habits: raiding hives, stripping berry bushes, fishing rivers; honey breeding and trust. */
public class BearHabitTests implements FabricGameTest {
	private static BearEntity bear(TestContext ctx, BlockPos pos) {
		BearEntity bear = ctx.spawnEntity(ModEntities.BROWN_BEAR, pos);
		bear.setAiDisabled(true);
		return bear;
	}

	/** A bear finds a beehive near, breaks it open (honeycomb spilling) and the bees go after it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bears")
	public void bearsRaidHives(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.setBlockState(new BlockPos(6, 1, 6), Blocks.BEEHIVE.getDefaultState().with(BeehiveBlock.HONEY_LEVEL, 5));
		BlockPos hive = ctx.getAbsolutePos(new BlockPos(6, 1, 6));
		BearEntity bear = bear(ctx, new BlockPos(1, 1, 1));
		BeeEntity bee = ctx.spawnEntity(EntityType.BEE, new BlockPos(6, 3, 4));
		ctx.runAtTick(2, () -> {
			ctx.assertTrue(hive.equals(bear.findHive()), "the bear finds the hive: " + bear.findHive());
			bear.raid(hive);
			ctx.expectBlock(Blocks.AIR, new BlockPos(6, 1, 6));
			List<ItemEntity> combs = world.getEntitiesByClass(ItemEntity.class, new Box(hive).expand(2.0), item -> item.getStack().isOf(Items.HONEYCOMB));
			ctx.assertTrue(!combs.isEmpty(), "honeycomb spills out");
			ctx.assertTrue(bear.getUuid().equals(bee.getAngryAt()) && bee.getTarget() == bear, "the bees go after the bear");
			bee.discard();
			bear.discard();
			ctx.complete();
		});
	}

	/** A bear strips a ripe sweet berry bush back to bare; an unripe one it leaves. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bears")
	public void bearsStripBerryBushes(TestContext ctx) {
		ctx.setBlockState(new BlockPos(1, 0, 1), Blocks.GRASS_BLOCK);
		ctx.setBlockState(new BlockPos(3, 0, 1), Blocks.GRASS_BLOCK);
		ctx.setBlockState(new BlockPos(1, 1, 1), Blocks.SWEET_BERRY_BUSH.getDefaultState().with(SweetBerryBushBlock.AGE, 3));
		ctx.setBlockState(new BlockPos(3, 1, 1), Blocks.SWEET_BERRY_BUSH.getDefaultState().with(SweetBerryBushBlock.AGE, 1));
		BearEntity bear = bear(ctx, new BlockPos(2, 1, 3));
		bear.eatBerries(ctx.getAbsolutePos(new BlockPos(1, 1, 1)));
		bear.eatBerries(ctx.getAbsolutePos(new BlockPos(3, 1, 1)));
		ctx.assertTrue(ctx.getBlockState(new BlockPos(1, 1, 1)).get(SweetBerryBushBlock.AGE) == 0, "the ripe bush stripped to bare");
		ctx.assertTrue(ctx.getBlockState(new BlockPos(3, 1, 1)).get(SweetBerryBushBlock.AGE) == 1, "the unripe one left");
		bear.discard();
		ctx.complete();
	}

	/** In a river, a bear wades in by the bank and swipes fish out onto it, overfishing the spot; not outside rivers. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bears", tickLimit = 60)
	public void bearsFishRivers(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 0, 0), new BlockPos(7, 0, 7))) {
			ctx.setBlockState(pos, Blocks.STONE);
		}
		for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 1, 0), new BlockPos(7, 1, 3))) {
			ctx.setBlockState(pos, Blocks.WATER);
		}
		BlockPos water = ctx.getAbsolutePos(new BlockPos(3, 1, 2));
		BearEntity bear = bear(ctx, new BlockPos(3, 1, 6));
		ctx.assertTrue(bear.bankNear(water) == null, "no fishing outside a river");
		fillBiome(ctx, "minecraft:river");
		BlockPos bank = bear.bankNear(water);
		ctx.assertTrue(bank != null && bank.getZ() > water.getZ(), "a bank by the water: " + bank);
		Overfishing.clear(world);
		int caught = 0;
		for (int i = 0; i < 60; i++) {
			if (bear.swipe(water, bank)) {
				caught++;
			}
		}
		Vec3d spot = Vec3d.ofCenter(water);
		ctx.assertTrue(caught > 0 && Overfishing.depletion(world, spot) >= caught - 0.01, "fish caught, and the spot overfished: " + caught);
		ctx.assertTrue(caught < 40, "fewer as the spot runs out: " + caught);
		int[] count = {caught};
		ctx.runAtTick(40, () -> {
			List<ItemEntity> fish = world.getEntitiesByClass(ItemEntity.class, new Box(water).expand(8.0), item -> item.getStack().isIn(ItemTags.FISHES));
			ctx.assertTrue(fish.stream().mapToInt(item -> item.getStack().getCount()).sum() >= count[0], "the fish are out: " + fish.size());
			ctx.assertTrue(fish.stream().anyMatch(item -> item.getZ() > water.getZ() + 0.5), "on the bank side");
			Overfishing.clear(world);
			bear.discard();
			ctx.complete();
		});
	}

	/**
	 * Two bears fed honey bottles (the bottles given back) breed, and their cub trusts the player who fed them: it won't
	 * target them. A wild bear trusts no one.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bears")
	public void honeyBredCubsTrustTheBreeder(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 5));
		try {
			BearEntity mother = bear(ctx, new BlockPos(1, 1, 1));
			BearEntity father = bear(ctx, new BlockPos(4, 1, 1));
			for (BearEntity parent : new BearEntity[] {mother, father}) {
				player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.HONEY_BOTTLE));
				parent.interactMob(player, Hand.MAIN_HAND);
				ctx.assertTrue(parent.isInLove() && player.getMainHandStack().isOf(Items.GLASS_BOTTLE), "fed honey, the bottle back");
			}
			mother.breed(world, father);
			List<BearEntity> cubs = world.getEntitiesByClass(BearEntity.class, mother.getBoundingBox().expand(6.0), BearEntity::isBaby);
			ctx.assertTrue(cubs.size() == 1, "a cub: " + cubs.size());
			BearEntity cub = cubs.get(0);
			ctx.assertTrue(cub.trusts(player) && !mother.trusts(player), "the cub trusts its breeder; its mother doesn't");
			cub.setTarget(player);
			ctx.assertTrue(cub.getTarget() == null && !cub.shouldAngerAt(player), "it won't turn on them");
			mother.setTarget(player);
			ctx.assertTrue(mother.getTarget() == player, "a wild bear will");
			cub.discard();
			mother.discard();
			father.discard();
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	private static void fillBiome(TestContext ctx, String biome) {
		MinecraftServer server = ctx.getWorld().getServer();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(-4, -4, -4));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(11, 20, 11));
		server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(),
				"fillbiome " + a.getX() + " " + a.getY() + " " + a.getZ() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + biome);
	}

	/** Left to itself, a bear walks to a hive near and breaks it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bear_hive", tickLimit = 800)
	public void bearsGoForHives(TestContext ctx) {
		for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 0, 0), new BlockPos(7, 0, 7))) {
			ctx.setBlockState(pos, Blocks.STONE);
		}
		ctx.setBlockState(new BlockPos(6, 1, 6), Blocks.BEE_NEST.getDefaultState().with(BeehiveBlock.HONEY_LEVEL, 2));
		BearEntity bear = ctx.spawnEntity(ModEntities.BROWN_BEAR, new BlockPos(1, 1, 1));
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			if (!done[0] && ctx.getBlockState(new BlockPos(6, 1, 6)).isAir()) {
				done[0] = true;
				bear.discard();
				ctx.complete();
			}
		});
	}

	/** Left to itself by day, a bear by a river wades in and swipes a fish out onto dry land. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "bear_fishing", tickLimit = 2400)
	public void bearsGoFishing(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(1000);
		fillBiome(ctx, "minecraft:river");
		for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 0, 0), new BlockPos(7, 0, 7))) {
			ctx.setBlockState(pos, Blocks.STONE);
		}
		for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 1, 0), new BlockPos(7, 1, 7))) {
			ctx.setBlockState(pos, pos.getZ() <= 3 ? Blocks.WATER : Blocks.STONE);
		}
		// (no hive left near by other tests to draw the bear off)
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
		world.getPointOfInterestStorage().getInSquare(type -> type.isIn(PointOfInterestTypeTags.BEE_HOME), origin, 40, PointOfInterestStorage.OccupationStatus.ANY)
				.map(PointOfInterest::getPos).toList().forEach(hive -> world.setBlockState(hive, Blocks.AIR.getDefaultState()));
		Overfishing.clear(world);
		BearEntity bear = ctx.spawnEntity(ModEntities.BROWN_BEAR, new BlockPos(4, 2, 6));
		Box around = new Box(ctx.getAbsolutePos(new BlockPos(-4, 1, -4))).union(new Box(ctx.getAbsolutePos(new BlockPos(11, 5, 11))));
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			if (!done[0] && !world.getEntitiesByClass(ItemEntity.class, around, item -> item.getStack().isIn(ItemTags.FISHES) && item.isOnGround() && !item.isTouchingWater()).isEmpty()) {
				done[0] = true;
				Overfishing.clear(world);
				bear.discard();
				ctx.complete();
			}
		});
	}
}
