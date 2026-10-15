package com.eternalreturn.gametest;

import com.eternalreturn.fishing.FishingItems;
import com.eternalreturn.survival.Meals;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CakeBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;

/**
 * No hunger, and food that heals (Meals): the food bar stays full and health doesn't come back by itself; each
 * food heals its own amount at its own pace, with its own stack size, eating time and effects; ordinary food only
 * when hurt; Hunger halves food healing, Saturation heals; cake slices heal; the mod's fish heal like raw salmon.
 */
public class FoodTests implements FabricGameTest {
	private static final String BATCH = "food";

	private static void within(TestContext ctx, float actual, float expected, String what) {
		ctx.assertTrue(Math.abs(actual - expected) < 0.6F, what + ": " + actual + ", expected about " + expected);
	}

	/** The table's values are on the items: stack sizes, eating times, effects. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void foodTableOnTheItems(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		try {
			ctx.assertTrue(new ItemStack(Items.COOKED_BEEF).getMaxCount() == 16 && new ItemStack(Items.COOKED_BEEF).getMaxUseTime(player) == 48,
					"steak: 16 a stack, 2.4 seconds to eat");
			ctx.assertTrue(new ItemStack(Items.PUMPKIN_PIE).getMaxCount() == 8 && new ItemStack(Items.DRIED_KELP).getMaxUseTime(player) == 8,
					"pie: 8 a stack; dried kelp: 0.4 seconds");
			ctx.assertTrue(new ItemStack(Items.HONEY_BOTTLE).getMaxUseTime(player) == 40, "honey: 2 seconds");
			FoodComponent steak = new ItemStack(Items.COOKED_BEEF).get(DataComponentTypes.FOOD);
			ctx.assertTrue(steak.canAlwaysEat() && steak.effects().stream().anyMatch(e -> e.effect().getEffectType() == StatusEffects.STRENGTH),
					"steak gives Strength and can always be eaten");
			FoodComponent notch = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).get(DataComponentTypes.FOOD);
			ctx.assertTrue(notch.effects().stream().anyMatch(e -> e.effect().getEffectType() == StatusEffects.REGENERATION), "the enchanted golden apple keeps its own effects");
			ItemStack fish = new ItemStack(FishingItems.FISH.get(0));
			ctx.assertTrue(fish.getMaxCount() == 64 && Meals.heal(fish).equals(Meals.heal(new ItemStack(Items.SALMON))), "the mod's fish heal like raw salmon");
		} finally {
			FishingTests.disconnect(player);
		}
		ctx.complete();
	}

	/**
	 * On Normal: health never comes back by itself and the food bar stays full; ordinary food only when hurt; a melon
	 * slice heals at once, steak spread over 10 seconds; Hunger halves it; Saturation heals; the healing still to come
	 * is saved with the player.
	 */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 400)
	public void foodHealsAndNothingElse(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Difficulty difficulty = world.getDifficulty();
		world.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		// The scripted player has no network connection the server ticks, so tick it as a real player is ticked.
		ctx.runAtEveryTick(player::playerTick);
		player.getHungerManager().addExhaustion(40.0F);
		ctx.assertTrue(!player.canConsume(false), "at full health, bread can't be eaten");
		ctx.assertTrue(player.canConsume(true), "food with a perk can");
		player.setHealth(6.0F);
		ctx.assertTrue(player.canConsume(false), "hurt, bread can be eaten");

		Meals.ate(player, new ItemStack(Items.MELON_SLICE));
		within(ctx, player.getHealth(), 8.0F, "a melon slice heals 2 at once");
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 200));
		Meals.ate(player, new ItemStack(Items.MELON_SLICE));
		within(ctx, player.getHealth(), 9.0F, "with Hunger, half that");
		player.removeStatusEffect(StatusEffects.HUNGER);
		player.setHealth(6.0F);
		Meals.ate(player, new ItemStack(Items.COOKED_BEEF));
		NbtCompound saved = new NbtCompound();
		player.writeCustomDataToNbt(saved);
		ctx.assertTrue(saved.getList("eternalreturn_meals", 10).size() == 1, "the healing to come is saved");
		ctx.runAtTick(ctx.getTick() + 100, () -> within(ctx, player.getHealth(), 12.0F, "steak: 6 of its 12 after 5 seconds"));
		ctx.runAtTick(ctx.getTick() + 220, () -> {
			within(ctx, player.getHealth(), 18.0F, "steak: all 12 after 10 seconds");
			ctx.assertTrue(player.getHungerManager().getFoodLevel() == 20, "the food bar stays full");
		});
		ctx.runAtTick(ctx.getTick() + 320, () -> {
			within(ctx, player.getHealth(), 18.0F, "no healing by itself on Normal");
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 2, 0));
		});
		ctx.runAtTick(ctx.getTick() + 330, () -> {
			ctx.assertTrue(player.getHealth() > 18.5F, "Saturation heals: " + player.getHealth());
			FishingTests.disconnect(player);
			world.getServer().setDifficulty(difficulty, true);
			ctx.complete();
		});
	}

	/** A slice of cake heals 2 hearts over 3 seconds, and is only eaten when hurt. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 200)
	public void cakeSlicesHeal(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Difficulty difficulty = world.getDifficulty();
		world.getServer().setDifficulty(Difficulty.NORMAL, true);
		ctx.setBlockState(new BlockPos(2, 0, 2), Blocks.STONE);
		ctx.setBlockState(new BlockPos(2, 1, 2), Blocks.CAKE);
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(1, 1, 1));
		ctx.runAtEveryTick(player::playerTick);
		BlockPos cake = ctx.getAbsolutePos(new BlockPos(2, 1, 2));
		BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(cake), Direction.UP, cake, false);
		player.interactionManager.interactBlock(player, world, ItemStack.EMPTY, Hand.MAIN_HAND, hit);
		ctx.assertTrue(world.getBlockState(cake).get(CakeBlock.BITES) == 0, "not eaten at full health");
		player.setHealth(10.0F);
		player.interactionManager.interactBlock(player, world, ItemStack.EMPTY, Hand.MAIN_HAND, hit);
		ctx.assertTrue(world.getBlockState(cake).get(CakeBlock.BITES) == 1, "a slice eaten when hurt");
		ctx.runAtTick(ctx.getTick() + 80, () -> {
			within(ctx, player.getHealth(), 14.0F, "a slice heals 4");
			FishingTests.disconnect(player);
			world.getServer().setDifficulty(difficulty, true);
			ctx.complete();
		});
	}
}
