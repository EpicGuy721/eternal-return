package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.TroughBlock;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.survival.AnimalHunger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerProfession;

import java.util.ArrayList;
import java.util.List;

/** Troughs: crafting and filling them, animals eating and drinking from them, thirst, pecking, farmers refilling them. */
public class TroughTests implements FabricGameTest {
	private static Block trough(String wood) {
		return Registries.BLOCK.get(EternalReturn.id(wood + "_trough"));
	}

	private static BlockState level(String contents, int level) {
		BlockState state = trough("oak").getDefaultState();
		for (TroughBlock.Contents c : TroughBlock.Contents.values()) {
			if (c.asString().equals(contents)) {
				state = state.with(TroughBlock.CONTENTS, c);
			}
		}
		return state.with(TroughBlock.LEVEL, level);
	}

	private static void floor(TestContext ctx) {
		for (BlockPos pos : BlockPos.iterate(new BlockPos(0, 0, 0), new BlockPos(7, 0, 7))) {
			ctx.setBlockState(pos, Blocks.STONE);
		}
	}

	/** Five slabs in a U make a trough of their wood; a water bucket, hay, carrots and seeds fill it, one kind at a time. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "troughs")
	public void craftingAndFillingTroughs(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		List<ItemStack> grid = new ArrayList<>(List.of(new ItemStack(Items.SPRUCE_SLAB), ItemStack.EMPTY, new ItemStack(Items.SPRUCE_SLAB)));
		grid.addAll(List.of(new ItemStack(Items.SPRUCE_SLAB), new ItemStack(Items.SPRUCE_SLAB), new ItemStack(Items.SPRUCE_SLAB)));
		CraftingRecipeInput input = CraftingRecipeInput.create(3, 2, grid);
		ItemStack made = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world).map(r -> r.value().craft(input, world.getRegistryManager()))
				.orElse(ItemStack.EMPTY);
		ctx.assertTrue(made.isOf(trough("spruce").asItem()), "five spruce slabs in a U: a spruce trough: " + made);

		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(3, 1, 3));
		try {
			BlockPos rel = new BlockPos(1, 1, 1);
			BlockPos pos = ctx.getAbsolutePos(rel);
			ctx.setBlockState(rel, trough("oak").getDefaultState());
			BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
			ctx.getBlockState(rel).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit);
			ctx.assertTrue(TroughBlock.holds(ctx.getBlockState(rel), TroughBlock.Contents.WATER) && ctx.getBlockState(rel).get(TroughBlock.LEVEL) == 4
					&& player.getMainHandStack().isOf(Items.BUCKET), "a water bucket fills it: " + ctx.getBlockState(rel));
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CARROT, 4));
			ctx.getBlockState(rel).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit);
			ctx.assertTrue(player.getMainHandStack().getCount() == 4, "no carrots in with the water");
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BUCKET));
			ctx.getBlockState(rel).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit);
			ctx.assertTrue(ctx.getBlockState(rel).get(TroughBlock.LEVEL) == 0 && player.getMainHandStack().isOf(Items.WATER_BUCKET), "a bucket takes it back");
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CARROT, 4));
			for (int i = 0; i < 2; i++) {
				ctx.getBlockState(rel).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit);
			}
			ctx.assertTrue(TroughBlock.holds(ctx.getBlockState(rel), TroughBlock.Contents.CARROTS) && ctx.getBlockState(rel).get(TroughBlock.LEVEL) == 2
					&& player.getMainHandStack().getCount() == 2, "a level a carrot: " + ctx.getBlockState(rel));
			ctx.setBlockState(rel, trough("oak").getDefaultState());
			player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.HAY_BLOCK));
			ctx.getBlockState(rel).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit);
			ctx.assertTrue(TroughBlock.holds(ctx.getBlockState(rel), TroughBlock.Contents.HAY) && ctx.getBlockState(rel).get(TroughBlock.LEVEL) == 4,
					"a hay bale fills it");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/** A hungry cow goes to a trough of hay and eats, a thirsty pig to one of water and drinks; each takes a level. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "trough_feeding", tickLimit = 800)
	public void animalsEatAndDrinkFromTroughs(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setWeather(6000, 0, false, false);
		floor(ctx);
		ctx.setBlockState(new BlockPos(6, 1, 1), level("hay", 2));
		ctx.setBlockState(new BlockPos(6, 1, 6), level("water", 2));
		CowEntity cow = ctx.spawnEntity(EntityType.COW, new BlockPos(1, 1, 1));
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 6));
		AnimalHunger.tick(cow);
		AnimalHunger.tick(pig);
		long now = world.getTimeOfDay();
		((AnimalHunger.Holder) cow).eternalreturn$setLastFed(now - AnimalHunger.DAY);
		((AnimalHunger.Holder) pig).eternalreturn$setLastWatered(now - 2 * AnimalHunger.DAY);
		boolean[] done = {false, false};
		ctx.runAtEveryTick(() -> {
			done[0] |= ctx.getBlockState(new BlockPos(6, 1, 1)).get(TroughBlock.LEVEL) == 1 && !AnimalHunger.isHungry(cow);
			done[1] |= ctx.getBlockState(new BlockPos(6, 1, 6)).get(TroughBlock.LEVEL) == 1 && !AnimalHunger.isThirsty(pig);
			if (done[0] && done[1]) {
				cow.discard();
				pig.discard();
				ctx.complete();
			}
		});
	}

	/**
	 * Thirst: an animal long without water loses health; one next to water drinks. Pecking: a hungry chicken on grass
	 * eats and the grass stays grass; a pig on stone finds nothing.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "animal_thirst")
	public void thirstAndPecking(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setWeather(6000, 0, false, false);
		EternalReturnConfig cfg = EternalReturnConfig.get();
		double grazeChance = cfg.survival.grazeChance;
		floor(ctx);
		ctx.setBlockState(new BlockPos(5, 0, 1), Blocks.GRASS_BLOCK);
		ctx.setBlockState(new BlockPos(6, 1, 5), Blocks.WATER);
		CowEntity parched = ctx.spawnEntity(EntityType.COW, new BlockPos(1, 1, 1));
		CowEntity drinker = ctx.spawnEntity(EntityType.COW, new BlockPos(5, 1, 5));
		ChickenEntity chicken = ctx.spawnEntity(EntityType.CHICKEN, new BlockPos(5, 1, 1));
		PigEntity pig = ctx.spawnEntity(EntityType.PIG, new BlockPos(1, 1, 5));
		try {
			cfg.survival.grazeChance = 1.0;
			long now = world.getTimeOfDay();
			for (var animal : List.of(parched, drinker, chicken, pig)) {
				animal.setAiDisabled(true);
				AnimalHunger.tick(animal);
			}
			((AnimalHunger.Holder) parched).eternalreturn$setLastWatered(now - (long) (3.5 * AnimalHunger.DAY));
			parched.age = 600;
			float before = parched.getHealth();
			AnimalHunger.tick(parched);
			ctx.assertTrue(parched.getHealth() == before - 1.0F, "a cow 3 days without water loses health: " + before + " -> " + parched.getHealth());

			((AnimalHunger.Holder) drinker).eternalreturn$setLastWatered(now - 2 * AnimalHunger.DAY);
			drinker.age = 20;
			AnimalHunger.tick(drinker);
			ctx.assertTrue(!AnimalHunger.isThirsty(drinker), "a thirsty cow next to water drinks");

			((AnimalHunger.Holder) chicken).eternalreturn$setLastFed(now - AnimalHunger.DAY);
			chicken.age = 20;
			AnimalHunger.tick(chicken);
			ctx.assertTrue(!AnimalHunger.isHungry(chicken) && ctx.getBlockState(new BlockPos(5, 0, 1)).isOf(Blocks.GRASS_BLOCK),
					"a hungry chicken pecks the grass for seeds, leaving it grass");
			((AnimalHunger.Holder) pig).eternalreturn$setLastFed(now - AnimalHunger.DAY);
			pig.age = 20;
			AnimalHunger.tick(pig);
			ctx.assertTrue(AnimalHunger.isHungry(pig), "a pig on stone finds nothing to peck");
			ctx.assertFalse(AnimalHunger.hungers(ctx.spawnEntity(EntityType.GOAT, new BlockPos(3, 1, 3))), "goats aren't farm animals that hunger");
		} finally {
			cfg.survival.grazeChance = grazeChance;
			ctx.killAllEntities();
		}
		ctx.complete();
	}

	/** A farmer tops up a carrot trough near from the carrots in its pockets. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "trough_farmer", tickLimit = 1000)
	public void farmersRefillTroughs(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		world.setTimeOfDay(1000);
		floor(ctx);
		ctx.setBlockState(new BlockPos(6, 1, 6), level("carrots", 1));
		VillagerEntity farmer = ctx.spawnEntity(EntityType.VILLAGER, new BlockPos(1, 1, 1));
		farmer.setVillagerData(farmer.getVillagerData().withProfession(VillagerProfession.FARMER));
		farmer.setExperience(1);
		farmer.getInventory().addStack(new ItemStack(Items.CARROT, 5));
		ctx.runAtEveryTick(() -> {
			if (ctx.getBlockState(new BlockPos(6, 1, 6)).get(TroughBlock.LEVEL) == 4) {
				ctx.assertTrue(farmer.getInventory().count(Items.CARROT) == 2, "three carrots from its pockets: " + farmer.getInventory().count(Items.CARROT));
				farmer.discard();
				ctx.complete();
			}
		});
	}
}
