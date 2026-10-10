package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.duck.NeedyVillager;
import com.eternalreturn.villager.VillagerNeeds;
import com.eternalreturn.villager.VillagerNeeds.Need;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.brain.LivingTargetCache;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.SleepTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Villagers' needs (VillagerNeeds): each need is met the way it should be and not otherwise, prices
 * rise per missed need on emerald trades only, refusals and shop hours keep the trade screen shut,
 * privacy for sleep, healing with food, and the needs survive saving. All tests share one batch, which
 * sets the day clock itself, so nothing else runs while the time is changed.
 */
public class VillagerNeedsTests implements FabricGameTest {
	private static final String BATCH = "villager_needs";

	private static VillagerEntity villager(TestContext ctx, BlockPos rel) {
		ServerWorld world = ctx.getWorld();
		VillagerEntity villager = EntityType.VILLAGER.create(world);
		Vec3d at = Vec3d.ofBottomCenter(ctx.getAbsolutePos(rel));
		villager.refreshPositionAndAngles(at.x, at.y, at.z, 0.0F, 0.0F);
		villager.initialize(world, world.getLocalDifficulty(villager.getBlockPos()), SpawnReason.COMMAND, null);
		villager.setVillagerData(new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 1));
		villager.setAiDisabled(true);
		world.spawnEntity(villager);
		VillagerNeeds.tick(villager);
		return villager;
	}

	private static void missFor(VillagerEntity villager, Need need, double days) {
		((NeedyVillager) villager).eternalreturn$setLastMet(need, VillagerNeeds.now(villager.getWorld()) - (long) (days * VillagerNeeds.DAY));
	}

	private static long lastMet(VillagerEntity villager, Need need) {
		return ((NeedyVillager) villager).eternalreturn$lastMet(need);
	}

	private static void floor(TestContext ctx, int size) {
		for (int x = -1; x <= size; x++) {
			for (int z = -1; z <= size; z++) {
				ctx.setBlockState(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** Emeralds for a book (an emerald trade) and wheat for an emerald (not one). */
	private static TradeOfferList offers() {
		TradeOfferList offers = new TradeOfferList();
		offers.add(new TradeOffer(new TradedItem(Items.EMERALD, 5), new ItemStack(Items.BOOK), 12, 1, 0.05F));
		offers.add(new TradeOffer(new TradedItem(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 12, 1, 0.05F));
		return offers;
	}

	/**
	 * A new villager starts with every need met. Two needs missed for 2.5 days add 10 emeralds to the book
	 * but not to the wheat; the screen opens. One missed for 7.5 days: it shakes its head and the screen
	 * stays shut. At night it is shut too, needs or not.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100)
	public void pricesRefusalsAndShopHours(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		floor(ctx, 4);
		VillagerEntity villager = villager(ctx, new BlockPos(2, 1, 2));
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(2, 1, 0));
		try {
			for (Need need : Need.values()) {
				ctx.assertTrue(VillagerNeeds.daysMissed(villager, need) == 0.0, "a new villager should start with " + need + " met");
			}
			villager.setOffers(offers());
			villager.interactMob(player, Hand.MAIN_HAND);
			ctx.assertTrue(villager.getCustomer() == player, "a content villager trades");
			ctx.assertTrue(villager.getOffers().get(0).getDisplayedFirstBuyItem().getCount() == 5, "no price rise yet");
			player.closeHandledScreen();

			missFor(villager, Need.SUNLIGHT, 2.5);
			missFor(villager, Need.GOSSIP, 2.5);
			missFor(villager, Need.EXERCISE, 1.5);
			ctx.assertTrue(VillagerNeeds.priceRise(villager) == 10, "two needs missed for 2.5 days: +10, got " + VillagerNeeds.priceRise(villager));
			ctx.assertTrue(VillagerNeeds.lacking(villager).equals(List.of(Need.GOSSIP, Need.SUNLIGHT, Need.EXERCISE)), "lacking " + VillagerNeeds.lacking(villager));
			villager.interactMob(player, Hand.MAIN_HAND);
			ctx.assertTrue(villager.getCustomer() == player, "an unhappy villager still trades");
			int book = villager.getOffers().get(0).getDisplayedFirstBuyItem().getCount();
			int wheat = villager.getOffers().get(1).getDisplayedFirstBuyItem().getCount();
			ctx.assertTrue(book == 15 && wheat == 20, "the book should cost 15 emeralds and the wheat trade stay at 20, got " + book + " and " + wheat);
			player.closeHandledScreen();

			missFor(villager, Need.WORK_LIFE, 7.5);
			ctx.assertTrue(VillagerNeeds.refuses(villager), "a need missed for 7.5 days means refusal");
			villager.interactMob(player, Hand.MAIN_HAND);
			ctx.assertTrue(villager.getCustomer() == null && villager.getHeadRollingTimeLeft() > 0, "a refusing villager shakes its head and doesn't trade");

			for (Need need : Need.values()) {
				missFor(villager, need, 0.0);
			}
			world.setTimeOfDay(18000);
			ctx.assertTrue(VillagerNeeds.isNight(world), "18000 is night");
			villager.interactMob(player, Hand.MAIN_HAND);
			ctx.assertTrue(villager.getCustomer() == null, "shops are closed at night");
		} finally {
			world.setTimeOfDay(6000);
			villager.discard();
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/**
	 * Exercise: a walk of 9 blocks counts, pacing about in a 3-block pen doesn't. Sunlight: under the open
	 * sky by day it counts, under a roof it doesn't. Work-life balance: a workstation 5 blocks from the bed
	 * breaks it, 10 blocks away is fine. Gossip counts when two villagers really share gossip.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 600)
	public void howEachNeedIsMet(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		floor(ctx, 12);
		VillagerEntity walker = villager(ctx, new BlockPos(0, 1, 2));
		VillagerEntity pacer = villager(ctx, new BlockPos(0, 1, 6));
		VillagerEntity roofed = villager(ctx, new BlockPos(10, 1, 10));
		for (int x = 9; x <= 11; x++) {
			for (int z = 9; z <= 11; z++) {
				ctx.setBlockState(x, 4, z, Blocks.STONE);
			}
		}
		try {
			for (VillagerEntity villager : List.of(walker, pacer, roofed)) {
				for (Need need : Need.values()) {
					missFor(villager, need, 3.0);
				}
			}
			Vec3d start = walker.getPos();
			for (int step = 0; step <= 90; step++) {
				walker.setPosition(start.add(step * 0.1, 0.0, 0.0));
				walker.setVelocity(0.1, 0.0, 0.0);
				VillagerNeeds.tick(walker);
				pacer.setPosition(pacer.getPos().withAxis(net.minecraft.util.math.Direction.Axis.X, start.x + (step % 30 < 15 ? step % 15 : 15 - step % 15) * 0.2));
				pacer.setVelocity(0.2, 0.0, 0.0);
				VillagerNeeds.tick(pacer);
			}
			ctx.assertTrue(VillagerNeeds.daysMissed(walker, Need.EXERCISE) == 0.0, "a 9-block walk is exercise");
			ctx.assertTrue(VillagerNeeds.daysMissed(pacer, Need.EXERCISE) >= 3.0, "pacing 3 blocks back and forth isn't");

			BlockPos bed = ctx.getAbsolutePos(new BlockPos(0, 1, 0));
			walker.getBrain().remember(MemoryModuleType.HOME, GlobalPos.create(world.getRegistryKey(), bed));
			walker.getBrain().remember(MemoryModuleType.JOB_SITE, GlobalPos.create(world.getRegistryKey(), bed.east(5)));
			missFor(walker, Need.WORK_LIFE, 3.0);
			VillagerNeeds.tick(walker);
			ctx.assertTrue(VillagerNeeds.daysMissed(walker, Need.WORK_LIFE) >= 3.0, "a workstation 5 blocks from the bed breaks work-life balance");
			walker.getBrain().remember(MemoryModuleType.JOB_SITE, GlobalPos.create(world.getRegistryKey(), bed.east(10)));
			VillagerNeeds.tick(walker);
			ctx.assertTrue(VillagerNeeds.daysMissed(walker, Need.WORK_LIFE) == 0.0, "10 blocks apart is fine");

			walker.talkWithVillager(world, pacer, world.getTime() + 100_000L);
			ctx.assertTrue(VillagerNeeds.daysMissed(walker, Need.GOSSIP) == 0.0 && VillagerNeeds.daysMissed(pacer, Need.GOSSIP) == 0.0, "both villagers gossiped");
		} catch (RuntimeException e) {
			walker.discard();
			pacer.discard();
			roofed.discard();
			throw e;
		}
		// Sunlight once the roof's shadow is in the light data (a few ticks, longer when the server is busy).
		boolean[] done = {false};
		ctx.runAtEveryTick(() -> {
			if (done[0] || world.isSkyVisible(BlockPos.ofFloored(roofed.getEyePos()))) {
				return;
			}
			done[0] = true;
			missFor(walker, Need.SUNLIGHT, 3.0);
			walker.age = 20;
			roofed.age = 20;
			VillagerNeeds.tick(walker);
			VillagerNeeds.tick(roofed);
			boolean open = VillagerNeeds.daysMissed(walker, Need.SUNLIGHT) == 0.0;
			boolean shaded = VillagerNeeds.daysMissed(roofed, Need.SUNLIGHT) >= 3.0;
			walker.discard();
			pacer.discard();
			roofed.discard();
			ctx.assertTrue(open, "under the open sky by day: sunlight");
			ctx.assertTrue(shaded, "under a roof: no sunlight");
			ctx.complete();
		});
	}

	/**
	 * Sleep: a villager next to its bed lies down with one other adult in sight, not with two (babies don't
	 * count), and a wall between them restores its privacy. A sleeping villager has slept.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100)
	public void sleepNeedsPrivacy(TestContext ctx) throws ReflectiveOperationException {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		floor(ctx, 8);
		ctx.setBlockState(1, 1, 1, Blocks.RED_BED);
		VillagerEntity sleeper = villager(ctx, new BlockPos(2, 1, 1));
		VillagerEntity first = villager(ctx, new BlockPos(2, 1, 4));
		VillagerEntity second = villager(ctx, new BlockPos(4, 1, 4));
		VillagerEntity baby = villager(ctx, new BlockPos(3, 1, 4));
		baby.setBreedingAge(-24000);
		sleeper.getBrain().remember(MemoryModuleType.HOME, GlobalPos.create(world.getRegistryKey(), ctx.getAbsolutePos(new BlockPos(1, 1, 1))));
		Method shouldRun = SleepTask.class.getDeclaredMethod("shouldRun", ServerWorld.class, LivingEntity.class);
		shouldRun.setAccessible(true);
		SleepTask task = new SleepTask();
		try {
			sleeper.getBrain().remember(MemoryModuleType.VISIBLE_MOBS, new LivingTargetCache(sleeper, List.of(first, baby)));
			ctx.assertTrue((boolean) shouldRun.invoke(task, world, sleeper), "one other adult (and a baby) in sight: it may sleep");
			sleeper.getBrain().remember(MemoryModuleType.VISIBLE_MOBS, new LivingTargetCache(sleeper, List.of(first, second, baby)));
			ctx.assertTrue(!(boolean) shouldRun.invoke(task, world, sleeper), "two other adults in sight: no privacy, no sleep");
			for (int x = 0; x <= 6; x++) {
				for (int y = 1; y <= 3; y++) {
					ctx.setBlockState(x, y, 3, Blocks.STONE);
				}
			}
			// Its line-of-sight cache is cleared by its AI each tick; these villagers have no AI running.
			sleeper.getVisibilityCache().clear();
			sleeper.getBrain().remember(MemoryModuleType.VISIBLE_MOBS, new LivingTargetCache(sleeper, List.of(first, second, baby)));
			ctx.assertTrue((boolean) shouldRun.invoke(task, world, sleeper), "with a wall between them it has its privacy");

			missFor(sleeper, Need.SLEEP, 3.0);
			sleeper.sleep(ctx.getAbsolutePos(new BlockPos(1, 1, 1)));
			VillagerNeeds.tick(sleeper);
			ctx.assertTrue(VillagerNeeds.daysMissed(sleeper, Need.SLEEP) == 0.0, "a sleeping villager has slept");
		} finally {
			sleeper.discard();
			first.discard();
			second.discard();
			baby.discard();
		}
		ctx.complete();
	}

	/**
	 * A hurt villager with bread eats one and heals by its food value (5); one at full health keeps its
	 * food. The needs survive saving and loading; a villager saved before this mod starts fresh.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100)
	public void healingAndSaving(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(6000);
		floor(ctx, 4);
		EternalReturnConfig cfg = EternalReturnConfig.get();
		double savedChance = cfg.villagers.needs.eatChance;
		VillagerEntity hurt = villager(ctx, new BlockPos(1, 1, 1));
		VillagerEntity healthy = villager(ctx, new BlockPos(3, 1, 3));
		try {
			cfg.villagers.needs.eatChance = 1.0;
			hurt.setHealth(10.0F);
			hurt.getInventory().addStack(new ItemStack(Items.BREAD, 2));
			healthy.getInventory().addStack(new ItemStack(Items.BREAD, 2));
			hurt.age = 40;
			healthy.age = 40;
			VillagerNeeds.tick(hurt);
			VillagerNeeds.tick(healthy);
			ctx.assertTrue(hurt.getHealth() == 15.0F && hurt.getInventory().count(Items.BREAD) == 1, "a hurt villager eats a bread and heals 5: health "
					+ hurt.getHealth() + ", bread left " + hurt.getInventory().count(Items.BREAD));
			ctx.assertTrue(healthy.getInventory().count(Items.BREAD) == 2, "a healthy villager keeps its bread");

			missFor(hurt, Need.GOSSIP, 4.0);
			long gossip = lastMet(hurt, Need.GOSSIP);
			NbtCompound saved = new NbtCompound();
			hurt.writeNbt(saved);
			VillagerEntity loaded = EntityType.VILLAGER.create(world);
			loaded.readNbt(saved);
			ctx.assertTrue(lastMet(loaded, Need.GOSSIP) == gossip, "gossip time survives saving: " + lastMet(loaded, Need.GOSSIP) + " vs " + gossip);

			saved.remove("EternalReturnNeeds");
			VillagerEntity old = EntityType.VILLAGER.create(world);
			old.readNbt(saved);
			ctx.assertTrue(lastMet(old, Need.GOSSIP) == Long.MIN_VALUE, "a villager from before this mod has no times yet");
			VillagerNeeds.tick(old);
			for (Need need : Need.values()) {
				ctx.assertTrue(VillagerNeeds.daysMissed(old, need) == 0.0, "an old villager starts with " + need + " met");
			}
		} finally {
			cfg.villagers.needs.eatChance = savedChance;
			hurt.discard();
			healthy.discard();
		}
		ctx.complete();
	}
}
